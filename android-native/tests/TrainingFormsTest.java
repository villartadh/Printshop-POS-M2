package ph.printshop.pos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Host-executable input and dynamic checkout regression tests, not Android widget acceptance. */
public final class TrainingFormsTest {
    private static int checks;
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    private static void reject(Runnable r){try{r.run();}catch(IllegalArgumentException|IllegalStateException expected){checks++;return;}throw new AssertionError("Invalid form accepted");}
    private static PricingProfile profile(PricingMath.Method m,boolean doc,List<PricingMath.Tier> tiers){return new PricingProfile(1,m,doc,tiers);}
    private static CheckoutPlan.Inputs checkout(PricingProfile p,boolean materials,String count,String pages,String copies,
        String w,String h,PricingMath.DimensionUnit unit,CheckoutPlan.UsageBasis basis,String cash){
        return TrainingForms.checkout(p,materials,count,pages,copies,w,h,unit,basis,cash);
    }
    public static void main(String[] args){
        check(TrainingForms.PAGE_SIZE==8,"page size");
        for(int offset:new int[]{0,8,16,80,800}){TrainingForms.validatePageOffset(offset);checks++;}
        for(int offset:new int[]{-8,-1,1,7,9,Integer.MAX_VALUE,Integer.MAX_VALUE-7})reject(()->TrainingForms.validatePageOffset(offset));
        check(TrainingForms.money(0).equals("0.00"),"zero");
        check(TrainingForms.money(1).equals("0.01"),"centavo");
        check(TrainingForms.money(12345).equals("123.45"),"price");
        check(TrainingForms.money(99999999999L).equals("999999999.99"),"max price");
        reject(()->TrainingForms.money(-1));reject(()->TrainingForms.money(100000000000L));
        check(TrainingForms.count("1")==1,"count");
        check(TrainingForms.count("100000")==100000,"maximum");
        for(String s:new String[]{"","0","100001","1.1","-1"," 4","9999999",null})reject(()->TrainingForms.count(s));
        for(PricingMath.Method method:PricingMath.Method.values())
            check(TrainingForms.measured(method)==Arrays.asList(PricingMath.Method.PER_SQFT,PricingMath.Method.PER_LINEAR_FT,PricingMath.Method.PER_METER).contains(method),"measured method");
        List<PricingMath.Tier> empty=Collections.emptyList();
        List<PricingMath.Tier> tiers=TrainingForms.addTier(PricingMath.Method.PER_PAGE,true,empty,"5","3.50");
        check(empty.isEmpty()&&tiers.size()==1&&tiers.get(0).minQuantityHundredths==500&&tiers.get(0).rateCentavos==350,"tier draft exact");
        List<PricingMath.Tier> sorted=TrainingForms.addTier(PricingMath.Method.PER_PAGE,true,tiers,"2.5","4.01");
        check(tiers.size()==1&&sorted.get(0).minQuantityHundredths==250&&sorted.get(1).minQuantityHundredths==500,"sorted immutable original");
        reject(()->TrainingForms.addTier(PricingMath.Method.PER_PAGE,true,tiers,"5.00","3.00"));
        reject(()->TrainingForms.addTier(PricingMath.Method.PER_UNIT,true,tiers,"10","1.00"));
        reject(()->TrainingForms.addTier(PricingMath.Method.PER_UNIT,false,tiers,"0","1.00"));
        reject(()->TrainingForms.addTier(PricingMath.Method.PER_UNIT,false,tiers,"1.001","1.00"));
        reject(()->TrainingForms.addTier(PricingMath.Method.PER_UNIT,false,tiers,"10","1.001"));
        List<PricingMath.Tier> full=new ArrayList<>();
        for(int i=1;i<=100;i++)full.add(new PricingMath.Tier(i*100,100));
        reject(()->TrainingForms.addTier(PricingMath.Method.PER_UNIT,false,full,"101","1"));
        PricingProfile ordinary=PricingProfile.defaults();
        CheckoutPlan.Inputs simple=checkout(ordinary,true,"3","bad","bad","bad","bad",PricingMath.DimensionUnit.M,CheckoutPlan.UsageBasis.PRODUCED_ITEM,"10.00");
        check(simple.count==3&&simple.pages==1&&simple.copies==1&&simple.width==null&&simple.height==null&&simple.dimensionUnit==null&&simple.basis==CheckoutPlan.UsageBasis.BILLABLE_UNIT&&simple.receivedCentavos==1000,"hidden fields excluded; legacy billable basis");
        CheckoutPlan.Inputs noRecipe=checkout(ordinary,false,"3",null,null,null,null,null,CheckoutPlan.UsageBasis.PRODUCED_ITEM,"10");
        check(noRecipe.basis==CheckoutPlan.UsageBasis.NONE,"no recipe has no basis");
        PricingProfile document=profile(PricingMath.Method.PER_PAGE,true,tiers);
        reject(()->checkout(document,true,null,"3","2",null,null,null,null,"25"));
        reject(()->checkout(document,true,null,"3","2",null,null,null,CheckoutPlan.UsageBasis.NONE,"25"));
        reject(()->checkout(document,true,null,"0","2",null,null,null,CheckoutPlan.UsageBasis.PRODUCED_ITEM,"25"));
        reject(()->checkout(document,true,null,"3","2",null,null,null,CheckoutPlan.UsageBasis.PRODUCED_ITEM,"25.001"));
        CheckoutPlan.Inputs doc=checkout(document,true,"hidden","3","2","hidden","hidden",PricingMath.DimensionUnit.M,CheckoutPlan.UsageBasis.PRODUCED_ITEM,"25");
        check(doc.count==1&&doc.pages==3&&doc.copies==2&&doc.width==null&&doc.dimensionUnit==null&&doc.receivedCentavos==2500,"canonical document");
        CatalogEntry service=CatalogEntry.create(CatalogEntry.Kind.SERVICE,"Synthetic document","page","4.20");
        CatalogEntry paper=CatalogEntry.create(CatalogEntry.Kind.MATERIAL,"Synthetic paper","sheet","1.00");
        List<CheckoutPlan.Material> materials=Collections.singletonList(new CheckoutPlan.Material(paper,500,1,48000,false));
        CheckoutPlan.Proposal plan=CheckoutPlan.prepare(UUID.randomUUID().toString(),service,document,1,doc,materials);
        check(plan.totalCentavos==2100&&plan.changeCentavos==400&&plan.billableHundredths==600&&plan.rateCentavos==350,"tier and exact document tender");
        check(plan.materials.get(0).quantityMilli==1000&&plan.materials.get(0).afterMilli==47000,"produced basis");
        CheckoutPlan.Inputs billed=checkout(document,true,null,"3","2",null,null,null,CheckoutPlan.UsageBasis.BILLABLE_UNIT,"25");
        check(CheckoutPlan.prepare(UUID.randomUUID().toString(),service,document,1,billed,materials).materials.get(0).quantityMilli==3000,"billable basis");
        for(PricingMath.Method method:Arrays.asList(PricingMath.Method.PER_SQFT,PricingMath.Method.PER_LINEAR_FT,PricingMath.Method.PER_METER)){
            PricingProfile measured=profile(method,false,empty);
            CheckoutPlan.Inputs dims=checkout(measured,false,"2","bad","bad","12","24",PricingMath.DimensionUnit.IN,null,"100");
            check(dims.count==2&&dims.pages==1&&dims.copies==1&&dims.width.equals("12")&&dims.height.equals("24")&&dims.dimensionUnit==PricingMath.DimensionUnit.IN&&dims.basis==CheckoutPlan.UsageBasis.NONE,"dimension fields canonical");
            CheckoutPlan.Proposal measuredPlan=CheckoutPlan.prepare(UUID.randomUUID().toString(),service,measured,0,dims,Collections.emptyList());
            check(measuredPlan.billableHundredths==(method==PricingMath.Method.PER_METER?122:400),"measured quantity");
        }
        System.out.println("TrainingForms: "+checks+" checks passed");
    }
}
