package ph.printshop.pos;
import java.nio.file.*;
import java.util.Arrays;
public final class RecoveryArchiveTest {
    static void check(boolean ok){if(!ok)throw new AssertionError("Recovery archive invariant");}
    public static void main(String[] args)throws Exception{
        Path directory=Files.createTempDirectory("encrypted-recovery-test");
        char[] password="Synthetic-Recovery-Secret".toCharArray(),wrong="Synthetic-Wrong-Secret".toCharArray();
        byte[] plaintext={11,24,52,71},archive=BackupEnvelope.seal(plaintext,22,password);
        try{
            Path first=RecoveryArchive.persist(directory,archive,22,password);
            check(first.getFileName().toString().endsWith(".psbak"));
            check(Arrays.equals(Files.readAllBytes(first),archive));
            try(BackupEnvelope.Decoded decoded=BackupEnvelope.open(Files.readAllBytes(first),22,password)){check(Arrays.equals(decoded.snapshot,plaintext));}
            Path second=RecoveryArchive.persist(directory,archive,22,password);check(!first.equals(second));
            try{RecoveryArchive.persist(directory,archive,22,wrong);throw new AssertionError("Wrong password accepted");}catch(IllegalArgumentException expected){}
            byte[] tampered=archive.clone();tampered[tampered.length-1]^=1;
            try{RecoveryArchive.persist(directory,tampered,22,password);throw new AssertionError("Tampered archive accepted");}catch(IllegalArgumentException expected){}
            try{RecoveryArchive.persist(directory,archive,23,password);throw new AssertionError("Wrong schema accepted");}catch(java.io.IOException expected){}
            try(var entries=Files.list(directory)){check(entries.count()==2);}
            check(Arrays.equals(Files.readAllBytes(first),archive));check(Arrays.equals(Files.readAllBytes(second),archive));
            System.out.println("RecoveryArchive: authenticated durable publication, no overwrite and rejection PASS");
        }finally{Arrays.fill(password,'\0');Arrays.fill(wrong,'\0');try(var paths=Files.walk(directory)){for(Path p:(Iterable<Path>)paths.sorted(java.util.Comparator.reverseOrder())::iterator)Files.deleteIfExists(p);}}
    }
}
