package ph.printshop.pos;

import java.util.List;

/** Integer-centavo fixture drawer accounting. Includes explicit expenses and drawer adjustments; void/refund policy is separate. */
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
    public enum DrawerKind { EXPENSE, CASH_IN, CASH_OUT }
    public static String category(DrawerKind kind,String category){
        if(kind==null)throw new IllegalArgumentException("Select a drawer activity");
        if(kind!=DrawerKind.EXPENSE){
            if(category!=null)throw new IllegalArgumentException("Category is only for expenses");
            return null;
        }
        if(category==null)throw new IllegalArgumentException("Enter an expense category (1–80 printable characters)");
        String label=category.trim();
        if(label.isEmpty()||label.length()>80||label.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Enter an expense category (1–80 printable characters)");
        return label;
    }
    public static long drawerAfter(long before,DrawerKind kind,long amount){
        money(before);money(amount);
        if(kind==null)throw new IllegalArgumentException("Select a drawer activity");
        if(amount==0)throw new IllegalArgumentException("Drawer amount must be positive");
        long after=kind==DrawerKind.CASH_IN?Math.addExact(before,amount):Math.subtractExact(before,amount);
        if(after<0)throw new IllegalArgumentException("Insufficient expected drawer cash");
        return money(after);
    }
    public static long variance(long expected,long counted){return Math.subtractExact(money(counted),money(expected));}
}
