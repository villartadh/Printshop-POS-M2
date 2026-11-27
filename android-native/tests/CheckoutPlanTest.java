package ph.printshop.pos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import static ph.printshop.pos.CheckoutPlan.*;

public final class CheckoutPlanTest {
    private static int checks;
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    private static void reject(Runnable run,String why){
        try{run.run();}catch(IllegalArgumentException|IllegalStateException expected){checks++;return;}
        throw new AssertionError("Accepted "+why);
    }
    private static final String REQUEST=UUID.randomUUID().toString();
    private static final CatalogEntry SERVICE=CatalogEntry.create(CatalogEntry.Kind.SERVICE,"Synthetic print","page","2.50");
    private static final CatalogEntry PAPER=CatalogEntry.create(CatalogEntry.Kind.MATERIAL,"Synthetic paper","sheet","1.99");
    private static final CatalogEntry INK=CatalogEntry.create(CatalogEntry.Kind.MATERIAL,"Synthetic ink","ml","1.25");
    private static final PricingProfile DEFAULT=PricingProfile.defaults();
    private static final PricingProfile DOCUMENT=new PricingProfile(1,PricingMath.Method.PER_PAGE,true,
        Arrays.asList(new PricingMath.Tier(1000,225),new PricingMath.Tier(2000,200)));
    private static Inputs count(int count,UsageBasis basis,long cash){return new Inputs(count,1,1,null,null,null,basis,cash);}
    private static Inputs document(int pages,int copies,UsageBasis basis,long cash){return new Inputs(1,pages,copies,null,null,null,basis,cash);}
    private static Material paper(long per,long available,boolean pending){return new Material(PAPER,per,1,available,pending);}
    private static Proposal create(CatalogEntry service,PricingProfile profile,long recipe,Inputs inputs,List<Material> materials){
        return CheckoutPlan.prepare(REQUEST,service,profile,recipe,inputs,materials);
    }
    public static void main(String[] args){
        Proposal manual=create(SERVICE,DEFAULT,0,count(3,UsageBasis.NONE,1000),Collections.emptyList());
        check(manual.totalCentavos==750&&manual.rateCentavos==250&&manual.changeCentavos==250,"cash exact");
        check(manual.materials.isEmpty()&&manual.materialCostCentavos==0,"empty recipe");
        check(manual.serviceId.equals(SERVICE.id)&&manual.serviceRevision==1&&manual.pricingRevision==0,"revisions");
        check(manual.billableHundredths==300&&manual.producedItems==3,"count quantities");
        check(manual.sameSnapshot(create(SERVICE,DEFAULT,0,count(3,UsageBasis.NONE,1000),Collections.emptyList())),"same snapshot");
        check(!manual.sameSnapshot(null),"null snapshot");
        check(!manual.sameSnapshot(CheckoutPlan.prepare(UUID.randomUUID().toString(),SERVICE,DEFAULT,0,count(3,UsageBasis.NONE,1000),Collections.emptyList())),"request identity");
        reject(()->create(SERVICE,DEFAULT,0,count(3,UsageBasis.NONE,749),Collections.emptyList()),"underpayment");
        reject(()->create(SERVICE,DEFAULT,0,count(3,UsageBasis.BILLABLE_UNIT,1000),Collections.emptyList()),"unneeded basis");
        reject(()->create(SERVICE,DEFAULT,0,count(0,UsageBasis.NONE,1000),Collections.emptyList()),"zero count");
        reject(()->create(SERVICE,DEFAULT,0,count(1,UsageBasis.NONE,-1),Collections.emptyList()),"negative cash");
        reject(()->create(SERVICE,DEFAULT,0,count(1,UsageBasis.NONE,MAX_TENDER_CENTAVOS+1),Collections.emptyList()),"overlimit tender");
        reject(()->create(SERVICE,DEFAULT,-1,count(1,UsageBasis.NONE,1000),Collections.emptyList()),"negative recipe revision");
        reject(()->create(SERVICE,DEFAULT,0,new Inputs(1,2,1,null,null,null,UsageBasis.NONE,1000),Collections.emptyList()),"hidden page values");
        reject(()->create(SERVICE,DEFAULT,0,new Inputs(1,1,1,"10","2",PricingMath.DimensionUnit.FT,UsageBasis.NONE,1000),Collections.emptyList()),"hidden dimensions");
        reject(()->create(SERVICE.archive(true),DEFAULT,0,count(1,UsageBasis.NONE,1000),Collections.emptyList()),"archived service");
        reject(()->create(PAPER,DEFAULT,0,count(1,UsageBasis.NONE,1000),Collections.emptyList()),"material as service");
        CatalogEntry free=SERVICE.edit(SERVICE.name,SERVICE.unit,"0.00");
        reject(()->create(free,DEFAULT,0,count(1,UsageBasis.NONE,1000),Collections.emptyList()),"zero total");
        reject(()->CheckoutPlan.prepare("bad",SERVICE,DEFAULT,0,count(1,UsageBasis.NONE,1000),Collections.emptyList()),"bad identity");
        reject(()->new Material(PAPER,1000,0,1,false),"stock with no history");
        reject(()->new Material(PAPER.archive(true),1000,1,1000,false),"archived material");
        reject(()->new Material(SERVICE,1000,1,1000,false),"service as material");
        reject(()->new Material(PAPER,0,1,1000,false),"zero recipe demand");
        reject(()->new Material(PAPER,1000,-1,1000,false),"negative stock revision");
        reject(()->new Material(PAPER,1000,Long.MAX_VALUE,1000,false),"stock revision overflow");
        reject(()->new Material(PAPER,1000,1,StockMath.MAX+1,false),"stock limit");
        List<Material> mats=Arrays.asList(paper(500,5000,false),new Material(INK,100,2,2000,false));
        Proposal doc=create(SERVICE,DOCUMENT,1,document(5,2,UsageBasis.BILLABLE_UNIT,2500),mats);
        check(doc.billableHundredths==1000&&doc.producedItems==2,"document count");
        check(doc.rateCentavos==225&&doc.totalCentavos==2250&&doc.changeCentavos==250,"tier threshold");
        check(doc.materialCostCentavos==1120,"cost summed before rounding");
        Consumption usedPaper=doc.materials.stream().filter(m->m.materialId.equals(PAPER.id)).findFirst().orElseThrow();
        check(usedPaper.quantityMilli==5000&&usedPaper.beforeMilli==5000&&usedPaper.afterMilli==0,"exact stock boundary");
        check(usedPaper.perUnitMilli==500&&usedPaper.catalogRevision==1&&usedPaper.stockRevision==1&&usedPaper.unitCostCentavos==199,"audit material snapshot");
        Proposal copies=create(SERVICE,DOCUMENT,1,document(5,2,UsageBasis.PRODUCED_ITEM,2500),mats);
        check(copies.materialCostCentavos==224&&copies.totalCentavos==doc.totalCentavos,"explicit produced-item basis");
        check(!doc.sameSnapshot(copies),"basis change");
        List<Material> reverse=new ArrayList<>(mats);Collections.reverse(reverse);
        Proposal reordered=create(SERVICE,DOCUMENT,1,document(5,2,UsageBasis.BILLABLE_UNIT,2500),reverse);
        check(doc.sameSnapshot(reordered),"stable material order");
        check(doc.fingerprint().matches("[0-9a-f]{64}"),"fingerprint format");
        check(doc.fingerprint().equals(reordered.fingerprint()),"fingerprint stable order");
        check(!doc.fingerprint().equals(copies.fingerprint()),"fingerprint basis sensitivity");
        check(!doc.fingerprint().equals(create(SERVICE,DOCUMENT,2,document(5,2,UsageBasis.BILLABLE_UNIT,2500),mats).fingerprint()),"fingerprint recipe revision sensitivity");
        check(!doc.fingerprint().equals(create(SERVICE,DOCUMENT,1,document(5,2,UsageBasis.BILLABLE_UNIT,2600),mats).fingerprint()),"fingerprint tender sensitivity");
        check(!doc.fingerprint().equals(CheckoutPlan.prepare(UUID.randomUUID().toString(),SERVICE,DOCUMENT,1,document(5,2,UsageBasis.BILLABLE_UNIT,2500),mats).fingerprint()),"fingerprint request identity sensitivity");
        try{doc.materials.clear();throw new AssertionError("mutable plan list");}catch(UnsupportedOperationException expected){checks++;}
        reject(()->create(SERVICE,DOCUMENT,1,document(5,2,UsageBasis.BILLABLE_UNIT,2500),
            Arrays.asList(paper(500,4999,false),mats.get(1))),"one milliunit shortage");
        reject(()->create(SERVICE,DOCUMENT,1,document(5,2,UsageBasis.BILLABLE_UNIT,2500),List.of(paper(500,5000,true))),"pending stock");
        reject(()->create(SERVICE,DOCUMENT,1,document(5,2,UsageBasis.BILLABLE_UNIT,2500),List.of(mats.get(0),mats.get(0))),"duplicate material");
        reject(()->create(SERVICE,DOCUMENT,0,document(5,2,UsageBasis.BILLABLE_UNIT,2500),mats),"unstamped recipe");
        reject(()->create(SERVICE,DOCUMENT,1,document(5,2,UsageBasis.NONE,2500),mats),"missing basis");
        reject(()->create(SERVICE,DEFAULT,1,count(1,UsageBasis.PRODUCED_ITEM,2500),mats),"legacy basis override");
        reject(()->create(SERVICE,DOCUMENT,1,new Inputs(2,5,2,null,null,null,UsageBasis.BILLABLE_UNIT,2500),mats),"extra document count");
        reject(()->doc.requireSameSnapshot(create(SERVICE,DOCUMENT,2,document(5,2,UsageBasis.BILLABLE_UNIT,2500),mats)),"stale recipe");
        reject(()->doc.requireSameSnapshot(create(SERVICE.edit(SERVICE.name,SERVICE.unit,"2.50"),DOCUMENT,1,document(5,2,UsageBasis.BILLABLE_UNIT,2500),mats)),"stale service");
        reject(()->doc.requireSameSnapshot(create(SERVICE,new PricingProfile(2,DOCUMENT.method,true,DOCUMENT.tiers),1,document(5,2,UsageBasis.BILLABLE_UNIT,2500),mats)),"stale pricing");
        reject(()->doc.requireSameSnapshot(create(SERVICE,DOCUMENT,1,document(5,2,UsageBasis.BILLABLE_UNIT,2600),mats)),"changed tender");
        reject(()->doc.requireSameSnapshot(create(SERVICE,DOCUMENT,1,document(5,2,UsageBasis.BILLABLE_UNIT,2500),List.of(new Material(PAPER,500,2,5000,false),mats.get(1)))),"changed stock revision");
        reject(()->doc.requireSameSnapshot(create(SERVICE,DOCUMENT,1,document(5,2,UsageBasis.BILLABLE_UNIT,2500),List.of(new Material(PAPER.edit(PAPER.name,PAPER.unit,"1.99"),500,1,5000,false),mats.get(1)))),"changed material catalog revision");
        doc.requireSameSnapshot(doc);check(true,"same plan accepted");
        PricingProfile area=new PricingProfile(1,PricingMath.Method.PER_SQFT,false,List.of());
        CatalogEntry banner=CatalogEntry.create(CatalogEntry.Kind.SERVICE,"Synthetic banner","sq ft","25");
        Inputs measured=new Inputs(1,1,1,"1.25","2",PricingMath.DimensionUnit.FT,UsageBasis.BILLABLE_UNIT,10000);
        Proposal areaPlan=create(banner,area,2,measured,List.of(paper(500,1250,false)));
        check(areaPlan.billableHundredths==250&&areaPlan.totalCentavos==6250&&areaPlan.changeCentavos==3750,"measured payment");
        check(areaPlan.materials.get(0).quantityMilli==1250&&areaPlan.materials.get(0).afterMilli==0,"fractional billed units exact stock");
        check(areaPlan.materialCostCentavos==249,"fractional material cost rounded");
        reject(()->create(banner,area,2,measured,List.of(paper(1,3,false))),"unrepresentable fractional milliunit");
        reject(()->create(banner,area,2,measured,List.of(paper(500,1249,false))),"measured shortage");
        reject(()->create(banner,area,2,new Inputs(1,1,1,"0","2",PricingMath.DimensionUnit.FT,UsageBasis.BILLABLE_UNIT,10000),List.of(paper(500,1250,false))),"zero dimension");
        reject(()->create(banner,area,2,measured,List.of(paper(StockMath.MAX,StockMath.MAX,false))),"excess required material");
        Proposal noRecipeHistoric=create(SERVICE,DEFAULT,3,count(1,UsageBasis.NONE,250),List.of());
        check(noRecipeHistoric.recipeRevision==3&&noRecipeHistoric.changeCentavos==0,"empty historic recipe and exact tender");
        String preview=CheckoutPreview.render(doc);
        check(preview.contains("CHECKOUT PREVIEW — NOT RECORDED"),"unrecorded notice");
        check(preview.contains("NOT A TAX DOCUMENT OR PROOF OF PAYMENT"),"non-tax notice");
        check(preview.contains("Total: PHP 22.50")&&preview.contains("Proposed change: PHP 2.50"),"preview exact money");
        check(preview.contains(REQUEST)&&preview.contains("No sale, payment or stock movement recorded."),"preview request evidence");
        reject(()->CheckoutPreview.render(null),"missing proposal");
        System.out.println("CheckoutPlan: "+checks+" checks passed");
    }
}
