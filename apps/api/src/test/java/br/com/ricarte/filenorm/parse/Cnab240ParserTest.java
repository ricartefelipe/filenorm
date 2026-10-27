package br.com.ricarte.filenorm.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class Cnab240ParserTest {

    private final Cnab240Parser parser = new Cnab240Parser();

    @Test
    void goldenReturnFileWithPaidAndUnpaidOccurrences() throws Exception {
        ParseResult result = parser.parse(fixture());

        assertThat(result.events()).hasSize(2);
        NormalizedEventData first = result.events().get(0);
        assertThat(first.amount().toPlainString()).isEqualTo("150.00");
        assertThat(first.direction()).isEqualTo("credit");
        assertThat(first.postedAt().toString()).isEqualTo("2025-07-11");
        assertThat(first.counterparty()).isEqualTo("CLIENTE SINTETICO UM");
        assertThat(first.externalId()).isEqualTo("cnab-341-00000000000000000101-06");
        assertThat(first.description()).contains("líquido 148.00");

        NormalizedEventData second = result.events().get(1);
        assertThat(second.amount().toPlainString()).isEqualTo("1234.56");
        assertThat(second.postedAt().toString()).isEqualTo("2025-07-12");
        assertThat(second.confidence()).isEqualByComparingTo("0.800");

        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(ParseIssue.SKIPPED);
            assertThat(issue.code()).isEqualTo("cnab_no_payment");
            assertThat(issue.recordRef()).isEqualTo("line 6");
        });
        assertThat(result.events().stream().map(NormalizedEventData::amount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
                .isEqualByComparingTo("1384.56");
    }

    @Test
    void rejectsTruncatedFile() throws Exception {
        List<String> lines = lines();
        lines.remove(lines.size() - 1);

        assertThatThrownBy(() -> parser.parse(join(lines))).hasMessage("cnab_truncated");
    }

    @Test
    void rejectsRecordCountMismatch() throws Exception {
        List<String> lines = lines();
        lines.remove(3);
        lines.remove(2);

        assertThatThrownBy(() -> parser.parse(join(lines))).hasMessage("cnab_record_count_mismatch");
    }

    @Test
    void rejectsShortLines() throws Exception {
        List<String> lines = lines();
        lines.set(3, lines.get(3).substring(0, 200));

        assertThatThrownBy(() -> parser.parse(join(lines))).hasMessage("cnab_invalid_line_length");
    }

    @Test
    void rejectsUnsupportedServiceAndRemittance() throws Exception {
        List<String> extrato = lines();
        extrato.set(1, replace(extrato.get(1), 10, "04"));
        assertThatThrownBy(() -> parser.parse(join(extrato))).hasMessage("cnab_layout_unsupported");

        List<String> remessa = lines();
        remessa.set(0, replace(remessa.get(0), 143, "1"));
        assertThatThrownBy(() -> parser.parse(join(remessa))).hasMessage("cnab_not_a_return_file");
    }

    @Test
    void reportsInvalidDateInsteadOfDroppingIt() throws Exception {
        List<String> lines = lines();
        lines.set(7, replace(lines.get(7), 138, "00000000"));

        ParseResult result = parser.parse(join(lines));

        assertThat(result.events()).hasSize(1);
        assertThat(result.issues()).extracting(issue -> issue.code() + "@" + issue.recordRef())
                .containsExactly("cnab_no_payment@line 6", "cnab_invalid_date@line 8");
    }

    @Test
    void reportsUSegmentWithoutMatchingT() throws Exception {
        List<String> lines = lines();
        lines.set(3, replace(lines.get(3), 9, "00009"));

        ParseResult result = parser.parse(join(lines));

        assertThat(result.events()).extracting(e -> e.amount().toPlainString()).containsExactly("1234.56");
        assertThat(result.issues()).extracting(ParseIssue::code).containsExactly("cnab_u_without_t", "cnab_no_payment");
    }

    @Test
    void failsWhenEveryPaymentIsInvalid() throws Exception {
        List<String> lines = lines();
        lines.set(3, replace(lines.get(3), 9, "00009"));
        lines.set(7, replace(lines.get(7), 138, "00000000"));

        assertThatThrownBy(() -> parser.parse(join(lines))).hasMessage("cnab_no_valid_records");
    }

    @Test
    void unknownRecordTypeIsReportedNotDropped() throws Exception {
        List<String> lines = lines();
        lines.add(8, replace(lines.get(7), 8, "7"));
        lines.set(9, replace(lines.get(9), 18, "000009"));
        lines.set(lines.size() - 1, replace(lines.get(lines.size() - 1), 24, "000011"));

        ParseResult result = parser.parse(join(lines));

        assertThat(result.issues()).extracting(issue -> issue.code() + "@" + issue.recordRef())
                .contains("cnab_unknown_record@line 9");
        assertThat(result.count(ParseIssue.REJECTED)).isEqualTo(1);
    }

    @Test
    void lotTrailerCountMustMatch() throws Exception {
        List<String> lines = lines();
        lines.set(8, replace(lines.get(8), 18, "000007"));

        assertThatThrownBy(() -> parser.parse(join(lines))).hasMessage("cnab_lot_count_mismatch");
    }

    @Test
    void foreignCurrencyTitleIsRejected() throws Exception {
        List<String> lines = lines();
        lines.set(2, replace(lines.get(2), 131, "10"));

        ParseResult result = parser.parse(join(lines));

        assertThat(result.events()).extracting(e -> e.amount().toPlainString()).containsExactly("1234.56");
        assertThat(result.issues()).extracting(ParseIssue::code).contains("cnab_unsupported_currency");
    }

    @Test
    void headerMustIdentifyBankAndLayoutVersion() throws Exception {
        List<String> noVersion = lines();
        noVersion.set(0, replace(noVersion.get(0), 164, "   "));
        assertThatThrownBy(() -> parser.parse(join(noVersion))).hasMessage("cnab_layout_version_missing");

        List<String> noBank = lines();
        noBank.set(0, replace(noBank.get(0), 1, "ABC"));
        assertThatThrownBy(() -> parser.parse(join(noBank))).hasMessage("cnab_invalid_bank_code");
    }

    @Test
    void detectsCnabByContentWithoutExtension() throws Exception {
        assertThat(FormatDetector.detect(fixture(), "retorno.txt", "text/plain")).isEqualTo("cnab240");
        assertThat(FormatDetector.detect(fixture(), "upload.bin", "application/octet-stream")).isEqualTo("cnab240");
    }

    private static byte[] fixture() throws Exception {
        return new ClassPathResource("fixtures/cnab240-retorno-cobranca.ret").getContentAsByteArray();
    }

    private static List<String> lines() throws Exception {
        String text = new String(fixture(), StandardCharsets.ISO_8859_1);
        return new ArrayList<>(List.of(text.split("\r\n")));
    }

    private static byte[] join(List<String> lines) {
        return (String.join("\r\n", lines) + "\r\n").getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String replace(String line, int start1, String value) {
        return line.substring(0, start1 - 1) + value + line.substring(start1 - 1 + value.length());
    }
}
