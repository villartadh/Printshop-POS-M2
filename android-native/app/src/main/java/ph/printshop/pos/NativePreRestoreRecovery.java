package ph.printshop.pos;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;

/** Pre-restore recovery only, never replaces a database. The caller retains the
 * matching exclusive lease from worker shutdown through future restore acceptance. */
final class NativePreRestoreRecovery {
    private NativePreRestoreRecovery(){}
    static Path create(Context context,String fixtureId,DatabaseLifecycleLease lease,String token,
                       char[] currentPassword,char[] archivePassword)throws IOException{
        StockMath.validateRequestId(fixtureId);
        Path live=context.getDatabasePath("checkout-fixture-"+fixtureId+".db").toPath();
        lease.requireExclusive(live);
        if(!Files.isRegularFile(live,LinkOption.NOFOLLOW_LINKS))throw new IOException("Training database missing");
        if(RestoreJournal.hasArtifacts(live))throw new IOException("Database has unresolved restore state");
        CashierSecurity.validatePassword(archivePassword);
        if(Arrays.equals(currentPassword,archivePassword))throw new IllegalArgumentException("Use an independent archive passphrase");
        byte[] archive=null;
        try{
            // SQLite itself recovers normal journal/WAL state under this exclusive lease.
            // This is a transactional snapshot, NOT a file replacement; RestoreJournal
            // independently refuses replacement while any SQLite sidecar remains.
            try(SQLiteDatabase db=SQLiteDatabase.openDatabase(live.toString(),null,SQLiteDatabase.OPEN_READWRITE)){
                if(db.getVersion()!=22)throw new IllegalStateException("Unsupported current schema");
                boolean confirmed;
                db.beginTransaction();
                try{
                    confirmed=CashierJournal.confirmRestoreOwner(db,token,currentPassword,System.currentTimeMillis());
                    db.setTransactionSuccessful(); // Persist denied credential attempts.
                }finally{db.endTransaction();}
                if(!confirmed)throw new IllegalStateException("Owner credential confirmation failed");
                db.beginTransaction();
                try{
                    CashierSecurity.requireOwner(CashierJournal.requireSession(db,token,System.currentTimeMillis()).role);
                    NativeBackupSnapshot.requireRestoreReady(db);
                    archive=NativeBackupSnapshot.create(db,context.getCacheDir(),archivePassword);
                    NativeBackupSnapshot.Review review=NativeBackupSnapshot.review(db,context.getCacheDir(),archive,archivePassword);
                    if(review.currentOpenShifts!=0||review.currentPendingOperations!=0||review.openShifts!=0||review.pendingOperations!=0)
                        throw new IllegalStateException("Resolve open shifts and pending requests");
                    db.setTransactionSuccessful();
                }finally{db.endTransaction();}
            }
            lease.requireExclusive(live);
            return RecoveryArchive.persist(context.getFilesDir().toPath().resolve("native-restore-recovery"),archive,22,archivePassword);
        }finally{if(archive!=null)Arrays.fill(archive,(byte)0);}
    }
}
