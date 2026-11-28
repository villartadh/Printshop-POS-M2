package ph.printshop.pos;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
public final class BackupStagingTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError("staging recovery failed");}
    public static void main(String[] args)throws Exception{
        if(args.length==1){
            try(BackupStaging stage=BackupStaging.acquire(new File(args[0]))){
                Files.write(new File(stage.directory,"native-backup-review-123.db").toPath(),new byte[]{1,2,3});
                Files.write(new File(stage.directory,"native-backup-review-123.db-wal").toPath(),new byte[]{1});
                Runtime.getRuntime().halt(17);
            }
        }
        File cache=Files.createTempDirectory("synthetic-backup-cache").toFile();
        try{
            File unrelated=new File(cache,"keep.db");Files.write(unrelated.toPath(),new byte[]{9});
            Process child=new ProcessBuilder(new File(System.getProperty("java.home"),"bin/java").getPath(),"-cp",System.getProperty("java.class.path"),BackupStagingTest.class.getName(),cache.getPath()).start();
            check(child.waitFor(20,TimeUnit.SECONDS));check(child.exitValue()==17);
            File stageDir=new File(cache,"native-backup-staging");
            check(new File(stageDir,"native-backup-review-123.db").exists());
            File legacy=new File(cache,"native-backup-42.db");Files.write(legacy.toPath(),new byte[]{4});
            try(BackupStaging stage=BackupStaging.acquire(cache)){
                check(!new File(stage.directory,"native-backup-review-123.db").exists());
                check(!new File(stage.directory,"native-backup-review-123.db-wal").exists());
                check(!legacy.exists());check(unrelated.exists());
                File generated=File.createTempFile("native-backup-",".db",stage.directory);
                check(generated.exists());
            }
            try(BackupStaging stage=BackupStaging.acquire(cache)){
                check(stage.directory.list().length==1);check(unrelated.exists());
            }
            System.out.println("BackupStaging: 10 checks PASS (abrupt process termination)");
        }finally{
            try(java.util.stream.Stream<java.nio.file.Path> paths=Files.walk(cache.toPath())){
                for(java.nio.file.Path path:(Iterable<java.nio.file.Path>)paths.sorted(java.util.Comparator.reverseOrder())::iterator)Files.deleteIfExists(path);
            }
        }
    }
}
