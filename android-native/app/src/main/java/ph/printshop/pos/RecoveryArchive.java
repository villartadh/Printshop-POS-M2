package ph.printshop.pos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Arrays;
import java.util.UUID;

/** Durable private encrypted recovery archive; never stores decrypted bytes.
 * A unique final filename is published only after byte-exact authenticated verification. */
final class RecoveryArchive {
    private RecoveryArchive(){}
    static Path persist(Path directory,byte[] archive,int schema,char[] password)throws IOException{
        Files.createDirectories(directory);
        if(Files.isSymbolicLink(directory))throw new IOException("Unsafe recovery directory");
        // Persist the directory entry too, including on first use.
        try(FileChannel parent=FileChannel.open(directory.toAbsolutePath().getParent(),StandardOpenOption.READ)){parent.force(true);}
        try(BackupEnvelope.Decoded checked=BackupEnvelope.open(archive,schema,password)){
            if(checked.schema!=schema)throw new IOException("Recovery schema mismatch");
        }
        String id=UUID.randomUUID().toString();
        Path staged=directory.resolve("recovery-"+id+".partial"),result=directory.resolve("recovery-"+id+".psbak");
        try{
            try(FileChannel out=FileChannel.open(staged,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
                ByteBuffer buffer=ByteBuffer.wrap(archive);
                while(buffer.hasRemaining())if(out.write(buffer)==0)throw new IOException("Recovery write stalled");
                out.force(true);
            }
            byte[] reread;
            try(InputStream in=Files.newInputStream(staged)){reread=BackupFileTransfer.read(in);}
            try{
                if(!Arrays.equals(archive,reread))throw new IOException("Recovery archive changed");
                try(BackupEnvelope.Decoded verified=BackupEnvelope.open(reread,schema,password)){
                    if(verified.schema!=schema)throw new IOException("Recovery schema mismatch");
                }
            }finally{Arrays.fill(reread,(byte)0);}
            Files.move(staged,result,StandardCopyOption.ATOMIC_MOVE);
            try(FileChannel dir=FileChannel.open(directory,StandardOpenOption.READ)){dir.force(true);}
            return result;
        }finally{
            Files.deleteIfExists(staged);
            // If the final move succeeded but directory sync failed, leave the potentially
            // recoverable ciphertext in place; caller still receives failure.
        }
    }
}
