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
        Estimate(long costCentavos,boolean stockSufficient){this.costCentavos=costCentavos;this.stockSufficient=stockSufficient;}
    }
    public static Estimate estimate(List<Component> components,int count){
        QuoteMath.total(0,count);
        if(components==null||components.size()>100)throw new IllegalArgumentException("Too many material bindings");
        BigInteger subtotal=BigInteger.ZERO;
        boolean sufficient=true;
        for(Component c:components){
            BigInteger required=BigInteger.valueOf(c.perUnitMilli).multiply(BigInteger.valueOf(count));
            if(required.compareTo(BigInteger.valueOf(StockMath.MAX))>0)throw new IllegalArgumentException("Required material exceeds supported quantity");
            sufficient &= required.compareTo(BigInteger.valueOf(c.availableMilli))<=0;
            subtotal=subtotal.add(required.multiply(BigInteger.valueOf(c.unitCost)));
        }
        BigInteger rounded=subtotal.add(BigInteger.valueOf(500)).divide(BigInteger.valueOf(1000));
        if(rounded.compareTo(BigInteger.valueOf(QuoteMath.total(99_999_999_999L,100_000)))>0)
            throw new IllegalArgumentException("Estimated material cost exceeds supported amount");
        return new Estimate(rounded.longValue(),sufficient);
    }
}
