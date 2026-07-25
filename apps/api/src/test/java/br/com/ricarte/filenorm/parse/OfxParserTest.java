package br.com.ricarte.filenorm.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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

        ParseResult result = parser.parse(ofx.getBytes());

        assertThat(result.events()).hasSize(2);
        assertThat(result.events().get(0).externalId()).isEqualTo("txn-1");
        assertThat(result.events().get(0).direction()).isEqualTo("debit");
        assertThat(result.events().get(0).amount().toPlainString()).isEqualTo("42.50");
        assertThat(result.events().get(1).direction()).isEqualTo("credit");
        assertThat(result.events().get(1).postedAt().toString()).isEqualTo("2025-07-15");
    }
}
