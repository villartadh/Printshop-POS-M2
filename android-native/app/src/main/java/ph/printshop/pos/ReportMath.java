package ph.printshop.pos;

import java.math.BigInteger;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/** Exact synthetic cash reporting, using Philippine shop calendar boundaries. */
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
            cashIn=BigInteger.ZERO,cashOut=BigInteger.ZERO,sales=BigInteger.ZERO;
        void add(String kind,long amount){
            if(amount<=0)throw new IllegalArgumentException("Invalid report event amount");
            BigInteger n=BigInteger.valueOf(amount);
            switch(kind){
                case "SALE":gross=gross.add(n);sales=sales.add(BigInteger.ONE);break;
                case "REFUND":refunds=refunds.add(n);break;
                case "VOID":voids=voids.add(n);break;
                case "EXPENSE":expenses=expenses.add(n);break;
                case "CASH_IN":cashIn=cashIn.add(n);break;
                case "CASH_OUT":cashOut=cashOut.add(n);break;
                default:throw new IllegalArgumentException("Unknown report event");
            }
        }
        BigInteger net(){return gross.subtract(refunds).subtract(voids);}
        BigInteger movement(){return net().subtract(expenses).add(cashIn).subtract(cashOut);}
    }
    static String display(BigInteger centavos){
        BigInteger[] parts=centavos.abs().divideAndRemainder(BigInteger.valueOf(100));
        return "PHP "+(centavos.signum()<0?"-":"")+parts[0]+"."+(parts[1].intValue()<10?"0":"")+parts[1];
    }
}
