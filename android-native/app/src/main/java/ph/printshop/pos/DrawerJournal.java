package ph.printshop.pos;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/** Additive v11 exact drawer projection, durable expense/adjustment requests and immutable effects. */
final class DrawerJournal {
    private DrawerJournal(){}
    static void upgrade(SQLiteDatabase db){
        db.execSQL("CREATE TABLE cashier_drawer_anchors (shift_id TEXT PRIMARY KEY NOT NULL REFERENCES cashier_shifts(id), baseline_cash INTEGER NOT NULL CHECK(typeof(baseline_cash)='integer' AND baseline_cash BETWEEN 0 AND 9999999999900000))");
        db.execSQL("CREATE TABLE cashier_drawer_legacy_sales (checkout_id TEXT PRIMARY KEY NOT NULL REFERENCES cashier_shift_checkouts(request_id))");
        db.execSQL("INSERT INTO cashier_drawer_legacy_sales SELECT l.request_id FROM cashier_shift_checkouts l JOIN checkout_requests r ON r.request_id=l.request_id WHERE r.state='APPLIED'");
        db.execSQL("INSERT INTO cashier_drawer_anchors SELECT s.id,s.opening_cash+COALESCE((SELECT SUM(c.paid) FROM cashier_shift_checkouts l JOIN cashier_drawer_legacy_sales k ON k.checkout_id=l.request_id JOIN checkout_cash c ON c.request_id=l.request_id WHERE l.shift_id=s.id),0) FROM cashier_shifts s");
        db.execSQL("CREATE TRIGGER drawer_legacy_no_insert BEFORE INSERT ON cashier_drawer_legacy_sales BEGIN SELECT RAISE(ABORT,'Legacy drawer provenance is immutable'); END");
        db.execSQL("CREATE TRIGGER drawer_legacy_no_update BEFORE UPDATE ON cashier_drawer_legacy_sales BEGIN SELECT RAISE(ABORT,'Legacy drawer provenance is immutable'); END");
        db.execSQL("CREATE TRIGGER drawer_legacy_no_delete BEFORE DELETE ON cashier_drawer_legacy_sales BEGIN SELECT RAISE(ABORT,'Legacy drawer provenance is immutable'); END");
        db.execSQL("CREATE TRIGGER drawer_anchor_initial BEFORE INSERT ON cashier_drawer_anchors WHEN EXISTS(SELECT 1 FROM cashier_drawer_anchors WHERE shift_id=NEW.shift_id) OR NOT EXISTS(SELECT 1 FROM cashier_shifts s WHERE s.id=NEW.shift_id AND s.state='OPEN' AND s.opening_cash=NEW.baseline_cash) BEGIN SELECT RAISE(ABORT,'Invalid drawer anchor'); END");
        db.execSQL("CREATE TRIGGER drawer_anchor_no_update BEFORE UPDATE ON cashier_drawer_anchors BEGIN SELECT RAISE(ABORT,'Drawer anchor is immutable'); END");
        db.execSQL("CREATE TRIGGER drawer_anchor_no_delete BEFORE DELETE ON cashier_drawer_anchors BEGIN SELECT RAISE(ABORT,'Drawer anchor is immutable'); END");
        db.execSQL("CREATE TRIGGER drawer_shift_anchor AFTER INSERT ON cashier_shifts BEGIN INSERT INTO cashier_drawer_anchors VALUES(NEW.id,NEW.opening_cash); SELECT CASE WHEN changes()<>1 THEN RAISE(ABORT,'Drawer anchor unavailable') END; END");
        db.execSQL("CREATE TABLE cashier_drawer_requests (id TEXT PRIMARY KEY NOT NULL, shift_id TEXT NOT NULL REFERENCES cashier_shifts(id), operator_id TEXT NOT NULL REFERENCES cashier_accounts(id), stage_revision INTEGER NOT NULL CHECK(typeof(stage_revision)='integer' AND stage_revision>=1), kind TEXT NOT NULL CHECK(kind IN ('EXPENSE','CASH_IN','CASH_OUT')), amount INTEGER NOT NULL CHECK(typeof(amount)='integer' AND amount BETWEEN 1 AND 9999999999900000), category TEXT, reason TEXT NOT NULL CHECK(typeof(reason)='text' AND length(reason) BETWEEN 1 AND 160 AND reason=trim(reason) AND instr(reason,char(0))=0 AND instr(reason,char(9))=0 AND instr(reason,char(10))=0 AND instr(reason,char(13))=0), before_cash INTEGER NOT NULL CHECK(typeof(before_cash)='integer' AND before_cash BETWEEN 0 AND 9999999999900000), after_cash INTEGER NOT NULL CHECK(typeof(after_cash)='integer' AND after_cash BETWEEN 0 AND 9999999999900000), created_ms INTEGER NOT NULL CHECK(typeof(created_ms)='integer' AND created_ms>=0), state TEXT NOT NULL CHECK(state IN ('PENDING','APPLIED','CANCELLED')), resolved_ms INTEGER, resolved_revision INTEGER, cancel_reason TEXT, CHECK((kind='EXPENSE' AND category IS NOT NULL AND typeof(category)='text' AND length(category) BETWEEN 1 AND 160 AND category=trim(category) AND instr(category,char(0))=0 AND instr(category,char(9))=0 AND instr(category,char(10))=0 AND instr(category,char(13))=0 AND length(category)<=80) OR (kind<>'EXPENSE' AND category IS NULL)), CHECK(after_cash=before_cash+CASE WHEN kind='CASH_IN' THEN amount ELSE -amount END), CHECK((state='PENDING' AND resolved_ms IS NULL AND resolved_revision IS NULL AND cancel_reason IS NULL) OR (state IN ('APPLIED','CANCELLED') AND resolved_ms IS NOT NULL AND typeof(resolved_ms)='integer' AND resolved_ms>=created_ms AND resolved_revision IS NOT NULL AND typeof(resolved_revision)='integer' AND resolved_revision>=stage_revision AND ((state='APPLIED' AND cancel_reason IS NULL) OR (state='CANCELLED' AND cancel_reason IS NOT NULL AND typeof(cancel_reason)='text' AND length(cancel_reason) BETWEEN 1 AND 160 AND cancel_reason=trim(cancel_reason) AND instr(cancel_reason,char(0))=0 AND instr(cancel_reason,char(9))=0 AND instr(cancel_reason,char(10))=0 AND instr(cancel_reason,char(13))=0)))) )");
        db.execSQL("CREATE UNIQUE INDEX drawer_one_pending ON cashier_drawer_requests(shift_id) WHERE state='PENDING'");
        db.execSQL("CREATE INDEX drawer_request_history ON cashier_drawer_requests(operator_id,created_ms DESC,id DESC)");
        db.execSQL("CREATE TABLE cashier_drawer_effects (event_no INTEGER PRIMARY KEY AUTOINCREMENT CHECK(event_no>=1), shift_id TEXT NOT NULL REFERENCES cashier_shifts(id), checkout_id TEXT UNIQUE REFERENCES checkout_requests(request_id), drawer_id TEXT UNIQUE REFERENCES cashier_drawer_requests(id), before_cash INTEGER NOT NULL CHECK(typeof(before_cash)='integer' AND before_cash BETWEEN 0 AND 9999999999900000), after_cash INTEGER NOT NULL CHECK(typeof(after_cash)='integer' AND after_cash BETWEEN 0 AND 9999999999900000), recorded_ms INTEGER NOT NULL CHECK(typeof(recorded_ms)='integer' AND recorded_ms>=0), CHECK((checkout_id IS NULL)<>(drawer_id IS NULL)))");
        db.execSQL("CREATE INDEX drawer_effect_chain ON cashier_drawer_effects(shift_id,event_no DESC)");
        db.execSQL("CREATE VIEW cashier_drawer_current AS SELECT a.shift_id,COALESCE((SELECT e.after_cash FROM cashier_drawer_effects e WHERE e.shift_id=a.shift_id ORDER BY e.event_no DESC LIMIT 1),a.baseline_cash) AS expected_cash FROM cashier_drawer_anchors a");
        db.execSQL("CREATE TRIGGER drawer_request_initial BEFORE INSERT ON cashier_drawer_requests WHEN NEW.state<>'PENDING' OR EXISTS(SELECT 1 FROM cashier_drawer_requests r WHERE r.id=NEW.id OR (r.shift_id=NEW.shift_id AND r.state='PENDING')) OR NOT EXISTS(SELECT 1 FROM cashier_shifts s JOIN cashier_accounts a ON a.id=s.operator_id JOIN cashier_drawer_current b ON b.shift_id=s.id WHERE s.id=NEW.shift_id AND s.state='OPEN' AND s.operator_id=NEW.operator_id AND a.active=1 AND a.revision=NEW.stage_revision AND (NEW.kind='EXPENSE' OR a.role='OWNER') AND NEW.created_ms>=s.opened_ms AND NEW.before_cash=b.expected_cash) OR EXISTS(SELECT 1 FROM cashier_shift_checkouts l JOIN checkout_requests r ON r.request_id=l.request_id WHERE l.shift_id=NEW.shift_id AND r.state='PENDING') BEGIN SELECT RAISE(ABORT,'Invalid drawer request review'); END");
        db.execSQL("CREATE TRIGGER drawer_request_no_delete BEFORE DELETE ON cashier_drawer_requests BEGIN SELECT RAISE(ABORT,'Drawer request audit is immutable'); END");
        db.execSQL("CREATE TRIGGER drawer_request_resolution BEFORE UPDATE ON cashier_drawer_requests WHEN OLD.state<>'PENDING' OR NEW.state NOT IN ('APPLIED','CANCELLED') OR NEW.id IS NOT OLD.id OR NEW.shift_id IS NOT OLD.shift_id OR NEW.operator_id IS NOT OLD.operator_id OR NEW.stage_revision IS NOT OLD.stage_revision OR NEW.kind IS NOT OLD.kind OR NEW.amount IS NOT OLD.amount OR NEW.category IS NOT OLD.category OR NEW.reason IS NOT OLD.reason OR NEW.before_cash IS NOT OLD.before_cash OR NEW.after_cash IS NOT OLD.after_cash OR NEW.created_ms IS NOT OLD.created_ms OR NOT EXISTS(SELECT 1 FROM cashier_shifts s JOIN cashier_accounts a ON a.id=s.operator_id JOIN cashier_drawer_current b ON b.shift_id=s.id WHERE s.id=OLD.shift_id AND s.state='OPEN' AND a.id=OLD.operator_id AND a.active=1 AND a.revision=NEW.resolved_revision AND (OLD.kind='EXPENSE' OR a.role='OWNER') AND (NEW.state='CANCELLED' OR b.expected_cash=OLD.before_cash)) OR (NEW.state='APPLIED' AND EXISTS(SELECT 1 FROM cashier_shift_checkouts l JOIN checkout_requests r ON r.request_id=l.request_id WHERE l.shift_id=OLD.shift_id AND r.state='PENDING')) BEGIN SELECT RAISE(ABORT,'Invalid drawer resolution'); END");
        db.execSQL("CREATE TRIGGER drawer_effect_initial BEFORE INSERT ON cashier_drawer_effects WHEN EXISTS(SELECT 1 FROM cashier_drawer_effects e WHERE e.event_no=NEW.event_no OR e.checkout_id=NEW.checkout_id OR e.drawer_id=NEW.drawer_id) OR NOT EXISTS(SELECT 1 FROM cashier_drawer_current b JOIN cashier_shifts s ON s.id=b.shift_id WHERE b.shift_id=NEW.shift_id AND s.state='OPEN' AND b.expected_cash=NEW.before_cash) OR (NEW.checkout_id IS NOT NULL AND (EXISTS(SELECT 1 FROM cashier_drawer_legacy_sales WHERE checkout_id=NEW.checkout_id) OR NOT EXISTS(SELECT 1 FROM cashier_shift_checkouts l JOIN checkout_requests r ON r.request_id=l.request_id JOIN checkout_cash c ON c.request_id=r.request_id WHERE l.shift_id=NEW.shift_id AND l.request_id=NEW.checkout_id AND r.state='APPLIED' AND r.resolved_ms=NEW.recorded_ms AND NEW.after_cash=NEW.before_cash+c.paid))) OR (NEW.drawer_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM cashier_drawer_requests r WHERE r.id=NEW.drawer_id AND r.shift_id=NEW.shift_id AND r.state='APPLIED' AND r.before_cash=NEW.before_cash AND r.after_cash=NEW.after_cash AND r.resolved_ms=NEW.recorded_ms)) BEGIN SELECT RAISE(ABORT,'Invalid drawer cash effect'); END");
        db.execSQL("CREATE TRIGGER drawer_effect_no_update BEFORE UPDATE ON cashier_drawer_effects BEGIN SELECT RAISE(ABORT,'Drawer effects are immutable'); END");
        db.execSQL("CREATE TRIGGER drawer_effect_no_delete BEFORE DELETE ON cashier_drawer_effects BEGIN SELECT RAISE(ABORT,'Drawer effects are immutable'); END");
        db.execSQL("CREATE TRIGGER drawer_request_apply AFTER UPDATE ON cashier_drawer_requests WHEN NEW.state='APPLIED' BEGIN INSERT INTO cashier_drawer_effects(shift_id,drawer_id,before_cash,after_cash,recorded_ms) VALUES(NEW.shift_id,NEW.id,NEW.before_cash,NEW.after_cash,NEW.resolved_ms); SELECT CASE WHEN changes()<>1 THEN RAISE(ABORT,'Drawer effect unavailable') END; END");
        db.execSQL("CREATE TRIGGER drawer_checkout_link_guard BEFORE INSERT ON cashier_shift_checkouts WHEN EXISTS(SELECT 1 FROM cashier_drawer_requests r WHERE r.shift_id=NEW.shift_id AND r.state='PENDING') BEGIN SELECT RAISE(ABORT,'Resolve pending drawer activity before checkout'); END");
        db.execSQL("CREATE TRIGGER drawer_checkout_apply_guard BEFORE UPDATE ON checkout_requests WHEN NEW.state='APPLIED' AND EXISTS(SELECT 1 FROM cashier_shift_checkouts WHERE request_id=NEW.request_id) AND NOT EXISTS(SELECT 1 FROM cashier_shift_checkouts l JOIN cashier_drawer_current b ON b.shift_id=l.shift_id WHERE l.request_id=NEW.request_id AND b.expected_cash+NEW.total<=9999999999900000 AND NOT EXISTS(SELECT 1 FROM cashier_drawer_requests r WHERE r.shift_id=l.shift_id AND r.state='PENDING')) BEGIN SELECT RAISE(ABORT,'Invalid checkout drawer cash effect'); END");
        db.execSQL("CREATE TRIGGER drawer_checkout_apply AFTER UPDATE ON checkout_requests WHEN NEW.state='APPLIED' AND EXISTS(SELECT 1 FROM cashier_shift_checkouts WHERE request_id=NEW.request_id) BEGIN INSERT INTO cashier_drawer_effects(shift_id,checkout_id,before_cash,after_cash,recorded_ms) SELECT l.shift_id,NEW.request_id,b.expected_cash,b.expected_cash+NEW.total,NEW.resolved_ms FROM cashier_shift_checkouts l JOIN cashier_drawer_current b ON b.shift_id=l.shift_id WHERE l.request_id=NEW.request_id; SELECT CASE WHEN changes()<>1 THEN RAISE(ABORT,'Checkout drawer effect unavailable') END; END");
        db.execSQL("DROP TRIGGER cashier_shift_close");
        db.execSQL("CREATE TRIGGER cashier_shift_close BEFORE UPDATE ON cashier_shifts WHEN OLD.state<>'OPEN' OR NEW.state<>'CLOSED' OR NEW.id IS NOT OLD.id OR NEW.operator_id IS NOT OLD.operator_id OR NEW.opened_ms IS NOT OLD.opened_ms OR NEW.opening_cash IS NOT OLD.opening_cash OR EXISTS(SELECT 1 FROM cashier_shift_checkouts l JOIN checkout_requests r ON r.request_id=l.request_id WHERE l.shift_id=OLD.id AND r.state='PENDING') OR EXISTS(SELECT 1 FROM cashier_drawer_requests r WHERE r.shift_id=OLD.id AND r.state='PENDING') OR NOT EXISTS(SELECT 1 FROM cashier_drawer_current b WHERE b.shift_id=OLD.id AND b.expected_cash=NEW.expected_cash) BEGIN SELECT RAISE(ABORT,'Invalid shift close or unresolved request'); END");
    }
    static final class Request {
        final String id,shiftId,operatorId,category,reason,state,cancelReason;
        final ShiftMath.DrawerKind kind;
        final long amount,before,after,stageRevision,createdMs;
        final Long resolvedRevision,resolvedMs;
        Request(Cursor c){
            id=c.getString(0);shiftId=c.getString(1);operatorId=c.getString(2);stageRevision=c.getLong(3);
            kind=ShiftMath.DrawerKind.valueOf(c.getString(4));amount=c.getLong(5);category=c.getString(6);reason=c.getString(7);
            before=c.getLong(8);after=c.getLong(9);createdMs=c.getLong(10);state=c.getString(11);
            resolvedMs=c.isNull(12)?null:c.getLong(12);resolvedRevision=c.isNull(13)?null:c.getLong(13);cancelReason=c.getString(14);
        }
        void verify(String shift,ShiftMath.DrawerKind kind,long amount,String category,String reason,long before){
            if(!shiftId.equals(shift)||this.kind!=kind||this.amount!=amount||!java.util.Objects.equals(this.category,category)||!this.reason.equals(reason)||this.before!=before)
                throw new IllegalStateException("Drawer request identity already used for different review");
        }
    }
    private static final String COLUMNS="id,shift_id,operator_id,stage_revision,kind,amount,category,reason,before_cash,after_cash,created_ms,state,resolved_ms,resolved_revision,cancel_reason";
    static long expected(SQLiteDatabase db,String shift){
        try(Cursor c=db.rawQuery("SELECT expected_cash FROM cashier_drawer_current WHERE shift_id=?",new String[]{shift})){
            if(!c.moveToFirst())throw new IllegalStateException("Drawer balance unavailable");
            return ShiftMath.money(c.getLong(0));
        }
    }
    static Request read(SQLiteDatabase db,String id){
        try(Cursor c=db.rawQuery("SELECT "+COLUMNS+" FROM cashier_drawer_requests WHERE id=?",new String[]{id})){
            return c.moveToFirst()?new Request(c):null;
        }
    }
    static Request pending(SQLiteDatabase db,String shift){
        try(Cursor c=db.rawQuery("SELECT "+COLUMNS+" FROM cashier_drawer_requests WHERE shift_id=? AND state='PENDING'",new String[]{shift})){
            return c.moveToFirst()?new Request(c):null;
        }
    }
    static void requireNoPending(SQLiteDatabase db,String shift){
        if(pending(db,shift)!=null)throw new IllegalStateException("Resolve pending drawer activity first");
    }
    private static void requireNoCheckout(SQLiteDatabase db,String shift){
        try(Cursor c=db.rawQuery("SELECT 1 FROM cashier_shift_checkouts l JOIN checkout_requests r ON r.request_id=l.request_id WHERE l.shift_id=? AND r.state='PENDING' LIMIT 1",new String[]{shift})){
            if(c.moveToFirst())throw new IllegalStateException("Resolve pending checkout before drawer activity");
        }
    }
    private static void authorizeKind(CashierJournal.Principal actor,ShiftMath.DrawerKind kind){
        if(kind==null)throw new IllegalArgumentException("Select a drawer activity");
        if(kind!=ShiftMath.DrawerKind.EXPENSE)CashierSecurity.requireOwner(actor.role);
    }
    static Request requireRequest(SQLiteDatabase db,CashierJournal.Principal actor,String id){
        StockMath.validateRequestId(id);Request r=read(db,id);
        if(r==null||!actor.id.equals(r.operatorId))throw new IllegalStateException("Drawer request belongs to another operator or is unavailable");
        authorizeKind(actor,r.kind);
        CashierJournal.requireShift(db,actor,r.shiftId,"PENDING".equals(r.state));return r;
    }
    static Request stage(SQLiteDatabase db,CashierJournal.Principal actor,String shift,String id,ShiftMath.DrawerKind kind,long amount,
                         String category,String reason,long before,long now){
        StockMath.validateRequestId(id);authorizeKind(actor,kind);
        String label=ShiftMath.category(kind,category),note=StockMath.reason(reason);
        long after=ShiftMath.drawerAfter(before,kind,amount);
        Request prior=read(db,id);
        if(prior!=null){
            if(RefundJournal.linked(db,id))throw new IllegalStateException("Use receipt compensation review");
            if(RecurringJournal.linked(db,id))throw new IllegalStateException("Use recurring expense review");
            requireRequest(db,actor,id);prior.verify(shift,kind,amount,label,note,before);return prior;
        }
        CashierJournal.requireShift(db,actor,shift,true);requireNoPending(db,shift);requireNoCheckout(db,shift);
        if(expected(db,shift)!=before)throw new IllegalStateException("Drawer changed; reload and review again");
        ContentValues row=new ContentValues();row.put("id",id);row.put("shift_id",shift);row.put("operator_id",actor.id);row.put("stage_revision",actor.revision);
        row.put("kind",kind.name());row.put("amount",amount);if(label!=null)row.put("category",label);row.put("reason",note);
        row.put("before_cash",before);row.put("after_cash",after);row.put("created_ms",now);row.put("state","PENDING");
        db.insertOrThrow("cashier_drawer_requests",null,row);return read(db,id);
    }
    static Request resolve(SQLiteDatabase db,CashierJournal.Principal actor,String id,boolean apply,String reason,long now){
        if(RefundJournal.linked(db,id))throw new IllegalStateException("Use receipt compensation review");
        if(RecurringJournal.linked(db,id))throw new IllegalStateException("Use recurring expense review");
        return resolveInternal(db,actor,id,apply,reason,now);
    }
    static Request resolveLinked(SQLiteDatabase db,CashierJournal.Principal actor,String id,boolean apply,String reason,long now){
        CashierSecurity.requireOwner(actor.role);
        if(!RefundJournal.linked(db,id))throw new IllegalStateException("Receipt compensation unavailable");
        return resolveInternal(db,actor,id,apply,reason,now);
    }
    static Request resolveRecurring(SQLiteDatabase db,CashierJournal.Principal actor,String id,boolean apply,String reason,long now){
        CashierSecurity.requireOwner(actor.role);
        if(!RecurringJournal.linked(db,id))throw new IllegalStateException("Recurring expense occurrence unavailable");
        return resolveInternal(db,actor,id,apply,reason,now);
    }
    private static Request resolveInternal(SQLiteDatabase db,CashierJournal.Principal actor,String id,boolean apply,String reason,long now){
        if(apply&&reason!=null)throw new IllegalArgumentException("Cancellation reason is only for cancellation");
        Request r=requireRequest(db,actor,id);String note=apply?null:StockMath.reason(reason);
        if(!"PENDING".equals(r.state)){
            if((apply&&"APPLIED".equals(r.state))||(!apply&&"CANCELLED".equals(r.state)&&note.equals(r.cancelReason)))return r;
            throw new IllegalStateException("Drawer request already resolved differently");
        }
        if(apply){
            requireNoCheckout(db,r.shiftId);
            if(expected(db,r.shiftId)!=r.before)throw new IllegalStateException("Drawer changed; reload and review again");
        }
        ContentValues row=new ContentValues();row.put("state",apply?"APPLIED":"CANCELLED");
        row.put("resolved_revision",actor.revision);row.put("resolved_ms",now);if(note!=null)row.put("cancel_reason",note);
        if(db.update("cashier_drawer_requests",row,"id=? AND state='PENDING'",new String[]{id})!=1)throw new IllegalStateException("Drawer request changed");
        return read(db,id);
    }
    static List<Request> history(SQLiteDatabase db,CashierJournal.Principal actor,int offset,int limit){
        if(offset<0||limit<1||limit>51)throw new IllegalArgumentException("Invalid drawer history page");
        List<Request> result=new ArrayList<>();
        // Own shift history only; no credential/session data.
        try(Cursor c=db.rawQuery("SELECT "+COLUMNS+" FROM cashier_drawer_requests WHERE operator_id=? AND NOT EXISTS(SELECT 1 FROM cashier_refund_links l WHERE l.drawer_id=cashier_drawer_requests.id) AND NOT EXISTS(SELECT 1 FROM cashier_recurring_occurrences o WHERE o.drawer_id=cashier_drawer_requests.id) ORDER BY created_ms DESC,id DESC LIMIT ? OFFSET ?",
            new String[]{actor.id,Integer.toString(limit),Integer.toString(offset)})){
            while(c.moveToNext())result.add(new Request(c));
        }
        return result;
    }
}
