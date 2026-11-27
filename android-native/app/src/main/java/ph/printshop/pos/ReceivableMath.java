package ph.printshop.pos;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Exact, side-effect-free policy for the future native credit journal. Not a payment API. */
public final class ReceivableMath {
    private ReceivableMath(){}
    public enum Mode { FULL_CASH, PARTIAL_CASH, UNPAID }
    public static final class Payment {
        public final Mode mode;
        public final long total,tender,paid,change,owed;
        private Payment(Mode mode,long total,long tender,long paid,long change,long owed){
            this.mode=mode;this.total=total;this.tender=tender;this.paid=paid;this.change=change;this.owed=owed;
        }
    }
    /** Customer identity is mandatory for any outstanding credit; walk-in stays full cash. */
    public static Payment checkout(Mode mode,long total,long tender,String customer){
        if(mode==null||total<=0)throw new IllegalArgumentException("Invalid credit review");
        ShiftMath.money(total);ShiftMath.money(tender);
        if(customer!=null)StockMath.validateRequestId(customer);
        switch(mode){
            case FULL_CASH:
                if(tender<total)throw new IllegalArgumentException("Insufficient full cash tender");
                return new Payment(mode,total,tender,total,tender-total,0);
            case PARTIAL_CASH:
                if(customer==null||tender<=0||tender>=total)throw new IllegalArgumentException("Partial payment requires customer and positive tender below total");
                return new Payment(mode,total,tender,tender,0,total-tender);
            case UNPAID:
                if(customer==null||tender!=0)throw new IllegalArgumentException("Unpaid sale requires customer and zero tender");
                return new Payment(mode,total,0,0,0,total);
            default:throw new IllegalArgumentException("Invalid credit mode");
        }
    }
    /** Outstanding is derived from immutable original debt and applied allocations, not a mutable customer balance. */
    public static final class Debt {
        public final String id,customer;
        public final long openedMs,original,settled;
        public Debt(String id,String customer,long openedMs,long original,long settled){
            StockMath.validateRequestId(id);StockMath.validateRequestId(customer);
            if(openedMs<0||original<=0||settled<0||settled>original)throw new IllegalArgumentException("Invalid receivable source");
            ShiftMath.money(original);
            this.id=id;this.customer=customer;this.openedMs=openedMs;this.original=original;this.settled=settled;
        }
        public long remaining(){return original-settled;}
    }
    public static final class Allocation {
        public final String debtId;
        public final long before,amount,after;
        private Allocation(String id,long before,long amount){debtId=id;this.before=before;this.amount=amount;after=before-amount;}
        @Override public boolean equals(Object x){
            if(!(x instanceof Allocation))return false;Allocation a=(Allocation)x;
            return debtId.equals(a.debtId)&&before==a.before&&amount==a.amount&&after==a.after;
        }
        @Override public int hashCode(){return Objects.hash(debtId,before,amount,after);}
    }
    public static final class Collection {
        public final String customer;
        public final long amount,drawerBefore,drawerAfter;
        public final BigInteger outstandingBefore,outstandingAfter;
        public final List<Allocation> allocations;
        private Collection(String customer,long amount,long drawerBefore,long drawerAfter,BigInteger outstanding,List<Allocation> allocations){
            this.customer=customer;this.amount=amount;this.drawerBefore=drawerBefore;this.drawerAfter=drawerAfter;
            outstandingBefore=outstanding;outstandingAfter=outstanding.subtract(BigInteger.valueOf(amount));
            this.allocations=Collections.unmodifiableList(new ArrayList<>(allocations));
        }
        public void requireSameReview(Collection fresh){
            if(fresh==null||!customer.equals(fresh.customer)||amount!=fresh.amount||drawerBefore!=fresh.drawerBefore||
                drawerAfter!=fresh.drawerAfter||!outstandingBefore.equals(fresh.outstandingBefore)||!allocations.equals(fresh.allocations))
                throw new IllegalStateException("Collection changed; reload and review");
        }
    }
    /** Cash collection allocates oldest debt first, independent of database row order. */
    public static Collection collect(String customer,long amount,long drawerBefore,List<Debt> snapshot){
        StockMath.validateRequestId(customer);
        if(amount<=0||snapshot==null)throw new IllegalArgumentException("Invalid collection");
        ShiftMath.money(amount);
        long drawerAfter=ShiftMath.addPayment(drawerBefore,amount);
        List<Debt> debts=new ArrayList<>();HashSet<String> seen=new HashSet<>();BigInteger outstanding=BigInteger.ZERO;
        for(Debt d:snapshot){
            if(d==null||!customer.equals(d.customer)||!seen.add(d.id))throw new IllegalArgumentException("Invalid customer debt snapshot");
            if(d.remaining()>0){debts.add(d);outstanding=outstanding.add(BigInteger.valueOf(d.remaining()));}
        }
        if(outstanding.compareTo(BigInteger.valueOf(amount))<0)throw new IllegalArgumentException("Collection exceeds outstanding balance");
        debts.sort(Comparator.comparingLong((Debt d)->d.openedMs).thenComparing(d->d.id));
        List<Allocation> allocations=new ArrayList<>();long left=amount;
        for(Debt d:debts){
            if(left==0)break;long allocated=Math.min(left,d.remaining());
            allocations.add(new Allocation(d.id,d.remaining(),allocated));left-=allocated;
        }
        if(left!=0)throw new IllegalStateException("Allocation incomplete");
        return new Collection(customer,amount,drawerBefore,drawerAfter,outstanding,allocations);
    }
    /** Credit reversals need a separate audited policy; never route through existing full-cash refund. */
    public static void requireLegacyRefundEligible(Payment p){
        if(p==null||p.mode!=Mode.FULL_CASH||p.owed!=0)throw new IllegalStateException("Credit sale requires dedicated reversal");
    }
}
