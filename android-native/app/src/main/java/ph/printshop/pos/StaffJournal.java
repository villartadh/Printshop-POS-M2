package ph.printshop.pos;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import java.util.UUID;

/** Additive v9 immutable account-disable audit. No credential or session secret is stored. */
final class StaffJournal {
    private StaffJournal(){}
    static void upgrade(SQLiteDatabase db){
        db.execSQL("CREATE TABLE cashier_disable_events (id TEXT PRIMARY KEY NOT NULL, account_id TEXT NOT NULL UNIQUE REFERENCES cashier_accounts(id), actor_id TEXT NOT NULL REFERENCES cashier_accounts(id), actor_revision INTEGER NOT NULL CHECK(typeof(actor_revision)='integer' AND actor_revision>=1), prior_revision INTEGER NOT NULL CHECK(typeof(prior_revision)='integer' AND prior_revision>=1), next_revision INTEGER NOT NULL CHECK(typeof(next_revision)='integer' AND next_revision=prior_revision+1), reason TEXT NOT NULL CHECK(typeof(reason)='text' AND length(reason) BETWEEN 1 AND 160 AND reason=trim(reason) AND instr(reason,char(0))=0 AND instr(reason,char(9))=0 AND instr(reason,char(10))=0 AND instr(reason,char(13))=0), disabled_ms INTEGER NOT NULL CHECK(typeof(disabled_ms)='integer' AND disabled_ms>=0), CHECK(account_id<>actor_id))");
        db.execSQL("CREATE TRIGGER cashier_disable_event_initial BEFORE INSERT ON cashier_disable_events WHEN EXISTS(SELECT 1 FROM cashier_disable_events WHERE id=NEW.id OR account_id=NEW.account_id) OR NOT EXISTS(SELECT 1 FROM cashier_accounts target JOIN cashier_accounts actor ON actor.id=NEW.actor_id WHERE target.id=NEW.account_id AND target.role='CASHIER' AND target.active=1 AND target.revision=NEW.prior_revision AND actor.role='OWNER' AND actor.active=1 AND actor.revision=NEW.actor_revision AND NEW.disabled_ms>=actor.created_ms AND NEW.disabled_ms>=target.created_ms) OR EXISTS(SELECT 1 FROM cashier_shifts WHERE operator_id=NEW.account_id AND state='OPEN') OR EXISTS(SELECT 1 FROM cashier_shift_checkouts l JOIN checkout_requests r ON r.request_id=l.request_id WHERE l.operator_id=NEW.account_id AND r.state='PENDING') OR EXISTS(SELECT 1 FROM cashier_credential_events WHERE account_id=NEW.account_id AND next_revision=NEW.next_revision) BEGIN SELECT RAISE(ABORT,'Invalid cashier disable event'); END");
        db.execSQL("CREATE TRIGGER cashier_disable_event_no_update BEFORE UPDATE ON cashier_disable_events BEGIN SELECT RAISE(ABORT,'Cashier disable audit is immutable'); END");
        db.execSQL("CREATE TRIGGER cashier_disable_event_no_delete BEFORE DELETE ON cashier_disable_events BEGIN SELECT RAISE(ABORT,'Cashier disable audit is immutable'); END");
        db.execSQL("CREATE TRIGGER cashier_account_disable_audit BEFORE UPDATE ON cashier_accounts WHEN OLD.active=1 AND NEW.active=0 AND (NEW.failures<>OLD.failures OR NEW.locked_until<>OLD.locked_until OR NOT EXISTS(SELECT 1 FROM cashier_disable_events e WHERE e.account_id=OLD.id AND e.prior_revision=OLD.revision AND e.next_revision=NEW.revision)) BEGIN SELECT RAISE(ABORT,'Cashier disable requires immutable audit'); END");
        db.execSQL("CREATE TRIGGER cashier_disable_event_apply AFTER INSERT ON cashier_disable_events BEGIN UPDATE cashier_accounts SET active=0,revision=NEW.next_revision WHERE id=NEW.account_id AND active=1 AND revision=NEW.prior_revision; SELECT CASE WHEN changes()<>1 THEN RAISE(ABORT,'Cashier disable transition changed') END; UPDATE cashier_sessions SET revoked_ms=MAX(issued_ms,NEW.disabled_ms) WHERE account_id=NEW.account_id AND revoked_ms IS NULL; END");
    }
    /** Inserting the event applies the exact disable and session revocation atomically via its trigger. */
    static void disable(SQLiteDatabase db,CashierJournal.Principal owner,CashierJournal.Principal target,String reason,long now){
        ContentValues v=new ContentValues();
        v.put("id",UUID.randomUUID().toString());v.put("account_id",target.id);v.put("actor_id",owner.id);
        v.put("actor_revision",owner.revision);v.put("prior_revision",target.revision);v.put("next_revision",Math.addExact(target.revision,1));
        v.put("reason",reason);v.put("disabled_ms",now);
        db.insertOrThrow("cashier_disable_events",null,v);
    }
}
