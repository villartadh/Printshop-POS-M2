package ph.printshop.pos;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Shared, Android-free form validation and canonical checkout input shaping. */
final class TrainingForms {
    static final int PAGE_SIZE=8;
    private TrainingForms(){}
    static void validatePageOffset(int offset){
        if(offset<0||offset%PAGE_SIZE!=0||offset>Integer.MAX_VALUE-PAGE_SIZE)
            throw new IllegalArgumentException("Invalid catalog page");
    }
    static String money(long centavos){
        QuoteMath.total(centavos,1);
        return BigDecimal.valueOf(centavos,2).toPlainString();
    }
    static int count(String text){
        if(text==null||!text.matches("[0-9]{1,6}"))throw new IllegalArgumentException("Invalid quantity");
        int value=Integer.parseInt(text);QuoteMath.total(0,value);return value;
    }
    static boolean measured(PricingMath.Method method){
        return method==PricingMath.Method.PER_SQFT||method==PricingMath.Method.PER_LINEAR_FT||method==PricingMath.Method.PER_METER;
    }
    static List<PricingMath.Tier> addTier(PricingMath.Method method,boolean pages,List<PricingMath.Tier> existing,String threshold,String rate){
        List<PricingMath.Tier> next=new ArrayList<>(existing);
        next.add(new PricingMath.Tier(PricingProfile.parseThreshold(threshold),QuoteMath.parseCentavos(rate)));
        return new ArrayList<>(new PricingProfile(0,method,pages,next).tiers);
    }
    static CheckoutPlan.Inputs checkout(PricingProfile profile,boolean hasMaterials,String count,String pages,String copies,
        String width,String height,PricingMath.DimensionUnit unit,CheckoutPlan.UsageBasis selected,String cash){
        boolean dimensions=measured(profile.method);
        // Hidden fields are never persisted; the domain plan additionally enforces the constraints.
        CheckoutPlan.UsageBasis basis=hasMaterials?(profile.needsAdvancedQuote()?selected:CheckoutPlan.UsageBasis.BILLABLE_UNIT):CheckoutPlan.UsageBasis.NONE;
        if(basis==null||(hasMaterials&&basis==CheckoutPlan.UsageBasis.NONE))throw new IllegalArgumentException("Choose the material usage basis");
        return new CheckoutPlan.Inputs(profile.pagesAndCopies?1:count(count),profile.pagesAndCopies?count(pages):1,
            profile.pagesAndCopies?count(copies):1,dimensions?width:null,dimensions?height:null,dimensions?unit:null,basis,QuoteMath.parseCentavos(cash));
    }
}
