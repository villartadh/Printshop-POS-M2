package ph.printshop.pos;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Versioned authenticated archive for a separately validated native SQLite snapshot.
 * No filesystem writes, session exports or restore side effects in this primitive.
 */
final class BackupEnvelope {
    private BackupEnvelope(){}
    private static final byte[] MAGIC="PSPOSBA1".getBytes(StandardCharsets.US_ASCII);
    private static final int SALT=16,NONCE=12,TAG=16,HEADER=8+4+4+SALT+NONCE;
    static final int MAX_BYTES=32*1024*1024;
    private static final SecureRandom RANDOM=new SecureRandom();

    static byte[] seal(byte[] snapshot,int schema,char[] password){
        CashierSecurity.validatePassword(password);
        if(snapshot==null||snapshot.length<1||snapshot.length>MAX_BYTES||schema<1)throw new IllegalArgumentException("Invalid backup snapshot");
        byte[] salt=new byte[SALT],nonce=new byte[NONCE];RANDOM.nextBytes(salt);RANDOM.nextBytes(nonce);
        byte[] header=ByteBuffer.allocate(HEADER).put(MAGIC).putInt(schema).putInt(snapshot.length).put(salt).put(nonce).array();
        byte[] encrypted=crypt(Cipher.ENCRYPT_MODE,snapshot,header,salt,nonce,password);
        return ByteBuffer.allocate(header.length+encrypted.length).put(header).put(encrypted).array();
    }
    static final class Decoded implements AutoCloseable{
        final int schema; final byte[] snapshot;
        Decoded(int schema,byte[] snapshot){this.schema=schema;this.snapshot=snapshot;}
        @Override public void close(){Arrays.fill(snapshot,(byte)0);}
    }
    static Decoded open(byte[] archive,int supportedSchema,char[] password){
        CashierSecurity.validatePassword(password);
        if(archive==null||archive.length<HEADER+TAG+1||archive.length>HEADER+TAG+MAX_BYTES)throw new IllegalArgumentException("Invalid backup archive");
        ByteBuffer buffer=ByteBuffer.wrap(archive);
        byte[] magic=new byte[MAGIC.length];buffer.get(magic);
        int schema=buffer.getInt(),size=buffer.getInt();
        if(!Arrays.equals(magic,MAGIC)||schema<1||schema>supportedSchema||size<1||size>MAX_BYTES||archive.length!=HEADER+TAG+size)
            throw new IllegalArgumentException("Unsupported or malformed backup");
        byte[] salt=new byte[SALT],nonce=new byte[NONCE];buffer.get(salt);buffer.get(nonce);
        byte[] header=Arrays.copyOf(archive,HEADER),ciphertext=Arrays.copyOfRange(archive,HEADER,archive.length);
        byte[] plaintext=crypt(Cipher.DECRYPT_MODE,ciphertext,header,salt,nonce,password);
        if(plaintext.length!=size){Arrays.fill(plaintext,(byte)0);throw new IllegalArgumentException("Backup length mismatch");}
        return new Decoded(schema,plaintext);
    }
    private static byte[] crypt(int mode,byte[] value,byte[] header,byte[] salt,byte[] nonce,char[] password){
        PBEKeySpec spec=new PBEKeySpec(password,salt,CashierSecurity.ITERATIONS,256);
        byte[] key=null;
        try{
            key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(TAG*8,nonce));
            cipher.updateAAD(header);
            return cipher.doFinal(value);
        }catch(GeneralSecurityException e){throw new IllegalArgumentException("Backup encryption or authentication failed",e);}
        finally{spec.clearPassword();if(key!=null)Arrays.fill(key,(byte)0);}
    }
}
