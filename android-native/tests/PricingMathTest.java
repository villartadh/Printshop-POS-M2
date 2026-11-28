package ph.printshop.pos;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static ph.printshop.pos.PricingMath.Method.*;
import static ph.printshop.pos.PricingMath.DimensionUnit.*;

public final class PricingMathTest {
    private static int checks;
    private static void check(boolean result, String description) {
        if (!result) throw new AssertionError(description);
        checks++;
    }
    private static void reject(Runnable operation) {
        try { operation.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Expected rejection");
    }
    private static long units(PricingMath.Method method, int qty, boolean pagesMode, int pages, int copies,
                              String w, String h, PricingMath.DimensionUnit unit) {
        return PricingMath.effectiveQuantity(method, qty, pagesMode, pages, copies, w, h, unit);
    }
    public static void main(String[] args) {
        List<PricingMath.Tier> noTiers = Collections.emptyList();
        for (PricingMath.Method m : new PricingMath.Method[]{FIXED, PER_UNIT, TIERED_BULK})
            check(units(m, 3, false, 0, 0, null, null, null) == 300, "count mode " + m);
        for (PricingMath.Method m : new PricingMath.Method[]{PER_PAGE, PER_COPY, PER_SHEET}) {
            check(units(m, 1, true, 12, 3, null, null, null) == 3600, "pages x copies " + m);
            check(units(m, 7, false, 0, 0, null, null, null) == 700, "count fallback " + m);
        }
        long area = units(PER_SQFT, 2, false, 0, 0, "3", "4", FT);
        check(area == 2400, "two 3x4 banners");
        check(PricingMath.quote(2500, area, noTiers).totalCentavos == 60000, "PHP 600 area quote");
        check(units(PER_SQFT, 1, false, 0, 0, "36", "48", IN) == 1200, "inch conversion");
        check(units(PER_SQFT, 1, false, 0, 0, "30.48", "60.96", CM) == 200, "cm conversion");
        check(units(PER_SQFT, 1, false, 0, 0, "0.304", "0.305", M) == 100, "meter conversion");
        check(units(PER_LINEAR_FT, 2, false, 0, 0, "12", "36", IN) == 600, "larger side in feet");
        check(units(PER_METER, 3, false, 0, 0, "50", "25", CM) == 150, "linear meter quantity");
        check(units(PER_METER, 1, false, 0, 0, "1", "2", FT) == 61, "ft to meter rounding");
        check(units(PER_SQFT, 1, false, 0, 0, "0.01", "0.01", FT) == 10, "minimum area");
        check(units(PER_LINEAR_FT, 1, false, 0, 0, "1.005", "1", FT) == 101, "half-up quantity");
        check(PricingMath.quote(199, 150, noTiers).totalCentavos == 299, "half-up centavo");
        check(PricingMath.quote(0, 100, noTiers).totalCentavos == 0, "zero rate valid");
        List<PricingMath.Tier> tiers = Arrays.asList(new PricingMath.Tier(2000, 150), new PricingMath.Tier(1000, 180));
        check(PricingMath.quote(199, 999, tiers).rateCentavos == 199, "below threshold");
        check(PricingMath.quote(199, 1000, tiers).rateCentavos == 180, "exact lower threshold");
        check(PricingMath.quote(199, 2000, tiers).totalCentavos == 3000, "highest qualifying tier");
        check(PricingMath.quote(199, 2500, tiers).rateCentavos == 150, "above highest tier");
        check(PricingMath.displayQuantity(125).equals("1.25"), "quantity format");
        check(PricingMath.displayQuantity(100).equals("1"), "integer quantity format");
        check(PricingMath.quote(99_999_999_999L, 10_000_000, noTiers).totalCentavos == QuoteMath.total(99_999_999_999L, 100000), "maximum range");
        reject(() -> units(PER_SQFT, 1, false, 0, 0, "0", "1", FT));
        reject(() -> units(PER_SQFT, 1, false, 0, 0, "1e3", "2", FT));
        reject(() -> units(PER_SQFT, 1, false, 0, 0, "1.0001", "2", FT));
        reject(() -> units(PER_SQFT, 1, false, 0, 0, "1000", "1000", FT));
        reject(() -> units(PER_SQFT, 1, false, 0, 0, "1", "1", null));
        reject(() -> units(PER_PAGE, 1, true, 100000, 2, null, null, null));
        reject(() -> units(PER_UNIT, 0, false, 0, 0, null, null, null));
        reject(() -> PricingMath.quote(-1, 100, noTiers));
        reject(() -> PricingMath.quote(1, 0, noTiers));
        reject(() -> PricingMath.quote(1, 10_000_001, noTiers));
        reject(() -> PricingMath.quote(1, 100, Arrays.asList(new PricingMath.Tier(100, 1), new PricingMath.Tier(100, 2))));
        reject(() -> new PricingMath.Tier(0, 1));
        reject(() -> new PricingMath.Tier(100, -1));
        System.out.println("PricingMath: " + checks + " checks passed");
    }
}
