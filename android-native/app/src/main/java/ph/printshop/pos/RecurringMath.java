package ph.printshop.pos;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Philippine-calendar recurrence; monthly dates clamp to month end from original anchor. */
final class RecurringMath {
    private RecurringMath(){}
    enum Cadence { DAILY, WEEKLY, MONTHLY }
    static LocalDate date(String value){
        if(value==null)throw new IllegalArgumentException("Invalid expense date");
        LocalDate date;
        try{date=LocalDate.parse(value);}catch(java.time.DateTimeException ex){throw new IllegalArgumentException("Invalid expense date");}
        if(date.getYear()<1970||date.getYear()>9999||!date.toString().equals(value))throw new IllegalArgumentException("Invalid expense date");
        return date;
    }
    static String today(long now){return Instant.ofEpochMilli(now).atZone(ZoneId.of("Asia/Manila")).toLocalDate().toString();}
    static String due(String first,Cadence cadence,long index){
        if(cadence==null||index<0||index>100000)throw new IllegalArgumentException("Invalid expense occurrence");
        LocalDate anchor=date(first);
        LocalDate result=cadence==Cadence.MONTHLY?anchor.plusMonths(index):anchor.plusDays(cadence==Cadence.WEEKLY?index*7:index);
        return date(result.toString()).toString();
    }
}
