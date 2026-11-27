package ph.printshop.pos;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.UUID;

/** Owner-controlled, audited opt-in. Applied only at the first successful sale commit. */
final class ProductionPolicy {
    private ProductionPolicy(){}
    static void upgrade(SQLiteDatabase db){
        db.execSQL("CREATE TABLE production_service_policy (service_id TEXT NOT NULL REFERENCES catalog(id), revision INTEGER NOT NULL CHECK(typeof(revision)='integer' AND revision>=1), service_revision INTEGER NOT NULL, enabled INTEGER NOT NULL CHECK(enabled IN (0,1)), actor_id TEXT NOT NULL REFERENCES cashier_accounts(id), actor_revision INTEGER NOT NULL, created_ms INTEGER NOT NULL CHECK(typeof(created_ms)='integer' AND created_ms>=0), PRIMARY KEY(service_id,revision), FOREIGN KEY(service_id,service_revision) REFERENCES catalog_history(id,revision))");
        db.execSQL("CREATE TRIGGER production_policy_guard BEFORE INSERT ON production_service_policy WHEN NOT EXISTS(SELECT 1 FROM catalog c JOIN cashier_accounts a ON a.id=NEW.actor_id WHERE c.id=NEW.service_id AND c.kind='SERVICE' AND c.archived=0 AND c.revision=NEW.service_revision AND a.role='OWNER' AND a.active=1 AND a.revision=NEW.actor_revision) OR NEW.revision<>COALESCE((SELECT MAX(revision)+1 FROM production_service_policy WHERE service_id=NEW.service_id),1) BEGIN SELECT RAISE(ABORT,'Invalid production policy'); END");
        db.execSQL("CREATE TRIGGER production_policy_no_update BEFORE UPDATE ON production_service_policy BEGIN SELECT RAISE(ABORT,'Production policy immutable'); END");
        db.execSQL("CREATE TRIGGER production_policy_no_delete BEFORE DELETE ON production_service_policy BEGIN SELECT RAISE(ABORT,'Production policy immutable'); END");
    }
    static final class Setting {
        final long revision; final boolean enabled;
        Setting(long revision,boolean enabled){this.revision=revision;this.enabled=enabled;}
    }
    static Setting read(SQLiteDatabase db,String service){
        try(Cursor c=db.rawQuery("SELECT revision,enabled FROM production_service_policy WHERE service_id=? ORDER BY revision DESC LIMIT 1",new String[]{service})){
            return c.moveToFirst()?new Setting(c.getLong(0),c.getInt(1)==1):new Setting(0,false);
        }
    }
    static Setting set(SQLiteDatabase db,CashierJournal.Principal actor,String service,long catalogRevision,long prior,boolean enabled,long now){
        CashierSecurity.requireOwner(actor.role);
        Setting current=read(db,service);
        if(current.revision!=prior||prior<0||prior==Long.MAX_VALUE)throw new IllegalStateException("Production policy changed; reload");
        ContentValues row=new ContentValues();
        row.put("service_id",service);row.put("revision",prior+1);row.put("service_revision",catalogRevision);
        row.put("enabled",enabled?1:0);row.put("actor_id",actor.id);row.put("actor_revision",actor.revision);row.put("created_ms",now);
        db.insertOrThrow("production_service_policy",null,row);
        return read(db,service);
    }
    static void onNewSale(SQLiteDatabase db,CashierJournal.Principal actor,String service,String receipt,long now){
        Setting current=read(db,service);
        if(current.enabled)ProductionJournal.append(db,actor,receipt,UUID.randomUUID().toString(),0,
            ProductionWorkflow.Status.PENDING,"Automatic production · service policy revision "+current.revision,now);
    }
}
