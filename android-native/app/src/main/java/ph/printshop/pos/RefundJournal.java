package ph.printshop.pos;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Immutable receipt-linked cash compensation, using the existing exact drawer effect chain. */
final class RefundJournal {
    private RefundJournal(){}
    enum Kind { REFUND, VOID }
    static void upgrade(SQLiteDatabase db){
        db.execSQL("CREATE TABLE cashier_refund_links (drawer_id TEXT PRIMARY KEY NOT NULL REFERENCES cashier_drawer_requests(id), checkout_id TEXT NOT NULL REFERENCES checkout_sales(request_id), kind TEXT NOT NULL CHECK(kind IN ('REFUND','VOID')), original_paid INTEGER NOT NULL CHECK(typeof(original_paid)='integer' AND original_paid BETWEEN 1 AND 9999999999900000), prior_paid INTEGER NOT NULL CHECK(typeof(prior_paid)='integer' AND prior_paid>=0 AND prior_paid<original_paid))");
        db.execSQL("CREATE INDEX refund_original ON cashier_refund_links(checkout_id)");
        db.execSQL("CREATE VIEW cashier_refund_totals AS SELECT c.request_id AS checkout_id,c.paid AS original_paid,COALESCE((SELECT SUM(d.amount) FROM cashier_refund_links l JOIN cashier_drawer_requests d ON d.id=l.drawer_id WHERE l.checkout_id=c.request_id AND d.state='APPLIED'),0) AS compensated FROM checkout_cash c JOIN checkout_requests r ON r.request_id=c.request_id JOIN cashier_shift_checkouts s ON s.request_id=c.request_id WHERE r.state='APPLIED'");
        db.execSQL("CREATE TRIGGER refund_link_initial BEFORE INSERT ON cashier_refund_links WHEN EXISTS(SELECT 1 FROM cashier_refund_links WHERE drawer_id=NEW.drawer_id) OR NOT EXISTS(SELECT 1 FROM cashier_drawer_requests d JOIN cashier_accounts a ON a.id=d.operator_id JOIN cashier_shifts s ON s.id=d.shift_id JOIN cashier_refund_totals t ON t.checkout_id=NEW.checkout_id WHERE d.id=NEW.drawer_id AND d.state='PENDING' AND d.kind='CASH_OUT' AND a.role='OWNER' AND a.active=1 AND a.revision=d.stage_revision AND s.state='OPEN' AND s.operator_id=a.id AND t.original_paid=NEW.original_paid AND t.compensated=NEW.prior_paid AND d.amount<=NEW.original_paid-NEW.prior_paid AND (NEW.kind='REFUND' OR (NEW.prior_paid=0 AND d.amount=NEW.original_paid))) OR EXISTS(SELECT 1 FROM cashier_refund_links l JOIN cashier_drawer_requests d ON d.id=l.drawer_id WHERE l.checkout_id=NEW.checkout_id AND d.state='PENDING') BEGIN SELECT RAISE(ABORT,'Invalid or competing receipt compensation'); END");
        db.execSQL("CREATE TRIGGER refund_link_no_update BEFORE UPDATE ON cashier_refund_links BEGIN SELECT RAISE(ABORT,'Receipt compensation link is immutable'); END");
        db.execSQL("CREATE TRIGGER refund_link_no_delete BEFORE DELETE ON cashier_refund_links BEGIN SELECT RAISE(ABORT,'Receipt compensation link is immutable'); END");
        db.execSQL("CREATE TRIGGER refund_apply_guard BEFORE UPDATE ON cashier_drawer_requests WHEN NEW.state='APPLIED' AND EXISTS(SELECT 1 FROM cashier_refund_links WHERE drawer_id=OLD.id) AND NOT EXISTS(SELECT 1 FROM cashier_refund_links l JOIN cashier_refund_totals t ON t.checkout_id=l.checkout_id WHERE l.drawer_id=OLD.id AND t.original_paid=l.original_paid AND t.compensated=l.prior_paid AND OLD.amount<=l.original_paid-l.prior_paid AND (l.kind='REFUND' OR (l.prior_paid=0 AND OLD.amount=l.original_paid))) BEGIN SELECT RAISE(ABORT,'Receipt compensation exceeds reviewed balance'); END");
    }
    static final class Review {
        final String checkoutId;
        final long originalPaid, compensated;
        Review(String checkoutId,long originalPaid,long compensated){
            this.checkoutId=checkoutId;this.originalPaid=originalPaid;this.compensated=compensated;
        }
        long remaining(){return originalPaid-compensated;}
    }
    static final class Request {
        final String checkoutId;
        final Kind kind;
        final long originalPaid,priorPaid;
        final DrawerJournal.Request drawer;
        Request(String checkoutId,Kind kind,long originalPaid,long priorPaid,DrawerJournal.Request drawer){
            this.checkoutId=checkoutId;this.kind=kind;this.originalPaid=originalPaid;this.priorPaid=priorPaid;this.drawer=drawer;
        }
    }
    static final class Receipt {
        final String serviceName,shiftId;
        final long recordedMs;
        final Review review;
        Receipt(Cursor c){
            review=new Review(c.getString(0),c.getLong(1),c.getLong(2));
            serviceName=c.getString(3);shiftId=c.getString(4);recordedMs=c.getLong(5);
        }
    }
    private static void page(int offset,int limit){
        if(offset<0||limit<1||limit>51)throw new IllegalArgumentException("Invalid compensation history page");
    }
    static java.util.List<Receipt> receipts(SQLiteDatabase db,CashierJournal.Principal actor,int offset,int limit){
        CashierSecurity.requireOwner(actor.role);page(offset,limit);
        java.util.List<Receipt> result=new java.util.ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT t.checkout_id,t.original_paid,t.compensated,r.service_name,l.shift_id,s.recorded_ms FROM cashier_refund_totals t JOIN checkout_requests r ON r.request_id=t.checkout_id JOIN cashier_shift_checkouts l ON l.request_id=t.checkout_id JOIN checkout_sales s ON s.request_id=t.checkout_id ORDER BY s.recorded_ms DESC,t.checkout_id DESC LIMIT ? OFFSET ?",new String[]{Integer.toString(limit),Integer.toString(offset)})){
            while(c.moveToNext())result.add(new Receipt(c));
        }
        return result;
    }
    static java.util.List<Request> history(SQLiteDatabase db,CashierJournal.Principal actor,int offset,int limit){
        CashierSecurity.requireOwner(actor.role);page(offset,limit);
        java.util.List<String> ids=new java.util.ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT l.drawer_id FROM cashier_refund_links l JOIN cashier_drawer_requests d ON d.id=l.drawer_id WHERE d.operator_id=? ORDER BY d.created_ms DESC,d.id DESC LIMIT ? OFFSET ?",new String[]{actor.id,Integer.toString(limit),Integer.toString(offset)})){
            while(c.moveToNext())ids.add(c.getString(0));
        }
        java.util.List<Request> rows=new java.util.ArrayList<>();
        for(String id:ids)rows.add(read(db,actor,id));
        return rows;
    }
    static boolean linked(SQLiteDatabase db,String id){
        try(Cursor c=db.rawQuery("SELECT 1 FROM cashier_refund_links WHERE drawer_id=?",new String[]{id})){return c.moveToFirst();}
    }
    static Review review(SQLiteDatabase db,CashierJournal.Principal actor,String checkoutId){
        CashierSecurity.requireOwner(actor.role);StockMath.validateRequestId(checkoutId);
        try(Cursor c=db.rawQuery("SELECT original_paid,compensated FROM cashier_refund_totals WHERE checkout_id=?",new String[]{checkoutId})){
            if(!c.moveToFirst())throw new IllegalStateException("Authorized original cash receipt unavailable");
            return new Review(checkoutId,c.getLong(0),c.getLong(1));
        }
    }
    static Request read(SQLiteDatabase db,CashierJournal.Principal actor,String id){
        CashierSecurity.requireOwner(actor.role);
        try(Cursor c=db.rawQuery("SELECT checkout_id,kind,original_paid,prior_paid FROM cashier_refund_links WHERE drawer_id=?",new String[]{id})){
            if(!c.moveToFirst())throw new IllegalStateException("Receipt compensation unavailable");
            return new Request(c.getString(0),Kind.valueOf(c.getString(1)),c.getLong(2),c.getLong(3),DrawerJournal.requireRequest(db,actor,id));
        }
    }
    /** Caller must wrap drawer staging and link insert in the same write transaction. */
    static Request stage(SQLiteDatabase db,CashierJournal.Principal actor,String shift,String id,String checkoutId,Kind kind,long originalPaid,long priorPaid,long amount,String reason,long before,long now){
        CashierSecurity.requireOwner(actor.role);StockMath.validateRequestId(id);
        if(kind==null)throw new IllegalArgumentException("Select refund or void");
        String note=StockMath.reason(reason);
        if(linked(db,id)){
            Request old=read(db,actor,id);
            old.drawer.verify(shift,ShiftMath.DrawerKind.CASH_OUT,amount,null,note,before);
            if(!old.checkoutId.equals(checkoutId)||old.kind!=kind||old.originalPaid!=originalPaid||old.priorPaid!=priorPaid)
                throw new IllegalStateException("Compensation identity already used for a different review");
            return old;
        }
        if(DrawerJournal.read(db,id)!=null)throw new IllegalStateException("Drawer identity already used");
        Review review=review(db,actor,checkoutId);
        if(review.originalPaid!=originalPaid||review.compensated!=priorPaid||amount<=0||amount>review.remaining()||
            (kind==Kind.VOID&&(priorPaid!=0||amount!=originalPaid)))
            throw new IllegalStateException("Original receipt changed or amount is invalid; review again");
        DrawerJournal.stage(db,actor,shift,id,ShiftMath.DrawerKind.CASH_OUT,amount,null,note,before,now);
        ContentValues row=new ContentValues();row.put("drawer_id",id);row.put("checkout_id",checkoutId);row.put("kind",kind.name());
        row.put("original_paid",originalPaid);row.put("prior_paid",priorPaid);
        db.insertOrThrow("cashier_refund_links",null,row);
        return read(db,actor,id);
    }
    static Request resolve(SQLiteDatabase db,CashierJournal.Principal actor,String id,boolean apply,String reason,long now){
        read(db,actor,id);DrawerJournal.resolveLinked(db,actor,id,apply,reason,now);
        return read(db,actor,id);
    }
}
