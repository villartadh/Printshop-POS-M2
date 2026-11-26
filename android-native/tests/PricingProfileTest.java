package ph.printshop.pos;

import java.util.Arrays;
import java.util.Collections;
import static ph.printshop.pos.PricingMath.Method.*;

public final class PricingProfileTest {
    private static int checks;
    private static void check(boolean value) {
        if (!value) throw new AssertionError("Pricing profile check " + (checks+1));
        checks++;
    }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Expected invalid profile rejection");
    }
    public static void main(String[] args) {
        PricingProfile standard=PricingProfile.defaults();
        check(standard.revision==0 && standard.method==PER_UNIT && !standard.needsAdvancedQuote());
        PricingMath.Tier a=new PricingMath.Tier(2000,100),b=new PricingMath.Tier(1000,150);
        java.util.ArrayList<PricingMath.Tier> draft=new java.util.ArrayList<>(Arrays.asList(a,b));
        PricingProfile p=new PricingProfile(1,PER_PAGE,true,draft);
        check(p.tiers.get(0).minQuantityHundredths==1000 && p.tiers.get(1).minQuantityHundredths==2000);
        draft.clear();check(p.tiers.size()==2);
        try {p.tiers.clear();throw new AssertionError("mutable profile");}catch(UnsupportedOperationException expected){checks++;}
        check(p.needsAdvancedQuote());
        check(p.sameConfiguration(new PricingProfile(9,PER_PAGE,true,Arrays.asList(b,a))));
        check(!p.sameConfiguration(new PricingProfile(1,PER_COPY,true,Arrays.asList(a,b))));
        check(!p.sameConfiguration(new PricingProfile(1,PER_PAGE,false,Arrays.asList(a,b))));
        check(!p.sameConfiguration(new PricingProfile(1,PER_PAGE,true,Collections.singletonList(a))));
        check(!new PricingProfile(1,FIXED,false,Collections.emptyList()).needsAdvancedQuote());
        check(new PricingProfile(1,PER_UNIT,false,Collections.singletonList(a)).needsAdvancedQuote());
        check(PricingProfile.parseThreshold("10.25")==1025);
        check(PricingProfile.parseThreshold("100000")==10_000_000);
        reject(()->PricingProfile.parseThreshold("0"));
        reject(()->PricingProfile.parseThreshold("1.001"));
        reject(()->PricingProfile.parseThreshold("1e3"));
        reject(()->PricingProfile.parseThreshold("100001"));
        reject(()->new PricingProfile(-1,PER_UNIT,false,Collections.emptyList()));
        reject(()->new PricingProfile(0,PER_SQFT,true,Collections.emptyList()));
        reject(()->new PricingProfile(1,PER_PAGE,false,Arrays.asList(a,a)));
        reject(()->new PricingProfile(1,null,false,Collections.emptyList()));
        System.out.println("PricingProfile: "+checks+" checks passed");
    }
}
