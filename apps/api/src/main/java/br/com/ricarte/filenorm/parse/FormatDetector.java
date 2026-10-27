package br.com.ricarte.filenorm.parse;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public final class FormatDetector {

    public static final Set<String> FORMATS = Set.of("ofx", "csv", "cnab240", "pdf");

    private static final int SAMPLE_BYTES = 8192;

    private FormatDetector() {
    }

    public static String detect(byte[] content, String filename, String contentType) {
        String lowerName = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String head = new String(content, 0, Math.min(content.length, 512), StandardCharsets.ISO_8859_1)
                .toUpperCase(Locale.ROOT);

        if (isPdf(content)) {
            return "pdf";
        }
        if (lowerName.endsWith(".ofx") || lowerName.endsWith(".qfx") || head.contains("<OFX")
                || head.contains("OFXHEADER") || head.contains("<STMTTRN")) {
            return "ofx";
        }
        if (looksLikeCnab(content) || lowerName.endsWith(".ret") || lowerName.endsWith(".rem")) {
            return "cnab240";
        }
        if (lowerName.endsWith(".pdf") || type.contains("pdf")) {
            return "pdf";
        }
        return "csv";
    }

    public static boolean isPdf(byte[] content) {
        return content.length >= 5 && new String(content, 0, 5, StandardCharsets.ISO_8859_1).equals("%PDF-");
    }

    /** CNAB 240: as primeiras linhas têm exatamente 240 caracteres e começam por um header de arquivo (tipo 0). */
    static boolean looksLikeCnab(byte[] content) {
        String sample = new String(content, 0, Math.min(content.length, SAMPLE_BYTES), StandardCharsets.ISO_8859_1);
        String[] lines = sample.split("\\r?\\n", -1);
        int complete = content.length > SAMPLE_BYTES ? lines.length - 1 : lines.length;
        int checked = 0;
        for (int i = 0; i < complete; i++) {
            String line = lines[i];
            if (line.isEmpty() && i == complete - 1) {
                break;
            }
            if (line.length() != 240) {
                return false;
            }
            if (i == 0 && line.charAt(7) != '0') {
                return false;
            }
            checked++;
        }
        return checked >= 2;
    }
}
