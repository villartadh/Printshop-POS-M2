package ph.printshop.pos;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.Files;

/** Private staging lease. The OS releases the lock if the process dies.
 * Only our exact temporary filenames (including SQLite sidecars) are removed.
 * Never touches live databases, external documents, or unrelated cache files.
 */
final class BackupStaging implements AutoCloseable {
    private final RandomAccessFile handle;
    private final FileLock lock;
    final File directory;
    private BackupStaging(File directory,RandomAccessFile handle,FileLock lock){
        this.directory=directory;this.handle=handle;this.lock=lock;
    }
    static BackupStaging acquire(File cache) throws IOException {
        File dir=new File(cache,"native-backup-staging");
        if(!dir.mkdir()&&!dir.isDirectory())throw new IOException("Backup staging unavailable");
        if(Files.isSymbolicLink(dir.toPath()))throw new IOException("Unsafe backup staging");
        File lease=new File(dir,"lease.lock");
        if(Files.isSymbolicLink(lease.toPath()))throw new IOException("Unsafe backup staging lease");
        RandomAccessFile file=new RandomAccessFile(lease,"rw");
        FileLock lock=null;
        try{
            lock=file.getChannel().lock();
            cleanupEntries(dir);
            // Prior versions staged these exact filenames directly in app cache.
            cleanupEntries(cache);
            return new BackupStaging(dir,file,lock);
        }catch(IOException|RuntimeException ex){
            try{if(lock!=null)lock.release();}finally{file.close();}
            throw ex;
        }
    }
    private static void cleanupEntries(File directory) throws IOException {
        File[] entries=directory.listFiles();
        if(entries==null)throw new IOException("Backup staging unavailable");
        for(File entry:entries){
            if(entry.getName().matches("native-backup-(review-)?-?[0-9]+\\.db(-journal|-wal|-shm)?")){
                if(Files.isDirectory(entry.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS))throw new IOException("Unsafe staging entry");
                Files.deleteIfExists(entry.toPath());
            }
        }
    }
    @Override public void close() throws IOException {
        try{lock.release();}finally{handle.close();}
    }
}
