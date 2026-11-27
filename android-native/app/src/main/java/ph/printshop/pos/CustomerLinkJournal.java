package ph.printshop.pos;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/** Optional customer attribution for existing full-cash authenticated checkout; no debt or new cash effects. */
final class CustomerLinkJournal {
    private CustomerLinkJournal(){}
    static void upgrade(SQLiteDatabase db){
        db.execSQL("CREATE TABLE cashier_checkout_customers (request_id TEXT PRIMARY KEY NOT NULL REFERENCES cashier_shift_checkouts(request_id), customer_id TEXT, customer_revision INTEGER, actor_id TEXT NOT NULL REFERENCES cashier_accounts(id), actor_revision INTEGER NOT NULL CHECK(typeof(actor_revision)='integer' AND actor_revision>=1), FOREIGN KEY(customer_id,customer_revision) REFERENCES cashier_customer_versions(id,revision), CHECK((customer_id IS NULL AND customer_revision IS NULL) OR (customer_id IS NOT NULL AND typeof(customer_revision)='integer' AND customer_revision>=1)))");
        db.execSQL("CREATE INDEX checkout_customer_history ON cashier_checkout_customers(customer_id,request_id)");
        db.execSQL("CREATE TRIGGER checkout_customer_initial BEFORE INSERT ON cashier_checkout_customers WHEN EXISTS(SELECT 1 FROM cashier_checkout_customers WHERE request_id=NEW.request_id) OR NOT EXISTS(SELECT 1 FROM cashier_shift_checkouts l JOIN checkout_requests r ON r.request_id=l.request_id JOIN cashier_accounts a ON a.id=l.operator_id JOIN cashier_shifts s ON s.id=l.shift_id WHERE l.request_id=NEW.request_id AND r.state='PENDING' AND a.id=NEW.actor_id AND a.active=1 AND a.revision=NEW.actor_revision AND s.state='OPEN') OR (NEW.customer_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM cashier_customer_current c WHERE c.id=NEW.customer_id AND c.revision=NEW.customer_revision AND c.active=1)) BEGIN SELECT RAISE(ABORT,'Invalid checkout customer selection'); END");
        db.execSQL("CREATE TRIGGER checkout_customer_no_update BEFORE UPDATE ON cashier_checkout_customers BEGIN SELECT RAISE(ABORT,'Checkout customer selection immutable'); END");
        db.execSQL("CREATE TRIGGER checkout_customer_no_delete BEFORE DELETE ON cashier_checkout_customers BEGIN SELECT RAISE(ABORT,'Checkout customer selection immutable'); END");
    }
    static final class Selection {
        final String id,name;final long revision;
        Selection(String id,String name,long revision){this.id=id;this.name=name;this.revision=revision;}
    }
    static List<Selection> list(SQLiteDatabase db,String search,int offset,int limit){
        if(offset<0||limit<1||limit>51)throw new IllegalArgumentException("Invalid customer page");
        search=CustomerJournal.text(search,80,false);
        List<Selection> result=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT id,name,revision FROM cashier_customer_current WHERE active=1 AND instr(lower(name),lower(?))>0 ORDER BY name COLLATE NOCASE,id LIMIT ? OFFSET ?",new String[]{search,Integer.toString(limit),Integer.toString(offset)})){
            while(c.moveToNext())result.add(new Selection(c.getString(0),c.getString(1),c.getLong(2)));
        }
        return result;
    }
    static Selection read(SQLiteDatabase db,String requestId){
        try(Cursor c=db.rawQuery("SELECT v.id,v.name,v.revision FROM cashier_checkout_customers l JOIN cashier_customer_versions v ON v.id=l.customer_id AND v.revision=l.customer_revision WHERE l.request_id=?",new String[]{requestId})){
            return c.moveToFirst()?new Selection(c.getString(0),c.getString(1),c.getLong(2)):null;
        }
    }
    static void verify(SQLiteDatabase db,String requestId,Selection selection){
        Selection prior=read(db,requestId);
        if((prior==null)!=(selection==null)||(prior!=null&&(!prior.id.equals(selection.id)||prior.revision!=selection.revision||!prior.name.equals(selection.name))))
            throw new IllegalStateException("Checkout customer selection changed");
    }
    static void link(SQLiteDatabase db,CashierJournal.Principal actor,String requestId,Selection selection){
        ContentValues v=new ContentValues();v.put("request_id",requestId);v.put("actor_id",actor.id);v.put("actor_revision",actor.revision);
        if(selection!=null){
            CustomerJournal.Customer current=CustomerJournal.current(db,selection.id);
            if(current==null||!current.active||current.revision!=selection.revision||!current.name.equals(selection.name))
                throw new IllegalStateException("Customer changed; reload");
            v.put("customer_id",selection.id);v.put("customer_revision",selection.revision);
        }
        db.insertOrThrow("cashier_checkout_customers",null,v);
    }
}
