package br.com.ricarte.filenorm.parse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class CsvParser {

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    };

    public ParseResult parse(byte[] content, String preset) {
        String text = new String(content);
        String[] lines = text.split("\\R");
        if (lines.length < 2) {
            throw new ParseException("csv_empty");
        }
        char delimiter = detectDelimiter(lines[0]);
        String[] headers = split(lines[0], delimiter);
        PresetMapping mapping = mappingFor(preset, headers);
        List<NormalizedEventData> events = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] cols = split(line, delimiter);
            String dateRaw = col(cols, mapping.dateIndex);
            String amountRaw = col(cols, mapping.amountIndex);
            String description = col(cols, mapping.descriptionIndex);
            if (dateRaw.isBlank() || amountRaw.isBlank()) {
                continue;
            }
            LocalDate postedAt = parseDate(dateRaw);
            BigDecimal signed = parseAmount(amountRaw);
            String direction = signed.signum() >= 0 ? "credit" : "debit";
            events.add(new NormalizedEventData(
                    "csv-" + i,
                    postedAt,
                    signed.abs(),
                    "BRL",
                    direction,
                    null,
                    description,
                    line,
                    mapping.confidence
            ));
        }
        if (events.isEmpty()) {
            throw new ParseException("csv_no_transactions");
        }
        return new ParseResult(events);
    }

    private PresetMapping mappingFor(String preset, String[] headers) {
        String normalized = preset == null || preset.isBlank() ? "generic" : preset.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "nubank" -> findColumns(headers, List.of("data", "date"), List.of("valor", "amount"), List.of("descrição", "descricao", "description"), new BigDecimal("0.900"));
            case "inter" -> findColumns(headers, List.of("data lancamento", "data", "date"), List.of("valor", "amount"), List.of("historico", "descrição", "descricao"), new BigDecimal("0.900"));
            default -> findColumns(headers, List.of("date", "data"), List.of("amount", "valor"), List.of("description", "descricao", "descrição", "memo"), new BigDecimal("0.800"));
        };
    }

    private PresetMapping findColumns(String[] headers, List<String> dateNames, List<String> amountNames, List<String> descNames, BigDecimal confidence) {
        int dateIdx = indexOf(headers, dateNames);
        int amountIdx = indexOf(headers, amountNames);
        int descIdx = indexOf(headers, descNames);
        if (dateIdx < 0 || amountIdx < 0) {
            throw new ParseException("csv_columns_not_found");
        }
        if (descIdx < 0) {
            descIdx = Math.max(dateIdx, amountIdx) + 1;
            if (descIdx >= headers.length) {
                descIdx = headers.length - 1;
            }
        }
        return new PresetMapping(dateIdx, amountIdx, descIdx, confidence);
    }

    private int indexOf(String[] headers, List<String> candidates) {
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].trim().toLowerCase(Locale.ROOT);
            for (String candidate : candidates) {
                if (header.equals(candidate) || header.contains(candidate)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private char detectDelimiter(String headerLine) {
        if (headerLine.contains(";")) {
            return ';';
        }
        return ',';
    }

    private String[] split(String line, char delimiter) {
        return line.split(java.util.regex.Pattern.quote(String.valueOf(delimiter)), -1);
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
            }
        }
        throw new ParseException("csv_invalid_date");
    }

    private BigDecimal parseAmount(String raw) {
        String normalized = raw.trim()
                .replace("R$", "")
                .replace(" ", "")
                .replace(".", "")
                .replace(",", ".");
        return new BigDecimal(normalized);
    }

    private record PresetMapping(int dateIndex, int amountIndex, int descriptionIndex, BigDecimal confidence) {
    }
}
