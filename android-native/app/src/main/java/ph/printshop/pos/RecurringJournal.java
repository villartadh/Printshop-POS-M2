package ph.printshop.pos;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/** Versioned owner expense schedules, with explicit durable drawer expense occurrences. */
final class RecurringJournal {
    private RecurringJournal(){}
    static void upgrade(SQLiteDatabase db){
        db.execSQL("CREATE TABLE cashier_recurring_versions (id TEXT NOT NULL, revision INTEGER NOT NULL CHECK(typeof(revision)='integer' AND revision>=1), name TEXT NOT NULL CHECK(length(name) BETWEEN 1 AND 80 AND name=trim(name) AND instr(name,char(0))=0 AND instr(name,char(9))=0 AND instr(name,char(10))=0 AND instr(name,char(13))=0), category TEXT NOT NULL CHECK(length(category) BETWEEN 1 AND 80 AND category=trim(category) AND instr(category,char(0))=0 AND instr(category,char(9))=0 AND instr(category,char(10))=0 AND instr(category,char(13))=0), amount INTEGER NOT NULL CHECK(typeof(amount)='integer' AND amount BETWEEN 1 AND 9999999999900000), cadence TEXT NOT NULL CHECK(cadence IN ('DAILY','WEEKLY','MONTHLY')), first_due TEXT NOT NULL CHECK(length(first_due)=10 AND first_due BETWEEN '1970-01-01' AND '9999-12-31' AND date(first_due,'+0 days') IS NOT NULL AND date(first_due,'+0 days')=first_due), active INTEGER NOT NULL CHECK(typeof(active)='integer' AND active IN (0,1)), actor_id TEXT NOT NULL REFERENCES cashier_accounts(id), actor_revision INTEGER NOT NULL CHECK(typeof(actor_revision)='integer' AND actor_revision>=1), recorded_ms INTEGER NOT NULL CHECK(typeof(recorded_ms)='integer' AND recorded_ms>=0), PRIMARY KEY(id,revision))");
        db.execSQL("CREATE TABLE cashier_recurring_occurrences (drawer_id TEXT PRIMARY KEY NOT NULL REFERENCES cashier_drawer_requests(id), schedule_id TEXT NOT NULL, schedule_revision INTEGER NOT NULL, due_index INTEGER NOT NULL CHECK(typeof(due_index)='integer' AND due_index BETWEEN 0 AND 100000), due_date TEXT NOT NULL CHECK(length(due_date)=10 AND date(due_date,'+0 days') IS NOT NULL AND date(due_date,'+0 days')=due_date), FOREIGN KEY(schedule_id,schedule_revision) REFERENCES cashier_recurring_versions(id,revision))");
        db.execSQL("CREATE INDEX recurring_occurrence_schedule ON cashier_recurring_occurrences(schedule_id,schedule_revision,due_index)");
        db.execSQL("CREATE VIEW cashier_recurring_current AS SELECT v.*,COALESCE((SELECT MAX(o.due_index)+1 FROM cashier_recurring_occurrences o JOIN cashier_drawer_requests d ON d.id=o.drawer_id WHERE o.schedule_id=v.id AND o.schedule_revision=v.revision AND d.state='APPLIED'),0) AS next_index FROM cashier_recurring_versions v WHERE v.revision=(SELECT MAX(w.revision) FROM cashier_recurring_versions w WHERE w.id=v.id)");
        db.execSQL("CREATE VIEW cashier_recurring_due AS SELECT v.*,CASE cadence WHEN 'DAILY' THEN date(first_due,'+'||next_index||' days') WHEN 'WEEKLY' THEN date(first_due,'+'||(next_index*7)||' days') ELSE date(first_due,'start of month','+'||next_index||' months','+'||(min(CAST(strftime('%d',first_due) AS INTEGER),CAST(strftime('%d',date(first_due,'start of month','+'||(next_index+1)||' months','-1 day')) AS INTEGER))-1)||' days') END AS next_due FROM cashier_recurring_current v");
        db.execSQL("CREATE TRIGGER recurring_version_initial BEFORE INSERT ON cashier_recurring_versions WHEN (NEW.revision=1 AND NEW.active<>1) OR NEW.revision<>COALESCE((SELECT MAX(revision)+1 FROM cashier_recurring_versions WHERE id=NEW.id),1) OR NOT EXISTS(SELECT 1 FROM cashier_accounts a WHERE a.id=NEW.actor_id AND a.role='OWNER' AND a.active=1 AND a.revision=NEW.actor_revision) OR EXISTS(SELECT 1 FROM cashier_recurring_occurrences o JOIN cashier_drawer_requests d ON d.id=o.drawer_id WHERE o.schedule_id=NEW.id AND (d.state='PENDING' OR (d.state='APPLIED' AND NEW.first_due<=o.due_date))) BEGIN SELECT RAISE(ABORT,'Invalid recurring expense revision'); END");
        db.execSQL("CREATE TRIGGER recurring_version_no_update BEFORE UPDATE ON cashier_recurring_versions BEGIN SELECT RAISE(ABORT,'Recurring expense revision immutable'); END");
        db.execSQL("CREATE TRIGGER recurring_version_no_delete BEFORE DELETE ON cashier_recurring_versions BEGIN SELECT RAISE(ABORT,'Recurring expense revision immutable'); END");
        db.execSQL("CREATE TRIGGER recurring_occurrence_initial BEFORE INSERT ON cashier_recurring_occurrences WHEN EXISTS(SELECT 1 FROM cashier_recurring_occurrences WHERE drawer_id=NEW.drawer_id) OR EXISTS(SELECT 1 FROM cashier_recurring_occurrences o JOIN cashier_drawer_requests d ON d.id=o.drawer_id WHERE o.schedule_id=NEW.schedule_id AND (d.state='PENDING' OR (d.state='APPLIED' AND o.due_date=NEW.due_date))) OR NOT EXISTS(SELECT 1 FROM cashier_recurring_due v JOIN cashier_drawer_requests d ON d.id=NEW.drawer_id JOIN cashier_accounts a ON a.id=d.operator_id WHERE v.id=NEW.schedule_id AND v.revision=NEW.schedule_revision AND v.active=1 AND v.next_index=NEW.due_index AND v.next_due=NEW.due_date AND NEW.due_date<=date(d.created_ms/1000,'unixepoch','+8 hours') AND d.state='PENDING' AND d.kind='EXPENSE' AND d.amount=v.amount AND d.category=v.category AND a.role='OWNER' AND a.active=1 AND a.revision=d.stage_revision) BEGIN SELECT RAISE(ABORT,'Invalid recurring expense occurrence'); END");
        db.execSQL("CREATE TRIGGER recurring_occurrence_no_update BEFORE UPDATE ON cashier_recurring_occurrences BEGIN SELECT RAISE(ABORT,'Recurring occurrence immutable'); END");
        db.execSQL("CREATE TRIGGER recurring_occurrence_no_delete BEFORE DELETE ON cashier_recurring_occurrences BEGIN SELECT RAISE(ABORT,'Recurring occurrence immutable'); END");
        db.execSQL("CREATE TRIGGER recurring_apply_guard BEFORE UPDATE ON cashier_drawer_requests WHEN NEW.state='APPLIED' AND EXISTS(SELECT 1 FROM cashier_recurring_occurrences WHERE drawer_id=OLD.id) AND NOT EXISTS(SELECT 1 FROM cashier_recurring_occurrences o JOIN cashier_recurring_due v ON v.id=o.schedule_id AND v.revision=o.schedule_revision WHERE o.drawer_id=OLD.id AND v.active=1 AND v.next_index=o.due_index AND v.next_due=o.due_date AND v.amount=OLD.amount AND v.category=OLD.category) BEGIN SELECT RAISE(ABORT,'Recurring expense occurrence changed'); END");
    }
    static final class Definition {
        final String id,name,category,firstDue,nextDue;
        final long revision,amount,nextIndex;
        final RecurringMath.Cadence cadence;
        final boolean active;
        Definition(Cursor c){
            id=c.getString(0);revision=c.getLong(1);name=c.getString(2);category=c.getString(3);amount=c.getLong(4);
            cadence=RecurringMath.Cadence.valueOf(c.getString(5));firstDue=c.getString(6);active=c.getInt(7)==1;
            nextIndex=c.getLong(8);nextDue=c.getString(9);
        }
    }
    private static final String DEFINITION="id,revision,name,category,amount,cadence,first_due,active,next_index,next_due";
    static Definition current(SQLiteDatabase db,String id){
        try(Cursor c=db.rawQuery("SELECT "+DEFINITION+" FROM cashier_recurring_due WHERE id=?",new String[]{id})){
            return c.moveToFirst()?new Definition(c):null;
        }
    }
    static List<Definition> list(SQLiteDatabase db,CashierJournal.Principal actor,int offset,int limit){
        CashierSecurity.requireOwner(actor.role);
        if(offset<0||limit<1||limit>51)throw new IllegalArgumentException("Invalid recurring page");
        List<Definition> rows=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT "+DEFINITION+" FROM cashier_recurring_due ORDER BY active DESC,next_due,id LIMIT ? OFFSET ?",new String[]{Integer.toString(limit),Integer.toString(offset)})){
            while(c.moveToNext())rows.add(new Definition(c));
        }
        return rows;
    }
    static Definition save(SQLiteDatabase db,CashierJournal.Principal actor,String id,long expectedRevision,String name,String category,long amount,RecurringMath.Cadence cadence,String firstDue,boolean active,long now){
        CashierSecurity.requireOwner(actor.role);StockMath.validateRequestId(id);
        name=ShiftMath.category(ShiftMath.DrawerKind.EXPENSE,name);category=ShiftMath.category(ShiftMath.DrawerKind.EXPENSE,category);
        ShiftMath.money(amount);if(amount==0||cadence==null)throw new IllegalArgumentException("Invalid recurring expense");
        RecurringMath.date(firstDue);
        Definition old=current(db,id);long revision=old==null?0:old.revision;
        // Exact retry of a committed configuration is not a new revision.
        if(old!=null&&revision==expectedRevision+1&&old.name.equals(name)&&old.category.equals(category)&&old.amount==amount&&old.cadence==cadence&&old.firstDue.equals(firstDue)&&old.active==active)return old;
        if(expectedRevision!=revision||(!active&&old==null))throw new IllegalStateException("Recurring expense changed; reload");
        ContentValues row=new ContentValues();row.put("id",id);row.put("revision",Math.addExact(revision,1));row.put("name",name);row.put("category",category);row.put("amount",amount);row.put("cadence",cadence.name());row.put("first_due",firstDue);row.put("active",active?1:0);row.put("actor_id",actor.id);row.put("actor_revision",actor.revision);row.put("recorded_ms",now);
        db.insertOrThrow("cashier_recurring_versions",null,row);return current(db,id);
    }
    static final class Occurrence {
        final String scheduleId,dueDate,name;
        final long revision,index;
        final DrawerJournal.Request drawer;
        Occurrence(String scheduleId,long revision,long index,String dueDate,String name,DrawerJournal.Request drawer){
            this.scheduleId=scheduleId;this.revision=revision;this.index=index;this.dueDate=dueDate;this.name=name;this.drawer=drawer;
        }
    }
    static boolean linked(SQLiteDatabase db,String id){
        try(Cursor c=db.rawQuery("SELECT 1 FROM cashier_recurring_occurrences WHERE drawer_id=?",new String[]{id})){return c.moveToFirst();}
    }
    static Occurrence read(SQLiteDatabase db,CashierJournal.Principal actor,String id){
        CashierSecurity.requireOwner(actor.role);
        try(Cursor c=db.rawQuery("SELECT o.schedule_id,o.schedule_revision,o.due_index,o.due_date,v.name FROM cashier_recurring_occurrences o JOIN cashier_recurring_versions v ON v.id=o.schedule_id AND v.revision=o.schedule_revision WHERE o.drawer_id=?",new String[]{id})){
            if(!c.moveToFirst())throw new IllegalStateException("Recurring expense occurrence unavailable");
            return new Occurrence(c.getString(0),c.getLong(1),c.getLong(2),c.getString(3),c.getString(4),DrawerJournal.requireRequest(db,actor,id));
        }
    }
    static Occurrence stage(SQLiteDatabase db,CashierJournal.Principal actor,String shift,String id,String schedule,long revision,long index,String date,long amount,String category,String reason,long before,long now){
        CashierSecurity.requireOwner(actor.role);StockMath.validateRequestId(id);StockMath.validateRequestId(schedule);RecurringMath.date(date);
        if(linked(db,id)){
            Occurrence prior=read(db,actor,id);
            prior.drawer.verify(shift,ShiftMath.DrawerKind.EXPENSE,amount,ShiftMath.category(ShiftMath.DrawerKind.EXPENSE,category),StockMath.reason(reason),before);
            if(!prior.scheduleId.equals(schedule)||prior.revision!=revision||prior.index!=index||!prior.dueDate.equals(date))throw new IllegalStateException("Recurring occurrence identity changed");
            return prior;
        }
        if(DrawerJournal.read(db,id)!=null)throw new IllegalStateException("Drawer identity already used");
        Definition d=current(db,schedule);
        if(d==null||!d.active||d.revision!=revision||d.nextIndex!=index||!date.equals(d.nextDue)||!date.equals(RecurringMath.due(d.firstDue,d.cadence,index))||date.compareTo(RecurringMath.today(now))>0||amount!=d.amount||!d.category.equals(category))
            throw new IllegalStateException("Recurring expense changed or not yet due");
        DrawerJournal.stage(db,actor,shift,id,ShiftMath.DrawerKind.EXPENSE,amount,category,reason,before,now);
        ContentValues row=new ContentValues();row.put("drawer_id",id);row.put("schedule_id",schedule);row.put("schedule_revision",revision);row.put("due_index",index);row.put("due_date",date);
        db.insertOrThrow("cashier_recurring_occurrences",null,row);return read(db,actor,id);
    }
    static List<Occurrence> history(SQLiteDatabase db,CashierJournal.Principal actor,int offset,int limit){
        CashierSecurity.requireOwner(actor.role);
        if(offset<0||limit<1||limit>51)throw new IllegalArgumentException("Invalid recurring page");
        List<String> ids=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT o.drawer_id FROM cashier_recurring_occurrences o JOIN cashier_drawer_requests d ON d.id=o.drawer_id WHERE d.operator_id=? ORDER BY d.created_ms DESC,d.id DESC LIMIT ? OFFSET ?",new String[]{actor.id,Integer.toString(limit),Integer.toString(offset)})){
            while(c.moveToNext())ids.add(c.getString(0));
        }
        List<Occurrence> rows=new ArrayList<>();for(String id:ids)rows.add(read(db,actor,id));return rows;
    }
    static Occurrence resolve(SQLiteDatabase db,CashierJournal.Principal actor,String id,boolean apply,String reason,long now){
        read(db,actor,id);DrawerJournal.resolveRecurring(db,actor,id,apply,reason,now);return read(db,actor,id);
    }
}
