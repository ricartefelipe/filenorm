package br.com.ricarte.filenorm.parse;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * OFX 1.x (SGML) e 2.x (XML) lidos por expressão regular: não há parser XML,
 * portanto não existe resolução de entidades externas (XXE) nem acesso à rede.
 */
@Component
public class OfxParser {

    private static final Pattern BLOCK = Pattern.compile("<STMTTRN>(.*?)</STMTTRN>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<([A-Z0-9.]+)>([^<\\r\\n]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEDGER_BALANCE = Pattern.compile("<LEDGERBAL>.*?<BALAMT>\\s*([^<\\r\\n]+)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern CURDEF = Pattern.compile("<CURDEF>\\s*([A-Z]{3})", Pattern.CASE_INSENSITIVE);
    private static final Pattern SGML_CHARSET = Pattern.compile("^CHARSET:\\s*(\\S+)", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Pattern XML_ENCODING = Pattern.compile("<\\?xml[^>]*encoding=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Set<String> DEBIT_TYPES = Set.of("DEBIT", "PAYMENT", "CHECK", "FEE", "SRVCHG", "ATM", "POS", "CASH");
    private static final Set<String> CREDIT_TYPES = Set.of("CREDIT", "DEP", "INT", "DIV", "DIRECTDEP");

    public ParseResult parse(byte[] content) {
        String text = TextDecoding.decode(content, declaredCharset(content));
        String currency = currencyOf(text);
        Matcher matcher = BLOCK.matcher(text);
        List<NormalizedEventData> events = new ArrayList<>();
        List<ParseIssue> issues = new ArrayList<>();
        Set<String> seenFitIds = new HashSet<>();
        int ordinal = 0;
        while (matcher.find()) {
            ordinal++;
            String ref = "STMTTRN #" + ordinal;
            String block = matcher.group(1);
            Map<String, String> tags = extractTags(block);
            String dtPosted = tags.get("DTPOSTED");
            String trnAmt = tags.get("TRNAMT");
            if (dtPosted == null || dtPosted.isBlank()) {
                issues.add(ParseIssue.rejected("ofx_missing_dtposted", ref, null));
                continue;
            }
            if (trnAmt == null || trnAmt.isBlank()) {
                issues.add(ParseIssue.rejected("ofx_missing_trnamt", ref, null));
                continue;
            }
            LocalDate postedAt = parseOfxDate(dtPosted);
            if (postedAt == null) {
                issues.add(ParseIssue.rejected("ofx_invalid_date", ref, dtPosted));
                continue;
            }
            BigDecimal amount;
            try {
                amount = Amounts.parse(trnAmt);
            } catch (Amounts.AmountFormatException | NumberFormatException ex) {
                issues.add(ParseIssue.rejected("ofx_invalid_amount", ref, trnAmt));
                continue;
            }
            if (amount.signum() == 0) {
                issues.add(ParseIssue.skipped("ofx_zero_amount", ref, null));
                continue;
            }
            String fitId = tags.get("FITID");
            BigDecimal confidence = new BigDecimal("0.950");
            if (fitId == null || fitId.isBlank()) {
                fitId = "ofx-" + ordinal;
                confidence = new BigDecimal("0.850");
                issues.add(ParseIssue.warning("ofx_missing_fitid", ref, null));
            } else if (!seenFitIds.add(fitId)) {
                issues.add(ParseIssue.warning("ofx_duplicate_fitid", ref, fitId));
            }
            String direction = amount.signum() > 0 ? "credit" : "debit";
            String trnType = tags.getOrDefault("TRNTYPE", "OTHER").toUpperCase(Locale.ROOT);
            if ("credit".equals(direction) && DEBIT_TYPES.contains(trnType)
                    || "debit".equals(direction) && CREDIT_TYPES.contains(trnType)) {
                confidence = new BigDecimal("0.700");
                issues.add(ParseIssue.warning("ofx_trntype_sign_mismatch", ref, trnType));
            }
            String name = tags.getOrDefault("NAME", "");
            String memo = tags.getOrDefault("MEMO", "");
            events.add(new NormalizedEventData(
                    fitId,
                    postedAt,
                    amount.abs(),
                    currency,
                    direction,
                    name.isBlank() ? null : name,
                    memo.isBlank() ? name : memo,
                    block.trim(),
                    confidence
            ));
        }
        if (events.isEmpty()) {
            throw new ParseException(issues.isEmpty() ? "ofx_no_transactions" : "ofx_no_valid_records");
        }
        // OFX declara só o saldo final (LEDGERBAL); sem saldo inicial a prova de fechamento fica indisponível.
        return new ParseResult(events, issues, new StatementBalances(null, ledgerBalance(text), "ofx_ledgerbal"));
    }

    private static BigDecimal ledgerBalance(String text) {
        Matcher matcher = LEDGER_BALANCE.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Amounts.parse(matcher.group(1));
        } catch (Amounts.AmountFormatException | NumberFormatException ex) {
            return null;
        }
    }

    static Charset declaredCharset(byte[] content) {
        String head = new String(content, 0, Math.min(content.length, 1024), StandardCharsets.ISO_8859_1);
        Matcher xml = XML_ENCODING.matcher(head);
        String name = xml.find() ? xml.group(1) : null;
        if (name == null) {
            Matcher sgml = SGML_CHARSET.matcher(head);
            name = sgml.find() ? sgml.group(1) : null;
        }
        if (name == null || name.equalsIgnoreCase("NONE")) {
            return null;
        }
        if (name.equals("1252")) {
            return TextDecoding.WINDOWS_1252;
        }
        try {
            return Charset.forName(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String currencyOf(String text) {
        Matcher matcher = CURDEF.matcher(text);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : "BRL";
    }

    private static LocalDate parseOfxDate(String raw) {
        String digits = raw.trim();
        if (digits.length() < 8 || !digits.substring(0, 8).chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            return LocalDate.parse(digits.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeException ex) {
            return null;
        }
    }

    private static Map<String, String> extractTags(String block) {
        Map<String, String> tags = new LinkedHashMap<>();
        Matcher tagMatcher = TAG.matcher(block);
        while (tagMatcher.find()) {
            tags.putIfAbsent(tagMatcher.group(1).toUpperCase(Locale.ROOT), tagMatcher.group(2).trim());
        }
        return tags;
    }
}
