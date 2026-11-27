package ph.printshop.pos;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import java.util.UUID;

/** Additive v8 credential lifecycle audit. No password, salt, hash or session token in audit rows. */
final class CredentialJournal {
    private CredentialJournal(){}
    static void upgrade(SQLiteDatabase db){
        db.execSQL("CREATE TABLE cashier_credential_events (id TEXT PRIMARY KEY NOT NULL, account_id TEXT NOT NULL REFERENCES cashier_accounts(id), actor_id TEXT NOT NULL REFERENCES cashier_accounts(id), prior_revision INTEGER NOT NULL CHECK(typeof(prior_revision)='integer' AND prior_revision>=1), next_revision INTEGER NOT NULL CHECK(typeof(next_revision)='integer' AND next_revision=prior_revision+1), action TEXT NOT NULL CHECK(action IN ('SELF_CHANGE','OWNER_RESET')), changed_ms INTEGER NOT NULL CHECK(typeof(changed_ms)='integer' AND changed_ms>=0), UNIQUE(account_id,next_revision), CHECK((action='SELF_CHANGE' AND account_id=actor_id) OR (action='OWNER_RESET' AND account_id<>actor_id)))");
        db.execSQL("CREATE TRIGGER cashier_credential_event_initial BEFORE INSERT ON cashier_credential_events WHEN EXISTS(SELECT 1 FROM cashier_credential_events WHERE id=NEW.id OR (account_id=NEW.account_id AND next_revision=NEW.next_revision)) OR NOT EXISTS(SELECT 1 FROM cashier_accounts a JOIN cashier_accounts actor ON actor.id=NEW.actor_id WHERE a.id=NEW.account_id AND a.active=1 AND a.revision=NEW.prior_revision AND actor.active=1 AND NEW.changed_ms>=a.created_ms AND NEW.changed_ms>=actor.created_ms AND ((NEW.action='SELF_CHANGE' AND actor.id=a.id) OR (NEW.action='OWNER_RESET' AND actor.role='OWNER' AND a.role='CASHIER' AND actor.id<>a.id))) BEGIN SELECT RAISE(ABORT,'Invalid credential audit event'); END");
        db.execSQL("CREATE TRIGGER cashier_credential_event_no_update BEFORE UPDATE ON cashier_credential_events BEGIN SELECT RAISE(ABORT,'Credential audit is immutable'); END");
        db.execSQL("CREATE TRIGGER cashier_credential_event_no_delete BEFORE DELETE ON cashier_credential_events BEGIN SELECT RAISE(ABORT,'Credential audit is immutable'); END");
        db.execSQL("DROP TRIGGER cashier_account_guard");
        db.execSQL("CREATE TRIGGER cashier_account_guard BEFORE UPDATE ON cashier_accounts WHEN NEW.id IS NOT OLD.id OR NEW.username IS NOT OLD.username OR NEW.role IS NOT OLD.role OR NEW.iterations IS NOT OLD.iterations OR NEW.created_ms IS NOT OLD.created_ms OR (OLD.active=0 AND NEW.active<>0) OR ((NEW.salt IS NOT OLD.salt OR NEW.password_hash IS NOT OLD.password_hash) AND (OLD.active<>1 OR NEW.active<>OLD.active OR NEW.salt IS OLD.salt OR NEW.password_hash IS OLD.password_hash OR NEW.revision<>OLD.revision+1 OR NEW.failures<>0 OR NEW.locked_until<>0 OR NOT EXISTS(SELECT 1 FROM cashier_credential_events e WHERE e.account_id=OLD.id AND e.prior_revision=OLD.revision AND e.next_revision=NEW.revision))) OR (NEW.salt IS OLD.salt AND NEW.password_hash IS OLD.password_hash AND ((NEW.active=OLD.active AND NEW.revision<>OLD.revision) OR (NEW.active<>OLD.active AND (OLD.role='OWNER' OR NEW.revision<>OLD.revision+1 OR EXISTS(SELECT 1 FROM cashier_shifts WHERE operator_id=OLD.id AND state='OPEN'))))) BEGIN SELECT RAISE(ABORT,'Invalid account transition'); END");
        db.execSQL("CREATE TRIGGER cashier_credential_revoke_sessions AFTER UPDATE ON cashier_accounts WHEN NEW.salt IS NOT OLD.salt OR NEW.password_hash IS NOT OLD.password_hash BEGIN UPDATE cashier_sessions SET revoked_ms=MAX(issued_ms,(SELECT changed_ms FROM cashier_credential_events WHERE account_id=NEW.id AND next_revision=NEW.revision)) WHERE account_id=NEW.id AND revoked_ms IS NULL; END");
    }
    static void append(SQLiteDatabase db,CashierJournal.Principal target,CashierJournal.Principal actor,String action,long now){
        ContentValues v=new ContentValues();
        v.put("id",UUID.randomUUID().toString());v.put("account_id",target.id);v.put("actor_id",actor.id);
        v.put("prior_revision",target.revision);v.put("next_revision",Math.addExact(target.revision,1));
        v.put("action",action);v.put("changed_ms",now);
        db.insertOrThrow("cashier_credential_events",null,v);
    }
}
