package ph.printshop.pos;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SavedQuotationTest {
    private static final String Q="00000000-0000-4000-8000-000000000001", R="00000000-0000-4000-8000-000000000002";
    private static final CatalogEntry S=new CatalogEntry("00000000-0000-4000-8000-000000000003",CatalogEntry.Kind.SERVICE,"Banner","sq ft",2500,1,false);
    private static final CatalogEntry M=new CatalogEntry("00000000-0000-4000-8000-000000000004",CatalogEntry.Kind.MATERIAL,"Media","sq ft",1000,1,false);
    private static final PricingProfile P=new PricingProfile(1,PricingMath.Method.PER_SQFT,false,Collections.emptyList());
    private static int checks;
    private static void check(boolean actual){if(!actual)throw new AssertionError("check "+(checks+1));checks++;}
    private static void refuse(Runnable action){try{action.run();throw new AssertionError("expected refusal "+(checks+1));}catch(IllegalArgumentException|IllegalStateException expected){checks++;}}
    private static CheckoutPlan.Inputs inputs(String width,String height,long tender,CheckoutPlan.UsageBasis basis){
        return new CheckoutPlan.Inputs(1,1,1,width,height,PricingMath.DimensionUnit.FT,basis,tender);
    }
    private static CheckoutPlan.Material material(CatalogEntry item,long qty,long rev,long stock,boolean pending){return new CheckoutPlan.Material(item,qty,rev,stock,pending);}
    public static void main(String[] args){
        CheckoutPlan.Inputs unpaid=inputs("2","6",0,CheckoutPlan.UsageBasis.BILLABLE_UNIT);
        SavedQuotation q=SavedQuotation.review(Q,S,P,1,unpaid,List.of(material(M,1000,0,0,false)));
        check(q.totalCentavos==30000);check(q.quantityHundredths==1200);check(q.materialCostCentavos==12000);
        check(!q.stockSufficientAtReview);check(q.representableForCheckout);check(q.inputs.receivedCentavos==0);
        check(q.materials.get(0).requiredHundredthMilli==1200000);
        CheckoutPlan.Proposal sale=CheckoutPlan.prepare(R,S,P,1,inputs("2","6",35000,CheckoutPlan.UsageBasis.BILLABLE_UNIT),List.of(material(M,1000,4,20000,false)));
        q.requireSameCommercialTerms(sale);checks++;check(sale.changeCentavos==5000);
        refuse(()->q.requireSameCommercialTerms(CheckoutPlan.prepare(R,S.edit("Banner renamed","sq ft","25"),P,1,inputs("2","6",35000,CheckoutPlan.UsageBasis.BILLABLE_UNIT),List.of(material(M,1000,4,20000,false)))));
        refuse(()->q.requireSameCommercialTerms(CheckoutPlan.prepare(R,S,P,2,inputs("2","6",35000,CheckoutPlan.UsageBasis.BILLABLE_UNIT),List.of(material(M,1000,4,20000,false)))));
        refuse(()->q.requireSameCommercialTerms(CheckoutPlan.prepare(R,S,P,1,inputs("3","4",35000,CheckoutPlan.UsageBasis.BILLABLE_UNIT),List.of(material(M,1000,4,20000,false)))));
        refuse(()->q.requireSameCommercialTerms(CheckoutPlan.prepare(R,S,P,1,inputs("2","6",35000,CheckoutPlan.UsageBasis.PRODUCED_ITEM),List.of(material(M,1000,4,20000,false)))));
        refuse(()->q.requireSameCommercialTerms(CheckoutPlan.prepare(R,S,P,1,inputs("2","6",35000,CheckoutPlan.UsageBasis.BILLABLE_UNIT),List.of(material(M.edit("Media","sq ft","10"),1000,4,20000,false)))));
        refuse(()->SavedQuotation.review(Q,S,P,1,inputs("2","6",1,CheckoutPlan.UsageBasis.BILLABLE_UNIT),List.of(material(M,1000,0,0,false))));
        refuse(()->SavedQuotation.review(Q,S,P,1,unpaid,Arrays.asList(material(M,1000,0,0,false),material(M,1000,0,0,false))));
        refuse(()->SavedQuotation.review(Q,S,P,0,unpaid,List.of(material(M,1000,0,0,false))));
        refuse(()->SavedQuotation.review(Q,S,P,1,unpaid,Collections.emptyList()));
        SavedQuotation pending=SavedQuotation.review(Q,S,P,1,unpaid,List.of(material(M,1000,4,20000,true)));
        check(pending.stockSufficientAtReview);check(pending.pendingStockAtReview);check(!q.pendingStockAtReview);check(q.commercialSnapshot().equals(pending.commercialSnapshot()));
        check(!q.commercialSnapshot().equals(SavedQuotation.review(Q,S,P,1,inputs("3","4",0,CheckoutPlan.UsageBasis.BILLABLE_UNIT),List.of(material(M,1000,4,20000,false))).commercialSnapshot()));
        refuse(()->CheckoutPlan.prepare(R,S,P,1,inputs("2","6",35000,CheckoutPlan.UsageBasis.BILLABLE_UNIT),List.of(material(M,1000,4,20000,true))));
        SavedQuotation fractional=SavedQuotation.review(Q,S,P,1,inputs("1","1.25",0,CheckoutPlan.UsageBasis.BILLABLE_UNIT),List.of(material(M,1,4,20000,false)));
        check(!fractional.representableForCheckout);check(fractional.materials.get(0).requiredHundredthMilli==125);
        refuse(()->fractional.requireSameCommercialTerms(sale));
        SavedQuotation free=SavedQuotation.review(Q,S.edit("Free sample","sq ft","0"),P,0,inputs("2","6",0,CheckoutPlan.UsageBasis.NONE),Collections.emptyList());
        check(free.totalCentavos==0);check(!free.representableForCheckout);
        try{q.materials.clear();throw new AssertionError("mutable");}catch(UnsupportedOperationException expected){checks++;}
        System.out.println("SavedQuotation: "+checks+" checks PASS");
    }
}
