package ph.printshop.pos;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;

/** Synthetic fixture restore only. No operator screen calls this coordinator.
 * One exclusive lifecycle lease spans proof, recovery, validation, replacement and reopen. */
final class NativeRestoreCoordinator {
    private NativeRestoreCoordinator(){}
    private static Path path(Context context,String fixture){
        StockMath.validateRequestId(fixture);
        return context.getDatabasePath("checkout-fixture-"+fixture+".db").toPath();
    }
    static boolean hasArtifacts(Path live){
        return RestoreJournal.hasArtifacts(live);
    }
    /** SQLite must checkpoint and switch out of persistent journal mode itself.
     * Never delete journal/WAL/SHM files manually. RestoreJournal rechecks after close. */
    static void checkpoint(SQLiteDatabase db)throws IOException{
        db.disableWriteAheadLogging();
        try(Cursor mode=db.rawQuery("PRAGMA journal_mode=DELETE",null)){
            if(!mode.moveToFirst()||!"delete".equalsIgnoreCase(mode.getString(0)))
                throw new IOException("SQLite could not enter stopped-writer journal mode");
        }
    }
    static Path restoreFixture(Context context,String fixture,String ownerToken,char[] ownerPassword,
                               byte[] archive,char[] archivePassword,char[] recoveryPassword)throws IOException{
        Path live=path(context,fixture);
        // Freeze input against concurrent caller mutation, bounded before copying.
        if(archive==null||archive.length==0||archive.length>BackupFileTransfer.MAX_ARCHIVE)
            throw new IllegalArgumentException("Invalid restore archive");
        byte[] frozen=archive.clone();
        try(DatabaseLifecycleLease lease=DatabaseLifecycleLease.open(live,true)){
            Path recovery=NativePreRestoreRecovery.create(context,fixture,lease,ownerToken,ownerPassword,recoveryPassword);
            try(SQLiteDatabase db=SQLiteDatabase.openDatabase(live.toString(),null,SQLiteDatabase.OPEN_READWRITE)){
                db.beginTransaction();
                try{
                    NativeBackupSnapshot.reviewForRestore(db,context.getCacheDir(),frozen,archivePassword,ownerToken);
                    db.setTransactionSuccessful();
                }finally{db.endTransaction();}
                checkpoint(db);
            }
            RestoreJournal journal=new RestoreJournal(live,lease);
            boolean attempt=false;
            try(BackupEnvelope.Decoded decoded=BackupEnvelope.open(frozen,22,archivePassword)){
                if(decoded.schema!=22)throw new IllegalArgumentException("Archive schema mismatch");
                attempt=true;
                journal.prepare(decoded.snapshot);
                journal.replace();
                try(SQLiteDatabase reopened=SQLiteDatabase.openDatabase(live.toString(),null,SQLiteDatabase.OPEN_READONLY)){
                    if(reopened.getVersion()!=22)throw new IllegalStateException("Restored schema mismatch");
                    NativeBackupSnapshot.validate(reopened); // Includes session exclusion.
                }
                journal.accept();
                return recovery;
            }catch(IOException|RuntimeException ex){
                if(attempt&&hasArtifacts(live))try{journal.recover();}catch(IOException|RuntimeException recoveryFailure){ex.addSuppressed(recoveryFailure);}
                throw ex;
            }
        }finally{Arrays.fill(frozen,(byte)0);}
    }
    /** Rollback-only recovery, not a way to bypass owner proof for a new restore. */
    static void rollbackInterruptedFixture(Context context,String fixture)throws IOException{
        Path live=path(context,fixture);
        try(DatabaseLifecycleLease lease=DatabaseLifecycleLease.open(live,true)){
            if(hasArtifacts(live))new RestoreJournal(live,lease).recover();
        }
    }
}
