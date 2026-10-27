package br.com.ricarte.filenorm.parse;

import br.com.ricarte.filenorm.config.FilenormProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Extração heurística de extratos em PDF com camada de texto (sem OCR): uma linha com
 * data dd/MM/aaaa e um valor no formato pt-BR vira um evento de confiança baixa.
 * Linhas de saldo e linhas com valor mas sem data completa são reportadas, não descartadas em silêncio.
 */
@Component
public class PdfTextParser {

    private static final Pattern AMOUNT = Pattern.compile(
            "(?<![\\d.,])(-\\s?)?((?:\\d{1,3}(?:\\.\\d{3})+|\\d+),\\d{2})(?![\\d,])(-|\\s?[DC]\\b)?");
    private static final Pattern FULL_DATE = Pattern.compile("(?<!\\d)(\\d{2})[/-](\\d{2})[/-](\\d{4})(?!\\d)");
    private static final Pattern SHORT_DATE = Pattern.compile("(?<![\\d/])\\d{2}/\\d{2}(?![/\\d])");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern BALANCE_LINE = Pattern.compile("\\b(saldo|sdo|total)\\b");
    private static final Pattern SALDO = Pattern.compile("\\b(saldo|sdo)\\b");
    private static final Pattern OPENING_BALANCE = Pattern.compile("\\b(anterior|inicial)\\b");
    private static final Pattern PARTIAL_BALANCE = Pattern.compile("\\b(bloqueado|disponivel|limite|aplicac\\w*|investiment\\w*)\\b");
    private static final int DEFAULT_MAX_PAGES = 100;

    private final int maxPages;

    @Autowired
    public PdfTextParser(FilenormProperties properties) {
        this.maxPages = properties.jobs().maxPdfPages();
    }

    public PdfTextParser() {
        this.maxPages = DEFAULT_MAX_PAGES;
    }

    public ParseResult parse(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            int pages = document.getNumberOfPages();
            if (pages > maxPages) {
                throw new ParseException("pdf_too_many_pages");
            }
            String text = new PDFTextStripper().getText(document).trim();
            if (text.isBlank()) {
                throw new ParseException("pdf_text_unavailable");
            }
            List<ParseIssue> issues = new ArrayList<>();
            List<NormalizedEventData> events = parseLines(text, issues);
            if (events.isEmpty()) {
                throw new ParseException(issues.isEmpty() ? "pdf_no_transactions" : "pdf_no_valid_records");
            }
            return new ParseResult(events, pages, issues, statementBalances(text));
        } catch (IOException ex) {
            throw new ParseException("pdf_read_failed");
        }
    }

    List<NormalizedEventData> parseLines(String text, List<ParseIssue> issues) {
        List<NormalizedEventData> events = new ArrayList<>();
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            String ref = "text line " + (i + 1);
            Matcher amountMatcher = AMOUNT.matcher(line);
            if (!amountMatcher.find()) {
                continue;
            }
            String normalized = Normalizer.normalize(line, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
            if (BALANCE_LINE.matcher(normalized).find()) {
                issues.add(ParseIssue.skipped("pdf_balance_line", ref, null));
                continue;
            }
            Matcher dateMatcher = FULL_DATE.matcher(line);
            if (!dateMatcher.find()) {
                if (SHORT_DATE.matcher(line).find()) {
                    issues.add(ParseIssue.rejected("pdf_date_without_year", ref, null));
                }
                continue;
            }
            LocalDate date = parseDate(dateMatcher.group(1) + "/" + dateMatcher.group(2) + "/" + dateMatcher.group(3));
            if (date == null) {
                issues.add(ParseIssue.rejected("pdf_invalid_date", ref, null));
                continue;
            }
            BigDecimal signedAmount = signed(amountMatcher);
            BigDecimal amount = signedAmount.abs();
            boolean debit = signedAmount.signum() < 0;
            if (amount.signum() == 0) {
                issues.add(ParseIssue.skipped("pdf_zero_amount", ref, null));
                continue;
            }
            events.add(new NormalizedEventData(
                    "pdf-L" + (i + 1),
                    date,
                    amount,
                    "BRL",
                    debit ? "debit" : "credit",
                    null,
                    line,
                    line,
                    new BigDecimal("0.600")
            ));
        }
        return events;
    }

    /**
     * Saldo inicial: primeira linha "saldo anterior/inicial". Saldo final: última linha de saldo
     * que não seja a inicial nem um saldo parcial (bloqueado/disponível/limite).
     */
    StatementBalances statementBalances(String text) {
        BigDecimal opening = null;
        BigDecimal closing = null;
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            String normalized = Normalizer.normalize(line, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
            if (!SALDO.matcher(normalized).find()) {
                continue;
            }
            Matcher amount = AMOUNT.matcher(line);
            BigDecimal value = null;
            while (amount.find()) {
                value = signed(amount);
            }
            if (value == null || PARTIAL_BALANCE.matcher(normalized).find()) {
                continue;
            }
            if (OPENING_BALANCE.matcher(normalized).find()) {
                if (opening == null) {
                    opening = value;
                }
            } else {
                closing = value;
            }
        }
        return new StatementBalances(opening, closing, "pdf_balance_lines");
    }

    private static BigDecimal signed(Matcher amount) {
        BigDecimal value = Amounts.parse(amount.group(2));
        String sign = (amount.group(1) == null ? "" : "-") + (amount.group(3) == null ? "" : amount.group(3).trim());
        return sign.contains("-") || sign.endsWith("D") ? value.negate() : value;
    }

    private static LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw, DATE);
        } catch (DateTimeException ex) {
            return null;
        }
    }
}
