package ph.printshop.pos;

import java.math.BigInteger;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/** Exact synthetic accrual sales and cash reporting, using Philippine shop calendar boundaries. */
final class ReportMath {
    private ReportMath(){}
    enum Period { DAY, WEEK, MONTH }
    static final class Range {
        final long start,end;
        Range(long start,long end){
            if(start<0||end<=start)throw new IllegalArgumentException("Invalid report range");
            this.start=start;this.end=end;
        }
    }
    static Range range(Period period,long now){
        if(now<0||period==null)throw new IllegalArgumentException("Invalid report period");
        Calendar c=new GregorianCalendar(TimeZone.getTimeZone("Asia/Manila"));
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
        if(period==Period.WEEK)c.add(Calendar.DAY_OF_MONTH,-((c.get(Calendar.DAY_OF_WEEK)+5)%7));
        if(period==Period.MONTH)c.set(Calendar.DAY_OF_MONTH,1);
        long start=c.getTimeInMillis();
        if(period==Period.MONTH)c.add(Calendar.MONTH,1);else c.add(Calendar.DAY_OF_MONTH,period==Period.WEEK?7:1);
        return new Range(start,c.getTimeInMillis());
    }
    static final class Totals {
        BigInteger gross=BigInteger.ZERO, refunds=BigInteger.ZERO, voids=BigInteger.ZERO, expenses=BigInteger.ZERO,
            cashIn=BigInteger.ZERO,cashOut=BigInteger.ZERO,sales=BigInteger.ZERO,
            upfrontCash=BigInteger.ZERO,debtCreated=BigInteger.ZERO,debtCollected=BigInteger.ZERO;
        void add(String kind,long amount){add(kind,amount,"SALE".equals(kind)?amount:0);}
        void add(String kind,long amount,long upfront){
            if(amount<=0||upfront<0||("SALE".equals(kind)?upfront!=amount:"CREDIT_SALE".equals(kind)?upfront>=amount:upfront!=0))throw new IllegalArgumentException("Invalid report event amount");
            BigInteger n=BigInteger.valueOf(amount);
            switch(kind){
                case "SALE":case "CREDIT_SALE":
                    gross=gross.add(n);sales=sales.add(BigInteger.ONE);upfrontCash=upfrontCash.add(BigInteger.valueOf(upfront));
                    debtCreated=debtCreated.add(BigInteger.valueOf(amount-upfront));break;
                case "COLLECTION":debtCollected=debtCollected.add(n);break;
                case "REFUND":refunds=refunds.add(n);break;
                case "VOID":voids=voids.add(n);break;
                case "EXPENSE":expenses=expenses.add(n);break;
                case "CASH_IN":cashIn=cashIn.add(n);break;
                case "CASH_OUT":cashOut=cashOut.add(n);break;
                default:throw new IllegalArgumentException("Unknown report event");
            }
        }
        BigInteger net(){return gross.subtract(refunds).subtract(voids);}
        BigInteger cashCollected(){return upfrontCash.add(debtCollected).subtract(refunds).subtract(voids);}
        BigInteger debtMovement(){return debtCreated.subtract(debtCollected);}
        BigInteger movement(){return cashCollected().subtract(expenses).add(cashIn).subtract(cashOut);}
    }
    static final class Bucket {
        final long start,end;
        final Totals totals=new Totals();
        Bucket(long start,long end){this.start=start;this.end=end;}
    }
    static final class Series {
        final java.util.List<Bucket> buckets=new java.util.ArrayList<>();
        final Range range;
        Series(Period period,Range range){
            Range canonical=ReportMath.range(period,range.start);
            if(canonical.start!=range.start||canonical.end!=range.end)throw new IllegalArgumentException("Invalid chart range");
            this.range=range;
            Calendar c=new GregorianCalendar(TimeZone.getTimeZone("Asia/Manila"));
            c.setTimeInMillis(range.start);
            while(c.getTimeInMillis()<range.end){
                long start=c.getTimeInMillis();
                c.add(period==Period.DAY?Calendar.HOUR_OF_DAY:Calendar.DAY_OF_MONTH,1);
                buckets.add(new Bucket(start,Math.min(c.getTimeInMillis(),range.end)));
                if(buckets.size()>31)throw new IllegalArgumentException("Invalid chart length");
            }
        }
        void add(String kind,long amount,long at){add(kind,amount,"SALE".equals(kind)?amount:0,at);}
        void add(String kind,long amount,long upfront,long at){
            if(at<range.start||at>=range.end)throw new IllegalArgumentException("Chart event outside range");
            if(!"SALE".equals(kind)&&!"CREDIT_SALE".equals(kind)&&!"REFUND".equals(kind)&&!"VOID".equals(kind))return;
            for(Bucket bucket:buckets)if(at>=bucket.start&&at<bucket.end){bucket.totals.add(kind,amount,upfront);return;}
            throw new IllegalStateException("Chart bucket unavailable");
        }
        BigInteger maxAbs(){
            BigInteger max=BigInteger.ZERO;
            for(Bucket b:buckets)max=max.max(b.totals.net().abs());
            return max;
        }
    }
    /** Signed thousandths of half chart width; never overflow or conceal nonzero values. */
    static int bar(BigInteger net,BigInteger max){
        if(max.signum()<0||net.abs().compareTo(max)>0)throw new IllegalArgumentException("Invalid chart scale");
        if(net.signum()==0)return 0;
        int absolute=net.abs().multiply(BigInteger.valueOf(1000)).divide(max).intValueExact();
        return net.signum()*Math.max(1,absolute);
    }
    static String display(BigInteger centavos){
        BigInteger[] parts=centavos.abs().divideAndRemainder(BigInteger.valueOf(100));
        return "PHP "+(centavos.signum()<0?"-":"")+parts[0]+"."+(parts[1].intValue()<10?"0":"")+parts[1];
    }
}

