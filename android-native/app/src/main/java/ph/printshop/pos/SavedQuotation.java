package ph.printshop.pos;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable commercial quotation, independent of cash received and inventory availability.
 * A quote never reserves stock. Conversion requires a fresh authorized checkout review.
 */
final class SavedQuotation {
    static final class Material {
        final String id,name,unit;
        final long revision,perUnitMilli,unitCost,requiredHundredthMilli;
        Material(CheckoutPlan.Material material,long usage) {
            id=material.entry.id;name=material.entry.name;unit=material.entry.unit;
            revision=material.entry.revision;perUnitMilli=material.perUnitMilli;
            unitCost=material.entry.centavos;
            BigInteger demand=BigInteger.valueOf(perUnitMilli).multiply(BigInteger.valueOf(usage));
            if(demand.signum()<=0||demand.compareTo(BigInteger.valueOf(StockMath.MAX).multiply(BigInteger.valueOf(100)))>0)
                throw new IllegalArgumentException("Material demand outside supported range");
            requiredHundredthMilli=demand.longValue();
        }
        boolean matches(CheckoutPlan.Consumption other) {
            return id.equals(other.materialId)&&name.equals(other.name)&&unit.equals(other.unit)&&revision==other.catalogRevision
                &&perUnitMilli==other.perUnitMilli&&unitCost==other.unitCostCentavos
                &&requiredHundredthMilli%100==0&&requiredHundredthMilli/100==other.quantityMilli;
        }
    }
    final String id,serviceId,serviceName,serviceUnit;
    final long serviceRevision,pricingRevision,recipeRevision,quantityHundredths,baseRateCentavos,rateCentavos,totalCentavos,materialCostCentavos;
    final PricingMath.Method method;
    final CheckoutPlan.Inputs inputs;
    final List<Material> materials;
    final boolean stockSufficientAtReview,pendingStockAtReview,representableForCheckout;

    private SavedQuotation(String id,CatalogEntry service,PricingProfile profile,long recipe,CheckoutPlan.Inputs inputs,
                           PricingMath.Result price,MaterialCost.Estimate estimate,List<Material> materials,boolean pendingStock) {
        this.id=id;serviceId=service.id;serviceName=service.name;serviceUnit=service.unit;
        serviceRevision=service.revision;pricingRevision=profile.revision;recipeRevision=recipe;method=profile.method;
        this.inputs=inputs;quantityHundredths=price.quantityHundredths;baseRateCentavos=service.centavos;rateCentavos=price.rateCentavos;
        totalCentavos=price.totalCentavos;materialCostCentavos=estimate.costCentavos;
        this.materials=Collections.unmodifiableList(new ArrayList<>(materials));
        stockSufficientAtReview=estimate.stockSufficient;pendingStockAtReview=pendingStock;
        boolean exact=true;
        for(Material m:materials)exact &= m.requiredHundredthMilli%100==0;
        representableForCheckout=exact && totalCentavos>0;
    }
    static SavedQuotation review(String id,CatalogEntry service,PricingProfile profile,long recipe,
                                 CheckoutPlan.Inputs inputs,List<CheckoutPlan.Material> source) {
        StockMath.validateRequestId(id);
        if(service==null||service.kind!=CatalogEntry.Kind.SERVICE||service.archived||profile==null||recipe<0||
            inputs==null||inputs.receivedCentavos!=0||source==null||source.size()>100)
            throw new IllegalArgumentException("Select a service and unpaid quotation inputs");
        if(!source.isEmpty()&&recipe==0)throw new IllegalArgumentException("Material recipe revision required");
        if(profile.pagesAndCopies?inputs.count!=1:inputs.pages!=1||inputs.copies!=1)
            throw new IllegalArgumentException("Reset hidden quantity inputs");
        boolean measured=profile.method==PricingMath.Method.PER_SQFT||profile.method==PricingMath.Method.PER_LINEAR_FT||profile.method==PricingMath.Method.PER_METER;
        if(!measured&&(inputs.width!=null||inputs.height!=null||inputs.dimensionUnit!=null))
            throw new IllegalArgumentException("Reset hidden dimension inputs");
        if(source.isEmpty()!=(inputs.basis==CheckoutPlan.UsageBasis.NONE)||
            (!source.isEmpty()&&!profile.needsAdvancedQuote()&&inputs.basis!=CheckoutPlan.UsageBasis.BILLABLE_UNIT))
            throw new IllegalArgumentException("Invalid material usage basis");
        long quantity=PricingMath.effectiveQuantity(profile.method,inputs.count,profile.pagesAndCopies,inputs.pages,inputs.copies,
            inputs.width,inputs.height,inputs.dimensionUnit);
        PricingMath.Result price=PricingMath.quote(service.centavos,quantity,profile.tiers);
        long usage=inputs.basis==CheckoutPlan.UsageBasis.PRODUCED_ITEM?(long)(profile.pagesAndCopies?inputs.copies:inputs.count)*100:quantity;
        List<Material> materials=new ArrayList<>();List<MaterialCost.Component> costs=new ArrayList<>();
        Set<String> seen=new HashSet<>();boolean pendingStock=false;
        for(CheckoutPlan.Material m:source) {
            if(m==null||!seen.add(m.entry.id))throw new IllegalArgumentException("Duplicate or invalid material");
            pendingStock |= m.pendingMovement;
            materials.add(new Material(m,usage));
            costs.add(new MaterialCost.Component(m.entry.centavos,m.perUnitMilli,m.availableMilli));
        }
        materials.sort(Comparator.comparing(m->m.id));
        return new SavedQuotation(id,service,profile,recipe,inputs,price,MaterialCost.estimateFractional(costs,usage),materials,pendingStock);
    }

    /** Canonical immutable commercial snapshot; no tender, stock balances or credentials. */
    String commercialSnapshot() {
        try {
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out=new java.io.DataOutputStream(bytes);
            out.writeUTF("SAVED-QUOTATION-V1");out.writeUTF(id);out.writeUTF(serviceId);out.writeUTF(serviceName);out.writeUTF(serviceUnit);
            out.writeLong(serviceRevision);out.writeLong(pricingRevision);out.writeLong(recipeRevision);out.writeUTF(method.name());
            out.writeLong(quantityHundredths);out.writeLong(baseRateCentavos);out.writeLong(rateCentavos);out.writeLong(totalCentavos);
            out.writeLong(materialCostCentavos);out.writeInt(inputs.count);out.writeInt(inputs.pages);out.writeInt(inputs.copies);
            nullable(out,inputs.width);nullable(out,inputs.height);nullable(out,inputs.dimensionUnit==null?null:inputs.dimensionUnit.name());
            out.writeUTF(inputs.basis.name());out.writeInt(materials.size());
            for(Material m:materials){out.writeUTF(m.id);out.writeUTF(m.name);out.writeUTF(m.unit);out.writeLong(m.revision);
                out.writeLong(m.perUnitMilli);out.writeLong(m.unitCost);out.writeLong(m.requiredHundredthMilli);}
            out.flush();return java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
        }catch(java.io.IOException ex){throw new IllegalStateException("Quotation snapshot encoding failed",ex);}
    }
    private static void nullable(java.io.DataOutputStream out,String value)throws java.io.IOException {
        out.writeBoolean(value!=null);if(value!=null)out.writeUTF(value);
    }

    /** Read-only historical terms; never reconstruct a stock or payment authorization from saved bytes. */
    static final class HistoricalMaterial {
        final String id,name,unit;
        final long revision,perUnitMilli,unitCost,requiredHundredthMilli;
        HistoricalMaterial(java.io.DataInputStream in)throws java.io.IOException{
            id=in.readUTF();name=in.readUTF();unit=in.readUTF();revision=in.readLong();
            perUnitMilli=in.readLong();unitCost=in.readLong();requiredHundredthMilli=in.readLong();
            StockMath.validateRequestId(id);QuoteMath.total(unitCost,1);
            if(revision<1||perUnitMilli<1||perUnitMilli>StockMath.MAX||requiredHundredthMilli<1||
                requiredHundredthMilli>StockMath.MAX*100||name.isEmpty()||unit.isEmpty())throw new IllegalArgumentException("Invalid historical material");
        }
    }
    static final class Historical {
        final String id,serviceId,serviceName,serviceUnit;
        final long serviceRevision,pricingRevision,recipeRevision,quantityHundredths,baseRate,rate,total,materialCost;
        final PricingMath.Method method;
        final CheckoutPlan.Inputs inputs;
        final List<HistoricalMaterial> materials;
        Historical(java.io.DataInputStream in)throws java.io.IOException{
            if(!"SAVED-QUOTATION-V1".equals(in.readUTF()))throw new IllegalArgumentException("Unsupported quotation snapshot");
            id=in.readUTF();serviceId=in.readUTF();serviceName=in.readUTF();serviceUnit=in.readUTF();
            StockMath.validateRequestId(id);StockMath.validateRequestId(serviceId);
            serviceRevision=in.readLong();pricingRevision=in.readLong();recipeRevision=in.readLong();
            method=PricingMath.Method.valueOf(in.readUTF());quantityHundredths=in.readLong();baseRate=in.readLong();rate=in.readLong();total=in.readLong();materialCost=in.readLong();
            QuoteMath.total(baseRate,1);QuoteMath.total(rate,1);
            if(serviceRevision<1||pricingRevision<0||recipeRevision<0||quantityHundredths<1||
                quantityHundredths>PricingMath.MAX_QUANTITY_HUNDREDTHS||total<0||materialCost<0||
                serviceName.isEmpty()||serviceUnit.isEmpty())throw new IllegalArgumentException("Invalid historical quotation");
            long expected=PricingMath.quote(rate,quantityHundredths,Collections.emptyList()).totalCentavos;
            if(total!=expected)throw new IllegalArgumentException("Historical quotation amount mismatch");
            int count=in.readInt(),pages=in.readInt(),copies=in.readInt();
            String width=readNullable(in),height=readNullable(in),dimension=readNullable(in);
            inputs=new CheckoutPlan.Inputs(count,pages,copies,width,height,dimension==null?null:PricingMath.DimensionUnit.valueOf(dimension),
                CheckoutPlan.UsageBasis.valueOf(in.readUTF()),0);
            int n=in.readInt();if(n<0||n>100)throw new IllegalArgumentException("Invalid historical material count");
            List<HistoricalMaterial> rows=new ArrayList<>();String last=null;
            for(int i=0;i<n;i++){HistoricalMaterial m=new HistoricalMaterial(in);
                if(last!=null&&last.compareTo(m.id)>=0)throw new IllegalArgumentException("Invalid historical material ordering");last=m.id;rows.add(m);}
            if(in.read()!=-1)throw new IllegalArgumentException("Trailing quotation data");
            materials=Collections.unmodifiableList(rows);
        }
        CheckoutPlan.Inputs withTender(long tender){
            return new CheckoutPlan.Inputs(inputs.count,inputs.pages,inputs.copies,inputs.width,inputs.height,inputs.dimensionUnit,inputs.basis,tender);
        }
    }
    private static String readNullable(java.io.DataInputStream in)throws java.io.IOException{
        return in.readBoolean()?in.readUTF():null;
    }
    static Historical decode(String encoded){
        if(encoded==null||encoded.isEmpty()||encoded.length()>250000)throw new IllegalArgumentException("Invalid quotation snapshot");
        try{
            byte[] bytes=java.util.Base64.getDecoder().decode(encoded);
            if(!java.util.Base64.getEncoder().encodeToString(bytes).equals(encoded))throw new IllegalArgumentException("Invalid quotation encoding");
            try(java.io.DataInputStream in=new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes))){return new Historical(in);}
        }catch(java.io.IOException ex){throw new IllegalArgumentException("Invalid quotation snapshot",ex);}
    }

    /** Stock availability/tender may change. Commercial inputs and provenance must not.
     * The checkout writer must independently revalidate its own current full snapshot.
     */
    void requireSameCommercialTerms(CheckoutPlan.Proposal current) {
        if(!representableForCheckout||current==null||!serviceId.equals(current.serviceId)||!serviceName.equals(current.serviceName)||
            !serviceUnit.equals(current.serviceUnit)||serviceRevision!=current.serviceRevision||pricingRevision!=current.pricingRevision||
            recipeRevision!=current.recipeRevision||method!=current.method||inputs.basis!=current.basis||
            quantityHundredths!=current.billableHundredths||baseRateCentavos!=current.baseRateCentavos||rateCentavos!=current.rateCentavos||totalCentavos!=current.totalCentavos||
            materialCostCentavos!=current.materialCostCentavos||inputs.count!=current.inputs.count||inputs.pages!=current.inputs.pages||
            inputs.copies!=current.inputs.copies||!Objects.equals(inputs.width,current.inputs.width)||
            !Objects.equals(inputs.height,current.inputs.height)||inputs.dimensionUnit!=current.inputs.dimensionUnit||
            materials.size()!=current.materials.size())throw new IllegalStateException("Quotation terms changed; review a new quotation");
        for(int i=0;i<materials.size();i++)if(!materials.get(i).matches(current.materials.get(i)))
            throw new IllegalStateException("Quotation material terms changed; review a new quotation");
    }
}
