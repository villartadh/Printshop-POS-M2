package ph.printshop.pos;

import java.util.List;

/** Integer-centavo fixture drawer accounting. Excludes voids/refunds/expenses until separately implemented. */
public final class ShiftMath {
    public static final long MAX_DRAWER=9_999_999_999_900_000L;
    private ShiftMath(){}
    public static long money(long amount){
        if(amount<0||amount>MAX_DRAWER)throw new IllegalArgumentException("Drawer amount outside supported range");
        return amount;
    }
    public static long addPayment(long expected,long appliedCash){
        money(expected);money(appliedCash);
        if(appliedCash==0)throw new IllegalArgumentException("Recorded cash payment must be positive");
        return money(Math.addExact(expected,appliedCash));
    }
    public static long expected(long opening,List<Long> payments){
        long result=money(opening);
        if(payments==null)throw new IllegalArgumentException("Payment list required");
        for(Long paid:payments){if(paid==null)throw new IllegalArgumentException("Invalid cash payment");result=addPayment(result,paid);}
        return result;
    }
    public static long variance(long expected,long counted){return Math.subtractExact(money(counted),money(expected));}
}
