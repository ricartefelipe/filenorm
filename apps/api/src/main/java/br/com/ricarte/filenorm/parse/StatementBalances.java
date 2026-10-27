package br.com.ricarte.filenorm.parse;

import java.math.BigDecimal;

/**
 * Saldos declarados pelo próprio arquivo. {@code opening} é o saldo antes do primeiro movimento e
 * {@code closing} o saldo após o último. Campos nulos significam que o formato não traz o dado.
 */
public record StatementBalances(BigDecimal opening, BigDecimal closing, String source) {

    public static StatementBalances unavailable(String source) {
        return new StatementBalances(null, null, source);
    }
}
