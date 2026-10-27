package br.com.ricarte.filenorm.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FormatDetectorTest {

    @Test
    void detectsByContentBeforeExtension() {
        assertThat(FormatDetector.detect("%PDF-1.7".getBytes(), "extrato.csv", "text/csv")).isEqualTo("pdf");
        assertThat(FormatDetector.detect("OFXHEADER:100\n<OFX>".getBytes(), "x.txt", "text/plain")).isEqualTo("ofx");
        assertThat(FormatDetector.detect("date,amount\n".getBytes(), "x.csv", "text/csv")).isEqualTo("csv");
    }

    @Test
    void shortOrIrregularLinesAreNotCnab() {
        String lines = "0".repeat(240) + "\n" + "1".repeat(239) + "\n";
        assertThat(FormatDetector.looksLikeCnab(lines.getBytes(StandardCharsets.ISO_8859_1))).isFalse();
    }
}
