package ph.printshop.pos;

import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import android.content.ContentValues;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * v16 training schema and authenticated CatalogStore collection persistence.
 * Future credit checkout must atomically persist its authoritative sale, inventory and drawer effects
 * alongside the source. Collection cash effects are provided by CreditCashJournal.
 */
final class ReceivableJournal {
    private ReceivableJournal(){}
    static void createCandidate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE credit_receivable_sources (id TEXT PRIMARY KEY NOT NULL, customer_id TEXT NOT NULL, customer_revision INTEGER NOT NULL CHECK(typeof(customer_revision)='integer' AND customer_revision>=1), shift_id TEXT NOT NULL REFERENCES cashier_shifts(id), operator_id TEXT NOT NULL REFERENCES cashier_accounts(id), actor_revision INTEGER NOT NULL CHECK(typeof(actor_revision)='integer' AND actor_revision>=1), plan_fingerprint TEXT NOT NULL CHECK(length(plan_fingerprint)=64 AND plan_fingerprint NOT GLOB '*[^0-9a-f]*'), total INTEGER NOT NULL CHECK(typeof(total)='integer' AND total BETWEEN 1 AND 9999999999900000), paid INTEGER NOT NULL CHECK(typeof(paid)='integer' AND paid>=0 AND paid<total), opened_ms INTEGER NOT NULL CHECK(typeof(opened_ms)='integer' AND opened_ms>=0), FOREIGN KEY(customer_id,customer_revision) REFERENCES cashier_customer_versions(id,revision))");
        db.execSQL("CREATE INDEX credit_source_customer ON credit_receivable_sources(customer_id,opened_ms,id)");
        db.execSQL("CREATE TRIGGER credit_source_insert BEFORE INSERT ON credit_receivable_sources WHEN EXISTS(SELECT 1 FROM credit_receivable_sources WHERE id=NEW.id) OR EXISTS(SELECT 1 FROM checkout_requests WHERE request_id=NEW.id) OR EXISTS(SELECT 1 FROM credit_collection_requests c WHERE c.customer_id=NEW.customer_id AND c.state='PENDING') OR NOT EXISTS(SELECT 1 FROM cashier_customer_current c WHERE c.id=NEW.customer_id AND c.revision=NEW.customer_revision AND c.active=1) OR NOT EXISTS(SELECT 1 FROM cashier_shifts s JOIN cashier_accounts a ON a.id=s.operator_id WHERE s.id=NEW.shift_id AND s.state='OPEN' AND a.id=NEW.operator_id AND a.active=1 AND a.revision=NEW.actor_revision AND NEW.opened_ms>=s.opened_ms) BEGIN SELECT RAISE(ABORT,'Invalid credit sale source'); END");
        db.execSQL("CREATE TRIGGER credit_source_no_update BEFORE UPDATE ON credit_receivable_sources BEGIN SELECT RAISE(ABORT,'Credit source immutable'); END");
        db.execSQL("CREATE TRIGGER credit_source_no_delete BEFORE DELETE ON credit_receivable_sources BEGIN SELECT RAISE(ABORT,'Credit source immutable'); END");
        db.execSQL("CREATE TABLE credit_collection_requests (id TEXT PRIMARY KEY NOT NULL, customer_id TEXT NOT NULL, customer_revision INTEGER NOT NULL CHECK(typeof(customer_revision)='integer' AND customer_revision>=1), shift_id TEXT NOT NULL REFERENCES cashier_shifts(id), operator_id TEXT NOT NULL REFERENCES cashier_accounts(id), actor_revision INTEGER NOT NULL CHECK(typeof(actor_revision)='integer' AND actor_revision>=1), amount INTEGER NOT NULL CHECK(typeof(amount)='integer' AND amount BETWEEN 1 AND 9999999999900000), before_cash INTEGER NOT NULL CHECK(typeof(before_cash)='integer' AND before_cash BETWEEN 0 AND 9999999999900000), after_cash INTEGER NOT NULL CHECK(typeof(after_cash)='integer' AND after_cash=before_cash+amount AND after_cash<=9999999999900000), created_ms INTEGER NOT NULL CHECK(typeof(created_ms)='integer' AND created_ms>=0), state TEXT NOT NULL CHECK(state IN ('PENDING','APPLIED','CANCELLED')), resolved_ms INTEGER, resolved_revision INTEGER, cancel_reason TEXT, FOREIGN KEY(customer_id,customer_revision) REFERENCES cashier_customer_versions(id,revision), CHECK((state='PENDING' AND resolved_ms IS NULL AND resolved_revision IS NULL AND cancel_reason IS NULL) OR (state IN ('APPLIED','CANCELLED') AND typeof(resolved_ms)='integer' AND resolved_ms>=created_ms AND typeof(resolved_revision)='integer' AND resolved_revision>=1 AND ((state='APPLIED' AND cancel_reason IS NULL) OR (state='CANCELLED' AND cancel_reason IS NOT NULL AND length(trim(cancel_reason)) BETWEEN 1 AND 160)))))");
        db.execSQL("CREATE UNIQUE INDEX credit_pending_customer ON credit_collection_requests(customer_id) WHERE state='PENDING'");
        db.execSQL("CREATE UNIQUE INDEX credit_pending_shift ON credit_collection_requests(shift_id) WHERE state='PENDING'");
        db.execSQL("CREATE TABLE credit_collection_allocations (collection_id TEXT NOT NULL REFERENCES credit_collection_requests(id), debt_id TEXT NOT NULL REFERENCES credit_receivable_sources(id), before_owed INTEGER NOT NULL CHECK(typeof(before_owed)='integer' AND before_owed BETWEEN 1 AND 9999999999900000), amount INTEGER NOT NULL CHECK(typeof(amount)='integer' AND amount BETWEEN 1 AND before_owed), after_owed INTEGER NOT NULL CHECK(typeof(after_owed)='integer' AND after_owed=before_owed-amount), PRIMARY KEY(collection_id,debt_id))");
        db.execSQL("CREATE INDEX credit_alloc_debt ON credit_collection_allocations(debt_id,collection_id)");
        db.execSQL("CREATE VIEW credit_receivable_open AS SELECT d.*,d.total-d.paid-COALESCE((SELECT SUM(a.amount) FROM credit_collection_allocations a JOIN credit_collection_requests c ON c.id=a.collection_id WHERE a.debt_id=d.id AND c.state='APPLIED'),0) AS remaining FROM credit_receivable_sources d");
        db.execSQL("CREATE TRIGGER credit_collection_insert BEFORE INSERT ON credit_collection_requests WHEN NEW.state<>'PENDING' OR EXISTS(SELECT 1 FROM credit_collection_requests WHERE id=NEW.id OR (state='PENDING' AND (customer_id=NEW.customer_id OR shift_id=NEW.shift_id))) OR NOT EXISTS(SELECT 1 FROM cashier_customer_current c WHERE c.id=NEW.customer_id AND c.revision=NEW.customer_revision) OR NOT EXISTS(SELECT 1 FROM cashier_shifts s JOIN cashier_accounts a ON a.id=s.operator_id JOIN cashier_drawer_current b ON b.shift_id=s.id WHERE s.id=NEW.shift_id AND s.state='OPEN' AND a.id=NEW.operator_id AND a.active=1 AND a.revision=NEW.actor_revision AND NEW.created_ms>=s.opened_ms AND b.expected_cash=NEW.before_cash) OR EXISTS(SELECT 1 FROM cashier_drawer_requests r WHERE r.shift_id=NEW.shift_id AND r.state='PENDING') OR EXISTS(SELECT 1 FROM cashier_shift_checkouts l JOIN checkout_requests r ON r.request_id=l.request_id WHERE l.shift_id=NEW.shift_id AND r.state='PENDING') BEGIN SELECT RAISE(ABORT,'Invalid collection review'); END");
        db.execSQL("CREATE TRIGGER credit_allocation_insert BEFORE INSERT ON credit_collection_allocations WHEN EXISTS(SELECT 1 FROM credit_collection_allocations WHERE collection_id=NEW.collection_id AND debt_id=NEW.debt_id) OR NOT EXISTS(SELECT 1 FROM credit_collection_requests c JOIN credit_receivable_open d ON d.id=NEW.debt_id WHERE c.id=NEW.collection_id AND c.state='PENDING' AND c.customer_id=d.customer_id AND d.opened_ms<=c.created_ms AND d.remaining=NEW.before_owed AND NEW.amount<=c.amount-COALESCE((SELECT SUM(a.amount) FROM credit_collection_allocations a WHERE a.collection_id=c.id),0) AND NEW.amount=min(d.remaining,c.amount-COALESCE((SELECT SUM(a.amount) FROM credit_collection_allocations a WHERE a.collection_id=c.id),0)) AND NOT EXISTS(SELECT 1 FROM credit_receivable_open older WHERE older.customer_id=c.customer_id AND older.remaining>0 AND (older.opened_ms<d.opened_ms OR (older.opened_ms=d.opened_ms AND older.id<d.id)) AND NOT EXISTS(SELECT 1 FROM credit_collection_allocations a WHERE a.collection_id=c.id AND a.debt_id=older.id AND a.after_owed=0))) BEGIN SELECT RAISE(ABORT,'Invalid FIFO collection allocation'); END");
        db.execSQL("CREATE TRIGGER credit_allocation_no_update BEFORE UPDATE ON credit_collection_allocations BEGIN SELECT RAISE(ABORT,'Collection allocation immutable'); END");
        db.execSQL("CREATE TRIGGER credit_allocation_no_delete BEFORE DELETE ON credit_collection_allocations BEGIN SELECT RAISE(ABORT,'Collection allocation immutable'); END");
        db.execSQL("CREATE TRIGGER credit_collection_no_delete BEFORE DELETE ON credit_collection_requests BEGIN SELECT RAISE(ABORT,'Collection request immutable'); END");
        db.execSQL("CREATE TRIGGER credit_collection_resolution BEFORE UPDATE ON credit_collection_requests WHEN OLD.state<>'PENDING' OR NEW.state NOT IN ('APPLIED','CANCELLED') OR NEW.id IS NOT OLD.id OR NEW.customer_id IS NOT OLD.customer_id OR NEW.customer_revision IS NOT OLD.customer_revision OR NEW.shift_id IS NOT OLD.shift_id OR NEW.operator_id IS NOT OLD.operator_id OR NEW.actor_revision IS NOT OLD.actor_revision OR NEW.amount IS NOT OLD.amount OR NEW.before_cash IS NOT OLD.before_cash OR NEW.after_cash IS NOT OLD.after_cash OR NEW.created_ms IS NOT OLD.created_ms OR NOT EXISTS(SELECT 1 FROM cashier_shifts s JOIN cashier_accounts a ON a.id=s.operator_id JOIN cashier_drawer_current b ON b.shift_id=s.id WHERE s.id=OLD.shift_id AND s.state='OPEN' AND a.id=OLD.operator_id AND a.active=1 AND a.revision=NEW.resolved_revision AND (NEW.state='CANCELLED' OR b.expected_cash=OLD.before_cash)) OR (NEW.state='APPLIED' AND (EXISTS(SELECT 1 FROM cashier_drawer_requests r WHERE r.shift_id=OLD.shift_id AND r.state='PENDING') OR EXISTS(SELECT 1 FROM cashier_shift_checkouts l JOIN checkout_requests r ON r.request_id=l.request_id WHERE l.shift_id=OLD.shift_id AND r.state='PENDING') OR COALESCE((SELECT SUM(amount) FROM credit_collection_allocations WHERE collection_id=OLD.id),0)<>OLD.amount OR EXISTS(SELECT 1 FROM credit_collection_allocations a JOIN credit_receivable_open d ON d.id=a.debt_id WHERE a.collection_id=OLD.id AND (d.customer_id<>OLD.customer_id OR d.remaining<>a.before_owed)))) BEGIN SELECT RAISE(ABORT,'Invalid collection resolution'); END");
    }
    /** Review identity binds the exact persisted payment and ordered allocation payload. */
    static final class Review {
        final String id,customer,shift,operator,fingerprint;
        final long customerRevision;
        final ReceivableMath.Collection collection;
        Review(String id,String customer,long revision,String shift,String operator,ReceivableMath.Collection collection){
            StockMath.validateRequestId(id);
            this.id=id;this.customer=customer;customerRevision=revision;this.shift=shift;this.operator=operator;this.collection=collection;
            List<AllocationRow> rows=new ArrayList<>();
            for(ReceivableMath.Allocation a:collection.allocations)rows.add(new AllocationRow(a.debtId,a.before,a.amount,a.after));
            fingerprint=identity(id,customer,revision,shift,operator,collection.amount,collection.drawerBefore,collection.drawerAfter,rows);
        }
    }
    static final class Debtor {
        final String id,name;final long revision;final boolean active;final BigInteger outstanding;
        Debtor(String id,String name,long revision,boolean active,BigInteger outstanding){
            this.id=id;this.name=name;this.revision=revision;this.active=active;this.outstanding=outstanding;
        }
    }
    static final String DEBTOR_PAGE="SELECT c.id,c.name,c.revision,c.active FROM cashier_customer_current c WHERE instr(lower(c.name),lower(?))>0 AND EXISTS(SELECT 1 FROM credit_receivable_open d WHERE d.customer_id=c.id AND d.remaining>0) ORDER BY c.name COLLATE NOCASE,c.id LIMIT ? OFFSET ?";
    static List<Debtor> debtors(SQLiteDatabase db,String search,int offset,int limit){
        if(offset<0||limit<1||limit>51)throw new IllegalArgumentException("Invalid debtor page");
        search=CustomerJournal.text(search,80,false);List<Debtor> rows=new ArrayList<>();
        try(Cursor c=db.rawQuery(DEBTOR_PAGE,new String[]{search,Integer.toString(limit),Integer.toString(offset)})){
            while(c.moveToNext())rows.add(new Debtor(c.getString(0),c.getString(1),c.getLong(2),c.getInt(3)==1,outstanding(db,c.getString(0))));
        }
        return rows;
    }
    static final class AllocationRow {
        final String debt;final long before,amount,after;
        AllocationRow(String debt,long before,long amount,long after){this.debt=debt;this.before=before;this.amount=amount;this.after=after;}
    }
    static final class Record {
        final String id,customer,shift,operator,state,reason,fingerprint,customerName;
        final long recordedMs;
        final long customerRevision,amount,before,after;
        final List<AllocationRow> allocations;
        Record(Cursor c,List<AllocationRow> rows){
            id=c.getString(0);customer=c.getString(1);customerRevision=c.getLong(2);shift=c.getString(3);operator=c.getString(4);
            amount=c.getLong(5);before=c.getLong(6);after=c.getLong(7);state=c.getString(8);reason=c.getString(9);customerName=c.getString(10);recordedMs=c.getLong(11);
            allocations=Collections.unmodifiableList(new ArrayList<>(rows));
            fingerprint=identity(id,customer,customerRevision,shift,operator,amount,before,after,allocations);
        }
        void requireIdentity(String value){if(!fingerprint.equals(value))throw new IllegalStateException("Collection identity changed");}
        void requireCurrent(ReceivableMath.Collection review){
            if(!customer.equals(review.customer)||amount!=review.amount||before!=review.drawerBefore||after!=review.drawerAfter||allocations.size()!=review.allocations.size())
                throw new IllegalStateException("Collection changed; review again");
            for(int i=0;i<allocations.size();i++){
                AllocationRow a=allocations.get(i);ReceivableMath.Allocation b=review.allocations.get(i);
                if(!a.debt.equals(b.debtId)||a.before!=b.before||a.amount!=b.amount||a.after!=b.after)throw new IllegalStateException("Collection allocation changed");
            }
        }
    }
    private static String identity(String id,String customer,long revision,String shift,String operator,long amount,long before,long after,List<AllocationRow> rows){
        StringBuilder payload=new StringBuilder("COLLECTION-V1|").append(id).append('|').append(customer).append('|').append(revision).append('|').append(shift).append('|').append(operator).append('|').append(amount).append('|').append(before).append('|').append(after);
        for(AllocationRow a:rows)payload.append('|').append(a.debt).append(':').append(a.before).append(':').append(a.amount).append(':').append(a.after);
        try{
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(payload.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder();for(byte b:bytes)hex.append(Character.forDigit((b&255)>>>4,16)).append(Character.forDigit(b&15,16));return hex.toString();
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException("Collection fingerprint unavailable",e);}
    }
    static List<ReceivableMath.Debt> debts(SQLiteDatabase db,String customer){
        StockMath.validateRequestId(customer);List<ReceivableMath.Debt> rows=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT id,opened_ms,total-paid,remaining FROM credit_receivable_open WHERE customer_id=? AND remaining>0 ORDER BY opened_ms,id",new String[]{customer})){
            while(c.moveToNext())rows.add(new ReceivableMath.Debt(c.getString(0),customer,c.getLong(1),c.getLong(2),Math.subtractExact(c.getLong(2),c.getLong(3))));
        }
        return rows;
    }
    static BigInteger outstanding(SQLiteDatabase db,String customer){
        BigInteger value=BigInteger.ZERO;for(ReceivableMath.Debt debt:debts(db,customer))value=value.add(BigInteger.valueOf(debt.remaining()));return value;
    }
    static final String HISTORY_PAGE="SELECT id FROM credit_collection_requests WHERE operator_id=? AND state<>'PENDING' ORDER BY resolved_ms DESC,id LIMIT ? OFFSET ?";
    static Record read(SQLiteDatabase db,String id){
        StockMath.validateRequestId(id);List<AllocationRow> rows=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT a.debt_id,a.before_owed,a.amount,a.after_owed FROM credit_collection_allocations a JOIN credit_receivable_sources d ON d.id=a.debt_id WHERE a.collection_id=? ORDER BY d.opened_ms,d.id",new String[]{id})){
            while(c.moveToNext())rows.add(new AllocationRow(c.getString(0),c.getLong(1),c.getLong(2),c.getLong(3)));
        }
        try(Cursor c=db.rawQuery("SELECT r.id,r.customer_id,r.customer_revision,r.shift_id,r.operator_id,r.amount,r.before_cash,r.after_cash,r.state,r.cancel_reason,v.name,COALESCE(r.resolved_ms,r.created_ms) FROM credit_collection_requests r JOIN cashier_customer_versions v ON v.id=r.customer_id AND v.revision=r.customer_revision WHERE r.id=?",new String[]{id})){
            return c.moveToFirst()?new Record(c,rows):null;
        }
    }
    static void stage(SQLiteDatabase db,CashierJournal.Principal actor,Review reviewed,long now){
        ContentValues v=new ContentValues();v.put("id",reviewed.id);v.put("customer_id",reviewed.customer);v.put("customer_revision",reviewed.customerRevision);
        v.put("shift_id",reviewed.shift);v.put("operator_id",actor.id);v.put("actor_revision",actor.revision);v.put("amount",reviewed.collection.amount);
        v.put("before_cash",reviewed.collection.drawerBefore);v.put("after_cash",reviewed.collection.drawerAfter);v.put("created_ms",now);v.put("state","PENDING");
        db.insertOrThrow("credit_collection_requests",null,v);
        for(ReceivableMath.Allocation a:reviewed.collection.allocations){
            ContentValues row=new ContentValues();row.put("collection_id",reviewed.id);row.put("debt_id",a.debtId);row.put("before_owed",a.before);row.put("amount",a.amount);row.put("after_owed",a.after);
            db.insertOrThrow("credit_collection_allocations",null,row);
        }
    }
    static Record resolve(SQLiteDatabase db,CashierJournal.Principal actor,String id,boolean applied,String reason,long now){
        ContentValues v=new ContentValues();v.put("state",applied?"APPLIED":"CANCELLED");v.put("resolved_ms",now);v.put("resolved_revision",actor.revision);
        if(reason==null)v.putNull("cancel_reason");else v.put("cancel_reason",reason);
        if(db.update("credit_collection_requests",v,"id=? AND state='PENDING'",new String[]{id})!=1)throw new IllegalStateException("Collection resolution unavailable");
        return read(db,id);
    }

}
