package ph.printshop.pos;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
public final class DatabaseLifecycleLeaseTest {
    static void check(boolean ok){if(!ok)throw new AssertionError("Lifecycle lease invariant");}
    static void denied(Path path,boolean exclusive)throws Exception{
        try(DatabaseLifecycleLease ignored=DatabaseLifecycleLease.open(path,exclusive)){throw new AssertionError("Expected busy");}
        catch(java.io.IOException expected){}
    }
    public static void main(String[] args)throws Exception{
        if(args.length==2){
            Path path=Path.of(args[0]);
            if("deny".equals(args[1])){denied(path,false);denied(path,true);return;}
            try(DatabaseLifecycleLease ignored=DatabaseLifecycleLease.open(path,true)){Runtime.getRuntime().halt(17);}
        }
        Path dir=Files.createTempDirectory("lifecycle-lease");
        try{
            Path db=dir.resolve("fixture.db");
            try(DatabaseLifecycleLease a=DatabaseLifecycleLease.open(db,false)){
                try(DatabaseLifecycleLease b=DatabaseLifecycleLease.open(db,false)){
                    denied(db,true);
                    try(DatabaseLifecycleLease other=DatabaseLifecycleLease.open(dir.resolve("other.db"),true)){other.requireExclusive(dir.resolve("other.db"));}
                    Process child=child(db,"deny");check(child.waitFor(20,TimeUnit.SECONDS));check(child.exitValue()==0);
                }
                denied(db,true);
            }
            try(DatabaseLifecycleLease exclusive=DatabaseLifecycleLease.open(db,true)){
                denied(db,false);denied(db,true);exclusive.requireExclusive(db);
                try{exclusive.requireExclusive(dir.resolve("other.db"));throw new AssertionError("Wrong DB");}catch(java.io.IOException expected){}
            }
            Process child=child(db,"halt");check(child.waitFor(20,TimeUnit.SECONDS));check(child.exitValue()==17);
            try(DatabaseLifecycleLease recovered=DatabaseLifecycleLease.open(db,true)){recovered.requireExclusive(db);}
            System.out.println("DatabaseLifecycleLease: local and cross-process exclusion, crash release PASS");
        }finally{try(var paths=Files.walk(dir)){for(Path p:(Iterable<Path>)paths.sorted(java.util.Comparator.reverseOrder())::iterator)Files.deleteIfExists(p);}}
    }
    static Process child(Path db,String mode)throws Exception{
        return new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",System.getProperty("java.class.path"),DatabaseLifecycleLeaseTest.class.getName(),db.toString(),mode).inheritIO().start();
    }
}
