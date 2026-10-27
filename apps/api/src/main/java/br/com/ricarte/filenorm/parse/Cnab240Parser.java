package br.com.ricarte.filenorm.parse;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * CNAB 240 FEBRABAN — arquivo de retorno de cobrança (serviço 01), segmentos T e U,
 * nas posições do padrão FEBRABAN (v08.x–v10.x). Leiautes de banco que se desviam do
 * padrão não são suportados. Posições abaixo são 1-based, como no manual FEBRABAN.
 */
@Component
public class Cnab240Parser {

    public static final String SUPPORTED_LAYOUT = "FEBRABAN 240 retorno de cobrança (serviço 01), segmentos T/U";

    private static final int LINE_LENGTH = 240;
    private static final DateTimeFormatter CNAB_DATE = DateTimeFormatter.ofPattern("ddMMuuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    public ParseResult parse(byte[] content) {
        String text = new String(content, StandardCharsets.ISO_8859_1);
        List<String> lines = new ArrayList<>(List.of(text.split("\\r?\\n", -1)));
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
        if (lines.isEmpty()) {
            throw new ParseException("cnab_no_transactions");
        }
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).length() != LINE_LENGTH) {
                throw new ParseException("cnab_invalid_line_length");
            }
        }
        String header = lines.get(0);
        String trailer = lines.get(lines.size() - 1);
        if (recordType(header) != '0') {
            throw new ParseException("cnab_missing_file_header");
        }
        if (recordType(trailer) != '9') {
            throw new ParseException("cnab_truncated");
        }
        if (!"2".equals(field(header, 143, 143))) {
            throw new ParseException("cnab_not_a_return_file");
        }
        String declaredCount = field(trailer, 24, 29);
        if (!declaredCount.chars().allMatch(Character::isDigit) || Integer.parseInt(declaredCount) != lines.size()) {
            throw new ParseException("cnab_record_count_mismatch");
        }
        String bank = field(header, 1, 3);

        List<NormalizedEventData> events = new ArrayList<>();
        List<ParseIssue> issues = new ArrayList<>();
        String lastT = null;
        for (int i = 1; i < lines.size() - 1; i++) {
            String line = lines.get(i);
            String ref = "line " + (i + 1);
            char type = recordType(line);
            if (type == '1') {
                if (!"01".equals(field(line, 10, 11))) {
                    throw new ParseException("cnab_layout_unsupported");
                }
                continue;
            }
            if (type != '3') {
                continue;
            }
            char segment = line.charAt(13);
            if (segment == 'T') {
                lastT = line;
                continue;
            }
            if (segment != 'U') {
                issues.add(ParseIssue.skipped("cnab_segment_ignored", ref, "segmento " + segment));
                continue;
            }
            String t = lastT;
            lastT = null;
            if (t == null || !field(t, 9, 13).matches("\\d+") || !field(line, 9, 13).matches("\\d+")
                    || Integer.parseInt(field(t, 9, 13)) + 1 != Integer.parseInt(field(line, 9, 13))) {
                issues.add(ParseIssue.rejected("cnab_u_without_t", ref, null));
                continue;
            }
            parseU(bank, t, line, ref, events, issues);
        }
        // Retorno só com ocorrências sem pagamento (ex.: entrada confirmada) é válido e gera zero eventos.
        if (events.isEmpty() && issues.stream().anyMatch(issue -> issue.severity().equals(ParseIssue.REJECTED))) {
            throw new ParseException("cnab_no_valid_records");
        }
        return new ParseResult(events, issues);
    }

    private void parseU(String bank, String t, String u, String ref, List<NormalizedEventData> events, List<ParseIssue> issues) {
        String occurrence = field(u, 16, 17);
        String paidRaw = field(u, 78, 92);
        String netRaw = field(u, 93, 107);
        if (!paidRaw.matches("\\d{15}")) {
            issues.add(ParseIssue.rejected("cnab_invalid_amount", ref, null));
            return;
        }
        BigDecimal paid = new BigDecimal(paidRaw).movePointLeft(2);
        if (paid.signum() == 0) {
            issues.add(ParseIssue.skipped("cnab_no_payment", ref, "ocorrência " + occurrence));
            return;
        }
        LocalDate creditDate = parseDate(field(u, 146, 153));
        LocalDate occurrenceDate = parseDate(field(u, 138, 145));
        LocalDate postedAt = creditDate != null ? creditDate : occurrenceDate;
        if (postedAt == null) {
            issues.add(ParseIssue.rejected("cnab_invalid_date", ref, null));
            return;
        }
        String ourNumber = field(t, 38, 57);
        String payer = field(t, 149, 188);
        String description = "Liquidação cobrança ocorrência " + occurrence
                + (netRaw.matches("\\d{15}") ? " (líquido " + new BigDecimal(netRaw).movePointLeft(2).toPlainString() + ")" : "");
        events.add(new NormalizedEventData(
                "cnab-" + bank + "-" + ourNumber + "-" + occurrence,
                postedAt,
                paid,
                "BRL",
                "credit",
                payer.isBlank() ? null : payer,
                description,
                t + "\n" + u,
                creditDate != null ? new BigDecimal("0.900") : new BigDecimal("0.800")
        ));
    }

    private static char recordType(String line) {
        return line.charAt(7);
    }

    private static String field(String line, int startInclusive1, int endInclusive1) {
        return line.substring(startInclusive1 - 1, endInclusive1).trim();
    }

    private static LocalDate parseDate(String raw) {
        if (!raw.matches("\\d{8}") || raw.equals("00000000")) {
            return null;
        }
        try {
            return LocalDate.parse(raw, CNAB_DATE);
        } catch (DateTimeException ex) {
            return null;
        }
    }
}
