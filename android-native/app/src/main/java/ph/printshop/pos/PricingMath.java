package ph.printshop.pos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Exact native preview pricing for count, page, dimensions and bulk tiers.
 * Monetary values are centavos; billable quantities are hundredths of a service unit.
 * Measurement is rounded half-up to four decimals, then the extended quantity to two,
 * following the desktop quantity contract. Physical unit conversions are exact.
 * No tax, sale, material consumption, formula evaluation or database mutation.
 */
public final class PricingMath {
    private PricingMath() {}
    public enum Method { FIXED, PER_UNIT, PER_PAGE, PER_COPY, PER_SHEET, PER_SQFT, PER_LINEAR_FT, PER_METER, TIERED_BULK }
    public enum DimensionUnit { FT, IN, CM, M }
    public static final long MAX_QUANTITY_HUNDREDTHS = 10_000_000L;
    private static final BigDecimal MIN_MEASURE = new BigDecimal("0.1");

    public static final class Tier {
        public final long minQuantityHundredths, rateCentavos;
        public Tier(long minimum, long rate) {
            if (minimum < 1 || minimum > MAX_QUANTITY_HUNDREDTHS)
                throw new IllegalArgumentException("Invalid tier threshold");
            QuoteMath.total(rate, 1);
            minQuantityHundredths = minimum; rateCentavos = rate;
        }
    }
    public static final class Result {
        public final long quantityHundredths, rateCentavos, totalCentavos;
        private Result(long quantity, long rate, long total) {
            quantityHundredths = quantity; rateCentavos = rate; totalCentavos = total;
        }
    }
    private static BigDecimal dimension(String value) {
        if (value == null || !value.matches("[0-9]{1,5}(\\.[0-9]{1,3})?"))
            throw new IllegalArgumentException("Enter a positive dimension with up to three decimal places");
        BigDecimal number = new BigDecimal(value);
        if (number.signum() <= 0) throw new IllegalArgumentException("Dimensions must be positive");
        return number;
    }
    private static BigDecimal unitsPerFoot(DimensionUnit unit) {
        switch (unit) {
            case FT: return BigDecimal.ONE;
            case IN: return new BigDecimal("12");
            case CM: return new BigDecimal("30.48");
            case M: return new BigDecimal("0.3048");
            default: throw new IllegalArgumentException("Unknown dimension unit");
        }
    }
    private static BigDecimal metersPerUnit(DimensionUnit unit) {
        switch (unit) {
            case FT: return new BigDecimal("0.3048");
            case IN: return new BigDecimal("0.0254");
            case CM: return new BigDecimal("0.01");
            case M: return BigDecimal.ONE;
            default: throw new IllegalArgumentException("Unknown dimension unit");
        }
    }

    /** For page/copy/sheet modes use pages x copies when the service enables them;
     * fixed/unit/bulk uses quantity; dimension methods use the larger side or area.
     */
    public static long effectiveQuantity(Method method, int quantity, boolean pagesAndCopies,
                                         int pages, int copies, String width, String height, DimensionUnit unit) {
        QuoteMath.total(0, quantity);
        if (method == null) throw new IllegalArgumentException("Select a pricing method");
        BigDecimal billable = BigDecimal.valueOf(quantity);
        switch (method) {
            case PER_PAGE: case PER_COPY: case PER_SHEET:
                if (pagesAndCopies) {
                    QuoteMath.total(0, pages); QuoteMath.total(0, copies);
                    billable = BigDecimal.valueOf((long) pages * copies);
                }
                break;
            case PER_SQFT: case PER_LINEAR_FT: case PER_METER:
                if (unit == null) throw new IllegalArgumentException("Select a dimension unit");
                BigDecimal w = dimension(width), h = dimension(height), divisor = unitsPerFoot(unit);
                BigDecimal measure;
                if (method == Method.PER_SQFT)
                    measure = w.multiply(h).divide(divisor.multiply(divisor), 4, RoundingMode.HALF_UP);
                else if (method == Method.PER_LINEAR_FT)
                    measure = w.max(h).divide(divisor, 4, RoundingMode.HALF_UP);
                else
                    measure = w.max(h).multiply(metersPerUnit(unit)).setScale(4, RoundingMode.HALF_UP);
                billable = measure.max(MIN_MEASURE).multiply(billable);
                break;
            default: break;
        }
        BigDecimal hundredths = billable.setScale(2, RoundingMode.HALF_UP).movePointRight(2);
        if (hundredths.compareTo(BigDecimal.ONE) < 0 ||
            hundredths.compareTo(BigDecimal.valueOf(MAX_QUANTITY_HUNDREDTHS)) > 0)
            throw new IllegalArgumentException("Billable quantity outside supported range");
        return hundredths.longValueExact();
    }

    /** The greatest qualifying tier wins, including on an exact threshold.
     * Tier rules also apply to measured quantities, as on the desktop.
     */
    public static Result quote(long baseRate, long quantityHundredths, List<Tier> tiers) {
        QuoteMath.total(baseRate, 1);
        if (quantityHundredths < 1 || quantityHundredths > MAX_QUANTITY_HUNDREDTHS)
            throw new IllegalArgumentException("Billable quantity outside supported range");
        if (tiers == null || tiers.size() > 100) throw new IllegalArgumentException("Invalid tier list");
        long rate = baseRate, matched = 0;
        Set<Long> thresholds = new HashSet<>();
        for (Tier tier : tiers) {
            if (tier == null || !thresholds.add(tier.minQuantityHundredths))
                throw new IllegalArgumentException("Duplicate or invalid bulk tier");
            if (tier.minQuantityHundredths <= quantityHundredths && tier.minQuantityHundredths > matched) {
                matched = tier.minQuantityHundredths; rate = tier.rateCentavos;
            }
        }
        long total = BigDecimal.valueOf(rate).multiply(BigDecimal.valueOf(quantityHundredths, 2))
            .setScale(0, RoundingMode.HALF_UP).longValueExact();
        return new Result(quantityHundredths, rate, total);
    }
    public static String displayQuantity(long hundredths) {
        if (hundredths < 1 || hundredths > MAX_QUANTITY_HUNDREDTHS)
            throw new IllegalArgumentException("Billable quantity outside supported range");
        return BigDecimal.valueOf(hundredths, 2).stripTrailingZeros().toPlainString();
    }
}
