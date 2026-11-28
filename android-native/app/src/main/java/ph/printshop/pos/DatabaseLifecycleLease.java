package ph.printshop.pos;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/** Per-database lifetime fence for cooperating store handles.
 * Multiple local helpers may share an ordinary lease, but restore needs zero
 * open helpers. A separate process cannot open the same database while leased.
 * The lock file is persistent: never unlink it while another process may hold it. */
final class DatabaseLifecycleLease implements AutoCloseable {
    private static final Map<Path,Entry> entries=new HashMap<>();
    private static final class Entry {
        final RandomAccessFile handle; final FileLock lock; final boolean exclusive;
        int references=1;
        Entry(RandomAccessFile handle,FileLock lock,boolean exclusive){
            this.handle=handle;this.lock=lock;this.exclusive=exclusive;
        }
    }
    private final Path database;
    private final Entry entry;
    private boolean closed;
    private DatabaseLifecycleLease(Path database,Entry entry){this.database=database;this.entry=entry;}
    static synchronized DatabaseLifecycleLease open(Path path,boolean exclusive)throws IOException {
        Path database=path.toFile().getCanonicalFile().toPath();
        Entry current=entries.get(database);
        if(current!=null){
            if(exclusive||current.exclusive)throw new IOException("Database lifecycle busy");
            current.references++;
            return new DatabaseLifecycleLease(database,current);
        }
        Files.createDirectories(database.getParent());
        Path lockPath=database.resolveSibling(database.getFileName()+".lifecycle-lock");
        if(Files.isSymbolicLink(lockPath))throw new IOException("Unsafe lifecycle lock");
        RandomAccessFile handle=new RandomAccessFile(lockPath.toFile(),"rw");
        try{
            FileLock lock=handle.getChannel().tryLock();
            if(lock==null)throw new IOException("Database lifecycle busy");
            Entry acquired=new Entry(handle,lock,exclusive);
            entries.put(database,acquired);
            return new DatabaseLifecycleLease(database,acquired);
        }catch(IOException|RuntimeException ex){
            handle.close();
            if(ex instanceof OverlappingFileLockException)throw new IOException("Database lifecycle busy",ex);
            throw ex;
        }
    }
    void requireExclusive(Path path)throws IOException{
        synchronized(DatabaseLifecycleLease.class){
            if(closed||!entry.exclusive||!entry.lock.isValid()||!database.equals(path.toFile().getCanonicalFile().toPath()))
                throw new IOException("Exclusive database lifecycle lease required");
        }
    }
    @Override public void close()throws IOException{
        synchronized(DatabaseLifecycleLease.class){
            if(closed)return;
            closed=true;
            if(--entry.references==0){
                try{entry.lock.release();}finally{
                    try{entry.handle.close();}finally{entries.remove(database);}
                }
            }
        }
    }
}
