package ph.printshop.pos;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Objects;

/** Fixture-only append-only saved quotations and audited cancellation. No financial effects. */
final class QuotationJournal {
    private QuotationJournal(){}
    static void upgrade(SQLiteDatabase db){
        db.execSQL("CREATE TABLE saved_quotations (id TEXT PRIMARY KEY NOT NULL, actor_id TEXT NOT NULL REFERENCES cashier_accounts(id), actor_revision INTEGER NOT NULL CHECK(typeof(actor_revision)='integer' AND actor_revision>=1), customer_id TEXT, customer_revision INTEGER, service_id TEXT NOT NULL, service_revision INTEGER NOT NULL CHECK(typeof(service_revision)='integer' AND service_revision>=1), total_centavos INTEGER NOT NULL CHECK(typeof(total_centavos)='integer' AND total_centavos BETWEEN 0 AND 9999999999900000), snapshot TEXT NOT NULL CHECK(length(snapshot) BETWEEN 1 AND 250000), stock_sufficient INTEGER NOT NULL CHECK(stock_sufficient IN (0,1)), stock_pending INTEGER NOT NULL CHECK(stock_pending IN (0,1)), convertible INTEGER NOT NULL CHECK(convertible IN (0,1) AND (convertible=0 OR total_centavos>0)), created_ms INTEGER NOT NULL CHECK(typeof(created_ms)='integer' AND created_ms>=0), FOREIGN KEY(service_id,service_revision) REFERENCES catalog_history(id,revision), FOREIGN KEY(customer_id,customer_revision) REFERENCES cashier_customer_versions(id,revision), CHECK((customer_id IS NULL AND customer_revision IS NULL) OR (customer_id IS NOT NULL AND typeof(customer_revision)='integer' AND customer_revision>=1)))");
        db.execSQL("CREATE TABLE saved_quotation_cancellations (quotation_id TEXT PRIMARY KEY NOT NULL REFERENCES saved_quotations(id), actor_id TEXT NOT NULL REFERENCES cashier_accounts(id), actor_revision INTEGER NOT NULL CHECK(typeof(actor_revision)='integer' AND actor_revision>=1), reason TEXT NOT NULL CHECK(length(trim(reason)) BETWEEN 1 AND 160), created_ms INTEGER NOT NULL CHECK(typeof(created_ms)='integer' AND created_ms>=0))");
        db.execSQL("CREATE INDEX saved_quotation_actor_history ON saved_quotations(actor_id,created_ms DESC,id)");
        db.execSQL("CREATE TRIGGER saved_quote_initial BEFORE INSERT ON saved_quotations WHEN EXISTS(SELECT 1 FROM saved_quotations WHERE id=NEW.id) OR NOT EXISTS(SELECT 1 FROM cashier_accounts a WHERE a.id=NEW.actor_id AND a.active=1 AND a.revision=NEW.actor_revision) OR NOT EXISTS(SELECT 1 FROM catalog c WHERE c.id=NEW.service_id AND c.revision=NEW.service_revision AND c.kind='SERVICE' AND c.archived=0) OR (NEW.customer_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM cashier_customer_current c WHERE c.id=NEW.customer_id AND c.revision=NEW.customer_revision AND c.active=1)) BEGIN SELECT RAISE(ABORT,'Invalid quotation identity or revision'); END");
        db.execSQL("CREATE TRIGGER saved_quote_immutable_update BEFORE UPDATE ON saved_quotations BEGIN SELECT RAISE(ABORT,'Quotation immutable'); END");
        db.execSQL("CREATE TRIGGER saved_quote_immutable_delete BEFORE DELETE ON saved_quotations BEGIN SELECT RAISE(ABORT,'Quotation immutable'); END");
        db.execSQL("CREATE TRIGGER saved_quote_cancel_guard BEFORE INSERT ON saved_quotation_cancellations WHEN EXISTS(SELECT 1 FROM saved_quotation_cancellations WHERE quotation_id=NEW.quotation_id) OR NOT EXISTS(SELECT 1 FROM saved_quotations q JOIN cashier_accounts a ON a.id=NEW.actor_id WHERE q.id=NEW.quotation_id AND a.active=1 AND a.revision=NEW.actor_revision AND (a.id=q.actor_id OR a.role='OWNER') AND NEW.created_ms>=q.created_ms) BEGIN SELECT RAISE(ABORT,'Invalid quotation cancellation'); END");
        db.execSQL("CREATE TRIGGER saved_quote_cancel_immutable_update BEFORE UPDATE ON saved_quotation_cancellations BEGIN SELECT RAISE(ABORT,'Quotation cancellation immutable'); END");
        db.execSQL("CREATE TRIGGER saved_quote_cancel_immutable_delete BEFORE DELETE ON saved_quotation_cancellations BEGIN SELECT RAISE(ABORT,'Quotation cancellation immutable'); END");
    }
    static final class Record{
        final String id,actor,customer,service,snapshot,cancelActor,cancelReason;
        final long customerRevision,serviceRevision,total,createdMs;
        final boolean cancelled,stockSufficient,stockPending,convertible;
        Record(Cursor c){
            id=c.getString(0);actor=c.getString(1);customer=c.getString(2);customerRevision=c.isNull(3)?0:c.getLong(3);
            service=c.getString(4);serviceRevision=c.getLong(5);total=c.getLong(6);snapshot=c.getString(7);
            createdMs=c.getLong(8);stockSufficient=c.getInt(9)==1;stockPending=c.getInt(10)==1;convertible=c.getInt(11)==1;
            cancelActor=c.getString(12);cancelReason=c.getString(13);cancelled=cancelActor!=null;
        }
        void verifyReplay(CashierJournal.Principal principal,SavedQuotation quote,CustomerLinkJournal.Selection selected){
            if(!actor.equals(principal.id)||!snapshot.equals(quote.commercialSnapshot())||
                !Objects.equals(customer,selected==null?null:selected.id)||customerRevision!=(selected==null?0:selected.revision))
                throw new IllegalStateException("Quotation identity already used");
        }
    }
    static Record read(SQLiteDatabase db,String id){
        try(Cursor c=db.rawQuery("SELECT q.id,q.actor_id,q.customer_id,q.customer_revision,q.service_id,q.service_revision,q.total_centavos,q.snapshot,q.created_ms,q.stock_sufficient,q.stock_pending,q.convertible,x.actor_id,x.reason FROM saved_quotations q LEFT JOIN saved_quotation_cancellations x ON x.quotation_id=q.id WHERE q.id=?",new String[]{id})){
            return c.moveToFirst()?new Record(c):null;
        }
    }
    static Record save(SQLiteDatabase db,CashierJournal.Principal actor,SavedQuotation quote,CustomerLinkJournal.Selection selected,long now){
        ContentValues v=new ContentValues();v.put("id",quote.id);v.put("actor_id",actor.id);v.put("actor_revision",actor.revision);
        if(selected!=null){CustomerJournal.Customer c=CustomerJournal.current(db,selected.id);
            if(c==null||!c.active||c.revision!=selected.revision||!c.name.equals(selected.name))throw new IllegalStateException("Customer changed; review again");
            v.put("customer_id",selected.id);v.put("customer_revision",selected.revision);}
        v.put("service_id",quote.serviceId);v.put("service_revision",quote.serviceRevision);v.put("total_centavos",quote.totalCentavos);
        v.put("snapshot",quote.commercialSnapshot());v.put("created_ms",now);v.put("stock_sufficient",quote.stockSufficientAtReview?1:0);
        v.put("stock_pending",quote.pendingStockAtReview?1:0);v.put("convertible",quote.representableForCheckout?1:0);
        db.insertOrThrow("saved_quotations",null,v);return read(db,quote.id);
    }
    static Record cancel(SQLiteDatabase db,CashierJournal.Principal actor,String id,String rawReason,long now){
        Record r=read(db,id);
        if(r==null||(!r.actor.equals(actor.id)&&actor.role!=CashierSecurity.Role.OWNER))throw new IllegalStateException("Quotation unavailable");
        String reason=CustomerJournal.text(rawReason,160,true);
        if(r.cancelled){if(!actor.id.equals(r.cancelActor)||!reason.equals(r.cancelReason))throw new IllegalStateException("Quotation already cancelled");return r;}
        ContentValues v=new ContentValues();v.put("quotation_id",id);v.put("actor_id",actor.id);v.put("actor_revision",actor.revision);
        v.put("reason",reason);v.put("created_ms",now);db.insertOrThrow("saved_quotation_cancellations",null,v);return read(db,id);
    }
}
