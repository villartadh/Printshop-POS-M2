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
        if(args.length==1){
            Path crash=Path.of(args[0]);
            try(DatabaseLifecycleLease lease=DatabaseLifecycleLease.open(crash,true)){
                RestoreJournal journal=new RestoreJournal(crash,lease);
                journal.prepare(NEXT);journal.replace();
                Runtime.getRuntime().halt(27);
            }
        }
        Path dir=Files.createTempDirectory("restore-journal-test");
        try{
            Path live=dir.resolve("fixture.db");Files.write(live,OLD);
            try(DatabaseLifecycleLease lease=DatabaseLifecycleLease.open(live,true)){
            RestoreJournal journal=new RestoreJournal(live,lease);
            Path wal=dir.resolve("fixture.db-wal");Files.write(wal,new byte[]{9});
            rejected(()->journal.prepare(NEXT));bytes(live,OLD);Files.delete(wal);
            journal.prepare(NEXT);bytes(live,OLD);
            rejected(()->journal.prepare(NEXT));rejected(journal::accept);
            journal.recover();bytes(live,OLD);
            journal.prepare(NEXT);journal.replace();bytes(live,NEXT);
            new RestoreJournal(live,lease).recover();bytes(live,OLD);
            journal.prepare(NEXT);journal.replace();journal.accept();
            new RestoreJournal(live,lease).recover();bytes(live,NEXT);
            try(var entries=Files.list(dir)){check(entries.count()==2);}
            // Crash before durable marker: old live survives; cleanup is safe.
            Files.write(dir.resolve("fixture.db.restore-previous"),NEXT);
            Files.write(dir.resolve("fixture.db.restore-candidate"),OLD);
            journal.recover();bytes(live,NEXT);
            // Corrupt candidate cannot be applied; original remains unchanged.
            journal.prepare(OLD);
            Files.write(dir.resolve("fixture.db.restore-candidate"),new byte[]{9});
            rejected(journal::replace);bytes(live,NEXT);journal.recover();bytes(live,NEXT);
            // Corrupted rollback evidence must never replace a live candidate.
            journal.prepare(OLD);journal.replace();
            Files.write(dir.resolve("fixture.db.restore-previous"),new byte[]{9});
            rejected(journal::recover);bytes(live,OLD);
            Files.write(dir.resolve("fixture.db.restore-previous"),NEXT);
            journal.recover();bytes(live,NEXT);
            // A partial marker-stage is uncommitted; cleanup leaves the live DB.
            Files.write(dir.resolve("fixture.db.restore-marker-stage"),new byte[]{2});
            journal.recover();bytes(live,NEXT);
            // Missing rollback copy is a hard failure; retain marker for repair.
            journal.prepare(OLD);journal.replace();
            Files.delete(dir.resolve("fixture.db.restore-previous"));
            rejected(journal::recover);check(Files.exists(dir.resolve("fixture.db.restore-pending")));bytes(live,OLD);
            }
            Path crash=dir.resolve("crash.db");Files.write(crash,OLD);
            Process child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",System.getProperty("java.class.path"),RestoreJournalTest.class.getName(),crash.toString()).inheritIO().start();
            check(child.waitFor(20,java.util.concurrent.TimeUnit.SECONDS));check(child.exitValue()==27);
            bytes(crash,NEXT);check(RestoreJournal.hasArtifacts(crash));
            try(DatabaseLifecycleLease lease=DatabaseLifecycleLease.open(crash,true)){
                new RestoreJournal(crash,lease).recover();
            }
            bytes(crash,OLD);check(!RestoreJournal.hasArtifacts(crash));
            System.out.println("RestoreJournal: integrity checks, stopped-writer recovery and abrupt process halt PASS");
        }finally{
            try(var paths=Files.walk(dir)){for(Path p:(Iterable<Path>)paths.sorted(java.util.Comparator.reverseOrder())::iterator)Files.deleteIfExists(p);}
        }
    }
}
