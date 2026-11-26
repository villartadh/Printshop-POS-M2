package ph.printshop.pos;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Immutable preview pricing preference snapshot; revision zero is the legacy default. */
public final class PricingProfile {
    public final long revision;
    public final PricingMath.Method method;
    public final boolean pagesAndCopies;
    public final List<PricingMath.Tier> tiers;

    public PricingProfile(long revision, PricingMath.Method method, boolean pagesAndCopies,
                          List<PricingMath.Tier> tiers) {
        if (revision < 0 || method == null || tiers == null || tiers.size() > 100)
            throw new IllegalArgumentException("Invalid pricing profile");
        if (pagesAndCopies && !supportsPages(method))
            throw new IllegalArgumentException("Page and copy inputs require a document pricing method");
        ArrayList<PricingMath.Tier> sorted = new ArrayList<>(tiers);
        for (PricingMath.Tier tier : sorted)
            if (tier == null) throw new IllegalArgumentException("Invalid bulk tier");
        sorted.sort(Comparator.comparingLong(t -> t.minQuantityHundredths));
        long last = 0;
        for (PricingMath.Tier tier : sorted) {
            if (tier.minQuantityHundredths == last) throw new IllegalArgumentException("Duplicate tier threshold");
            last = tier.minQuantityHundredths;
        }
        this.revision = revision; this.method = method; this.pagesAndCopies = pagesAndCopies;
        this.tiers = Collections.unmodifiableList(sorted);
    }
    public static PricingProfile defaults() {
        return new PricingProfile(0, PricingMath.Method.PER_UNIT, false, Collections.emptyList());
    }
    public static boolean supportsPages(PricingMath.Method method) {
        return method == PricingMath.Method.PER_PAGE || method == PricingMath.Method.PER_COPY ||
               method == PricingMath.Method.PER_SHEET;
    }
    public boolean sameConfiguration(PricingProfile other) {
        if (other == null || method != other.method || pagesAndCopies != other.pagesAndCopies ||
            tiers.size() != other.tiers.size()) return false;
        for (int i=0;i<tiers.size();i++) {
            PricingMath.Tier a=tiers.get(i), b=other.tiers.get(i);
            if (a.minQuantityHundredths!=b.minQuantityHundredths || a.rateCentavos!=b.rateCentavos) return false;
        }
        return true;
    }
    /** Until advanced quote inputs are connected, prevent silently quoting with the wrong billing basis. */
    public boolean needsAdvancedQuote() {
        return (method != PricingMath.Method.PER_UNIT && method != PricingMath.Method.FIXED) ||
            pagesAndCopies || !tiers.isEmpty();
    }
    public static long parseThreshold(String text) {
        if (text == null || !text.matches("[0-9]{1,6}(\\.[0-9]{1,2})?"))
            throw new IllegalArgumentException("Enter a positive tier quantity with up to two decimals");
        long quantity = new BigDecimal(text).movePointRight(2).longValueExact();
        if (quantity < 1 || quantity > PricingMath.MAX_QUANTITY_HUNDREDTHS)
            throw new IllegalArgumentException("Tier quantity outside supported range");
        return quantity;
    }
    public static String label(PricingMath.Method method) {
        switch (method) {
            case FIXED: return "Fixed per item";
            case PER_UNIT: return "Per unit";
            case PER_PAGE: return "Per page";
            case PER_COPY: return "Per copy";
            case PER_SHEET: return "Per sheet";
            case PER_SQFT: return "Per square foot";
            case PER_LINEAR_FT: return "Per linear foot";
            case PER_METER: return "Per linear meter";
            case TIERED_BULK: return "Bulk tiers per unit";
            default: throw new IllegalArgumentException("Unknown method");
        }
    }
}
