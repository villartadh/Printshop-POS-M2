package ph.printshop.pos;
import java.math.BigInteger;
import java.util.*;
public final class ReceivableMathTest {
    static int checks;
    static final String C="00000000-0000-0000-0000-000000000001", OTHER="00000000-0000-0000-0000-000000000002";
    static String id(int i){return String.format(java.util.Locale.ROOT,"10000000-0000-0000-0000-%012d",i);}
    static void check(boolean v,String s){if(!v)throw new AssertionError(s);checks++;}
    static void reject(Runnable r){try{r.run();throw new AssertionError("Expected refusal");}catch(IllegalArgumentException|IllegalStateException expected){checks++;}}
    static ReceivableMath.Debt debt(int i,long opened,long original,long settled){return new ReceivableMath.Debt(id(i),C,opened,original,settled);}
    public static void main(String[] args){
        var full=ReceivableMath.checkout(ReceivableMath.Mode.FULL_CASH,100,150,null);
        check(full.paid==100&&full.change==50&&full.owed==0,"full cash change");
        var partial=ReceivableMath.checkout(ReceivableMath.Mode.PARTIAL_CASH,100,35,C);
        check(partial.paid==35&&partial.change==0&&partial.owed==65,"partial");
        var unpaid=ReceivableMath.checkout(ReceivableMath.Mode.UNPAID,100,0,C);
        check(unpaid.paid==0&&unpaid.owed==100&&unpaid.change==0,"unpaid");
        reject(()->ReceivableMath.checkout(ReceivableMath.Mode.FULL_CASH,100,99,C));
        reject(()->ReceivableMath.checkout(ReceivableMath.Mode.PARTIAL_CASH,100,0,C));
        reject(()->ReceivableMath.checkout(ReceivableMath.Mode.PARTIAL_CASH,100,100,C));
        reject(()->ReceivableMath.checkout(ReceivableMath.Mode.PARTIAL_CASH,100,101,C));
        reject(()->ReceivableMath.checkout(ReceivableMath.Mode.PARTIAL_CASH,100,10,null));
        reject(()->ReceivableMath.checkout(ReceivableMath.Mode.UNPAID,100,0,null));
        reject(()->ReceivableMath.checkout(ReceivableMath.Mode.UNPAID,100,1,C));
        reject(()->ReceivableMath.checkout(null,100,0,C));
        reject(()->ReceivableMath.checkout(ReceivableMath.Mode.FULL_CASH,0,0,C));
        reject(()->ReceivableMath.checkout(ReceivableMath.Mode.FULL_CASH,1,-1,C));
        reject(()->ReceivableMath.checkout(ReceivableMath.Mode.FULL_CASH,1,Long.MAX_VALUE,C));
        reject(()->ReceivableMath.checkout(ReceivableMath.Mode.UNPAID,100,0,"not-uuid"));
        ReceivableMath.requireLegacyRefundEligible(full);check(true,"full refund eligibility");
        reject(()->ReceivableMath.requireLegacyRefundEligible(partial));reject(()->ReceivableMath.requireLegacyRefundEligible(unpaid));
        var debts=List.of(debt(4,3,10,10),debt(3,2,100,50),debt(2,1,100,20),debt(1,1,100,10));
        var review=ReceivableMath.collect(C,120,1000,debts);
        check(review.outstandingBefore.equals(BigInteger.valueOf(220))&&review.outstandingAfter.equals(BigInteger.valueOf(100)),"outstanding");
        check(review.drawerAfter==1120,"cash once");
        check(review.allocations.size()==2&&review.allocations.get(0).debtId.equals(id(1))&&review.allocations.get(0).amount==90,"FIFO tie");
        check(review.allocations.get(1).debtId.equals(id(2))&&review.allocations.get(1).before==80&&review.allocations.get(1).after==50,"partial allocation");
        var reverse=new ArrayList<>(debts);Collections.reverse(reverse);
        review.requireSameReview(ReceivableMath.collect(C,120,1000,reverse));check(true,"row-order independent");
        reject(()->review.requireSameReview(ReceivableMath.collect(C,120,1001,debts)));
        reject(()->review.requireSameReview(ReceivableMath.collect(C,119,1000,debts)));
        reject(()->review.requireSameReview(ReceivableMath.collect(C,120,1000,List.of(debt(1,1,100,11),debt(2,1,100,20),debt(3,2,100,50)))));
        reject(()->ReceivableMath.collect(C,221,0,debts));
        reject(()->ReceivableMath.collect(C,1,0,List.of()));
        reject(()->ReceivableMath.collect(C,0,0,debts));
        reject(()->ReceivableMath.collect(C,1,ShiftMath.MAX_DRAWER,debts));
        reject(()->ReceivableMath.collect(C,1,0,List.of(debt(1,0,5,0),debt(1,0,5,0))));
        reject(()->ReceivableMath.collect(C,1,0,List.of(new ReceivableMath.Debt(id(1),OTHER,0,5,0))));
        reject(()->debt(1,0,5,6));reject(()->debt(1,-1,5,0));reject(()->debt(1,0,0,0));
        try{review.allocations.clear();throw new AssertionError("mutable");}catch(UnsupportedOperationException e){checks++;}
        var large=new ArrayList<ReceivableMath.Debt>();for(int i=0;i<1000;i++)large.add(debt(i,1,ShiftMath.MAX_DRAWER,0));
        var big=ReceivableMath.collect(C,1,0,large);
        check(big.outstandingBefore.compareTo(BigInteger.valueOf(Long.MAX_VALUE))>0,"aggregate beyond signed long");
        Random rand=new Random(20261127);
        for(int i=0;i<500;i++){
            int n=1+rand.nextInt(20);long available=0;var rows=new ArrayList<ReceivableMath.Debt>();
            for(int j=0;j<n;j++){long owed=1+rand.nextInt(10000),settled=rand.nextInt((int)owed);rows.add(debt(j,rand.nextInt(5),owed,settled));available+=owed-settled;}
            long amount=1+rand.nextInt((int)available);var result=ReceivableMath.collect(C,amount,0,rows);
            check(result.allocations.stream().mapToLong(a->a.amount).sum()==amount,"allocation conservation");
            check(result.allocations.stream().allMatch(a->a.before>=a.amount&&a.after==a.before-a.amount&&a.after>=0),"no source overpayment");
            check(result.outstandingAfter.equals(BigInteger.valueOf(available-amount)),"balance conservation");
            Collections.shuffle(rows,rand);result.requireSameReview(ReceivableMath.collect(C,amount,0,rows));checks++;
        }
        System.out.println("ReceivableMath: "+checks+" checks PASS");
    }
}
