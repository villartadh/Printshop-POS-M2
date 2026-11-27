package ph.printshop.pos;
import java.time.Instant;
class RecurringMathTest {
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    static void bad(Runnable r){try{r.run();throw new AssertionError("invalid recurrence accepted");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        RecurringMath.Cadence m=RecurringMath.Cadence.MONTHLY,w=RecurringMath.Cadence.WEEKLY,d=RecurringMath.Cadence.DAILY;
        check(RecurringMath.due("2024-01-31",m,0).equals("2024-01-31"),"first");
        check(RecurringMath.due("2024-01-31",m,1).equals("2024-02-29"),"leap");
        check(RecurringMath.due("2024-01-31",m,2).equals("2024-03-31"),"no clamp drift");
        check(RecurringMath.due("2026-01-31",m,1).equals("2026-02-28"),"nonleap");
        check(RecurringMath.due("2026-12-31",m,2).equals("2027-02-28"),"year");
        check(RecurringMath.due("2026-12-30",w,1).equals("2027-01-06"),"weekly");
        check(RecurringMath.due("2026-12-31",d,1).equals("2027-01-01"),"daily");
        check(RecurringMath.today(Instant.parse("2026-11-26T15:59:59.999Z").toEpochMilli()).equals("2026-11-26"),"before Manila midnight");
        check(RecurringMath.today(Instant.parse("2026-11-26T16:00:00Z").toEpochMilli()).equals("2026-11-27"),"Manila midnight");
        bad(()->RecurringMath.date("2026-02-29"));bad(()->RecurringMath.date("2026-2-01"));bad(()->RecurringMath.date(null));
        bad(()->RecurringMath.due("2026-01-01",m,-1));bad(()->RecurringMath.due("2026-01-01",m,100001));bad(()->RecurringMath.due("2026-01-01",null,0));
        System.out.println("RecurringMath: "+checks+" checks PASS");
    }
}
