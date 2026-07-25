package br.com.ricarte.filenorm.parse;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Cnab240Parser {

    private static final DateTimeFormatter CNAB_DATE = DateTimeFormatter.ofPattern("ddMMyyyy");

    public ParseResult parse(byte[] content) {
        String text = new String(content, Charset.forName("ISO-8859-1"));
        String[] lines = text.split("\\R");
        List<NormalizedEventData> events = new ArrayList<>();
        int ordinal = 0;
        for (String line : lines) {
            if (line.length() < 240) {
                continue;
            }
            char segment = line.charAt(13);
            if (segment != 'U' && segment != 'T') {
                continue;
            }
            if (segment == 'U') {
                NormalizedEventData event = parseSegmentU(line, ordinal++);
                if (event != null) {
                    events.add(event);
                }
            }
        }
        if (events.isEmpty()) {
            throw new ParseException("cnab_no_transactions");
        }
        return new ParseResult(events);
    }

    private NormalizedEventData parseSegmentU(String line, int ordinal) {
        String amountRaw = safe(line, 77, 92);
        String dateRaw = safe(line, 137, 145);
        if (amountRaw.isBlank() || dateRaw.isBlank()) {
            return null;
        }
        BigDecimal amount = new BigDecimal(amountRaw).movePointLeft(2);
        LocalDate postedAt = LocalDate.parse(dateRaw, CNAB_DATE);
        String direction = amount.signum() >= 0 ? "credit" : "debit";
        return new NormalizedEventData(
                "cnab-u-" + ordinal,
                postedAt,
                amount.abs(),
                "BRL",
                direction,
                null,
                "CNAB segment U",
                line,
                new BigDecimal("0.850")
        );
    }

    private String safe(String line, int startInclusive, int endExclusive) {
        if (startInclusive >= line.length()) {
            return "";
        }
        int end = Math.min(endExclusive, line.length());
        return line.substring(startInclusive, end).trim();
    }
}
