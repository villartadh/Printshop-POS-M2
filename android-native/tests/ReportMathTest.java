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
        ReportMath.Range day=ReportMath.range(ReportMath.Period.DAY,ms("2026-11-27T00:00:00Z"));
        ReportMath.Series series=new ReportMath.Series(ReportMath.Period.DAY,day);
        check(series.buckets.size()==24,"24 hourly buckets");
        series.add("SALE",100,day.start);series.add("REFUND",300,day.start+3600000);
        series.add("VOID",100,day.end-1);series.add("CASH_IN",999,day.start);
        check(series.buckets.get(0).totals.net().intValueExact()==100,"start inclusion and cash-in excluded");
        check(series.buckets.get(1).totals.net().intValueExact()==-300,"next hour exact");
        check(series.buckets.get(23).totals.net().intValueExact()==-100,"end minus one");
        check(series.maxAbs().intValueExact()==300,"shared signed scale");
        check(ReportMath.bar(BigInteger.valueOf(100),series.maxAbs())==333,"positive bar");
        check(ReportMath.bar(BigInteger.valueOf(-300),series.maxAbs())==-1000,"negative full scale");
        check(ReportMath.bar(BigInteger.ZERO,BigInteger.ZERO)==0,"zero scale");
        check(ReportMath.bar(BigInteger.ONE,BigInteger.TEN.pow(100))==1,"minimum nonzero visible");
        check(ReportMath.bar(BigInteger.ONE.negate(),BigInteger.TEN.pow(100))==-1,"negative min visible");
        try{series.add("SALE",1,day.end);throw new AssertionError("end accepted");}catch(IllegalArgumentException expected){checks++;}
        try{ReportMath.bar(BigInteger.TEN,BigInteger.ONE);throw new AssertionError("scale accepted");}catch(IllegalArgumentException expected){checks++;}
        ReportMath.Range week=ReportMath.range(ReportMath.Period.WEEK,day.start);
        check(new ReportMath.Series(ReportMath.Period.WEEK,week).buckets.size()==7,"weekly chart");
        ReportMath.Range leap=ReportMath.range(ReportMath.Period.MONTH,ms("2024-02-29T12:00:00Z"));
        check(new ReportMath.Series(ReportMath.Period.MONTH,leap).buckets.size()==29,"leap chart");
        ReportMath.Range month=ReportMath.range(ReportMath.Period.MONTH,ms("2026-12-31T12:00:00Z"));
        check(new ReportMath.Series(ReportMath.Period.MONTH,month).buckets.size()==31,"31 day chart");
        try{new ReportMath.Series(ReportMath.Period.DAY,new ReportMath.Range(day.start,day.end-1));throw new AssertionError("noncanonical chart accepted");}catch(IllegalArgumentException expected){checks++;}
        ReportMath.Totals mixed=new ReportMath.Totals();
        mixed.add("SALE",500);mixed.add("CREDIT_SALE",600,200);mixed.add("CREDIT_SALE",300,0);
        mixed.add("COLLECTION",400);mixed.add("REFUND",100);mixed.add("EXPENSE",50);mixed.add("CASH_IN",20);mixed.add("CASH_OUT",10);
        check(mixed.gross.intValueExact()==1400&&mixed.sales.intValueExact()==3,"credit gross and receipt count");
        check(mixed.net().intValueExact()==1300,"net accrual revenue");
        check(mixed.upfrontCash.intValueExact()==700&&mixed.debtCreated.intValueExact()==700,"upfront versus debt");
        check(mixed.debtCollected.intValueExact()==400&&mixed.debtMovement().intValueExact()==300,"debt period movement");
        check(mixed.cashCollected().intValueExact()==1000&&mixed.movement().intValueExact()==960,"cash reconciliation");
        ReportMath.Totals later=new ReportMath.Totals();later.add("COLLECTION",400);
        check(later.net().signum()==0&&later.sales.signum()==0&&later.debtMovement().intValueExact()==-400&&later.movement().intValueExact()==400,"collection-only period");
        series.add("CREDIT_SALE",500,100,day.start);series.add("COLLECTION",900,day.start);
        check(series.buckets.get(0).totals.net().intValueExact()==600,"chart includes credit gross excludes collection");
        try{mixed.add("CREDIT_SALE",100,100);throw new AssertionError("invalid credit accepted");}catch(IllegalArgumentException expected){checks++;}
        try{mixed.add("COLLECTION",100,20);throw new AssertionError("invalid collection upfront accepted");}catch(IllegalArgumentException expected){checks++;}
        for(int i=0;i<1000;i++){mixed.add("CREDIT_SALE",max,0);mixed.add("COLLECTION",max);}
        check(mixed.debtCreated.compareTo(BigInteger.valueOf(Long.MAX_VALUE))>0&&mixed.debtMovement().intValueExact()==300,"large debt exact");
        System.out.println("ReportMath: "+checks+" checks PASS");
    }
}

