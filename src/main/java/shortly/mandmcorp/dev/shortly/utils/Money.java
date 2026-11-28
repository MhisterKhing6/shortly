package shortly.mandmcorp.dev.shortly.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money arithmetic in whole pesewas (long), rounding half-up only when converting from cedis.
 * Stored amounts stay as cedi values with at most two decimals.
 */
public final class Money {

    private Money() {}

    /** Cedis to pesewas, half-up. */
    public static long p(double cedis) {
        return BigDecimal.valueOf(cedis).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    /** Pesewas to cedis (exact two-decimal value). */
    public static double c(long pesewas) {
        return BigDecimal.valueOf(pesewas).movePointLeft(2).doubleValue();
    }

    public static String format(long pesewas) {
        return BigDecimal.valueOf(pesewas).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
