package br.com.ricarte.filenorm.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class PdfTextParserTest {

    private final PdfTextParser parser = new PdfTextParser();

    @Test
    void extractsTransactionsFromTextPdf() throws Exception {
        byte[] pdf = pdf(1, List.of(
                "EXTRATO SINTETICO",
                "10/07/2025 SALDO ANTERIOR 1.000,00",
                "10/07/2025 PIX RECEBIDO 1500,00",
                "11/07/2025 COMPRA CARTAO 42,50 D",
                "12/07/2025 TED ENVIADA -2.000,00 8.457,50",
                "13/07 TARIFA -10,00"));

        ParseResult result = parser.parse(pdf);

        assertThat(result.pages()).isEqualTo(1);
        assertThat(result.events()).extracting(e -> e.direction() + " " + e.amount().toPlainString())
                .containsExactly("credit 1500.00", "debit 42.50", "debit 2000.00");
        assertThat(result.events()).allSatisfy(e -> assertThat(e.confidence()).isEqualByComparingTo("0.600"));
        assertThat(result.issues()).extracting(ParseIssue::code).containsExactly("pdf_balance_line", "pdf_date_without_year");
    }

    @Test
    void enforcesPageLimit() throws Exception {
        byte[] pdf = pdf(101, List.of("10/07/2025 PIX 1,00"));

        assertThatThrownBy(() -> parser.parse(pdf)).hasMessage("pdf_too_many_pages");
    }

    @Test
    void rejectsCorruptedPdf() {
        assertThatThrownBy(() -> parser.parse("%PDF-1.7 not really".getBytes())).hasMessage("pdf_read_failed");
    }

    @Test
    void lineHeuristicsDoNotTruncateAmounts() {
        List<ParseIssue> issues = new ArrayList<>();
        List<NormalizedEventData> events = parser.parseLines("01/08/2025 DEPOSITO 12345,67\n02/08/2025 TOTALPASS 9,90-", issues);

        assertThat(events).extracting(e -> e.direction() + " " + e.amount().toPlainString())
                .containsExactly("credit 12345.67", "debit 9.90");
        assertThat(issues).isEmpty();
    }

    private static byte[] pdf(int pages, List<String> lines) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int p = 0; p < pages; p++) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (p > 0) {
                    continue;
                }
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(font, 10);
                    stream.newLineAtOffset(40, 740);
                    for (String line : lines) {
                        stream.showText(line);
                        stream.newLineAtOffset(0, -14);
                    }
                    stream.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void provesClosureWithOpeningAndClosingBalanceLines() throws Exception {
        byte[] pdf = pdf(1, List.of(
                "01/08/2025 SALDO ANTERIOR 1.000,00",
                "02/08/2025 PIX RECEBIDO 1.500,00",
                "03/08/2025 COMPRA CARTAO 42,50 D",
                "SALDO BLOQUEADO 10,00",
                "04/08/2025 SALDO FINAL 2.457,50"));

        ParseResult result = parser.parse(pdf);

        BalanceCheck check = BalanceCheck.verify(result.balances(), result.events());
        assertThat(check.status()).isEqualTo(BalanceCheck.VERIFIED);
        assertThat(check.opening()).isEqualByComparingTo("1000.00");
        assertThat(check.closing()).isEqualByComparingTo("2457.50");
    }

    @Test
    void debitPrintedWithoutSignIsCaughtByTheBalance() throws Exception {
        byte[] pdf = pdf(1, List.of(
                "01/08/2025 SALDO ANTERIOR 1.000,00",
                "02/08/2025 PAGAMENTO FORNECEDOR 200,00",
                "02/08/2025 SALDO DO DIA 800,00"));

        ParseResult result = parser.parse(pdf);

        BalanceCheck check = BalanceCheck.verify(result.balances(), result.events());
        assertThat(check.status()).isEqualTo(BalanceCheck.MISMATCH);
        assertThat(check.difference()).isEqualByComparingTo("-400.00");
    }
}
