package br.com.ricarte.filenorm.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AmountsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "-42.50|-42.50",
            "42,50|42.50",
            "1.500,00|1500.00",
            "1,500.00|1500.00",
            "1500,00|1500.00",
            "R$ 1.234.567,89|1234567.89",
            "42,50 D|-42.50",
            "42,50 C|42.50",
            "(10,00)|-10.00",
            "10,00-|-10.00",
            "+7|7.00",
            "1.234.567|1234567.00",
            "0,5|0.50"
    })
    void parsesBrazilianAndUsFormats(String raw, String expected) {
        assertThat(Amounts.parse(raw).toPlainString()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.500", "1,500", "abc", "", "1.2.3", "12,345,6", "10.505"})
    void rejectsAmbiguousOrInvalidAmounts(String raw) {
        assertThatThrownBy(() -> Amounts.parse(raw)).isInstanceOf(Amounts.AmountFormatException.class);
    }
}
