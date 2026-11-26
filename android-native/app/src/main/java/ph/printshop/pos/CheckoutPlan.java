package ph.printshop.pos;

import java.math.BigInteger;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable proposed single-service cash checkout, calculated from authoritative snapshots.
 * No database mutation, recorded sale, payment-provider call, receipt issuance or tax accounting.
 * A future transaction must rebuild the plan under its write lock and compare the snapshot
 * before inserting a sale/payments/stock movements together.
 */
public final class CheckoutPlan {
    public enum UsageBasis { NONE, BILLABLE_UNIT, PRODUCED_ITEM }
    public static final long MAX_TENDER_CENTAVOS=9_999_999_999_900_000L;
    private static final BigInteger HUNDRED=BigInteger.valueOf(100);
    private CheckoutPlan() {}

    /** Inputs from the quotation screen. The service catalog determines the rate, not a mutable UI price. */
    public static final class Inputs {
        public final int count,pages,copies;
        public final String width,height;
        public final PricingMath.DimensionUnit dimensionUnit;
        public final UsageBasis basis;
        public final long receivedCentavos;
        public Inputs(int count,int pages,int copies,String width,String height,PricingMath.DimensionUnit dimensionUnit,
                      UsageBasis basis,long receivedCentavos) {
            QuoteMath.total(0,count);QuoteMath.total(0,pages);QuoteMath.total(0,copies);
            if(basis==null||receivedCentavos<0||receivedCentavos>MAX_TENDER_CENTAVOS)
                throw new IllegalArgumentException("Invalid material basis or cash tender");
            this.count=count;this.pages=pages;this.copies=copies;
            this.width=width;this.height=height;this.dimensionUnit=dimensionUnit;
            this.basis=basis;this.receivedCentavos=receivedCentavos;
        }
    }

    /** Material snapshot must be populated from the saved service recipe, catalog and current stock ledger. */
    public static final class Material {
        public final CatalogEntry entry;
        public final long perUnitMilli,stockRevision,availableMilli;
        public final boolean pendingMovement;
        public Material(CatalogEntry entry,long perUnitMilli,long stockRevision,long availableMilli,boolean pendingMovement) {
            if(entry==null||entry.kind!=CatalogEntry.Kind.MATERIAL||entry.archived)
                throw new IllegalArgumentException("Recipe material unavailable");
            if(perUnitMilli<1||perUnitMilli>StockMath.MAX||stockRevision<0||stockRevision==Long.MAX_VALUE||
               availableMilli<0||availableMilli>StockMath.MAX||(stockRevision==0&&availableMilli!=0))
                throw new IllegalArgumentException("Invalid material stock snapshot");
            this.entry=entry;this.perUnitMilli=perUnitMilli;this.stockRevision=stockRevision;
            this.availableMilli=availableMilli;this.pendingMovement=pendingMovement;
        }
    }

    public static final class Consumption {
        public final String materialId,name,unit;
        public final long catalogRevision,stockRevision,perUnitMilli,quantityMilli,beforeMilli,afterMilli,unitCostCentavos;
        private Consumption(Material m,long quantity) {
            materialId=m.entry.id;name=m.entry.name;unit=m.entry.unit;
            catalogRevision=m.entry.revision;stockRevision=m.stockRevision;perUnitMilli=m.perUnitMilli;
            quantityMilli=quantity;beforeMilli=m.availableMilli;afterMilli=StockMath.apply(beforeMilli,-quantity);
            unitCostCentavos=m.entry.centavos;
        }
        private boolean same(Consumption x) {
            return materialId.equals(x.materialId)&&name.equals(x.name)&&unit.equals(x.unit)&&
                catalogRevision==x.catalogRevision&&stockRevision==x.stockRevision&&perUnitMilli==x.perUnitMilli&&
                quantityMilli==x.quantityMilli&&beforeMilli==x.beforeMilli&&afterMilli==x.afterMilli&&unitCostCentavos==x.unitCostCentavos;
        }
    }

    /** Values frozen for confirmation and later write-transaction revalidation. */
    public static final class Proposal {
        public final String requestId,serviceId,serviceName,serviceUnit;
        public final long serviceRevision,pricingRevision,recipeRevision,billableHundredths,baseRateCentavos,rateCentavos,
            totalCentavos,materialCostCentavos,receivedCentavos,changeCentavos;
        public final int producedItems;
        public final PricingMath.Method method;
        public final UsageBasis basis;
        public final List<Consumption> materials;
        public final Inputs inputs;
        private Proposal(String id,CatalogEntry service,PricingProfile profile,long recipeRevision,Inputs inputs,
                         PricingMath.Result price,int produced,long cost,List<Consumption> materials) {
            this.requestId=id;serviceId=service.id;serviceName=service.name;serviceUnit=service.unit;
            serviceRevision=service.revision;pricingRevision=profile.revision;this.recipeRevision=recipeRevision;
            this.inputs=inputs;billableHundredths=price.quantityHundredths;producedItems=produced;
            baseRateCentavos=service.centavos;rateCentavos=price.rateCentavos;totalCentavos=price.totalCentavos;
            materialCostCentavos=cost;receivedCentavos=inputs.receivedCentavos;changeCentavos=QuoteMath.change(totalCentavos,receivedCentavos);
            method=profile.method;basis=inputs.basis;this.materials=Collections.unmodifiableList(new ArrayList<>(materials));
        }
        /** Same request identity is never permission to substitute an edited or stale confirmation. */
        public boolean sameSnapshot(Proposal x) {
            if(x==null||!requestId.equals(x.requestId)||!serviceId.equals(x.serviceId)||!serviceName.equals(x.serviceName)||
                !serviceUnit.equals(x.serviceUnit)||serviceRevision!=x.serviceRevision||pricingRevision!=x.pricingRevision||
                recipeRevision!=x.recipeRevision||method!=x.method||basis!=x.basis||billableHundredths!=x.billableHundredths||
                producedItems!=x.producedItems||baseRateCentavos!=x.baseRateCentavos||rateCentavos!=x.rateCentavos||
                totalCentavos!=x.totalCentavos||materialCostCentavos!=x.materialCostCentavos||receivedCentavos!=x.receivedCentavos||
                changeCentavos!=x.changeCentavos||materials.size()!=x.materials.size()||
                inputs.count!=x.inputs.count||inputs.pages!=x.inputs.pages||inputs.copies!=x.inputs.copies||
                !java.util.Objects.equals(inputs.width,x.inputs.width)||!java.util.Objects.equals(inputs.height,x.inputs.height)||
                inputs.dimensionUnit!=x.inputs.dimensionUnit)return false;
            for(int i=0;i<materials.size();i++)if(!materials.get(i).same(x.materials.get(i)))return false;
            return true;
        }
        /** Versioned canonical fingerprint for a future durable idempotency record.
         * It commits to every confirmed input and audited snapshot, in sorted material order.
         * It is not an authentication signature. Persist the version with the fingerprint.
         */
        public String fingerprint() {
            try {
                ByteArrayOutputStream buffer=new ByteArrayOutputStream();
                DataOutputStream out=new DataOutputStream(buffer);
                out.writeUTF("CHECKOUT-PLAN-V1");
                out.writeUTF(requestId);out.writeUTF(serviceId);out.writeUTF(serviceName);out.writeUTF(serviceUnit);
                out.writeLong(serviceRevision);out.writeLong(pricingRevision);out.writeLong(recipeRevision);
                out.writeLong(billableHundredths);out.writeLong(baseRateCentavos);out.writeLong(rateCentavos);
                out.writeLong(totalCentavos);out.writeLong(materialCostCentavos);out.writeLong(receivedCentavos);out.writeLong(changeCentavos);
                out.writeInt(producedItems);out.writeUTF(method.name());out.writeUTF(basis.name());
                out.writeInt(inputs.count);out.writeInt(inputs.pages);out.writeInt(inputs.copies);
                nullable(out,inputs.width);nullable(out,inputs.height);
                nullable(out,inputs.dimensionUnit==null?null:inputs.dimensionUnit.name());
                out.writeInt(materials.size());
                for(Consumption m:materials){
                    out.writeUTF(m.materialId);out.writeUTF(m.name);out.writeUTF(m.unit);
                    out.writeLong(m.catalogRevision);out.writeLong(m.stockRevision);out.writeLong(m.perUnitMilli);
                    out.writeLong(m.quantityMilli);out.writeLong(m.beforeMilli);out.writeLong(m.afterMilli);out.writeLong(m.unitCostCentavos);
                }
                out.flush();
                byte[] hash=MessageDigest.getInstance("SHA-256").digest(buffer.toByteArray());
                char[] result=new char[hash.length*2];String hex="0123456789abcdef";
                for(int i=0;i<hash.length;i++){int b=hash[i]&255;result[i*2]=hex.charAt(b>>>4);result[i*2+1]=hex.charAt(b&15);}
                return new String(result);
            }catch(IOException|NoSuchAlgorithmException ex){throw new IllegalStateException("Checkout fingerprint unavailable",ex);}
        }
        private static void nullable(DataOutputStream out,String value)throws IOException {
            out.writeBoolean(value!=null);if(value!=null)out.writeUTF(value);
        }
        public void requireSameSnapshot(Proposal current) {
            if(!sameSnapshot(current))throw new IllegalStateException("Checkout details changed; review a fresh quotation before confirmation");
        }
    }

    private static boolean measured(PricingMath.Method m) {
        return m==PricingMath.Method.PER_SQFT||m==PricingMath.Method.PER_LINEAR_FT||m==PricingMath.Method.PER_METER;
    }
    public static Proposal prepare(String requestId,CatalogEntry service,PricingProfile profile,long recipeRevision,
                                   Inputs inputs,List<Material> materials) {
        StockMath.validateRequestId(requestId);
        if(service==null||service.kind!=CatalogEntry.Kind.SERVICE||service.archived||profile==null||inputs==null||
           recipeRevision<0||materials==null||materials.size()>100)
            throw new IllegalArgumentException("Select an active service and valid recipe");
        if(!materials.isEmpty()&&recipeRevision==0)throw new IllegalArgumentException("Material recipe revision required");
        // Ignore no hidden quantity fields: reject stale, conflicting inputs instead.
        if(profile.pagesAndCopies&&inputs.count!=1)throw new IllegalArgumentException("Document checkout uses pages and copies, not an extra count");
        if(!profile.pagesAndCopies&&(inputs.pages!=1||inputs.copies!=1))
            throw new IllegalArgumentException("Hidden page/copy inputs must be reset");
        if(!measured(profile.method)&&(inputs.width!=null||inputs.height!=null||inputs.dimensionUnit!=null))
            throw new IllegalArgumentException("Hidden dimension inputs must be reset");
        if(materials.isEmpty()&&inputs.basis!=UsageBasis.NONE)
            throw new IllegalArgumentException("No recipe: use no material basis");
        if(!materials.isEmpty()&&inputs.basis==UsageBasis.NONE)
            throw new IllegalArgumentException("Choose the material usage basis");
        if(!materials.isEmpty()&&!profile.needsAdvancedQuote()&&inputs.basis!=UsageBasis.BILLABLE_UNIT)
            throw new IllegalArgumentException("Legacy recipes use the billable-unit basis");
        long quantity=PricingMath.effectiveQuantity(profile.method,inputs.count,profile.pagesAndCopies,inputs.pages,inputs.copies,
            inputs.width,inputs.height,inputs.dimensionUnit);
        int produced=profile.pagesAndCopies?inputs.copies:inputs.count;
        PricingMath.Result price=PricingMath.quote(service.centavos,quantity,profile.tiers);
        if(price.totalCentavos<=0)throw new IllegalArgumentException("Zero-value checkout requires a separate policy");
        QuoteMath.change(price.totalCentavos,inputs.receivedCentavos);
        long usage=inputs.basis==UsageBasis.PRODUCED_ITEM?(long)produced*100:quantity;
        ArrayList<Consumption> consumption=new ArrayList<>();
        ArrayList<MaterialCost.Component> costs=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        for(Material m:materials) {
            if(m==null||!seen.add(m.entry.id))throw new IllegalArgumentException("Duplicate or invalid recipe material");
            if(m.pendingMovement)throw new IllegalStateException("Resolve pending inventory movement before checkout");
            // Existing ledger is exact at 0.001 of a material unit. Do not silently under/over consume.
            BigInteger[] required=BigInteger.valueOf(m.perUnitMilli).multiply(BigInteger.valueOf(usage)).divideAndRemainder(HUNDRED);
            if(required[1].signum()!=0||required[0].signum()<=0||required[0].compareTo(BigInteger.valueOf(StockMath.MAX))>0)
                throw new IllegalArgumentException("Recipe demand is not representable in exact stock milliunits");
            consumption.add(new Consumption(m,required[0].longValueExact()));
            costs.add(new MaterialCost.Component(m.entry.centavos,m.perUnitMilli,m.availableMilli));
        }
        MaterialCost.Estimate estimate=MaterialCost.estimateFractional(costs,usage);
        if(!estimate.stockSufficient)throw new IllegalArgumentException("Insufficient material stock");
        consumption.sort(Comparator.comparing(m->m.materialId));
        return new Proposal(requestId,service,profile,recipeRevision,inputs,price,produced,estimate.costCentavos,consumption);
    }
}
