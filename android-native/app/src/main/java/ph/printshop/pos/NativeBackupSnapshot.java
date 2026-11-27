package ph.printshop.pos;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.io.FileInputStream;
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
    static byte[] create(SQLiteDatabase source,File privateDirectory,char[] password){
        if(!source.inTransaction())throw new IllegalStateException("Backup requires consistent transaction");
        CashierSecurity.validatePassword(password);
        File candidate=null;byte[] plaintext=null;
        try{
            candidate=File.createTempFile("native-backup-",".db",privateDirectory);
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
            if(candidate!=null)SQLiteDatabase.deleteDatabase(candidate);
        }
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
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM cashier_sessions",null)){
            if(!c.moveToFirst()||c.getLong(0)!=0)throw new IllegalStateException("Backup must not contain session tokens");
        }
    }
}
