package ph.printshop.pos;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;

/** Bounded archive transfer; never decrypts or writes a database. */
final class BackupFileTransfer {
    private BackupFileTransfer(){}
    // Envelope header and GCM tag fit comfortably inside this allowance.
    static final int MAX_ARCHIVE=BackupEnvelope.MAX_BYTES+128;
    static void write(OutputStream destination,byte[] archive) throws IOException{
        if(destination==null||archive==null||archive.length==0||archive.length>MAX_ARCHIVE)throw new IOException("Invalid backup destination or archive");
        destination.write(archive);destination.flush();
    }
    /** Reopen and compare exactly, without allocating a second full archive.
     * Reject partial writes, silent corruption, appended bytes and stalled providers. */
    static void verify(InputStream source,byte[] archive) throws IOException{
        if(source==null||archive==null||archive.length==0||archive.length>MAX_ARCHIVE)
            throw new IOException("Invalid backup verification source");
        byte[] chunk=new byte[8192];
        try{
            int position=0;
            while(position<archive.length){
                int count=source.read(chunk,0,Math.min(chunk.length,archive.length-position));
                if(count<=0||count>Math.min(chunk.length,archive.length-position))throw new IOException("Backup export incomplete");
                for(int i=0;i<count;i++)if(chunk[i]!=archive[position+i])throw new IOException("Backup export differs");
                position+=count;
            }
            if(source.read()!=-1)throw new IOException("Backup export has trailing bytes");
        }finally{java.util.Arrays.fill(chunk,(byte)0);}
    }
    static byte[] read(InputStream source) throws IOException{
        if(source==null)throw new IOException("Missing backup source");
        ByteArrayOutputStream result=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int count;
        while((count=source.read(chunk))!=-1){
            if(count==0)throw new IOException("Backup source stalled");
            if(count>MAX_ARCHIVE-result.size())throw new IOException("Backup archive too large");
            result.write(chunk,0,count);
        }
        if(result.size()==0)throw new IOException("Backup archive empty");
        return result.toByteArray();
    }
}
