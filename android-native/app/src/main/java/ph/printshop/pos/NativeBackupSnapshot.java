package ph.printshop.pos;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.TreeMap;
import java.util.Map;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Copies trusted app-owned schema and committed rows into an isolated SQLite file.
 * The caller holds the source writer transaction, so pending WAL writes cannot race the snapshot.
 * No raw live-file copy. Recreates triggers only after data is copied; sessions are not exported.
 */
final class NativeBackupSnapshot {
    private NativeBackupSnapshot(){}
    private static final class Schema {
        final String type,name,sql;
        Schema(Cursor c){type=c.getString(0);name=c.getString(1);sql=c.getString(2);}
    }
    static synchronized byte[] create(SQLiteDatabase source,File privateDirectory,char[] password){
        if(!source.inTransaction())throw new IllegalStateException("Backup requires consistent transaction");
        CashierSecurity.validatePassword(password);
        File candidate=null;byte[] plaintext=null;BackupStaging staging=null;
        try{
            staging=BackupStaging.acquire(privateDirectory);
            candidate=File.createTempFile("native-backup-",".db",staging.directory);
            List<Schema> schema=new ArrayList<>();
            try(Cursor c=source.rawQuery("SELECT type,name,sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name<>'android_metadata' ORDER BY CASE type WHEN 'table' THEN 0 WHEN 'index' THEN 1 WHEN 'view' THEN 2 ELSE 3 END,name",null)){
                while(c.moveToNext())schema.add(new Schema(c));
            }
            int version=source.getVersion();
            try(SQLiteDatabase target=SQLiteDatabase.openOrCreateDatabase(candidate,null)){
                target.setForeignKeyConstraintsEnabled(false);
                target.beginTransaction();
                try{
                    for(Schema item:schema)if("table".equals(item.type)){
                        target.execSQL(item.sql);
                        if(!"cashier_sessions".equals(item.name))copyTable(source,target,item.name);
                        if(candidate.length()>BackupEnvelope.MAX_BYTES)throw new IllegalStateException("Backup exceeds supported size");
                    }
                    // Preserve AUTOINCREMENT high-water marks, not merely the largest present row.
                    if(hasSequence(source)) {
                        target.execSQL("DELETE FROM sqlite_sequence");
                        copyTable(source,target,"sqlite_sequence");
                    }
                    for(Schema item:schema)if(!"table".equals(item.type))target.execSQL(item.sql);
                    target.setVersion(version);
                    validate(target);
                    target.setTransactionSuccessful();
                }finally{target.endTransaction();}
            }
            long size=candidate.length();
            if(size<1||size>BackupEnvelope.MAX_BYTES)throw new IllegalStateException("Backup exceeds supported size");
            plaintext=new byte[(int)size];
            try(FileInputStream in=new FileInputStream(candidate)){
                int pos=0;while(pos<plaintext.length){int read=in.read(plaintext,pos,plaintext.length-pos);if(read<0)throw new IOException("Snapshot truncated");pos+=read;}
                if(in.read()!=-1)throw new IOException("Snapshot changed");
            }
            return BackupEnvelope.seal(plaintext,version,password);
        }catch(IOException ex){throw new IllegalStateException("Unable to stage backup",ex);}
        finally{
            if(plaintext!=null)Arrays.fill(plaintext,(byte)0);
            try{if(candidate!=null)SQLiteDatabase.deleteDatabase(candidate);}
            finally{release(staging);}
        }
    }
    static final class Review {
        final int schema;
        final long sales, pendingSales, openShifts, creditSources, pendingOperations,currentOpenShifts,currentPendingOperations;
        Review(int schema,long sales,long pendingSales,long openShifts,long creditSources,long pendingOperations,long currentOpenShifts,long currentPendingOperations){
            this.schema=schema;this.sales=sales;this.pendingSales=pendingSales;this.openShifts=openShifts;this.creditSources=creditSources;this.pendingOperations=pendingOperations;this.currentOpenShifts=currentOpenShifts;this.currentPendingOperations=currentPendingOperations;
        }
    }
    private static Map<String,String> schema(SQLiteDatabase db){
        Map<String,String> result=new TreeMap<>();
        try(Cursor c=db.rawQuery("SELECT type,name,sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name<>'android_metadata'",null)){
            while(c.moveToNext())result.put(c.getString(0)+":"+c.getString(1),c.getString(2));
        }return result;
    }
    /** Read-only candidate inspection; never opens candidate in SQLiteOpenHelper or runs migrations. */
    static synchronized Review review(SQLiteDatabase trusted,File privateDirectory,byte[] archive,char[] password){
        if(!trusted.inTransaction())throw new IllegalStateException("Backup review requires authorized transaction");
        File file=null;BackupStaging staging=null;
        try(BackupEnvelope.Decoded decoded=BackupEnvelope.open(archive,trusted.getVersion(),password)){
            if(decoded.schema!=trusted.getVersion())throw new IllegalArgumentException("Older native schema requires isolated migration");
            staging=BackupStaging.acquire(privateDirectory);
            file=File.createTempFile("native-backup-review-",".db",staging.directory);
            try(FileOutputStream out=new FileOutputStream(file)){out.write(decoded.snapshot);out.getFD().sync();}
            try(SQLiteDatabase candidate=SQLiteDatabase.openDatabase(file.getPath(),null,SQLiteDatabase.OPEN_READONLY)){
                if(candidate.getVersion()!=decoded.schema||!schema(trusted).equals(schema(candidate)))throw new IllegalArgumentException("Backup schema does not match this app");
                validate(candidate);
                return new Review(decoded.schema,count(candidate,"SELECT COUNT(*) FROM checkout_sales"),
                    count(candidate,"SELECT COUNT(*) FROM checkout_requests WHERE state='PENDING'")+count(candidate,"SELECT COUNT(*) FROM credit_checkout_requests WHERE state='PENDING'"),
                    count(candidate,"SELECT COUNT(*) FROM cashier_shifts WHERE state='OPEN'"),count(candidate,"SELECT COUNT(*) FROM credit_receivable_sources"),pendingOperations(candidate),count(trusted,"SELECT COUNT(*) FROM cashier_shifts WHERE state='OPEN'"),pendingOperations(trusted));
            }
        }catch(IOException|android.database.sqlite.SQLiteException ex){throw new IllegalArgumentException("Backup candidate is not a valid native database",ex);}
        finally{try{if(file!=null)SQLiteDatabase.deleteDatabase(file);}finally{release(staging);}}
    }
    private static void release(BackupStaging staging){
        if(staging!=null)try{staging.close();}catch(IOException ex){throw new IllegalStateException("Unable to release backup staging",ex);}
    }
    static synchronized void cleanup(File privateDirectory){
        try(BackupStaging ignored=BackupStaging.acquire(privateDirectory)){}
        catch(IOException ex){throw new IllegalStateException("Unable to clean backup staging",ex);}
    }
    private static long pendingOperations(SQLiteDatabase db){
        long total=0;
        for(String table:new String[]{"stock_requests","checkout_requests","credit_checkout_requests","credit_collection_requests","cashier_drawer_requests"})
            total+=count(db,"SELECT COUNT(*) FROM "+table+" WHERE state='PENDING'");
        return total;
    }
    private static long count(SQLiteDatabase db,String sql){
        try(Cursor c=db.rawQuery(sql,null)){if(!c.moveToFirst())throw new IllegalStateException("Backup query failed");return c.getLong(0);}
    }
    private static void requireNoRows(SQLiteDatabase db,String sql){
        try(Cursor c=db.rawQuery(sql,null)){if(c.moveToFirst())throw new IllegalStateException("Backup ledger consistency check failed");}
    }
    private static boolean hasSequence(SQLiteDatabase db){
        try(Cursor c=db.rawQuery("SELECT 1 FROM sqlite_master WHERE name='sqlite_sequence'",null)){return c.moveToFirst();}
    }
    private static void copyTable(SQLiteDatabase source,SQLiteDatabase target,String table){
        String quoted="\""+table.replace("\"","\"\"")+"\"";
        try(Cursor c=source.rawQuery("SELECT * FROM "+quoted,null)){
            int columns=c.getColumnCount();StringBuilder sql=new StringBuilder("INSERT INTO ").append(quoted).append(" VALUES(");
            for(int i=0;i<columns;i++){if(i!=0)sql.append(',');sql.append('?');}sql.append(')');
            while(c.moveToNext()){
                Object[] row=new Object[columns];
                for(int i=0;i<columns;i++)switch(c.getType(i)){
                    case Cursor.FIELD_TYPE_NULL:row[i]=null;break;
                    case Cursor.FIELD_TYPE_INTEGER:row[i]=c.getLong(i);break;
                    case Cursor.FIELD_TYPE_FLOAT:row[i]=c.getDouble(i);break;
                    case Cursor.FIELD_TYPE_STRING:row[i]=c.getString(i);break;
                    case Cursor.FIELD_TYPE_BLOB:row[i]=c.getBlob(i);break;
                    default:throw new IllegalStateException("Unsupported snapshot field");
                }
                target.execSQL(sql.toString(),row);
            }
        }
    }
    static void validate(SQLiteDatabase db){
        try(Cursor c=db.rawQuery("PRAGMA integrity_check",null)){
            if(!c.moveToFirst()||!"ok".equals(c.getString(0))||c.moveToNext())throw new IllegalStateException("Backup integrity check failed");
        }
        try(Cursor c=db.rawQuery("PRAGMA foreign_key_check",null)){
            if(c.moveToFirst())throw new IllegalStateException("Backup foreign key check failed");
        }
        requireNoRows(db,"SELECT 1 FROM checkout_requests r LEFT JOIN checkout_sales s ON s.request_id=r.request_id WHERE (r.state='APPLIED')<>(s.request_id IS NOT NULL) LIMIT 1");
        requireNoRows(db,"SELECT 1 FROM credit_checkout_requests r LEFT JOIN credit_receivable_sources s ON s.id=r.id WHERE (r.state='APPLIED')<>(s.id IS NOT NULL) LIMIT 1");
        requireNoRows(db,"SELECT 1 FROM credit_receivable_open WHERE remaining<0 OR remaining>total-paid LIMIT 1");
        requireNoRows(db,"SELECT 1 FROM cashier_refund_totals WHERE compensated<0 OR compensated>original_paid LIMIT 1");
        requireNoRows(db,"SELECT 1 FROM cashier_cash_effects_v2 e JOIN cashier_drawer_anchors a ON a.shift_id=e.shift_id WHERE e.before_cash<>COALESCE((SELECT p.after_cash FROM cashier_cash_effects_v2 p WHERE p.shift_id=e.shift_id AND p.event_no<e.event_no ORDER BY p.event_no DESC LIMIT 1),a.baseline_cash) LIMIT 1");
        requireNoRows(db,"SELECT 1 FROM stock_movements m JOIN stock_movements p ON p.material_id=m.material_id AND p.stock_revision=m.stock_revision-1 WHERE m.before_quantity<>p.after_quantity LIMIT 1");
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM cashier_sessions",null)){
            if(!c.moveToFirst()||c.getLong(0)!=0)throw new IllegalStateException("Backup must not contain session tokens");
        }
    }
}
