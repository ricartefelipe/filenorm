package br.com.ricarte.filenorm.parse;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Converte valores monetários escritos em pt-BR ou en-US para BigDecimal com sinal.
 * Valores ambíguos (ex.: "1.500" sem centavos) são recusados em vez de adivinhados.
 */
public final class Amounts {

    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern GROUPED = Pattern.compile("\\d{1,3}([.,]\\d{3})+");

    private Amounts() {
    }

    public static BigDecimal parse(String raw) {
        if (raw == null) {
            throw new AmountFormatException("amount_missing");
        }
        String value = raw.replace(' ', ' ').trim().replace("R$", "").replace(" ", "");
        if (value.isEmpty()) {
            throw new AmountFormatException("amount_missing");
        }
        boolean negative = false;
        String upper = value.toUpperCase();
        if (upper.endsWith("D") || upper.endsWith("C")) {
            negative = upper.endsWith("D");
            value = value.substring(0, value.length() - 1);
        }
        if (value.startsWith("(") && value.endsWith(")")) {
            negative = true;
            value = value.substring(1, value.length() - 1);
        }
        if (value.startsWith("-")) {
            negative = !negative;
            value = value.substring(1);
        } else if (value.startsWith("+")) {
            value = value.substring(1);
        } else if (value.endsWith("-")) {
            negative = !negative;
            value = value.substring(0, value.length() - 1);
        }
        BigDecimal amount = new BigDecimal(normalizeSeparators(value));
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new AmountFormatException("amount_precision");
        }
        amount = amount.setScale(2);
        return negative ? amount.negate() : amount;
    }

    private static String normalizeSeparators(String value) {
        int lastDot = value.lastIndexOf('.');
        int lastComma = value.lastIndexOf(',');
        if (lastDot < 0 && lastComma < 0) {
            return requireDigits(value);
        }
        if (lastDot >= 0 && lastComma >= 0) {
            char decimal = lastDot > lastComma ? '.' : ',';
            char thousands = decimal == '.' ? ',' : '.';
            int decimalAt = Math.max(lastDot, lastComma);
            String integer = value.substring(0, decimalAt);
            if (integer.indexOf(decimal) >= 0 || !GROUPED.matcher(integer).matches() && !DIGITS.matcher(integer).matches()) {
                throw new AmountFormatException("amount_invalid");
            }
            return requireDigits(integer.replace(String.valueOf(thousands), "")) + "." + requireDigits(value.substring(decimalAt + 1));
        }
        char separator = lastDot >= 0 ? '.' : ',';
        int occurrences = value.length() - value.replace(String.valueOf(separator), "").length();
        String fraction = value.substring(value.lastIndexOf(separator) + 1);
        if (occurrences == 1 && fraction.length() != 3) {
            return requireDigits(value.substring(0, value.lastIndexOf(separator))) + "." + requireDigits(fraction);
        }
        if (occurrences > 1 && GROUPED.matcher(value).matches()) {
            return value.replace(String.valueOf(separator), "");
        }
        throw new AmountFormatException("amount_ambiguous");
    }

    private static String requireDigits(String value) {
        if (!DIGITS.matcher(value).matches()) {
            throw new AmountFormatException("amount_invalid");
        }
        return value;
    }

    public static class AmountFormatException extends RuntimeException {

        private final String code;

        public AmountFormatException(String code) {
            super(code);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
