package ph.printshop.pos;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/** A separate credit review type. Cannot be passed to the existing full-cash checkout stage API. */
final class CreditCheckoutPlan {
    final String id,customerId,serviceId,fingerprint;
    final long customerRevision,serviceRevision,pricingRevision,recipeRevision,billableHundredths,usageHundredths,rate,total,paid,owed;
    final CheckoutPlan.Inputs actualInputs;
    final List<CheckoutPlan.Consumption> materials;
    private CreditCheckoutPlan(String customerId,long customerRevision,CheckoutPlan.Proposal pricing,CheckoutPlan.Inputs actual,ReceivableMath.Payment payment){
        this.id=pricing.requestId;this.customerId=customerId;this.customerRevision=customerRevision;serviceId=pricing.serviceId;
        serviceRevision=pricing.serviceRevision;pricingRevision=pricing.pricingRevision;recipeRevision=pricing.recipeRevision;
        billableHundredths=pricing.billableHundredths;usageHundredths=pricing.basis==CheckoutPlan.UsageBasis.PRODUCED_ITEM?pricing.producedItems*100L:billableHundredths;
        rate=pricing.rateCentavos;total=payment.total;paid=payment.paid;owed=payment.owed;actualInputs=actual;materials=pricing.materials;
        fingerprint=digest("CREDIT-CHECKOUT-V1|"+pricing.fingerprint()+"|"+customerId+"|"+customerRevision+"|"+payment.mode+"|"+paid);
    }
    static CreditCheckoutPlan prepare(String id,String customerId,long customerRevision,ReceivableMath.Mode mode,CatalogEntry service,PricingProfile profile,long recipeRevision,CheckoutPlan.Inputs actual,List<CheckoutPlan.Material> materials){
        StockMath.validateRequestId(customerId);
        if(customerRevision<1||mode==null||mode==ReceivableMath.Mode.FULL_CASH||service==null||profile==null||actual==null)
            throw new IllegalArgumentException("Invalid credit checkout review");
        long quantity=PricingMath.effectiveQuantity(profile.method,actual.count,profile.pagesAndCopies,actual.pages,actual.copies,actual.width,actual.height,actual.dimensionUnit);
        long total=PricingMath.quote(service.centavos,quantity,profile.tiers).totalCentavos;
        ReceivableMath.Payment payment=ReceivableMath.checkout(mode,total,actual.receivedCentavos,customerId);
        // Internally reuse the existing pure pricing/stock calculator with neutral exact-total tender.
        // This internal object is never returned, persisted or recorded as a payment.
        CheckoutPlan.Inputs priceOnly=new CheckoutPlan.Inputs(actual.count,actual.pages,actual.copies,actual.width,actual.height,actual.dimensionUnit,actual.basis,total);
        CheckoutPlan.Proposal pricing=CheckoutPlan.prepare(id,service,profile,recipeRevision,priceOnly,materials);
        return new CreditCheckoutPlan(customerId,customerRevision,pricing,actual,payment);
    }
    void requireSameReview(CreditCheckoutPlan fresh){
        if(fresh==null||!fingerprint.equals(fresh.fingerprint))throw new IllegalStateException("Credit checkout changed; review again");
    }
    private static String digest(String value){
        try{
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            char[] out=new char[hash.length*2];String hex="0123456789abcdef";
            for(int i=0;i<hash.length;i++){int b=hash[i]&255;out[i*2]=hex.charAt(b>>>4);out[i*2+1]=hex.charAt(b&15);}return new String(out);
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException("Credit fingerprint unavailable",e);}
    }
}
