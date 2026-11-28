package ph.printshop.pos;
import java.util.Arrays;

public final class BackupEnvelopeTest {
    private static int checks;
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    private static void reject(Runnable r){try{r.run();throw new AssertionError("Invalid backup accepted");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        char[] password="Synthetic-backup-password-01".toCharArray(),wrong="Synthetic-backup-password-02".toCharArray();
        byte[] snapshot="Synthetic SQLite snapshot; not real customer data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try{
            byte[] archive=BackupEnvelope.seal(snapshot,22,password),second=BackupEnvelope.seal(snapshot,22,password);
            check(!Arrays.equals(archive,second),"salt and nonce randomized");
            check(!new String(archive,java.nio.charset.StandardCharsets.ISO_8859_1).contains(new String(snapshot,java.nio.charset.StandardCharsets.UTF_8)),"plaintext exposed");
            byte[] decoded;
            try(BackupEnvelope.Decoded opened=BackupEnvelope.open(archive,22,password)){
                check(opened.schema==22,"schema roundtrip");check(Arrays.equals(opened.snapshot,snapshot),"snapshot roundtrip");decoded=opened.snapshot;
            }
            check(Arrays.equals(decoded,new byte[decoded.length]),"decoded buffer not cleared");
            reject(()->BackupEnvelope.open(archive,22,wrong));
            reject(()->BackupEnvelope.open(archive,21,password));
            reject(()->BackupEnvelope.open(Arrays.copyOf(archive,archive.length-1),22,password));
            reject(()->BackupEnvelope.open(Arrays.copyOf(archive,archive.length+1),22,password));
            for(int pos:new int[]{0,11,15,16,32,44,archive.length-1}){
                byte[] bad=archive.clone();bad[pos]^=1;reject(()->BackupEnvelope.open(bad,22,password));
            }
            reject(()->BackupEnvelope.seal(new byte[0],22,password));
            reject(()->BackupEnvelope.seal(snapshot,0,password));
            reject(()->BackupEnvelope.seal(snapshot,22,"short".toCharArray()));
            try(BackupEnvelope.Decoded opened=BackupEnvelope.open(second,30,password)){check(Arrays.equals(opened.snapshot,snapshot),"older archive readable");}
            System.out.println("BackupEnvelope: "+checks+" checks PASS");
        }finally{Arrays.fill(password,'\0');Arrays.fill(wrong,'\0');Arrays.fill(snapshot,(byte)0);}
    }
}
