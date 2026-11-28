package ph.printshop.pos;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Stopped-writer replacement foundation. Caller must hold an exclusive database
 * lifecycle fence, validate both databases and retain an encrypted recovery archive.
 * Not connected to the operator UI. A durable rollback copy is retained until
 * independent post-reopen acceptance is complete. */
final class RestoreJournal {
    private final Path live, previous, staged, marker, markerStage;
    private final DatabaseLifecycleLease lease;
    RestoreJournal(Path live,DatabaseLifecycleLease lease) {
        this.lease=lease;
        this.live=live.toAbsolutePath();
        previous=sibling(".restore-previous");
        staged=sibling(".restore-candidate");
        marker=sibling(".restore-pending");
        markerStage=sibling(".restore-marker-stage");
    }
    private Path sibling(String suffix){return live.resolveSibling(live.getFileName()+suffix);}
    private void syncDirectory() throws IOException {
        try(FileChannel channel=FileChannel.open(live.getParent(),StandardOpenOption.READ)){channel.force(true);}
    }
    private static void syncFile(Path file)throws IOException {
        try(FileChannel channel=FileChannel.open(file,StandardOpenOption.WRITE)){channel.force(true);}
    }
    private void move(Path from,Path to)throws IOException {
        Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        syncDirectory();
    }
    private void requireRegular(Path file)throws IOException {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)==0)throw new IOException("Missing or unsafe restore file");
    }
    private void requireStopped()throws IOException {
        if(lease==null)throw new IOException("Exclusive database lifecycle lease required");
        lease.requireExclusive(live);
        for(String suffix:new String[]{"-wal","-shm","-journal"})
            if(Files.exists(sibling(suffix),LinkOption.NOFOLLOW_LINKS))throw new IOException("Database sidecars remain; stop and checkpoint writers first");
    }
    private boolean exists(Path file){return Files.exists(file,LinkOption.NOFOLLOW_LINKS);}
    static boolean hasArtifacts(Path live){
        for(String suffix:new String[]{".restore-pending",".restore-previous",".restore-candidate",".restore-marker-stage"})
            if(Files.exists(live.resolveSibling(live.getFileName()+suffix),LinkOption.NOFOLLOW_LINKS))return true;
        return false;
    }
    private byte[] digest(Path path)throws IOException{
        requireRegular(path);
        try{
            MessageDigest hash=MessageDigest.getInstance("SHA-256");
            try(InputStream in=Files.newInputStream(path)){
                byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1){if(n==0)throw new IOException("Restore hash stalled");hash.update(buffer,0,n);}
            }
            return hash.digest();
        }catch(NoSuchAlgorithmException ex){throw new IOException("SHA-256 unavailable",ex);}
    }
    private byte[] markerBytes()throws IOException{
        requireRegular(marker);
        if(Files.size(marker)!=65)throw new IOException("Invalid restore marker");
        byte[] data=Files.readAllBytes(marker);
        if(data.length!=65||data[0]!=2)throw new IOException("Invalid restore marker");
        return data;
    }
    private void verify(Path file,byte[] data,int start)throws IOException{
        if(!MessageDigest.isEqual(digest(file),Arrays.copyOfRange(data,start,start+32)))
            throw new IOException("Restore file integrity mismatch");
    }
    void prepare(byte[] candidate)throws IOException {
        requireStopped();requireRegular(live);
        if(candidate==null||candidate.length==0||candidate.length>BackupEnvelope.MAX_BYTES)throw new IOException("Invalid restore candidate");
        if(hasArtifacts(live))throw new IOException("Unresolved restore state");
        // Before the marker exists the current live file is never modified.
        Files.copy(live,previous);syncFile(previous);
        Files.write(staged,candidate,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);syncFile(staged);
        syncDirectory();
        byte[] proof=new byte[65];proof[0]=2;
        System.arraycopy(digest(previous),0,proof,1,32);
        System.arraycopy(digest(staged),0,proof,33,32);
        Files.write(markerStage,proof,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        syncFile(markerStage);move(markerStage,marker);
    }
    void replace()throws IOException {
        requireStopped();byte[] proof=markerBytes();verify(previous,proof,1);verify(staged,proof,33);verify(live,proof,1);
        move(staged,live);
    }
    /** Default startup disposition is rollback, never automatic promotion. */
    void recover()throws IOException {
        requireStopped();
        if(exists(marker)){
            byte[] proof=markerBytes();verify(previous,proof,1);
            // Keep the rollback copy until the restored live path is durable.
            Files.deleteIfExists(staged);
            Files.copy(previous,staged);syncFile(staged);move(staged,live);
            Files.delete(marker);syncDirectory();
        }else requireRegular(live);
        Files.deleteIfExists(staged);Files.deleteIfExists(previous);Files.deleteIfExists(markerStage);syncDirectory();
    }
    /** Only after independent candidate reopen/validation and explicit acceptance. */
    void accept()throws IOException {
        requireStopped();byte[] proof=markerBytes();verify(previous,proof,1);verify(live,proof,33);
        if(exists(staged))throw new IOException("Replacement not applied");
        Files.delete(marker);syncDirectory();
        Files.delete(previous);syncDirectory();
    }
}
