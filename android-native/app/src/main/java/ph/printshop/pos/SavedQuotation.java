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
