package ph.printshop.pos;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Sale-linked, append-only job events. Payment is read live from immutable financial ledgers. */
final class ProductionJournal {
    private ProductionJournal(){}
    static void upgrade(SQLiteDatabase db){
        db.execSQL("CREATE VIEW production_sale_state AS SELECT r.request_id AS receipt_id,l.operator_id AS operator_id,r.service_name AS service_name,r.total AS total,0 AS balance,CASE WHEN EXISTS(SELECT 1 FROM cashier_refund_links x JOIN cashier_drawer_requests d ON d.id=x.drawer_id WHERE x.checkout_id=r.request_id AND x.kind='VOID' AND d.state='APPLIED') THEN 'VOIDED' WHEN t.compensated>0 THEN 'REFUNDED' ELSE 'PAID' END AS payment,EXISTS(SELECT 1 FROM cashier_refund_links x JOIN cashier_drawer_requests d ON d.id=x.drawer_id WHERE x.checkout_id=r.request_id AND d.state='PENDING') AS pending_compensation FROM checkout_requests r JOIN checkout_sales s ON s.request_id=r.request_id JOIN cashier_shift_checkouts l ON l.request_id=r.request_id JOIN cashier_refund_totals t ON t.checkout_id=r.request_id WHERE r.state='APPLIED' UNION ALL SELECT r.id,r.operator_id,h.name,r.total,d.remaining,CASE WHEN d.remaining=0 THEN 'PAID' WHEN d.remaining=r.total THEN 'UNPAID' ELSE 'PARTIAL' END,0 FROM credit_checkout_requests r JOIN credit_receivable_open d ON d.id=r.id JOIN catalog_history h ON h.id=r.service_id AND h.revision=r.service_revision WHERE r.state='APPLIED'");
        db.execSQL("CREATE TABLE production_job_events (event_id TEXT PRIMARY KEY NOT NULL CHECK(length(event_id) BETWEEN 1 AND 80),receipt_id TEXT NOT NULL,revision INTEGER NOT NULL CHECK(typeof(revision)='integer' AND revision>=1),status TEXT NOT NULL CHECK(status IN ('PENDING','IN_PRODUCTION','QUALITY_CHECKING','READY_FOR_PICKUP','COMPLETED')),actor_id TEXT NOT NULL REFERENCES cashier_accounts(id),actor_revision INTEGER NOT NULL CHECK(typeof(actor_revision)='integer' AND actor_revision>=1),note TEXT NOT NULL CHECK(length(trim(note)) BETWEEN 1 AND 160),created_ms INTEGER NOT NULL CHECK(typeof(created_ms)='integer' AND created_ms>=0),UNIQUE(receipt_id,revision))");
        db.execSQL("CREATE VIEW production_job_current AS SELECT e.*,s.operator_id,s.service_name,s.total,s.balance,s.payment,s.pending_compensation,CASE WHEN e.status='PENDING' AND s.payment IN ('REFUNDED','VOIDED') THEN 'CANCELLED' ELSE e.status END AS effective_status FROM production_job_events e JOIN production_sale_state s ON s.receipt_id=e.receipt_id WHERE e.revision=(SELECT MAX(x.revision) FROM production_job_events x WHERE x.receipt_id=e.receipt_id)");
        db.execSQL("CREATE TRIGGER production_event_guard BEFORE INSERT ON production_job_events WHEN EXISTS(SELECT 1 FROM production_job_events WHERE event_id=NEW.event_id) OR NOT EXISTS(SELECT 1 FROM production_sale_state s JOIN cashier_accounts a ON a.id=NEW.actor_id WHERE s.receipt_id=NEW.receipt_id AND s.payment NOT IN ('VOIDED','REFUNDED') AND s.pending_compensation=0 AND a.active=1 AND a.revision=NEW.actor_revision AND (a.role='OWNER' OR a.id=s.operator_id) AND (NEW.status<>'COMPLETED' OR (s.payment='PAID' AND s.balance=0))) OR NEW.revision<>COALESCE((SELECT MAX(revision)+1 FROM production_job_events WHERE receipt_id=NEW.receipt_id),1) OR (NEW.revision=1 AND NEW.status<>'PENDING') OR (NEW.revision>1 AND NOT EXISTS(SELECT 1 FROM production_job_events p WHERE p.receipt_id=NEW.receipt_id AND p.revision=NEW.revision-1 AND p.created_ms<=NEW.created_ms AND ((p.status='PENDING' AND NEW.status='IN_PRODUCTION') OR (p.status='IN_PRODUCTION' AND NEW.status='QUALITY_CHECKING') OR (p.status='QUALITY_CHECKING' AND NEW.status IN ('IN_PRODUCTION','READY_FOR_PICKUP')) OR (p.status='READY_FOR_PICKUP' AND NEW.status IN ('QUALITY_CHECKING','COMPLETED'))))) BEGIN SELECT RAISE(ABORT,'Invalid production event'); END");
        db.execSQL("CREATE TRIGGER production_event_no_update BEFORE UPDATE ON production_job_events BEGIN SELECT RAISE(ABORT,'Production event immutable'); END");
        db.execSQL("CREATE TRIGGER production_event_no_delete BEFORE DELETE ON production_job_events BEGIN SELECT RAISE(ABORT,'Production event immutable'); END");
    }
    static final class Job{
        final String receipt,service,actor,note;
        final long revision,created;
        final ProductionWorkflow.Status status;
        final ProductionWorkflow.FinancialState financial;
        final boolean pendingCompensation;
        Job(Cursor c){
            receipt=c.getString(0);service=c.getString(1);actor=c.getString(2);note=c.getString(3);revision=c.getLong(4);created=c.getLong(5);
            status=ProductionWorkflow.Status.valueOf(c.getString(6));
            financial=new ProductionWorkflow.FinancialState(c.getLong(7),c.getLong(8),true,ProductionWorkflow.Payment.valueOf(c.getString(9)));
            pendingCompensation=c.getInt(10)!=0;
        }
    }
    private static final String SELECT="SELECT receipt_id,service_name,actor_id,note,revision,created_ms,effective_status,total,balance,payment,pending_compensation FROM production_job_current";
    static Job read(SQLiteDatabase db,CashierJournal.Principal actor,String receipt){
        try(Cursor c=db.rawQuery(SELECT+" WHERE receipt_id=?"+(actor.role==CashierSecurity.Role.OWNER?"":" AND operator_id=?"),
            actor.role==CashierSecurity.Role.OWNER?new String[]{receipt}:new String[]{receipt,actor.id})){
            return c.moveToFirst()?new Job(c):null;
        }
    }
    static List<Job> page(SQLiteDatabase db,CashierJournal.Principal actor,int offset){
        TrainingForms.validatePageOffset(offset);
        List<Job> result=new ArrayList<>();
        boolean owner=actor.role==CashierSecurity.Role.OWNER;
        try(Cursor c=db.rawQuery(SELECT+(owner?"":" WHERE operator_id=?")+" ORDER BY created_ms DESC,receipt_id LIMIT 9 OFFSET ?",
            owner?new String[]{Integer.toString(offset)}:new String[]{actor.id,Integer.toString(offset)})){
            while(c.moveToNext())result.add(new Job(c));
        }return result;
    }
    static final class Event{
        final long revision,created,actorRevision;
        final String actor,status,note;
        Event(Cursor c){revision=c.getLong(0);created=c.getLong(1);actorRevision=c.getLong(2);actor=c.getString(3);status=c.getString(4);note=c.getString(5);}
    }
    static List<Event> history(SQLiteDatabase db,CashierJournal.Principal actor,String receipt,int offset){
        TrainingForms.validatePageOffset(offset);
        if(read(db,actor,receipt)==null)throw new IllegalStateException("Job unavailable");
        List<Event> rows=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT e.revision,e.created_ms,e.actor_revision,a.username,e.status,e.note FROM production_job_events e JOIN cashier_accounts a ON a.id=e.actor_id WHERE receipt_id=? ORDER BY e.revision DESC LIMIT 9 OFFSET ?",new String[]{receipt,Integer.toString(offset)})){
            while(c.moveToNext())rows.add(new Event(c));
        }return rows;
    }
    static Job append(SQLiteDatabase db,CashierJournal.Principal actor,String receipt,String event,long prior,ProductionWorkflow.Status target,String rawNote,long now){
        StockMath.validateRequestId(receipt);StockMath.validateRequestId(event);
        if(prior<0||prior==Long.MAX_VALUE||target==null)throw new IllegalArgumentException("Invalid production revision");
        String note=CustomerJournal.text(rawNote,160,true);
        try(Cursor c=db.rawQuery("SELECT receipt_id,revision,status,actor_id,note FROM production_job_events WHERE event_id=?",new String[]{event})){
            if(c.moveToFirst()){
                if(!receipt.equals(c.getString(0))||c.getLong(1)!=prior+1||!target.name().equals(c.getString(2))||!actor.id.equals(c.getString(3))||!Objects.equals(note,c.getString(4)))throw new IllegalStateException("Production event identity changed");
                Job replay=read(db,actor,receipt);if(replay==null)throw new IllegalStateException("Job unavailable");return replay;
            }
        }
        Job current=read(db,actor,receipt);
        if(prior==0){if(current!=null||target!=ProductionWorkflow.Status.PENDING)throw new IllegalStateException("Job already exists or invalid initial status");}
        else{
            if(current==null||current.revision!=prior)throw new IllegalStateException("Job changed; reload");
            if(current.pendingCompensation)throw new IllegalStateException("Resolve pending receipt compensation first");
            ProductionWorkflow.requireTransition(current.status,target,current.financial);
        }
        ContentValues v=new ContentValues();v.put("event_id",event);v.put("receipt_id",receipt);v.put("revision",prior+1);
        v.put("status",target.name());v.put("actor_id",actor.id);v.put("actor_revision",actor.revision);v.put("note",note);v.put("created_ms",now);
        db.insertOrThrow("production_job_events",null,v);return read(db,actor,receipt);
    }
}
