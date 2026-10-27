package br.com.ricarte.filenorm.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CsvParserTest {

    private final CsvParser parser = new CsvParser();

    @Test
    void parsesGenericPresetWithDotDecimals() {
        String csv = """
                date,amount,description
                2025-07-10,-42.50,Padaria
                2025-07-15,1500.00,Salario
                """;

        ParseResult result = parser.parse(csv.getBytes(StandardCharsets.UTF_8), "generic");

        assertThat(result.events()).hasSize(2);
        assertThat(result.events().get(0).direction()).isEqualTo("debit");
        assertThat(result.events().get(0).amount().toPlainString()).isEqualTo("42.50");
        assertThat(result.events().get(1).direction()).isEqualTo("credit");
        assertThat(result.events().get(1).amount().toPlainString()).isEqualTo("1500.00");
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void parsesBrazilianDecimals() {
        String csv = """
                Data;Valor;Descrição
                10/07/2025;-42,50;Compra mercado
                15/07/2025;1.500,00;Transferencia recebida
                """;

        ParseResult result = parser.parse(csv.getBytes(StandardCharsets.UTF_8), "nubank");

        assertThat(result.events()).extracting(e -> e.amount().toPlainString()).containsExactly("42.50", "1500.00");
        assertThat(result.events().get(0).postedAt().toString()).isEqualTo("2025-07-10");
    }

    @Test
    void goldenNubankFixtureInWindows1252WithQuotesAndInvalidRow() throws Exception {
        byte[] content = new ClassPathResource("fixtures/nubank-conta.csv").getContentAsByteArray();

        ParseResult result = parser.parse(content, "nubank");

        assertThat(result.events()).hasSize(3);
        NormalizedEventData first = result.events().get(0);
        assertThat(first.description()).isEqualTo("Padaria Pão, Centro");
        assertThat(first.amount().toPlainString()).isEqualTo("42.50");
        assertThat(first.direction()).isEqualTo("debit");
        assertThat(result.events().get(1).description()).isEqualTo("Salário");
        assertThat(result.events()).extracting(e -> e.amount().toPlainString()).containsExactly("42.50", "1500.00", "42.50");
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(ParseIssue.REJECTED);
            assertThat(issue.code()).isEqualTo("csv_amount_invalid");
            assertThat(issue.recordRef()).isEqualTo("line 4");
        });
    }

    @Test
    void decodesLatin1() {
        String csv = "Data;Valor;Descrição\n10/07/2025;-5,00;Pão\n";

        ParseResult result = parser.parse(csv.getBytes(Charset.forName("ISO-8859-1")), "nubank");

        assertThat(result.events().get(0).description()).isEqualTo("Pão");
    }

    @Test
    void ignoresBalanceColumnWhenLookingForAmount() {
        String csv = "Data;Valor Saldo;Valor;Historico\n10/07/2025;1000,00;-5,00;Tarifa\n";

        ParseResult result = parser.parse(csv.getBytes(StandardCharsets.UTF_8), "inter");

        assertThat(result.events().get(0).amount().toPlainString()).isEqualTo("5.00");
        assertThat(result.events().get(0).direction()).isEqualTo("debit");
    }

    @Test
    void reportsFooterAndInvalidRowsInsteadOfFailingOrDroppingSilently() {
        String csv = """
                date,amount,description
                2025-07-10,-1,A
                TOTAL,,
                2025-07-11,x,B
                31/02/2025,3,C
                2025-07-12,2,D
                """;

        ParseResult result = parser.parse(csv.getBytes(StandardCharsets.UTF_8), "generic");

        assertThat(result.events()).hasSize(2);
        assertThat(result.issues()).extracting(ParseIssue::code)
                .containsExactly("csv_invalid_date", "csv_amount_invalid", "csv_invalid_date");
        assertThat(result.count(ParseIssue.REJECTED)).isEqualTo(3);
    }

    @Test
    void failsWhenNoRowIsValid() {
        String csv = "date,amount\n2025-07-10,abc\n";

        assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8), "generic"))
                .isInstanceOf(ParseException.class)
                .hasMessage("csv_no_valid_records");
    }

    @Test
    void rejectsUnknownPresetAndMissingColumns() {
        byte[] csv = "date,amount\n2025-07-10,1\n".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(csv, "bradesco")).hasMessage("csv_unknown_preset");
        assertThatThrownBy(() -> parser.parse("foo,bar\n1,2\n".getBytes(StandardCharsets.UTF_8), "generic"))
                .hasMessage("csv_columns_not_found");
    }

    @Test
    void goldenStatementWithBalanceColumnCloses() throws Exception {
        ParseResult result = parser.parse(new ClassPathResource("fixtures/extrato-com-saldo.csv").getContentAsByteArray(), "generic");

        assertThat(result.events()).extracting(e -> e.direction() + " " + e.amount().toPlainString())
                .containsExactly("credit 1500.00", "debit 29.90", "debit 470.10");
        assertThat(result.events().get(2).description()).isEqualTo("BOLETO FORNECEDOR X; LTDA");
        BalanceCheck check = BalanceCheck.verify(result.balances(), result.events());
        assertThat(check.status()).isEqualTo(BalanceCheck.VERIFIED);
        assertThat(check.opening()).isEqualByComparingTo("1000.00");
        assertThat(check.closing()).isEqualByComparingTo("2000.00");
        assertThat(result.issues()).extracting(ParseIssue::code).containsExactly("csv_balance_only_row");
    }

    @Test
    void missingRowBreaksTheBalanceChainAtTheRightLine() {
        String csv = """
                Data;Descrição;Valor;Saldo
                01/08/2025;SALDO ANTERIOR;;1.000,00
                02/08/2025;PIX RECEBIDO;1.500,00;2.500,00
                05/08/2025;BOLETO;-470,10;2.000,00
                """;

        ParseResult result = parser.parse(csv.getBytes(StandardCharsets.UTF_8), "generic");

        assertThat(result.issues()).filteredOn(issue -> issue.code().equals("csv_balance_break")).singleElement()
                .satisfies(issue -> {
                    assertThat(issue.recordRef()).isEqualTo("line 4");
                    assertThat(issue.detail()).isEqualTo("saldo esperado 2029.90, informado 2000.00");
                });
        BalanceCheck check = BalanceCheck.verify(result.balances(), result.events());
        assertThat(check.status()).isEqualTo(BalanceCheck.MISMATCH);
        assertThat(check.difference()).isEqualByComparingTo("-29.90");
    }

    @Test
    void unsignedDebitIsCaughtByTheBalance() {
        String csv = """
                date;amount;balance
                2025-08-01;100,00;1.100,00
                2025-08-02;30,00;1.070,00
                """;

        ParseResult result = parser.parse(csv.getBytes(StandardCharsets.UTF_8), "generic");

        assertThat(BalanceCheck.verify(result.balances(), result.events()).status()).isEqualTo(BalanceCheck.MISMATCH);
        assertThat(result.issues()).extracting(ParseIssue::code).containsExactly("csv_balance_break");
    }

    @Test
    void newestFirstStatementsAreVerifiedInChronologicalOrder() {
        String csv = """
                Data;Valor;Saldo
                03/08/2025;-50,00;950,00
                02/08/2025;-100,00;1.000,00
                01/08/2025;100,00;1.100,00
                """;

        ParseResult result = parser.parse(csv.getBytes(StandardCharsets.UTF_8), "generic");

        BalanceCheck check = BalanceCheck.verify(result.balances(), result.events());
        assertThat(check.status()).isEqualTo(BalanceCheck.VERIFIED);
        assertThat(check.opening()).isEqualByComparingTo("1000.00");
        assertThat(check.closing()).isEqualByComparingTo("950.00");
    }

    @Test
    void rejectedRowShowsUpAsBalanceDifference() {
        String csv = """
                Data;Valor;Saldo
                01/08/2025;100,00;1.100,00
                02/08/2025;abc;1.050,00
                03/08/2025;-50,00;1.000,00
                """;

        ParseResult result = parser.parse(csv.getBytes(StandardCharsets.UTF_8), "generic");

        BalanceCheck check = BalanceCheck.verify(result.balances(), result.events());
        assertThat(check.status()).isEqualTo(BalanceCheck.MISMATCH);
        assertThat(check.difference()).isEqualByComparingTo("-50.00");
        assertThat(result.issues()).extracting(ParseIssue::code).containsExactly("csv_amount_invalid");
    }

    @Test
    void withoutBalanceColumnThereIsNothingToVerify() {
        ParseResult result = parser.parse("date,amount\n2025-07-10,1\n".getBytes(StandardCharsets.UTF_8), "generic");

        assertThat(result.balances()).isNull();
    }
}
