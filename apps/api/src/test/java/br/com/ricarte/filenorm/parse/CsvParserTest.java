package br.com.ricarte.filenorm.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsvParserTest {

    private final CsvParser parser = new CsvParser();

    @Test
    void parsesGenericPreset() {
        String csv = """
                date,amount,description
                2025-07-10,-42.50,Padaria
                2025-07-15,1500.00,Salario
                """;

        ParseResult result = parser.parse(csv.getBytes(), "generic");

        assertThat(result.events()).hasSize(2);
        assertThat(result.events().get(0).direction()).isEqualTo("debit");
        assertThat(result.events().get(1).direction()).isEqualTo("credit");
    }

    @Test
    void parsesNubankPreset() {
        String csv = """
                Data;Valor;Descrição
                10/07/2025;-42,50;Compra mercado
                15/07/2025;1500,00;Transferencia recebida
                """;

        ParseResult result = parser.parse(csv.getBytes(), "nubank");

        assertThat(result.events()).hasSize(2);
        assertThat(result.events().get(0).postedAt().toString()).isEqualTo("2025-07-10");
    }
}
