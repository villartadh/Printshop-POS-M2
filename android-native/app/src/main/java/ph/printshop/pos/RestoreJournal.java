package ph.printshop.pos;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;

/** Stopped-writer replacement foundation. Caller must hold an exclusive database
 * lifecycle fence, validate both databases and retain an encrypted recovery archive.
 * Not connected to the operator UI. A durable rollback copy is retained until
 * independent post-reopen acceptance is complete. */
final class RestoreJournal {
    private final Path live, previous, staged, marker;
    RestoreJournal(Path live) {
        this.live=live.toAbsolutePath();
        previous=sibling(".restore-previous");
        staged=sibling(".restore-candidate");
        marker=sibling(".restore-pending");
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
        for(String suffix:new String[]{"-wal","-shm","-journal"})
            if(Files.exists(sibling(suffix),LinkOption.NOFOLLOW_LINKS))throw new IOException("Database sidecars remain; stop and checkpoint writers first");
    }
    private boolean exists(Path file){return Files.exists(file,LinkOption.NOFOLLOW_LINKS);}
    void prepare(byte[] candidate)throws IOException {
        requireStopped();requireRegular(live);
        if(candidate==null||candidate.length==0||candidate.length>BackupEnvelope.MAX_BYTES)throw new IOException("Invalid restore candidate");
        if(exists(marker)||exists(previous)||exists(staged))throw new IOException("Unresolved restore state");
        // Before the marker exists the current live file is never modified.
        Files.copy(live,previous);syncFile(previous);
        Files.write(staged,candidate,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);syncFile(staged);
        syncDirectory();
        Files.write(marker,new byte[]{1},StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);syncFile(marker);syncDirectory();
    }
    void replace()throws IOException {
        requireStopped();requireRegular(marker);requireRegular(previous);requireRegular(staged);
        move(staged,live);
    }
    /** Default startup disposition is rollback, never automatic promotion. */
    void recover()throws IOException {
        requireStopped();
        if(exists(marker)){
            requireRegular(marker);requireRegular(previous);
            // Keep the rollback copy until the restored live path is durable.
            Files.deleteIfExists(staged);
            Files.copy(previous,staged);syncFile(staged);move(staged,live);
            Files.delete(marker);syncDirectory();
        }else requireRegular(live);
        Files.deleteIfExists(staged);Files.deleteIfExists(previous);syncDirectory();
    }
    /** Only after independent candidate reopen/validation and explicit acceptance. */
    void accept()throws IOException {
        requireStopped();requireRegular(marker);requireRegular(previous);requireRegular(live);
        if(exists(staged))throw new IOException("Replacement not applied");
        Files.delete(marker);syncDirectory();
        Files.delete(previous);syncDirectory();
    }
}
