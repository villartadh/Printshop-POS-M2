package ph.printshop.pos;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Integer centavos; no floating point or implicit rounding. */
public final class QuoteMath {
    private QuoteMath() {}
    public static long parseCentavos(String value) {
        if (value == null || !value.matches("[0-9]{1,9}(\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException("Enter a nonnegative amount with up to two decimal places.");
        }
        return new BigDecimal(value).movePointRight(2).longValueExact();
    }
    public static long total(long unitCentavos, int quantity) {
        if (unitCentavos < 0 || unitCentavos > 99_999_999_999L || quantity < 1 || quantity > 100_000) {
            throw new IllegalArgumentException("Price or quantity outside supported range.");
        }
        return Math.multiplyExact(unitCentavos, quantity);
    }
    public static long change(long total, long received) {
        if (total < 0 || received < total) throw new IllegalArgumentException("Insufficient cash.");
        return Math.subtractExact(received, total);
    }
    public static String display(long centavos) {
        return "PHP " + BigDecimal.valueOf(centavos, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
