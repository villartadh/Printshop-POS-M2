package ph.printshop.pos;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Read-only report over immutable authenticated receipt and drawer sources. */
final class ReportJournal {
    private ReportJournal(){}
    // Each applied source appears exactly once. Linked compensation has its own kind, never ordinary cash-out.
    static final String EVENTS="SELECT 'SALE' AS kind,c.paid AS amount,r.service_id AS service_id,r.service_name AS service_name,s.recorded_ms AS event_ms,r.request_id AS event_id,x.shift_id AS shift_id,x.operator_id AS operator_id,r.request_id AS receipt_id,NULL AS reason FROM checkout_sales s JOIN checkout_cash c ON c.request_id=s.request_id JOIN checkout_requests r ON r.request_id=s.request_id JOIN cashier_shift_checkouts x ON x.request_id=s.request_id WHERE r.state='APPLIED' AND s.recorded_ms>=? AND s.recorded_ms<? UNION ALL SELECT COALESCE(l.kind,d.kind),d.amount,r.service_id,r.service_name,d.resolved_ms,d.id,d.shift_id,d.operator_id,l.checkout_id,d.reason FROM cashier_drawer_requests d LEFT JOIN cashier_refund_links l ON l.drawer_id=d.id LEFT JOIN checkout_requests r ON r.request_id=l.checkout_id WHERE d.state='APPLIED' AND d.resolved_ms>=? AND d.resolved_ms<?";
    static final String EVENT_PAGE=EVENTS+" ORDER BY event_ms DESC,kind ASC,event_id ASC LIMIT ? OFFSET ?";
    static final String CLOSES="SELECT expected_cash,counted_cash,variance FROM cashier_shifts WHERE state='CLOSED' AND closed_ms>=? AND closed_ms<?";
    static final class Service {
        final String id,name;
        final ReportMath.Totals totals=new ReportMath.Totals();
        Service(String id,String name){this.id=id;this.name=name;}
    }
    static final class Report {
        final ReportMath.Range range;
        final ReportMath.Series chart;
        final ReportMath.Totals totals=new ReportMath.Totals();
        final List<Service> services=new ArrayList<>();
        BigInteger closedExpected=BigInteger.ZERO,closedCounted=BigInteger.ZERO,closedVariance=BigInteger.ZERO,closedShifts=BigInteger.ZERO;
        Report(ReportMath.Range range,ReportMath.Period period){this.range=range;chart=period==null?null:new ReportMath.Series(period,range);}
    }
    static Report read(SQLiteDatabase db,CashierJournal.Principal actor,ReportMath.Range range){
        return read(db,actor,range,null);
    }
    static Report read(SQLiteDatabase db,CashierJournal.Principal actor,ReportMath.Range range,ReportMath.Period period){
        CashierSecurity.requireOwner(actor.role);
        Report result=new Report(range,period);
        // Group by immutable service ID AND sold name to preserve rename history without merging unrelated services.
        Map<String,Map<String,Service>> byName=new TreeMap<>();
        String a=Long.toString(range.start),b=Long.toString(range.end);
        try(Cursor c=db.rawQuery(EVENTS,new String[]{a,b,a,b})){
            while(c.moveToNext()){
                String kind=c.getString(0);long amount=c.getLong(1);
                result.totals.add(kind,amount);
                if(result.chart!=null)result.chart.add(kind,amount,c.getLong(4));
                if(!c.isNull(2)){
                    String id=c.getString(2),name=c.getString(3);
                    Service service=byName.computeIfAbsent(name,k->new TreeMap<>()).computeIfAbsent(id,k->new Service(id,name));
                    service.totals.add(kind,amount);
                }
            }
        }
        for(Map<String,Service> ids:byName.values())result.services.addAll(ids.values());
        try(Cursor c=db.rawQuery(CLOSES,new String[]{a,b})){
            while(c.moveToNext()){
                result.closedExpected=result.closedExpected.add(BigInteger.valueOf(c.getLong(0)));
                result.closedCounted=result.closedCounted.add(BigInteger.valueOf(c.getLong(1)));
                result.closedVariance=result.closedVariance.add(BigInteger.valueOf(c.getLong(2)));
                result.closedShifts=result.closedShifts.add(BigInteger.ONE);
            }
        }
        return result;
    }
    static final String RECURRING_PROVENANCE="SELECT o.schedule_id,o.schedule_revision,o.due_date,v.name FROM cashier_recurring_occurrences o JOIN cashier_recurring_versions v ON v.id=o.schedule_id AND v.revision=o.schedule_revision WHERE o.drawer_id=?";
    static final class Event {
        final String kind,serviceId,serviceName,id,shiftId,operatorId,receiptId,reason;
        final long amount,recordedMs;
        String recurringSchedule,recurringDue,recurringName;
        Long recurringRevision;
        Event(Cursor c){
            kind=c.getString(0);amount=c.getLong(1);serviceId=c.getString(2);serviceName=c.getString(3);
            recordedMs=c.getLong(4);id=c.getString(5);shiftId=c.getString(6);operatorId=c.getString(7);receiptId=c.getString(8);reason=c.getString(9);
        }
    }
    static List<Event> events(SQLiteDatabase db,CashierJournal.Principal actor,ReportMath.Range range,int offset,int limit){
        CashierSecurity.requireOwner(actor.role);
        if(offset<0||limit<1||limit>51)throw new IllegalArgumentException("Invalid report event page");
        String a=Long.toString(range.start),b=Long.toString(range.end);
        List<Event> result=new ArrayList<>();
        try(Cursor c=db.rawQuery(EVENT_PAGE,new String[]{a,b,a,b,Integer.toString(limit),Integer.toString(offset)})){
            while(c.moveToNext())result.add(new Event(c));
        }
        for(Event event:result)if("EXPENSE".equals(event.kind)){
            try(Cursor c=db.rawQuery(RECURRING_PROVENANCE,new String[]{event.id})){
                if(c.moveToFirst()){
                    event.recurringSchedule=c.getString(0);event.recurringRevision=c.getLong(1);
                    event.recurringDue=c.getString(2);event.recurringName=c.getString(3);
                }
            }
        }
        return result;
    }

}

