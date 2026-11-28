package ph.printshop.pos;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/** v10 owner-attributed manual administrative mutations. No secrets or retrospective events. */
final class AdminJournal {
    private AdminJournal(){}
    static void upgrade(SQLiteDatabase db){
        db.execSQL("CREATE TABLE cashier_admin_events (event_no INTEGER PRIMARY KEY AUTOINCREMENT CHECK(event_no>=1), actor_id TEXT NOT NULL REFERENCES cashier_accounts(id), actor_revision INTEGER NOT NULL CHECK(typeof(actor_revision)='integer' AND actor_revision>=1), action TEXT NOT NULL CHECK(action IN ('CATALOG_CREATE','CATALOG_EDIT','CATALOG_ARCHIVE','CATALOG_RESTORE','PRICING_SAVE','RECIPE_SAVE','STOCK_STAGE','STOCK_APPLY','STOCK_CANCEL')), subject_id TEXT NOT NULL REFERENCES catalog(id), revision INTEGER NOT NULL CHECK(typeof(revision)='integer' AND revision>=0 AND (action IN ('STOCK_STAGE','STOCK_CANCEL') OR revision>=1)), request_id TEXT REFERENCES stock_requests(request_id), recorded_ms INTEGER NOT NULL CHECK(typeof(recorded_ms)='integer' AND recorded_ms>=0), CHECK((action IN ('STOCK_STAGE','STOCK_APPLY','STOCK_CANCEL'))=(request_id IS NOT NULL)))");
        db.execSQL("CREATE UNIQUE INDEX cashier_admin_revision ON cashier_admin_events(action,subject_id,revision) WHERE request_id IS NULL");
        db.execSQL("CREATE UNIQUE INDEX cashier_admin_request ON cashier_admin_events(action,request_id) WHERE request_id IS NOT NULL");
        db.execSQL("CREATE TRIGGER cashier_admin_event_initial BEFORE INSERT ON cashier_admin_events WHEN EXISTS(SELECT 1 FROM cashier_admin_events e WHERE e.event_no=NEW.event_no OR (e.action=NEW.action AND ((NEW.request_id IS NULL AND e.request_id IS NULL AND e.subject_id=NEW.subject_id AND e.revision=NEW.revision) OR e.request_id=NEW.request_id))) OR NOT EXISTS(SELECT 1 FROM cashier_accounts a WHERE a.id=NEW.actor_id AND a.active=1 AND a.role='OWNER' AND a.revision=NEW.actor_revision AND NEW.recorded_ms>=a.created_ms) OR (NEW.action GLOB 'CATALOG_*' AND NOT EXISTS(SELECT 1 FROM catalog_history h WHERE h.id=NEW.subject_id AND h.revision=NEW.revision AND NEW.action='CATALOG_'||h.action)) OR (NEW.action='PRICING_SAVE' AND NOT EXISTS(SELECT 1 FROM pricing_profile_versions p WHERE p.service_id=NEW.subject_id AND p.revision=NEW.revision AND NEW.recorded_ms>=p.created_ms)) OR (NEW.action='RECIPE_SAVE' AND NOT EXISTS(SELECT 1 FROM recipe_history h WHERE h.service_id=NEW.subject_id AND h.revision=NEW.revision AND NEW.recorded_ms>=h.created_ms)) OR (NEW.request_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM stock_requests r WHERE r.request_id=NEW.request_id AND r.material_id=NEW.subject_id AND NEW.recorded_ms>=r.created_ms AND ((NEW.action='STOCK_STAGE' AND r.state='PENDING' AND NEW.revision=r.expected_stock_revision) OR (NEW.action='STOCK_CANCEL' AND r.state='CANCELLED' AND NEW.revision=r.expected_stock_revision AND NEW.recorded_ms>=r.resolved_ms AND NOT EXISTS(SELECT 1 FROM stock_movements m WHERE m.request_id=r.request_id)) OR (NEW.action='STOCK_APPLY' AND r.state='APPLIED' AND NEW.revision=r.expected_stock_revision+1 AND NEW.recorded_ms>=r.resolved_ms AND EXISTS(SELECT 1 FROM stock_movements m WHERE m.request_id=r.request_id AND m.material_id=r.material_id AND m.catalog_revision=r.catalog_revision AND m.stock_revision=NEW.revision AND m.delta=r.delta AND m.reason=r.reason))))) BEGIN SELECT RAISE(ABORT,'Invalid administrative audit event'); END");
        db.execSQL("CREATE TRIGGER cashier_admin_event_no_update BEFORE UPDATE ON cashier_admin_events BEGIN SELECT RAISE(ABORT,'Administrative audit is immutable'); END");
        db.execSQL("CREATE TRIGGER cashier_admin_event_no_delete BEFORE DELETE ON cashier_admin_events BEGIN SELECT RAISE(ABORT,'Administrative audit is immutable'); END");
        db.execSQL("CREATE TRIGGER admin_catalog_history_insert BEFORE INSERT ON catalog_history WHEN EXISTS(SELECT 1 FROM cashier_admin_events e WHERE e.action GLOB 'CATALOG_*' AND e.subject_id=NEW.id AND e.revision=NEW.revision) BEGIN SELECT RAISE(ABORT,'Audited source is immutable'); END");
        db.execSQL("CREATE TRIGGER admin_catalog_history_update BEFORE UPDATE ON catalog_history WHEN EXISTS(SELECT 1 FROM cashier_admin_events e WHERE e.action GLOB 'CATALOG_*' AND e.subject_id=OLD.id AND e.revision=OLD.revision) OR EXISTS(SELECT 1 FROM cashier_admin_events e WHERE e.action GLOB 'CATALOG_*' AND e.subject_id=NEW.id AND e.revision=NEW.revision) BEGIN SELECT RAISE(ABORT,'Audited source is immutable'); END");
        db.execSQL("CREATE TRIGGER admin_catalog_history_delete BEFORE DELETE ON catalog_history WHEN EXISTS(SELECT 1 FROM cashier_admin_events e WHERE e.action GLOB 'CATALOG_*' AND e.subject_id=OLD.id AND e.revision=OLD.revision) BEGIN SELECT RAISE(ABORT,'Audited source is immutable'); END");
        db.execSQL("CREATE TRIGGER admin_pricing_profile_versions_insert BEFORE INSERT ON pricing_profile_versions WHEN EXISTS(SELECT 1 FROM cashier_admin_events e WHERE e.action='PRICING_SAVE' AND e.subject_id=NEW.service_id AND e.revision=NEW.revision) BEGIN SELECT RAISE(ABORT,'Audited source is immutable'); END");
        db.execSQL("CREATE TRIGGER admin_pricing_profile_versions_update BEFORE UPDATE ON pricing_profile_versions WHEN EXISTS(SELECT 1 FROM cashier_admin_events e WHERE e.action='PRICING_SAVE' AND e.subject_id=OLD.service_id AND e.revision=OLD.revision) OR EXISTS(SELECT 1 FROM cashier_admin_events e WHERE e.action='PRICING_SAVE' AND e.subject_id=NEW.service_id AND e.revision=NEW.revision) BEGIN SELECT RAISE(ABORT,'Audited source is immutable'); END");
        db.execSQL("CREATE TRIGGER admin_pricing_profile_versions_delete BEFORE DELETE ON pricing_profile_versions WHEN EXISTS(SELECT 1 FROM cashier_admin_events e WHERE e.action='PRICING_SAVE' AND e.subject_id=OLD.service_id AND e.revision=OLD.revision) BEGIN SELECT RAISE(ABORT,'Audited source is immutable'); END");
        db.execSQL("CREATE TRIGGER admin_pricing_tier_versions_insert BEFORE INSERT ON pricing_tier_versions WHEN EXISTS(SELECT 1 FROM cashier_admin_events e WHERE e.action='PRICING_SAVE' AND e.subject_id=NEW.service_id AND e.revision=NEW.profile_revision) BEGIN SELECT RAISE(ABORT,'Audited source is immutable'); END");
        db.execSQL("CREATE TRIGGER admin_pricing_tier_versions_update BEFORE UPDATE ON pricing_tier_versions WHEN EXISTS(SELECT 1 FROM cashier_admin_events e WHERE e.action='PRICING_SAVE' AND e.subject_id=OLD.service_id AND e.revision=OLD.profile_revision) OR EXISTS(SELECT 1 FROM cashier_admin_events e WHERE e.action='PRICING_SAVE' AND e.subject_id=NEW.service_id AND e.revision=NEW.profile_revision) BEGIN SELECT RAISE(ABORT,'Audited source is immutable'); END");
        db.execSQL("CREATE TRIGGER admin_pricing_tier_versions_delete BEFORE DELETE ON pricing_tier_versions WHEN EXISTS(SELECT 1 FROM cashier_admin_events e WHERE e.action='PRICING_SAVE' AND e.subject_id=OLD.service_id AND e.revision=OLD.profile_revision) BEGIN SELECT RAISE(ABORT,'Audited source is immutable'); END");
        db.execSQL("CREATE TRIGGER admin_recipe_history_insert BEFORE INSERT ON recipe_history WHEN EXISTS(SELECT 1 FROM cashier_admin_events e WHERE e.action='RECIPE_SAVE' AND e.subject_id=NEW.service_id AND e.revision=NEW.revision) BEGIN SELECT RAISE(ABORT,'Audited source is immutable'); END");
        db.execSQL("CREATE TRIGGER admin_recipe_history_update BEFORE UPDATE ON recipe_history WHEN EXISTS(SELECT 1 FROM cashier_admin_events e WHERE e.action='RECIPE_SAVE' AND e.subject_id=OLD.service_id AND e.revision=OLD.revision) OR EXISTS(SELECT 1 FROM cashier_admin_events e WHERE e.action='RECIPE_SAVE' AND e.subject_id=NEW.service_id AND e.revision=NEW.revision) BEGIN SELECT RAISE(ABORT,'Audited source is immutable'); END");
        db.execSQL("CREATE TRIGGER admin_recipe_history_delete BEFORE DELETE ON recipe_history WHEN EXISTS(SELECT 1 FROM cashier_admin_events e WHERE e.action='RECIPE_SAVE' AND e.subject_id=OLD.service_id AND e.revision=OLD.revision) BEGIN SELECT RAISE(ABORT,'Audited source is immutable'); END");
        db.execSQL("CREATE TRIGGER admin_stock_movements_insert BEFORE INSERT ON stock_movements WHEN EXISTS(SELECT 1 FROM cashier_admin_events e JOIN stock_requests r ON r.request_id=e.request_id WHERE (e.action='STOCK_APPLY' AND (e.request_id=NEW.request_id OR (e.subject_id=NEW.material_id AND e.revision=NEW.stock_revision))) OR (e.action='STOCK_CANCEL' AND e.request_id=NEW.request_id)) BEGIN SELECT RAISE(ABORT,'Audited stock movement is immutable'); END");
        db.execSQL("CREATE TRIGGER admin_stock_movements_update BEFORE UPDATE ON stock_movements WHEN EXISTS(SELECT 1 FROM cashier_admin_events e JOIN stock_requests r ON r.request_id=e.request_id WHERE (e.action='STOCK_APPLY' AND (e.request_id=OLD.request_id OR (e.subject_id=OLD.material_id AND e.revision=OLD.stock_revision))) OR (e.action='STOCK_CANCEL' AND e.request_id=OLD.request_id)) OR EXISTS(SELECT 1 FROM cashier_admin_events e JOIN stock_requests r ON r.request_id=e.request_id WHERE (e.action='STOCK_APPLY' AND (e.request_id=NEW.request_id OR (e.subject_id=NEW.material_id AND e.revision=NEW.stock_revision))) OR (e.action='STOCK_CANCEL' AND e.request_id=NEW.request_id)) BEGIN SELECT RAISE(ABORT,'Audited stock movement is immutable'); END");
        db.execSQL("CREATE TRIGGER admin_stock_movements_delete BEFORE DELETE ON stock_movements WHEN EXISTS(SELECT 1 FROM cashier_admin_events e JOIN stock_requests r ON r.request_id=e.request_id WHERE (e.action='STOCK_APPLY' AND (e.request_id=OLD.request_id OR (e.subject_id=OLD.material_id AND e.revision=OLD.stock_revision))) OR (e.action='STOCK_CANCEL' AND e.request_id=OLD.request_id)) BEGIN SELECT RAISE(ABORT,'Audited stock movement is immutable'); END");
        db.execSQL("CREATE TRIGGER admin_stock_request_insert BEFORE INSERT ON stock_requests WHEN EXISTS(SELECT 1 FROM cashier_admin_events e JOIN stock_requests r ON r.request_id=e.request_id WHERE r.request_id=NEW.request_id OR (r.material_id=NEW.material_id AND r.state='PENDING' AND NEW.state='PENDING')) BEGIN SELECT RAISE(ABORT,'Audited stock request cannot be replaced'); END");
    }
    /** Call only inside the same transaction as the linked source mutation. */
    static void append(SQLiteDatabase db,CashierJournal.Principal actor,String action,String subject,long revision,String request){
        if(actor==null)return; // Ordinary preview / pre-auth history has no authenticated actor.
        CashierSecurity.requireOwner(actor.role);
        ContentValues row=new ContentValues();
        row.put("actor_id",actor.id);row.put("actor_revision",actor.revision);
        row.put("action",action);row.put("subject_id",subject);row.put("revision",revision);
        if(request!=null)row.put("request_id",request);
        row.put("recorded_ms",System.currentTimeMillis());
        db.insertOrThrow("cashier_admin_events",null,row);
    }
    static final class Entry {
        final long eventNo,actorRevision,revision,recordedMs;
        final String actorId,actorName,action,subjectId,subjectName,requestId,reason;
        Entry(Cursor c){
            eventNo=c.getLong(0);actorId=c.getString(1);actorName=c.getString(2);actorRevision=c.getLong(3);
            action=c.getString(4);subjectId=c.getString(5);subjectName=c.getString(6);revision=c.getLong(7);
            requestId=c.getString(8);recordedMs=c.getLong(9);reason=c.getString(10);
        }
    }
    /** Keyset pagination avoids duplicates if later events are inserted between pages. */
    static List<Entry> list(SQLiteDatabase db,long before,int limit){
        if(before<0||limit<1||limit>50)throw new IllegalArgumentException("Invalid administrative history page");
        List<Entry> result=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT e.event_no,e.actor_id,a.username,e.actor_revision,e.action,e.subject_id,c.name,e.revision,e.request_id,e.recorded_ms,r.reason FROM cashier_admin_events e JOIN cashier_accounts a ON a.id=e.actor_id JOIN catalog c ON c.id=e.subject_id LEFT JOIN stock_requests r ON r.request_id=e.request_id WHERE (CAST(? AS INTEGER)=0 OR e.event_no<CAST(? AS INTEGER)) ORDER BY e.event_no DESC LIMIT ?",new String[]{Long.toString(before),Long.toString(before),Integer.toString(limit)})){
            while(c.moveToNext())result.add(new Entry(c));
        }
        return result;
    }
}
