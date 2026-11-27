package ph.printshop.pos;
import java.util.*;
public final class CreditCheckoutPlanTest {
    static int checks;
    static final String ID="00000000-0000-0000-0000-000000000001",C="00000000-0000-0000-0000-000000000002";
    static final CatalogEntry SERVICE=CatalogEntry.create(CatalogEntry.Kind.SERVICE,"Synthetic print","page","5.00");
    static final CatalogEntry PAPER=CatalogEntry.create(CatalogEntry.Kind.MATERIAL,"Synthetic paper","sheet","1.00");
    static final PricingProfile PROFILE=PricingProfile.defaults();
    static CheckoutPlan.Inputs input(long paid){return new CheckoutPlan.Inputs(2,1,1,null,null,null,CheckoutPlan.UsageBasis.BILLABLE_UNIT,paid);}
    static CreditCheckoutPlan plan(long paid,long rev,long stock){return CreditCheckoutPlan.prepare(ID,C,rev,paid==0?ReceivableMath.Mode.UNPAID:ReceivableMath.Mode.PARTIAL_CASH,SERVICE,PROFILE,1,input(paid),List.of(new CheckoutPlan.Material(PAPER,1000,1,stock,false)));}
    static void check(boolean ok){if(!ok)throw new AssertionError("Credit checkout plan");checks++;}
    static void reject(Runnable r){try{r.run();throw new AssertionError("Expected refusal");}catch(IllegalArgumentException|IllegalStateException expected){checks++;}}
    public static void main(String[] args){
        var p=plan(200,1,5000);check(p.total==1000&&p.paid==200&&p.owed==800);
        check(p.actualInputs.receivedCentavos==200&&p.billableHundredths==200&&p.usageHundredths==200);
        check(p.materials.get(0).quantityMilli==2000&&p.materials.get(0).afterMilli==3000);
        p.requireSameReview(plan(200,1,5000));check(true);
        var zero=plan(0,1,5000);check(zero.paid==0&&zero.owed==1000&&zero.materials.get(0).quantityMilli==2000);
        reject(()->p.requireSameReview(zero));reject(()->p.requireSameReview(plan(201,1,5000)));
        reject(()->p.requireSameReview(plan(200,2,5000)));reject(()->p.requireSameReview(plan(200,1,5001)));
        reject(()->plan(200,1,1999));reject(()->plan(1000,1,5000));reject(()->plan(1001,1,5000));
        reject(()->plan(200,0,5000));reject(()->CheckoutPlan.prepare(ID,SERVICE,PROFILE,1,input(200),List.of(new CheckoutPlan.Material(PAPER,1000,1,5000,false))));
        check(p.fingerprint.matches("[0-9a-f]{64}"));
        System.out.println("CreditCheckoutPlan: "+checks+" checks PASS");
    }
}
