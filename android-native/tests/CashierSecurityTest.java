package ph.printshop.pos;
import java.util.Arrays;
import java.util.List;
public final class CashierSecurityTest {
    private static int checks;
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    private static void reject(Runnable action,String label){
        try{action.run();}catch(IllegalArgumentException|ArithmeticException expected){checks++;return;}
        throw new AssertionError("Accepted "+label);
    }
    public static void main(String[] args){
        check(CashierSecurity.username("CaSHIER_01").equals("cashier_01"),"normalized");
        reject(()->CashierSecurity.username("a"),"short username");reject(()->CashierSecurity.username(" user"),"space");
        reject(()->CashierSecurity.username("a@b"),"unsupported character");reject(()->CashierSecurity.username(null),"null username");
        reject(()->CashierSecurity.createCredential("short".toCharArray()),"short password");
        reject(()->CashierSecurity.validatePassword("            ".toCharArray()),"blank password");
        reject(()->CashierSecurity.validatePassword("password\nexample".toCharArray()),"control password");
        reject(()->CashierSecurity.validatePassword(new char[129]),"long password");
        char[] password="Synthetic-Passphrase-01".toCharArray();
        try{
            CashierSecurity.Credential a=CashierSecurity.createCredential(password),b=CashierSecurity.createCredential(password);
            check(a.saltHex.matches("[0-9a-f]{32}")&&a.hashHex.matches("[0-9a-f]{64}"),"credential format");
            check(!a.saltHex.equals(b.saltHex)&&!a.hashHex.equals(b.hashHex),"individual salts");
            check(CashierSecurity.verify(password,a.saltHex,a.hashHex),"correct password");
            check(!CashierSecurity.verify("Synthetic-Wrong-Password".toCharArray(),a.saltHex,a.hashHex),"wrong password");
            check(!CashierSecurity.verify(null,a.saltHex,a.hashHex),"null denied");
            reject(()->CashierSecurity.verify(password,"bad",a.hashHex),"bad persisted salt");
        }finally{Arrays.fill(password,'\0');}
        String token=CashierSecurity.newToken(),other=CashierSecurity.newToken(),hash=CashierSecurity.tokenHash(token);
        check(token.matches("[0-9a-f]{64}")&&!token.equals(other),"token randomness");
        check(hash.matches("[0-9a-f]{64}")&&!hash.equals(token)&&hash.equals(CashierSecurity.tokenHash(token)),"token hash");
        check(!hash.equals(CashierSecurity.tokenHash(other)),"token isolation");reject(()->CashierSecurity.tokenHash("bad"),"bad token");
        long start=1000,end=CashierSecurity.sessionExpiry(start);
        check(end-start==28_800_000,"session TTL");
        check(CashierSecurity.validSession(start,end,start,false,true),"issued valid");
        check(CashierSecurity.validSession(start,end,end-1,false,true),"before expiration");
        check(!CashierSecurity.validSession(start,end,end,false,true),"expiration");
        check(!CashierSecurity.validSession(start,end,start-1,false,true),"clock backwards");
        check(!CashierSecurity.validSession(start,end,start,true,true),"revoked");
        check(!CashierSecurity.validSession(start,end,start,false,false),"inactive");
        reject(()->CashierSecurity.sessionExpiry(Long.MAX_VALUE),"TTL overflow");
        CashierSecurity.Failure f=CashierSecurity.failedAttempt(0,0,100);
        check(f.count==1&&f.lockedUntil==0,"first failure");
        for(int n=1;n<5;n++)f=CashierSecurity.failedAttempt(f.count,f.lockedUntil,100);
        check(f.count==5&&f.lockedUntil==60_100,"persistent lock threshold");
        CashierSecurity.Failure locked=CashierSecurity.failedAttempt(f.count,f.lockedUntil,1000);
        check(locked.count==5&&locked.lockedUntil==f.lockedUntil,"lock not extended");
        check(CashierSecurity.failedAttempt(5,f.lockedUntil,f.lockedUntil).count==1,"next attempt after lock");
        reject(()->CashierSecurity.failedAttempt(-1,0,0),"invalid failure state");
        check(ShiftMath.expected(10000,List.of(2250L,750L))==13000L,"cash expected");
        check(ShiftMath.expected(0,List.of())==0,"empty drawer");
        check(ShiftMath.variance(13000,12900)==-100,"shortage");
        check(ShiftMath.variance(13000,13100)==100,"overage");
        check(ShiftMath.variance(13000,13000)==0,"balanced");
        reject(()->ShiftMath.expected(-1,List.of()),"negative opening");
        reject(()->ShiftMath.addPayment(0,0),"zero payment");
        reject(()->ShiftMath.addPayment(ShiftMath.MAX_DRAWER,1),"drawer limit");
        reject(()->ShiftMath.variance(0,-1),"negative count");
        System.out.println("CashierSecurity/ShiftMath: "+checks+" checks passed");
    }
}
