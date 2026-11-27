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
    // Preserve original service revision/name; never attribute a collection to a new sale.
    static final String CREDIT_EVENTS="SELECT 'CREDIT_SALE' AS kind,d.total AS amount,r.service_id AS service_id,COALESCE(h.name,'Historical service name unavailable') AS service_name,d.opened_ms AS event_ms,d.id AS event_id,d.shift_id,d.operator_id,d.id AS receipt_id,NULL AS reason,d.paid AS upfront_cash,d.customer_id,d.customer_revision FROM credit_receivable_sources d JOIN credit_checkout_requests r ON r.id=d.id LEFT JOIN catalog_history h ON h.id=r.service_id AND h.revision=r.service_revision WHERE d.opened_ms>=? AND d.opened_ms<? UNION ALL SELECT 'COLLECTION',c.amount,NULL,NULL,c.resolved_ms,c.id,c.shift_id,c.operator_id,NULL,NULL,0,c.customer_id,c.customer_revision FROM credit_collection_requests c WHERE c.state='APPLIED' AND c.resolved_ms>=? AND c.resolved_ms<?";
    static final String ALL_EVENTS="SELECT legacy.*,CASE WHEN kind='SALE' THEN amount ELSE 0 END AS upfront_cash,NULL AS customer_id,NULL AS customer_revision FROM ("+EVENTS+") legacy UNION ALL "+CREDIT_EVENTS;
    static final String ALL_EVENT_PAGE=ALL_EVENTS+" ORDER BY event_ms DESC,kind ASC,event_id ASC LIMIT ? OFFSET ?";
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
        try(Cursor c=db.rawQuery(ALL_EVENTS,new String[]{a,b,a,b,a,b,a,b})){
            while(c.moveToNext()){
                String kind=c.getString(0);long amount=c.getLong(1);
                long upfront=c.getLong(10);
                result.totals.add(kind,amount,upfront);
                if(result.chart!=null)result.chart.add(kind,amount,upfront,c.getLong(4));
                if(!c.isNull(2)){
                    String id=c.getString(2),name=c.getString(3);
                    Service service=byName.computeIfAbsent(name,k->new TreeMap<>()).computeIfAbsent(id,k->new Service(id,name));
                    service.totals.add(kind,amount,upfront);
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
        final long amount,recordedMs,upfrontCash;
        final String customerId;final Long customerRevision;
        String recurringSchedule,recurringDue,recurringName;
        Long recurringRevision;
        Event(Cursor c){
            kind=c.getString(0);amount=c.getLong(1);serviceId=c.getString(2);serviceName=c.getString(3);
            recordedMs=c.getLong(4);id=c.getString(5);shiftId=c.getString(6);operatorId=c.getString(7);receiptId=c.getString(8);reason=c.getString(9);upfrontCash=c.getLong(10);customerId=c.getString(11);customerRevision=c.isNull(12)?null:c.getLong(12);
        }
    }
    static List<Event> events(SQLiteDatabase db,CashierJournal.Principal actor,ReportMath.Range range,int offset,int limit){
        CashierSecurity.requireOwner(actor.role);
        if(offset<0||limit<1||limit>51)throw new IllegalArgumentException("Invalid report event page");
        String a=Long.toString(range.start),b=Long.toString(range.end);
        List<Event> result=new ArrayList<>();
        try(Cursor c=db.rawQuery(ALL_EVENT_PAGE,new String[]{a,b,a,b,a,b,a,b,Integer.toString(limit),Integer.toString(offset)})){
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

