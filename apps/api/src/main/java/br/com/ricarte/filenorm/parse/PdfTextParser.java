package br.com.ricarte.filenorm.parse;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfTextParser {

    private static final Pattern AMOUNT = Pattern.compile("(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2})");
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    };

    public ParseResult parse(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document).trim();
            int pages = document.getNumberOfPages();
            if (text.isBlank()) {
                throw new ParseException("pdf_text_unavailable");
            }
            List<NormalizedEventData> events = parseLines(text);
            if (events.isEmpty()) {
                throw new ParseException("pdf_no_transactions");
            }
            return new ParseResult(events, pages);
        } catch (IOException ex) {
            throw new ParseException("pdf_read_failed");
        }
    }

    private List<NormalizedEventData> parseLines(String text) {
        List<NormalizedEventData> events = new ArrayList<>();
        String[] lines = text.split("\\R");
        int ordinal = 0;
        for (String line : lines) {
            Matcher amountMatcher = AMOUNT.matcher(line);
            if (!amountMatcher.find()) {
                continue;
            }
            LocalDate date = findDate(line);
            if (date == null) {
                continue;
            }
            String amountRaw = amountMatcher.group(1);
            BigDecimal signed = parseBrazilianAmount(amountRaw);
            String direction = signed.signum() >= 0 ? "credit" : "debit";
            events.add(new NormalizedEventData(
                    "pdf-" + ordinal,
                    date,
                    signed.abs(),
                    "BRL",
                    direction,
                    null,
                    line.trim(),
                    line.trim(),
                    new BigDecimal("0.600")
            ));
            ordinal++;
        }
        return events;
    }

    private LocalDate findDate(String line) {
        Pattern datePattern = Pattern.compile("\\b(\\d{2}[/-]\\d{2}[/-]\\d{4})\\b");
        Matcher matcher = datePattern.matcher(line);
        if (!matcher.find()) {
            return null;
        }
        String raw = matcher.group(1).replace('-', '/');
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private BigDecimal parseBrazilianAmount(String raw) {
        String normalized = raw.replace(".", "").replace(",", ".");
        return new BigDecimal(normalized);
    }
}
