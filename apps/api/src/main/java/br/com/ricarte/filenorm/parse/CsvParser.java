package br.com.ricarte.filenorm.parse;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CsvParser {

    public static final Set<String> PRESETS = Set.of("generic", "nubank", "inter");

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            strict("dd/MM/uuuu"),
            strict("dd-MM-uuuu"),
            strict("uuuu/MM/dd")
    };
    private static final List<String> BALANCE_HEADERS = List.of("saldo", "balance");
    private static final int MAX_DETAIL = 200;

    public ParseResult parse(byte[] content, String preset) {
        String text = TextDecoding.decode(content);
        List<CsvRecord> records = CsvRecord.read(text, detectDelimiter(text));
        if (records.size() < 2) {
            throw new ParseException("csv_empty");
        }
        String[] headers = records.get(0).fields();
        PresetMapping mapping = mappingFor(preset, headers);
        int balanceIndex = balanceColumn(headers);
        List<NormalizedEventData> events = new ArrayList<>();
        List<ParseIssue> issues = new ArrayList<>();
        List<BalanceRow> rows = new ArrayList<>();
        for (CsvRecord record : records.subList(1, records.size())) {
            String ref = "line " + record.line();
            if (record.isBlank()) {
                continue;
            }
            String[] cols = record.fields();
            String dateRaw = col(cols, mapping.dateIndex);
            String amountRaw = col(cols, mapping.amountIndex);
            String description = col(cols, mapping.descriptionIndex);
            if (dateRaw.isBlank() && amountRaw.isBlank()) {
                issues.add(ParseIssue.skipped("csv_row_without_transaction", ref, abbreviate(record.raw())));
                continue;
            }
            BigDecimal balance = parseBalance(col(cols, balanceIndex));
            if (amountRaw.isBlank() && balance != null) {
                // Linha só de saldo (ex.: "SALDO ANTERIOR"): ancora a prova de fechamento, não é movimento.
                issues.add(ParseIssue.skipped("csv_balance_only_row", ref, null));
                rows.add(new BalanceRow(ref, parseDate(dateRaw), BigDecimal.ZERO, balance));
                continue;
            }
            LocalDate postedAt = parseDate(dateRaw);
            if (postedAt == null) {
                issues.add(ParseIssue.rejected("csv_invalid_date", ref, abbreviate(dateRaw)));
                rows.add(new BalanceRow(ref, null, null, balance));
                continue;
            }
            BigDecimal signed;
            try {
                signed = Amounts.parse(amountRaw);
            } catch (Amounts.AmountFormatException | NumberFormatException ex) {
                String code = ex instanceof Amounts.AmountFormatException afe ? "csv_" + afe.getCode() : "csv_amount_invalid";
                issues.add(ParseIssue.rejected(code, ref, abbreviate(amountRaw)));
                rows.add(new BalanceRow(ref, postedAt, null, balance));
                continue;
            }
            rows.add(new BalanceRow(ref, postedAt, signed, balance));
            if (signed.signum() == 0) {
                issues.add(ParseIssue.skipped("csv_zero_amount", ref, null));
                continue;
            }
            events.add(new NormalizedEventData(
                    "csv-L" + record.line(),
                    postedAt,
                    signed.abs(),
                    "BRL",
                    signed.signum() > 0 ? "credit" : "debit",
                    null,
                    description,
                    record.raw(),
                    mapping.confidence
            ));
        }
        if (events.isEmpty()) {
            throw new ParseException(issues.isEmpty() ? "csv_no_transactions" : "csv_no_valid_records");
        }
        StatementBalances balances = balanceIndex < 0 ? null : runningBalances(rows, issues);
        return new ParseResult(events, issues, balances);
    }

    /**
     * Coluna de saldo: confere a sequência linha a linha (saldo anterior + valor = saldo informado)
     * e aponta a linha onde ela quebra. O saldo inicial é deduzido da primeira linha com saldo;
     * o final é o da última. Aceita extratos em ordem crescente ou decrescente de data.
     */
    private StatementBalances runningBalances(List<BalanceRow> rows, List<ParseIssue> issues) {
        List<BalanceRow> ascending = new ArrayList<>(rows);
        List<BalanceRow> descending = new ArrayList<>(rows);
        java.util.Collections.reverse(descending);
        LocalDate first = rows.stream().map(BalanceRow::date).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        LocalDate last = descending.stream().map(BalanceRow::date).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        List<BalanceRow> chronological;
        if (first != null && last != null && first.isAfter(last)) {
            chronological = descending;
        } else if (first != null && first.equals(last)) {
            chronological = breaks(descending, null) < breaks(ascending, null) ? descending : ascending;
        } else {
            chronological = ascending;
        }
        BigDecimal opening = null;
        BigDecimal pending = BigDecimal.ZERO;
        BigDecimal closing = null;
        boolean trailingWithoutBalance = false;
        for (BalanceRow row : chronological) {
            if (opening == null) {
                if (row.signed() == null) {
                    return StatementBalances.unavailable("csv_running_balance");
                }
                pending = pending.add(row.signed());
                if (row.balance() != null) {
                    opening = row.balance().subtract(pending);
                    closing = row.balance();
                }
                continue;
            }
            trailingWithoutBalance = row.balance() == null;
            if (row.balance() != null) {
                closing = row.balance();
            }
        }
        if (opening == null) {
            return StatementBalances.unavailable("csv_running_balance");
        }
        breaks(chronological, issues);
        return new StatementBalances(opening, trailingWithoutBalance ? null : closing, "csv_running_balance");
    }

    /** Conta (e opcionalmente reporta) as linhas em que o saldo informado não bate com o acumulado. */
    private int breaks(List<BalanceRow> rows, List<ParseIssue> issues) {
        BigDecimal running = null;
        boolean unknown = false;
        int count = 0;
        for (BalanceRow row : rows) {
            if (row.signed() == null) {
                unknown = true;
            } else if (running != null) {
                running = running.add(row.signed());
            }
            if (row.balance() == null) {
                continue;
            }
            if (running != null && !unknown && running.compareTo(row.balance()) != 0) {
                count++;
                if (issues != null) {
                    issues.add(ParseIssue.warning("csv_balance_break", row.ref(),
                            "saldo esperado " + running.toPlainString() + ", informado " + row.balance().toPlainString()));
                }
            }
            running = row.balance();
            unknown = false;
        }
        return count;
    }

    private int balanceColumn(String[] headers) {
        for (int i = 0; i < headers.length; i++) {
            String header = normalizeHeader(headers[i]);
            if (BALANCE_HEADERS.stream().anyMatch(header::contains)) {
                return i;
            }
        }
        return -1;
    }

    private static BigDecimal parseBalance(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Amounts.parse(raw);
        } catch (Amounts.AmountFormatException | NumberFormatException ex) {
            return null;
        }
    }

    private record BalanceRow(String ref, LocalDate date, BigDecimal signed, BigDecimal balance) {
    }

    private PresetMapping mappingFor(String preset, String[] headers) {
        String normalized = preset == null || preset.isBlank() ? "generic" : preset.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "nubank" -> findColumns(headers, List.of("data", "date"), List.of("valor", "amount"), List.of("descricao", "description"), new BigDecimal("0.900"));
            case "inter" -> findColumns(headers, List.of("data lancamento", "data", "date"), List.of("valor", "amount"), List.of("historico", "descricao"), new BigDecimal("0.900"));
            case "generic" -> findColumns(headers, List.of("date", "data"), List.of("amount", "valor"), List.of("description", "descricao", "historico", "memo"), new BigDecimal("0.800"));
            default -> throw new ParseException("csv_unknown_preset");
        };
    }

    private PresetMapping findColumns(String[] headers, List<String> dateNames, List<String> amountNames, List<String> descNames, BigDecimal confidence) {
        int dateIdx = indexOf(headers, dateNames);
        int amountIdx = indexOf(headers, amountNames);
        int descIdx = indexOf(headers, descNames);
        if (dateIdx < 0 || amountIdx < 0) {
            throw new ParseException("csv_columns_not_found");
        }
        return new PresetMapping(dateIdx, amountIdx, descIdx, confidence);
    }

    /** Casamento exato tem prioridade; depois prefixo/contém, ignorando colunas de saldo. */
    private int indexOf(String[] headers, List<String> candidates) {
        for (String candidate : candidates) {
            for (int i = 0; i < headers.length; i++) {
                if (normalizeHeader(headers[i]).equals(candidate)) {
                    return i;
                }
            }
        }
        for (String candidate : candidates) {
            for (int i = 0; i < headers.length; i++) {
                String header = normalizeHeader(headers[i]);
                if (header.contains(candidate) && BALANCE_HEADERS.stream().noneMatch(header::contains)) {
                    return i;
                }
            }
        }
        return -1;
    }

    static String normalizeHeader(String header) {
        String stripped = Normalizer.normalize(header.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return stripped.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** Escolhe o delimitador mais frequente fora de aspas na primeira linha. */
    static char detectDelimiter(String text) {
        int end = text.indexOf('\n');
        String header = end < 0 ? text : text.substring(0, end);
        char best = ',';
        int bestCount = 0;
        for (char candidate : new char[]{';', ',', '\t', '|'}) {
            int count = 0;
            boolean quoted = false;
            for (char c : header.toCharArray()) {
                if (c == '"') {
                    quoted = !quoted;
                } else if (c == candidate && !quoted) {
                    count++;
                }
            }
            if (count > bestCount) {
                best = candidate;
                bestCount = count;
            }
        }
        return best;
    }

    private String col(String[] cols, int index) {
        if (index < 0 || index >= cols.length) {
            return "";
        }
        return cols[index].trim();
    }

    private LocalDate parseDate(String raw) {
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw.trim(), formatter);
            } catch (DateTimeParseException ignored) {
                // tenta o próximo formato
            }
        }
        return null;
    }

    /** STRICT: 31/02 é data inválida, não 28/02. */
    private static DateTimeFormatter strict(String pattern) {
        return DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_DETAIL ? value : value.substring(0, MAX_DETAIL);
    }

    private record PresetMapping(int dateIndex, int amountIndex, int descriptionIndex, BigDecimal confidence) {
    }

    /** Leitor RFC 4180: aspas, aspas escapadas e quebras de linha dentro de campos. */
    record CsvRecord(int line, String[] fields, String raw) {

        boolean isBlank() {
            for (String field : fields) {
                if (!field.isBlank()) {
                    return false;
                }
            }
            return true;
        }

        static List<CsvRecord> read(String text, char delimiter) {
            List<CsvRecord> records = new ArrayList<>();
            List<String> fields = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            StringBuilder raw = new StringBuilder();
            boolean quoted = false;
            int line = 1;
            int recordLine = 1;
            int i = 0;
            while (i < text.length()) {
                char c = text.charAt(i);
                if (quoted) {
                    if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        raw.append("\"\"");
                        i += 2;
                        continue;
                    }
                    if (c == '"') {
                        quoted = false;
                    } else {
                        if (c == '\n') {
                            line++;
                        }
                        field.append(c);
                    }
                    raw.append(c);
                    i++;
                    continue;
                }
                if (c == '"' && field.isEmpty()) {
                    quoted = true;
                    raw.append(c);
                } else if (c == delimiter) {
                    fields.add(field.toString());
                    field.setLength(0);
                    raw.append(c);
                } else if (c == '\r' || c == '\n') {
                    if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                        i++;
                    }
                    fields.add(field.toString());
                    records.add(new CsvRecord(recordLine, fields.toArray(String[]::new), raw.toString()));
                    fields.clear();
                    field.setLength(0);
                    raw.setLength(0);
                    line++;
                    recordLine = line;
                } else {
                    field.append(c);
                    raw.append(c);
                }
                i++;
            }
            if (!field.isEmpty() || !fields.isEmpty()) {
                fields.add(field.toString());
                records.add(new CsvRecord(recordLine, fields.toArray(String[]::new), raw.toString()));
            }
            return records;
        }
    }
}
