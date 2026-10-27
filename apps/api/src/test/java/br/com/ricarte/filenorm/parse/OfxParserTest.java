package br.com.ricarte.filenorm.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class OfxParserTest {

    private final OfxParser parser = new OfxParser();

    @Test
    void parsesStmtTrnBlocks() {
        String ofx = """
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRN>
                <TRNTYPE>DEBIT
                <DTPOSTED>20250710
                <TRNAMT>-42.50
                <FITID>txn-1
                <NAME>Padaria
                <MEMO>Compra
                </STMTTRN>
                <STMTTRN>
                <TRNTYPE>CREDIT
                <DTPOSTED>20250715
                <TRNAMT>1500.00
                <FITID>txn-2
                <NAME>Salario
                <MEMO>Credito
                </STMTTRN>
                </BANKMSGSRSV1>
                </OFX>
                """;

        ParseResult result = parser.parse(ofx.getBytes(StandardCharsets.UTF_8));

        assertThat(result.events()).hasSize(2);
        assertThat(result.events().get(0).externalId()).isEqualTo("txn-1");
        assertThat(result.events().get(0).direction()).isEqualTo("debit");
        assertThat(result.events().get(0).amount().toPlainString()).isEqualTo("42.50");
        assertThat(result.events().get(1).direction()).isEqualTo("credit");
        assertThat(result.events().get(1).postedAt().toString()).isEqualTo("2025-07-15");
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void goldenSgmlFixture() throws Exception {
        ParseResult result = parser.parse(new ClassPathResource("fixtures/sample.ofx").getContentAsByteArray());

        assertThat(result.events()).extracting(NormalizedEventData::externalId).containsExactly("20250710001", "20250715001");
        assertThat(result.events()).extracting(e -> e.amount().toPlainString()).containsExactly("42.50", "1500.00");
        assertThat(result.events()).extracting(NormalizedEventData::counterparty).containsExactly("Padaria Central", "Empresa XYZ");
    }

    @Test
    void honoursDeclaredCharsetCurrencyAndBrazilianAmounts() {
        String ofx = "OFXHEADER:100\nCHARSET:1252\n<OFX><CURDEF>USD<STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20250710120000[-3:BRT]"
                + "<TRNAMT>1.234,56<FITID>1<MEMO>Pão de Açúcar</STMTTRN></OFX>";

        ParseResult result = parser.parse(ofx.getBytes(TextDecoding.WINDOWS_1252));

        NormalizedEventData event = result.events().get(0);
        assertThat(event.amount().toPlainString()).isEqualTo("1234.56");
        assertThat(event.currency()).isEqualTo("USD");
        assertThat(event.description()).isEqualTo("Pão de Açúcar");
        assertThat(event.postedAt().toString()).isEqualTo("2025-07-10");
    }

    @Test
    void reportsIncompleteAndSuspiciousTransactions() {
        String ofx = "<OFX>"
                + "<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20250710<TRNAMT>10.00<FITID>a</STMTTRN>"
                + "<STMTTRN><TRNTYPE>DEBIT<TRNAMT>-1.00<FITID>b</STMTTRN>"
                + "<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20250710<TRNAMT>-2.00<FITID>a</STMTTRN>"
                + "<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20250710<TRNAMT>-3.00</STMTTRN>"
                + "</OFX>";

        ParseResult result = parser.parse(ofx.getBytes(StandardCharsets.UTF_8));

        assertThat(result.events()).hasSize(3);
        assertThat(result.issues()).extracting(ParseIssue::code).containsExactly(
                "ofx_trntype_sign_mismatch", "ofx_missing_dtposted", "ofx_duplicate_fitid", "ofx_missing_fitid");
        assertThat(result.count(ParseIssue.REJECTED)).isEqualTo(1);
        assertThat(result.events().get(0).confidence()).isEqualByComparingTo("0.700");
    }

    @Test
    void doesNotResolveExternalEntities() {
        String ofx = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY ext SYSTEM \"file:///etc/passwd\">]>"
                + "<OFX><STMTTRN><DTPOSTED>20250710<TRNAMT>-1.00<FITID>x<MEMO>&ext;</STMTTRN></OFX>";

        ParseResult result = parser.parse(ofx.getBytes(StandardCharsets.UTF_8));

        assertThat(result.events().get(0).description()).isEqualTo("&ext;");
    }

    @Test
    void failsWithoutTransactions() {
        assertThatThrownBy(() -> parser.parse("<OFX></OFX>".getBytes(StandardCharsets.UTF_8)))
                .hasMessage("ofx_no_transactions");
    }

    @Test
    void capturesLedgerBalanceButCannotProveClosureWithoutOpeningBalance() {
        String ofx = "<OFX><STMTTRN><DTPOSTED>20250710<TRNAMT>-1.00<FITID>x</STMTTRN>"
                + "<LEDGERBAL><BALAMT>1234.56<DTASOF>20250731</LEDGERBAL></OFX>";

        ParseResult result = parser.parse(ofx.getBytes(StandardCharsets.UTF_8));

        assertThat(result.balances().closing()).isEqualByComparingTo("1234.56");
        BalanceCheck check = BalanceCheck.verify(result.balances(), result.events());
        assertThat(check.status()).isEqualTo(BalanceCheck.UNAVAILABLE);
        assertThat(check.reason()).isEqualTo("opening_balance_missing");
    }
}
