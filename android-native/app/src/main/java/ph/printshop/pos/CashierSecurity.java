package ph.printshop.pos;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Local credential primitives. No default credentials, plaintext persisted tokens or password logging. */
public final class CashierSecurity {
    public enum Role { OWNER, CASHIER }
    public static final int ITERATIONS=600_000;
    public static final long SESSION_MS=8L*60*60*1000;
    public static final int FAILURE_LIMIT=5;
    public static final long LOCK_MS=60_000L;
    private static final SecureRandom RANDOM=new SecureRandom();
    private CashierSecurity() {}
    static void requireOwner(Role role){
        if(role!=Role.OWNER)throw new IllegalStateException("Owner authorization required");
    }
    public static String username(String value){
        if(value==null||!value.matches("[A-Za-z0-9._-]{3,32}"))throw new IllegalArgumentException("Use 3–32 letters, digits, dots, underscores or hyphens");
        return value.toLowerCase(Locale.ROOT);
    }
    public static void validatePassword(char[] password){
        if(password==null||password.length<12||password.length>128)throw new IllegalArgumentException("Choose a password with 12–128 characters");
        boolean visible=false;
        for(char c:password){if(Character.isISOControl(c))throw new IllegalArgumentException("Password contains a control character");if(!Character.isWhitespace(c))visible=true;}
        if(!visible)throw new IllegalArgumentException("Password cannot be blank");
    }
    public static final class Credential {
        public final String saltHex, hashHex;
        private Credential(String salt,String hash){saltHex=salt;hashHex=hash;}
    }
    public static Credential createCredential(char[] password){
        validatePassword(password);
        byte[] salt=new byte[16];RANDOM.nextBytes(salt);
        byte[] hash=derive(password,salt);
        try{return new Credential(hex(salt),hex(hash));}
        finally{Arrays.fill(hash,(byte)0);}
    }
    /** Caller owns and clears supplied character arrays. Invalid stored values fail closed. */
    public static boolean verify(char[] password,String saltHex,String hashHex){
        if(password==null||password.length==0||password.length>128)return false;
        byte[] salt=decode(saltHex,16),expected=decode(hashHex,32),actual=derive(password,salt);
        try{return MessageDigest.isEqual(expected,actual);}
        finally{Arrays.fill(expected,(byte)0);Arrays.fill(actual,(byte)0);}
    }
    private static byte[] derive(char[] password,byte[] salt){
        PBEKeySpec spec=new PBEKeySpec(password,salt,ITERATIONS,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
        catch(java.security.GeneralSecurityException ex){throw new IllegalStateException("Credential derivation unavailable",ex);}
        finally{spec.clearPassword();}
    }
    public static String newToken(){
        byte[] b=new byte[32];RANDOM.nextBytes(b);
        try{return hex(b);}finally{Arrays.fill(b,(byte)0);}
    }
    public static String tokenHash(String token){
        byte[] b=decode(token,32);
        try{return hex(MessageDigest.getInstance("SHA-256").digest(b));}
        catch(NoSuchAlgorithmException ex){throw new IllegalStateException("Session hashing unavailable",ex);}
        finally{Arrays.fill(b,(byte)0);}
    }
    private static byte[] decode(String value,int size){
        if(value==null||value.length()!=size*2||!value.matches("[0-9a-f]+"))throw new IllegalArgumentException("Invalid credential or session encoding");
        byte[] result=new byte[size];
        for(int i=0;i<size;i++)result[i]=(byte)((Character.digit(value.charAt(i*2),16)<<4)|Character.digit(value.charAt(i*2+1),16));
        return result;
    }
    private static String hex(byte[] b){
        char[] result=new char[b.length*2];String chars="0123456789abcdef";
        for(int i=0;i<b.length;i++){int v=b[i]&255;result[i*2]=chars.charAt(v>>>4);result[i*2+1]=chars.charAt(v&15);}
        return new String(result);
    }
    public static long sessionExpiry(long now){
        if(now<0)throw new IllegalArgumentException("Invalid clock");
        return Math.addExact(now,SESSION_MS);
    }
    public static boolean validSession(long issued,long expires,long now,boolean revoked,boolean active){
        return !revoked&&active&&issued>=0&&expires>issued&&now>=issued&&now<expires;
    }
    public static final class Failure {
        public final int count;public final long lockedUntil;
        private Failure(int count,long until){this.count=count;lockedUntil=until;}
    }
    public static Failure failedAttempt(int previous,long lockedUntil,long now){
        if(previous<0||previous>FAILURE_LIMIT||lockedUntil<0||now<0)throw new IllegalArgumentException("Invalid login state");
        if(now<lockedUntil)return new Failure(previous,lockedUntil);
        int next=(previous==FAILURE_LIMIT?0:previous)+1;
        return new Failure(next,next==FAILURE_LIMIT?Math.addExact(now,LOCK_MS):0);
    }
}
