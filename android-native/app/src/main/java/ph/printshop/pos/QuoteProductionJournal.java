package ph.printshop.pos;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Pre-sale job intent. Physical production is initiated only after a recorded sale. */
final class QuoteProductionJournal {
    private QuoteProductionJournal(){}
    static void upgrade(SQLiteDatabase db){
        db.execSQL("CREATE TABLE production_quote_events (event_id TEXT PRIMARY KEY NOT NULL CHECK(length(event_id) BETWEEN 1 AND 80),quote_id TEXT NOT NULL REFERENCES saved_quotations(id),revision INTEGER NOT NULL CHECK(typeof(revision)='integer' AND revision>=1),status TEXT NOT NULL CHECK(status IN ('QUOTATION','AWAITING_APPROVAL','CANCELLED')),actor_id TEXT NOT NULL REFERENCES cashier_accounts(id),actor_revision INTEGER NOT NULL,note TEXT NOT NULL CHECK(length(trim(note)) BETWEEN 1 AND 160),created_ms INTEGER NOT NULL CHECK(typeof(created_ms)='integer' AND created_ms>=0),UNIQUE(quote_id,revision))");
        db.execSQL("CREATE VIEW production_quote_current AS SELECT e.*,q.actor_id AS quote_actor,h.name AS service_name,q.total_centavos,COALESCE(c.checkout_id,z.checkout_id) AS receipt_id,CASE WHEN x.quotation_id IS NOT NULL THEN 'CANCELLED' ELSE e.status END AS effective_status FROM production_quote_events e JOIN saved_quotations q ON q.id=e.quote_id JOIN catalog_history h ON h.id=q.service_id AND h.revision=q.service_revision LEFT JOIN saved_quotation_cancellations x ON x.quotation_id=q.id LEFT JOIN saved_quotation_cash_conversions c ON c.quotation_id=q.id LEFT JOIN saved_quotation_credit_conversions z ON z.quotation_id=q.id WHERE e.revision=(SELECT MAX(p.revision) FROM production_quote_events p WHERE p.quote_id=e.quote_id)");
        db.execSQL("CREATE TRIGGER production_quote_guard BEFORE INSERT ON production_quote_events WHEN NOT EXISTS(SELECT 1 FROM saved_quotations q JOIN cashier_accounts a ON a.id=NEW.actor_id WHERE q.id=NEW.quote_id AND a.active=1 AND a.revision=NEW.actor_revision AND (a.role='OWNER' OR a.id=q.actor_id) AND NEW.created_ms>=q.created_ms) OR EXISTS(SELECT 1 FROM saved_quotation_cancellations WHERE quotation_id=NEW.quote_id) OR EXISTS(SELECT 1 FROM saved_quotation_cash_conversions WHERE quotation_id=NEW.quote_id) OR EXISTS(SELECT 1 FROM saved_quotation_credit_conversions WHERE quotation_id=NEW.quote_id) OR NEW.revision<>COALESCE((SELECT MAX(revision)+1 FROM production_quote_events WHERE quote_id=NEW.quote_id),1) OR (NEW.revision=1 AND NEW.status<>'QUOTATION') OR (NEW.revision>1 AND NOT EXISTS(SELECT 1 FROM production_quote_events p WHERE p.quote_id=NEW.quote_id AND p.revision=NEW.revision-1 AND p.created_ms<=NEW.created_ms AND ((p.status='QUOTATION' AND NEW.status IN ('AWAITING_APPROVAL','CANCELLED')) OR (p.status='AWAITING_APPROVAL' AND NEW.status IN ('QUOTATION','CANCELLED'))))) BEGIN SELECT RAISE(ABORT,'Invalid quotation job event'); END");
        db.execSQL("CREATE TRIGGER production_quote_no_update BEFORE UPDATE ON production_quote_events BEGIN SELECT RAISE(ABORT,'Quotation job event immutable'); END");
        db.execSQL("CREATE TRIGGER production_quote_no_delete BEFORE DELETE ON production_quote_events BEGIN SELECT RAISE(ABORT,'Quotation job event immutable'); END");
    }
    static final class Job{
        final String quote,service,receipt,note,status;
        final long revision,total;
        Job(Cursor c){quote=c.getString(0);service=c.getString(1);receipt=c.getString(2);note=c.getString(3);status=c.getString(4);revision=c.getLong(5);total=c.getLong(6);}
    }
    private static final String SELECT="SELECT quote_id,service_name,receipt_id,note,effective_status,revision,total_centavos FROM production_quote_current";
    static Job read(SQLiteDatabase db,CashierJournal.Principal actor,String quote){
        try(Cursor c=db.rawQuery(SELECT+" WHERE quote_id=?"+(actor.role==CashierSecurity.Role.OWNER?"":" AND quote_actor=?"),
            actor.role==CashierSecurity.Role.OWNER?new String[]{quote}:new String[]{quote,actor.id})){return c.moveToFirst()?new Job(c):null;}
    }
    static List<Job> page(SQLiteDatabase db,CashierJournal.Principal actor,int offset){
        TrainingForms.validatePageOffset(offset);List<Job> rows=new ArrayList<>();boolean owner=actor.role==CashierSecurity.Role.OWNER;
        try(Cursor c=db.rawQuery(SELECT+(owner?"":" WHERE quote_actor=?")+" ORDER BY created_ms DESC,quote_id LIMIT 9 OFFSET ?",owner?new String[]{Integer.toString(offset)}:new String[]{actor.id,Integer.toString(offset)})){
            while(c.moveToNext())rows.add(new Job(c));
        }return rows;
    }
    static List<ProductionJournal.Event> history(SQLiteDatabase db,CashierJournal.Principal actor,String quote,int offset){
        TrainingForms.validatePageOffset(offset);if(read(db,actor,quote)==null)throw new IllegalStateException("Quotation job unavailable");
        List<ProductionJournal.Event> rows=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT e.revision,e.created_ms,e.actor_revision,a.username,e.status,e.note FROM production_quote_events e JOIN cashier_accounts a ON a.id=e.actor_id WHERE quote_id=? ORDER BY e.revision DESC LIMIT 9 OFFSET ?",new String[]{quote,Integer.toString(offset)})){while(c.moveToNext())rows.add(new ProductionJournal.Event(c));}
        return rows;
    }
    static Job append(SQLiteDatabase db,CashierJournal.Principal actor,String quote,String event,long prior,ProductionWorkflow.Status status,String raw,long now){
        StockMath.validateRequestId(quote);StockMath.validateRequestId(event);
        if(prior<0||prior==Long.MAX_VALUE||status==null)throw new IllegalArgumentException("Invalid quotation job revision");
        String note=CustomerJournal.text(raw,160,true);QuotationJournal.Record q=QuotationJournal.read(db,quote);
        if(q==null||(!q.actor.equals(actor.id)&&actor.role!=CashierSecurity.Role.OWNER))throw new IllegalStateException("Quotation unavailable");
        try(Cursor c=db.rawQuery("SELECT quote_id,revision,status,actor_id,note FROM production_quote_events WHERE event_id=?",new String[]{event})){
            if(c.moveToFirst()){
                if(!quote.equals(c.getString(0))||c.getLong(1)!=prior+1||!status.name().equals(c.getString(2))||!actor.id.equals(c.getString(3))||!note.equals(c.getString(4)))throw new IllegalStateException("Quotation job event identity changed");
                return read(db,actor,quote);
            }
        }
        if(q.cancelled||q.cashCheckout!=null||q.creditCheckout!=null)throw new IllegalStateException("Quotation already resolved");
        Job current=read(db,actor,quote);
        if(prior==0){if(current!=null||status!=ProductionWorkflow.Status.QUOTATION)throw new IllegalStateException("Invalid initial quotation job");}
        else if(current==null||current.revision!=prior||!("QUOTATION".equals(current.status)&&(status==ProductionWorkflow.Status.AWAITING_APPROVAL||status==ProductionWorkflow.Status.CANCELLED)||"AWAITING_APPROVAL".equals(current.status)&&(status==ProductionWorkflow.Status.QUOTATION||status==ProductionWorkflow.Status.CANCELLED)))throw new IllegalStateException("Invalid quotation job transition");
        ContentValues v=new ContentValues();v.put("event_id",event);v.put("quote_id",quote);v.put("revision",prior+1);v.put("status",status.name());v.put("actor_id",actor.id);v.put("actor_revision",actor.revision);v.put("note",note);v.put("created_ms",now);
        db.insertOrThrow("production_quote_events",null,v);return read(db,actor,quote);
    }
    /** The immutable conversion link doubles as the job handover identity. */
    static boolean onConversion(SQLiteDatabase db,CashierJournal.Principal actor,String quote,String receipt,long now){
        Job job=read(db,actor,quote);
        if(job==null||"CANCELLED".equals(job.status))return false;
        if(!receipt.equals(job.receipt))throw new IllegalStateException("Quotation job conversion link missing");
        ProductionJournal.append(db,actor,receipt,UUID.randomUUID().toString(),0,ProductionWorkflow.Status.PENDING,"Converted quotation job · "+quote,now);
        return true;
    }
}
