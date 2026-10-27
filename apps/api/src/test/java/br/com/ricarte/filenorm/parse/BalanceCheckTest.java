package br.com.ricarte.filenorm.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BalanceCheckTest {

    private final List<NormalizedEventData> events = List.of(
            event("credit", "1500.00"),
            event("debit", "42.50"));

    @Test
    void verifiesWhenBalancesClose() {
        BalanceCheck check = BalanceCheck.verify(new StatementBalances(new BigDecimal("1000.00"), new BigDecimal("2457.50"), "x"), events);

        assertThat(check.status()).isEqualTo(BalanceCheck.VERIFIED);
        assertThat(check.difference()).isEqualByComparingTo("0");
    }

    @Test
    void reportsDifferenceToTheCent() {
        BalanceCheck check = BalanceCheck.verify(new StatementBalances(new BigDecimal("1000.00"), new BigDecimal("2457.51"), "x"), events);

        assertThat(check.status()).isEqualTo(BalanceCheck.MISMATCH);
        assertThat(check.computedClosing()).isEqualByComparingTo("2457.50");
        assertThat(check.difference()).isEqualByComparingTo("0.01");
    }

    @Test
    void isUnavailableWithoutBothBalances() {
        assertThat(BalanceCheck.verify(null, events).reason()).isEqualTo("no_balances_in_format");
        assertThat(BalanceCheck.verify(new StatementBalances(null, BigDecimal.TEN, "ofx"), events).reason()).isEqualTo("opening_balance_missing");
        assertThat(BalanceCheck.verify(new StatementBalances(BigDecimal.TEN, null, "csv"), events).reason()).isEqualTo("closing_balance_missing");
        assertThat(BalanceCheck.verify(StatementBalances.unavailable("pdf"), events).status()).isEqualTo(BalanceCheck.UNAVAILABLE);
    }

    private static NormalizedEventData event(String direction, String amount) {
        return new NormalizedEventData("x", LocalDate.of(2025, 8, 1), new BigDecimal(amount), "BRL", direction, null, "", "", BigDecimal.ONE);
    }
}
