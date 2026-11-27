package ph.printshop.pos;
import java.math.BigInteger;
import java.util.List;

/** Cost estimate, separate from selling price. Sum exact components before rounding once. */
public final class MaterialCost {
    private MaterialCost(){}
    public static final class Component {
        public final long unitCost, perUnitMilli, availableMilli;
        public Component(long unitCost,long perUnitMilli,long availableMilli){
            QuoteMath.total(unitCost,1);
            if(perUnitMilli<=0||perUnitMilli>StockMath.MAX||availableMilli<0||availableMilli>StockMath.MAX)
                throw new IllegalArgumentException("Invalid material quantities");
            this.unitCost=unitCost;this.perUnitMilli=perUnitMilli;this.availableMilli=availableMilli;
        }
    }
    public static final class Estimate {
        public final long costCentavos;
        public final boolean stockSufficient;
        public final int componentCount;
        Estimate(long costCentavos,boolean stockSufficient,int componentCount){this.costCentavos=costCentavos;this.stockSufficient=stockSufficient;this.componentCount=componentCount;}
    }
    public static Estimate estimate(List<Component> components,int count){
        QuoteMath.total(0,count);
        return estimateFractional(components,(long)count*100);
    }
    /** Hundredths of a billed unit; demand is exact at 1/100 of a stock milliunit.
     * Compare against exact on-hand stock before rounding the monetary total once.
     */
    public static Estimate estimateFractional(List<Component> components,long unitsHundredths){
        if(unitsHundredths<1||unitsHundredths>PricingMath.MAX_QUANTITY_HUNDREDTHS)
            throw new IllegalArgumentException("Material demand quantity outside supported range");
        if(components==null||components.size()>100)throw new IllegalArgumentException("Too many material bindings");
        BigInteger scale=BigInteger.valueOf(100);
        BigInteger subtotal=BigInteger.ZERO;
        boolean sufficient=true;
        for(Component c:components){
            if(c==null)throw new IllegalArgumentException("Invalid material component");
            BigInteger required=BigInteger.valueOf(c.perUnitMilli).multiply(BigInteger.valueOf(unitsHundredths));
            if(required.compareTo(BigInteger.valueOf(StockMath.MAX).multiply(scale))>0)throw new IllegalArgumentException("Required material exceeds supported quantity");
            sufficient &= required.compareTo(BigInteger.valueOf(c.availableMilli).multiply(scale))<=0;
            subtotal=subtotal.add(required.multiply(BigInteger.valueOf(c.unitCost)));
        }
        BigInteger rounded=subtotal.add(BigInteger.valueOf(50_000)).divide(BigInteger.valueOf(100_000));
        if(rounded.compareTo(BigInteger.valueOf(QuoteMath.total(99_999_999_999L,100_000)))>0)
            throw new IllegalArgumentException("Estimated material cost exceeds supported amount");
        return new Estimate(rounded.longValue(),sufficient,components.size());
    }
}
