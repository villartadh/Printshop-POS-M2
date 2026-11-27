package ph.printshop.pos;
import java.nio.file.*;
import java.util.Arrays;
public final class RestoreJournalTest {
    static final byte[] OLD={1,2,3}, NEXT={4,5,6};
    static void check(boolean ok){if(!ok)throw new AssertionError("Restore journal invariant");}
    static void rejected(Throwing action)throws Exception{
        try{action.run();throw new AssertionError("Expected rejection");}catch(java.io.IOException expected){}
    }
    interface Throwing{void run()throws Exception;}
    static void bytes(Path path,byte[] expected)throws Exception{check(Arrays.equals(Files.readAllBytes(path),expected));}
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("restore-journal-test");
        try{
            Path live=dir.resolve("fixture.db");Files.write(live,OLD);
            RestoreJournal journal=new RestoreJournal(live);
            Path wal=dir.resolve("fixture.db-wal");Files.write(wal,new byte[]{9});
            rejected(()->journal.prepare(NEXT));bytes(live,OLD);Files.delete(wal);
            journal.prepare(NEXT);bytes(live,OLD);
            rejected(()->journal.prepare(NEXT));rejected(journal::accept);
            journal.recover();bytes(live,OLD);
            journal.prepare(NEXT);journal.replace();bytes(live,NEXT);
            new RestoreJournal(live).recover();bytes(live,OLD);
            journal.prepare(NEXT);journal.replace();journal.accept();
            new RestoreJournal(live).recover();bytes(live,NEXT);
            try(var entries=Files.list(dir)){check(entries.count()==1);}
            // Crash before durable marker: old live survives; cleanup is safe.
            Files.write(dir.resolve("fixture.db.restore-previous"),NEXT);
            Files.write(dir.resolve("fixture.db.restore-candidate"),OLD);
            journal.recover();bytes(live,NEXT);
            // Missing rollback copy is a hard failure; retain marker for repair.
            journal.prepare(OLD);journal.replace();
            Files.delete(dir.resolve("fixture.db.restore-previous"));
            rejected(journal::recover);check(Files.exists(dir.resolve("fixture.db.restore-pending")));bytes(live,OLD);
            System.out.println("RestoreJournal: stopped-writer replacement and recovery checks PASS");
        }finally{
            try(var paths=Files.walk(dir)){for(Path p:(Iterable<Path>)paths.sorted(java.util.Comparator.reverseOrder())::iterator)Files.deleteIfExists(p);}
        }
    }
}
