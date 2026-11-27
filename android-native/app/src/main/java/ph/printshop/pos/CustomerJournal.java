package ph.printshop.pos;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/** Immutable owner-managed synthetic customer directory; no receivables or checkout effects yet. */
final class CustomerJournal {
    private CustomerJournal(){}
    static void upgrade(SQLiteDatabase db){
        db.execSQL("CREATE TABLE cashier_customer_versions (id TEXT NOT NULL CHECK(length(id) BETWEEN 1 AND 80), revision INTEGER NOT NULL CHECK(typeof(revision)='integer' AND revision>=1), name TEXT NOT NULL CHECK(length(name) BETWEEN 1 AND 80 AND name=trim(name) AND instr(name,char(0))=0 AND instr(name,char(9))=0 AND instr(name,char(10))=0 AND instr(name,char(13))=0), contact TEXT NOT NULL CHECK(length(contact)<=120 AND contact=trim(contact) AND instr(contact,char(0))=0 AND instr(contact,char(9))=0 AND instr(contact,char(10))=0 AND instr(contact,char(13))=0), active INTEGER NOT NULL CHECK(typeof(active)='integer' AND active IN (0,1)), actor_id TEXT NOT NULL REFERENCES cashier_accounts(id), actor_revision INTEGER NOT NULL CHECK(typeof(actor_revision)='integer' AND actor_revision>=1), recorded_ms INTEGER NOT NULL CHECK(typeof(recorded_ms)='integer' AND recorded_ms>=0), PRIMARY KEY(id,revision))");
        db.execSQL("CREATE VIEW cashier_customer_current AS SELECT v.* FROM cashier_customer_versions v WHERE v.revision=(SELECT MAX(w.revision) FROM cashier_customer_versions w WHERE w.id=v.id)");
        db.execSQL("CREATE TRIGGER customer_version_initial BEFORE INSERT ON cashier_customer_versions WHEN (NEW.revision=1 AND NEW.active<>1) OR NEW.revision<>COALESCE((SELECT MAX(revision)+1 FROM cashier_customer_versions WHERE id=NEW.id),1) OR NOT EXISTS(SELECT 1 FROM cashier_accounts a WHERE a.id=NEW.actor_id AND a.role='OWNER' AND a.active=1 AND a.revision=NEW.actor_revision AND NEW.recorded_ms>=a.created_ms) OR EXISTS(SELECT 1 FROM cashier_customer_current c WHERE c.id=NEW.id AND NEW.recorded_ms<c.recorded_ms) BEGIN SELECT RAISE(ABORT,'Invalid customer revision'); END");
        db.execSQL("CREATE TRIGGER customer_version_no_update BEFORE UPDATE ON cashier_customer_versions BEGIN SELECT RAISE(ABORT,'Customer revision immutable'); END");
        db.execSQL("CREATE TRIGGER customer_version_no_delete BEFORE DELETE ON cashier_customer_versions BEGIN SELECT RAISE(ABORT,'Customer revision immutable'); END");
    }
    static final class Customer {
        final String id,name,contact,actorId;
        final long revision,actorRevision,recordedMs;
        final boolean active;
        Customer(Cursor c){
            id=c.getString(0);revision=c.getLong(1);name=c.getString(2);contact=c.getString(3);
            active=c.getInt(4)==1;actorId=c.getString(5);actorRevision=c.getLong(6);recordedMs=c.getLong(7);
        }
    }
    private static final String COLUMNS="id,revision,name,contact,active,actor_id,actor_revision,recorded_ms";
    static String text(String value,int max,boolean required){
        if(value==null)throw new IllegalArgumentException("Invalid customer details");
        String result=value.trim();
        if((required&&result.isEmpty())||result.length()>max||result.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid customer details");
        return result;
    }
    static Customer current(SQLiteDatabase db,String id){
        try(Cursor c=db.rawQuery("SELECT "+COLUMNS+" FROM cashier_customer_current WHERE id=?",new String[]{id})){
            return c.moveToFirst()?new Customer(c):null;
        }
    }
    static Customer save(SQLiteDatabase db,CashierJournal.Principal actor,String id,long expected,String name,String contact,boolean active,long now){
        CashierSecurity.requireOwner(actor.role);StockMath.validateRequestId(id);
        if(expected<0)throw new IllegalArgumentException("Invalid customer revision");
        name=text(name,80,true);contact=text(contact,120,false);
        Customer previous=current(db,id);long revision=previous==null?0:previous.revision;
        if(previous!=null&&revision>0&&expected==revision-1&&previous.name.equals(name)&&previous.contact.equals(contact)&&previous.active==active)return previous;
        if(expected!=revision||(!active&&previous==null))throw new IllegalStateException("Customer changed; reload");
        if(previous!=null&&previous.name.equals(name)&&previous.contact.equals(contact)&&previous.active==active)return previous;
        ContentValues row=new ContentValues();
        row.put("id",id);row.put("revision",Math.addExact(revision,1));row.put("name",name);row.put("contact",contact);
        row.put("active",active?1:0);row.put("actor_id",actor.id);row.put("actor_revision",actor.revision);row.put("recorded_ms",now);
        db.insertOrThrow("cashier_customer_versions",null,row);return current(db,id);
    }
    private static void page(int offset,int limit){
        if(offset<0||limit<1||limit>51)throw new IllegalArgumentException("Invalid customer page");
    }
    static List<Customer> list(SQLiteDatabase db,CashierJournal.Principal actor,boolean active,String search,int offset,int limit){
        CashierSecurity.requireOwner(actor.role);page(offset,limit);search=text(search,80,false);
        List<Customer> result=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT "+COLUMNS+" FROM cashier_customer_current WHERE active=? AND instr(lower(name),lower(?))>0 ORDER BY name COLLATE NOCASE,id LIMIT ? OFFSET ?",new String[]{active?"1":"0",search,Integer.toString(limit),Integer.toString(offset)})){
            while(c.moveToNext())result.add(new Customer(c));
        }
        return result;
    }
    static List<Customer> history(SQLiteDatabase db,CashierJournal.Principal actor,String id,int offset,int limit){
        CashierSecurity.requireOwner(actor.role);StockMath.validateRequestId(id);page(offset,limit);
        List<Customer> result=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT "+COLUMNS+" FROM cashier_customer_versions WHERE id=? ORDER BY revision DESC LIMIT ? OFFSET ?",new String[]{id,Integer.toString(limit),Integer.toString(offset)})){
            while(c.moveToNext())result.add(new Customer(c));
        }
        return result;
    }
}
