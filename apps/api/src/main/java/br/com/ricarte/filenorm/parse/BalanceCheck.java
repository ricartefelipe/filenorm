package br.com.ricarte.filenorm.parse;

import java.math.BigDecimal;

/**
 * Prova de fechamento: saldo inicial + créditos − débitos = saldo final.
 * {@code verified}: fechou ao centavo; {@code mismatch}: não fechou ({@code difference} = declarado − calculado);
 * {@code unavailable}: o arquivo não traz os dois saldos ({@code reason} explica).
 */
public record BalanceCheck(
        String status,
        String source,
        BigDecimal opening,
        BigDecimal closing,
        BigDecimal computedClosing,
        BigDecimal difference,
        String reason
) {

    public static final String VERIFIED = "verified";
    public static final String MISMATCH = "mismatch";
    public static final String UNAVAILABLE = "unavailable";

    public static BalanceCheck verify(StatementBalances balances, Iterable<NormalizedEventData> events) {
        if (balances == null) {
            return new BalanceCheck(UNAVAILABLE, null, null, null, null, null, "no_balances_in_format");
        }
        if (balances.opening() == null || balances.closing() == null) {
            String reason = balances.opening() == null && balances.closing() == null ? "balances_not_found"
                    : balances.opening() == null ? "opening_balance_missing" : "closing_balance_missing";
            return new BalanceCheck(UNAVAILABLE, balances.source(), balances.opening(), balances.closing(), null, null, reason);
        }
        BigDecimal computed = balances.opening();
        for (NormalizedEventData event : events) {
            computed = "debit".equals(event.direction()) ? computed.subtract(event.amount()) : computed.add(event.amount());
        }
        BigDecimal difference = balances.closing().subtract(computed);
        String status = difference.signum() == 0 ? VERIFIED : MISMATCH;
        return new BalanceCheck(status, balances.source(), balances.opening(), balances.closing(), computed, difference, null);
    }
}
