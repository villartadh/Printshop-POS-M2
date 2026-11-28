package ph.printshop.pos;

/** A display-only checkout summary. It is not an issued receipt or evidence of payment. */
public final class CheckoutPreview {
    private CheckoutPreview() {}
    public static String render(CheckoutPlan.Proposal p) {
        if(p==null)throw new IllegalArgumentException("No checkout proposal");
        return "PRINTSHOP POS\nCHECKOUT PREVIEW — NOT RECORDED\nNOT A TAX DOCUMENT OR PROOF OF PAYMENT\n"+
            p.serviceName+"\nPricing: "+PricingProfile.label(p.method)+"\nBillable quantity: "+
            PricingMath.displayQuantity(p.billableHundredths)+"\nRate: "+QuoteMath.display(p.rateCentavos)+
            "\nTotal: "+QuoteMath.display(p.totalCentavos)+"\nProposed cash: "+QuoteMath.display(p.receivedCentavos)+
            "\nProposed change: "+QuoteMath.display(p.changeCentavos)+"\nRequest: "+p.requestId+
            "\nNo sale, payment or stock movement recorded.";
    }
}
