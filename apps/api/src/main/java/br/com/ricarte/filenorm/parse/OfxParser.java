package br.com.ricarte.filenorm.parse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class OfxParser {

    private static final Pattern BLOCK = Pattern.compile("<STMTTRN>(.*?)</STMTTRN>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<([A-Z0-9]+)>([^<\\r\\n]*)", Pattern.CASE_INSENSITIVE);

    public ParseResult parse(byte[] content) {
        String text = new String(content);
        text = text.replaceAll("[\\r\\n]+", "\n");
        Matcher matcher = BLOCK.matcher(text);
        List<NormalizedEventData> events = new ArrayList<>();
        int ordinal = 0;
        while (matcher.find()) {
            String block = matcher.group(1);
            var tags = extractTags(block);
            String fitId = tags.getOrDefault("FITID", "ofx-" + ordinal);
            String trnType = tags.getOrDefault("TRNTYPE", "OTHER");
            String dtPosted = tags.get("DTPOSTED");
            String trnAmt = tags.get("TRNAMT");
            if (dtPosted == null || trnAmt == null) {
                continue;
            }
            BigDecimal amount = new BigDecimal(trnAmt.trim().replace(",", "."));
            String direction = amount.signum() >= 0 ? "credit" : "debit";
            String name = tags.getOrDefault("NAME", tags.getOrDefault("MEMO", ""));
            String memo = tags.getOrDefault("MEMO", name);
            LocalDate postedAt = parseOfxDate(dtPosted);
            events.add(new NormalizedEventData(
                    fitId,
                    postedAt,
                    amount.abs(),
                    "BRL",
                    direction,
                    name.isBlank() ? null : name,
                    memo.isBlank() ? name : memo,
                    block.trim(),
                    new BigDecimal("0.950")
            ));
            ordinal++;
        }
        if (events.isEmpty()) {
            throw new ParseException("ofx_no_transactions");
        }
        return new ParseResult(events);
    }

    private static LocalDate parseOfxDate(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() >= 8) {
            return LocalDate.parse(digits.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
        }
        throw new ParseException("ofx_invalid_date");
    }

    private static java.util.Map<String, String> extractTags(String block) {
        java.util.Map<String, String> tags = new java.util.LinkedHashMap<>();
        Matcher tagMatcher = TAG.matcher(block);
        while (tagMatcher.find()) {
            tags.put(tagMatcher.group(1).toUpperCase(Locale.ROOT), tagMatcher.group(2).trim());
        }
        return tags;
    }

    static BigDecimal normalizeAmount(String raw) {
        return new BigDecimal(raw.trim().replace(",", ".")).setScale(2, RoundingMode.HALF_UP);
    }
}
