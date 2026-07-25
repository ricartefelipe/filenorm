package br.com.ricarte.filenorm.parse;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class FormatDetector {

    private FormatDetector() {
    }

    public static String detect(byte[] content, String filename, String contentType) {
        String lowerName = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String head = new String(content, 0, Math.min(content.length, 512), StandardCharsets.UTF_8)
                .toUpperCase(Locale.ROOT);

        if (lowerName.endsWith(".ofx") || lowerName.endsWith(".qfx") || head.contains("<OFX")) {
            return "ofx";
        }
        if (lowerName.endsWith(".csv") || type.contains("csv") || type.contains("text/plain")) {
            if (looksLikeCnab(content)) {
                return "cnab240";
            }
            return "csv";
        }
        if (lowerName.endsWith(".ret") || lowerName.endsWith(".rem") || looksLikeCnab(content)) {
            return "cnab240";
        }
        if (lowerName.endsWith(".pdf") || type.contains("pdf")) {
            return "pdf";
        }
        if (head.contains("STMTTRN") || head.contains("<BANKMSGSRSV1")) {
            return "ofx";
        }
        return "csv";
    }

    private static boolean looksLikeCnab(byte[] content) {
        String sample = new String(content, 0, Math.min(content.length, 480), StandardCharsets.UTF_8);
        String[] lines = sample.split("\\R");
        int longLines = 0;
        for (String line : lines) {
            if (line.length() >= 240) {
                longLines++;
            }
        }
        return longLines >= 2;
    }
}
