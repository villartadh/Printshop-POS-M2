package ph.printshop.pos;
import java.math.BigDecimal;
import java.util.UUID;

/** Exact material quantities in thousandths of the catalog unit. */
public final class StockMath {
    public static final long MAX = 999_999_999_999L;
    private StockMath() {}
    public static long parse(String quantity) {
        if (quantity == null || !quantity.matches("[0-9]{1,9}(\\.[0-9]{1,3})?")) throw new IllegalArgumentException("Use up to three decimal places.");
        return new BigDecimal(quantity).movePointRight(3).longValueExact();
    }
    public static long apply(long before, long delta) {
        if (before < 0 || before > MAX || delta == 0 || delta < -MAX || delta > MAX) throw new IllegalArgumentException("Invalid stock movement.");
        long after = Math.addExact(before, delta);
        if (after < 0 || after > MAX) throw new IllegalArgumentException("Insufficient stock or quantity limit exceeded.");
        return after;
    }
    public static String reason(String value) {
        if (value == null) throw new IllegalArgumentException("Movement reason required.");
        String r = value.trim();
        if (r.isEmpty() || r.length() > 160 || r.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Enter a printable reason (1–160 characters).");
        return r;
    }
    public static void validateRequestId(String id) {
        if (id == null || !UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException("Invalid movement identity.");
    }
    public static String display(long quantity) { return BigDecimal.valueOf(quantity, 3).stripTrailingZeros().toPlainString(); }
    public static final class Balance {
        public final long quantity, revision;
        public Balance(long quantity, long revision) { this.quantity = quantity; this.revision = revision; }
    }
}
