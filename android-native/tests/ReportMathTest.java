package ph.printshop.pos;
import java.math.BigInteger;
import java.time.Instant;
class ReportMathTest {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private static long ms(String value){return Instant.parse(value).toEpochMilli();}
    private static void range(ReportMath.Period p,String now,String start,String end){
        ReportMath.Range r=ReportMath.range(p,ms(now));
        check(r.start==ms(start),p+" start");check(r.end==ms(end),p+" end");
    }
    public static void main(String[] args){
        range(ReportMath.Period.DAY,"2026-11-26T15:59:59.999Z","2026-11-25T16:00:00Z","2026-11-26T16:00:00Z");
        range(ReportMath.Period.DAY,"2026-11-26T16:00:00Z","2026-11-26T16:00:00Z","2026-11-27T16:00:00Z");
        range(ReportMath.Period.WEEK,"2026-11-29T15:59:59Z","2026-11-22T16:00:00Z","2026-11-29T16:00:00Z");
        range(ReportMath.Period.WEEK,"2026-11-29T16:00:00Z","2026-11-29T16:00:00Z","2026-12-06T16:00:00Z");
        range(ReportMath.Period.MONTH,"2024-02-29T12:00:00Z","2024-01-31T16:00:00Z","2024-02-29T16:00:00Z");
        range(ReportMath.Period.MONTH,"2026-12-31T16:00:00Z","2026-12-31T16:00:00Z","2027-01-31T16:00:00Z");
        ReportMath.Totals t=new ReportMath.Totals();
        check(t.net().signum()==0&&t.movement().signum()==0,"empty report");
        t.add("SALE",2600);t.add("REFUND",200);t.add("VOID",2100);
        t.add("EXPENSE",125);t.add("CASH_IN",500);t.add("CASH_OUT",200);
        check(t.net().equals(BigInteger.valueOf(300)),"net sales");
        check(t.movement().equals(BigInteger.valueOf(475)),"movement");
        check(t.sales.equals(BigInteger.ONE),"sale count");
        ReportMath.Totals negative=new ReportMath.Totals();negative.add("REFUND",101);
        check(negative.net().equals(BigInteger.valueOf(-101)),"refund-only period");
        check(ReportMath.display(negative.net()).equals("PHP -1.01"),"negative display");
        check(ReportMath.display(BigInteger.ONE).equals("PHP 0.01"),"centavo display");
        check(ReportMath.display(BigInteger.ZERO).equals("PHP 0.00"),"zero display");
        long max=9999999999900000L;
        for(int i=0;i<1000;i++){negative.add("CASH_IN",max);negative.add("CASH_OUT",max);}
        check(negative.cashIn.compareTo(BigInteger.valueOf(Long.MAX_VALUE))>0,"arbitrary precision");
        check(negative.cashIn.equals(negative.cashOut)&&negative.movement().equals(BigInteger.valueOf(-101)),"gross overflow safe");
        check(ReportMath.display(negative.cashIn).equals("PHP 99999999999000000.00"),"large exact formatting");
        try{negative.add("SALE",0);throw new AssertionError("zero accepted");}catch(IllegalArgumentException expected){checks++;}
        try{negative.add("CANCELLED",100);throw new AssertionError("cancelled accepted");}catch(IllegalArgumentException expected){checks++;}
        try{new ReportMath.Range(20,20);throw new AssertionError("empty range");}catch(IllegalArgumentException expected){checks++;}
        System.out.println("ReportMath: "+checks+" checks PASS");
    }
}
