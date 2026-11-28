package ph.printshop.pos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Arrays;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

/** Durable private encrypted recovery archive; never stores decrypted bytes.
 * A unique final filename is published only after byte-exact authenticated verification. */
final class RecoveryArchive {
    private RecoveryArchive(){}
    private static final String NAME="recovery-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.psbak";
    static Path fixtureDirectory(Path files,String fixture,boolean create)throws IOException{
        if(fixture==null||!UUID.fromString(fixture).toString().equals(fixture))
            throw new IllegalArgumentException("Invalid recovery fixture");
        Path root=files.resolve("native-restore-recovery");
        if(create){
            Files.createDirectories(root);
            if(Files.isSymbolicLink(root))throw new IOException("Unsafe recovery root");
            try(FileChannel parent=FileChannel.open(files,StandardOpenOption.READ)){parent.force(true);}
        }
        if(Files.exists(root,LinkOption.NOFOLLOW_LINKS)&&!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Unsafe recovery root");
        Path scoped=root.resolve(fixture);
        if(Files.exists(scoped,LinkOption.NOFOLLOW_LINKS)&&!Files.isDirectory(scoped,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Unsafe recovery directory");
        return scoped;
    }
    static final class Entry{
        final String name;final long modified;
        Entry(String name,long modified){this.name=name;this.modified=modified;}
    }
    static List<Entry> list(Path directory)throws IOException{
        List<Entry> result=new ArrayList<>();
        if(!Files.exists(directory,LinkOption.NOFOLLOW_LINKS))return result;
        if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))throw new IOException("Unsafe recovery directory");
        try(DirectoryStream<Path> stream=Files.newDirectoryStream(directory)){
            for(Path file:stream){
                String name=file.getFileName().toString();
                if(!name.matches(NAME)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))continue;
                long size=Files.size(file);if(size<1||size>BackupFileTransfer.MAX_ARCHIVE)continue;
                if(result.size()>=10000)throw new IOException("Recovery archive list too large");
                result.add(new Entry(name,Files.getLastModifiedTime(file,LinkOption.NOFOLLOW_LINKS).toMillis()));
            }
        }
        result.sort(Comparator.comparingLong((Entry e)->e.modified).reversed().thenComparing(e->e.name));
        return result;
    }
    static byte[] read(Path directory,String name)throws IOException{
        if(name==null||!name.matches(NAME)||!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Invalid recovery archive");
        Path file=directory.resolve(name);
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Recovery archive unavailable");
        try(InputStream in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){return BackupFileTransfer.read(in);}
    }
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
