package ph.printshop.pos;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.UUID;

/** Synthetic fixture account/session and shift ledger. Caller owns transactions. */
final class CashierJournal {
    private CashierJournal(){}
    static void create(SQLiteDatabase db){
        db.execSQL("CREATE TABLE cashier_accounts (id TEXT PRIMARY KEY NOT NULL, username TEXT UNIQUE NOT NULL CHECK(length(username) BETWEEN 3 AND 32 AND username=lower(username) AND username NOT GLOB '*[^a-z0-9._-]*'), role TEXT NOT NULL CHECK(role IN ('OWNER','CASHIER')), salt TEXT NOT NULL CHECK(length(salt)=32 AND salt NOT GLOB '*[^0-9a-f]*'), password_hash TEXT NOT NULL CHECK(length(password_hash)=64 AND password_hash NOT GLOB '*[^0-9a-f]*'), iterations INTEGER NOT NULL CHECK(iterations=600000), revision INTEGER NOT NULL CHECK(revision>=1), active INTEGER NOT NULL CHECK(active IN (0,1)), failures INTEGER NOT NULL CHECK(failures BETWEEN 0 AND 5), locked_until INTEGER NOT NULL CHECK(locked_until>=0), created_ms INTEGER NOT NULL CHECK(created_ms>=0), CHECK(typeof(iterations)='integer' AND typeof(revision)='integer' AND typeof(active)='integer' AND typeof(failures)='integer' AND typeof(locked_until)='integer' AND typeof(created_ms)='integer'))");
        db.execSQL("CREATE UNIQUE INDEX cashier_single_owner ON cashier_accounts(role) WHERE role='OWNER'");
        db.execSQL("CREATE TABLE cashier_sessions (token_hash TEXT PRIMARY KEY NOT NULL CHECK(length(token_hash)=64 AND token_hash NOT GLOB '*[^0-9a-f]*'), account_id TEXT NOT NULL REFERENCES cashier_accounts(id), account_revision INTEGER NOT NULL CHECK(account_revision>=1), issued_ms INTEGER NOT NULL CHECK(issued_ms>=0), expires_ms INTEGER NOT NULL CHECK(expires_ms>issued_ms AND expires_ms-issued_ms=28800000), revoked_ms INTEGER CHECK(revoked_ms IS NULL OR revoked_ms>=issued_ms), CHECK(typeof(account_revision)='integer' AND typeof(issued_ms)='integer' AND typeof(expires_ms)='integer' AND (revoked_ms IS NULL OR typeof(revoked_ms)='integer')))");
        db.execSQL("CREATE TABLE cashier_shifts (id TEXT PRIMARY KEY NOT NULL, operator_id TEXT NOT NULL REFERENCES cashier_accounts(id), opened_ms INTEGER NOT NULL CHECK(opened_ms>=0), opening_cash INTEGER NOT NULL CHECK(opening_cash BETWEEN 0 AND 9999999999900000 AND typeof(opening_cash)='integer'), state TEXT NOT NULL CHECK(state IN ('OPEN','CLOSED')), closed_ms INTEGER, expected_cash INTEGER, counted_cash INTEGER, variance INTEGER, CHECK((state='OPEN' AND closed_ms IS NULL AND expected_cash IS NULL AND counted_cash IS NULL AND variance IS NULL) OR (state='CLOSED' AND closed_ms IS NOT NULL AND expected_cash IS NOT NULL AND counted_cash IS NOT NULL AND variance IS NOT NULL AND closed_ms>=opened_ms AND expected_cash BETWEEN 0 AND 9999999999900000 AND counted_cash BETWEEN 0 AND 9999999999900000 AND variance=counted_cash-expected_cash AND typeof(expected_cash)='integer' AND typeof(counted_cash)='integer' AND typeof(variance)='integer')), CHECK(typeof(opened_ms)='integer' AND (closed_ms IS NULL OR typeof(closed_ms)='integer')))");
        db.execSQL("CREATE UNIQUE INDEX cashier_one_open_shift ON cashier_shifts(operator_id) WHERE state='OPEN'");
        db.execSQL("CREATE TABLE cashier_shift_checkouts (request_id TEXT PRIMARY KEY NOT NULL REFERENCES checkout_requests(request_id), shift_id TEXT NOT NULL REFERENCES cashier_shifts(id), operator_id TEXT NOT NULL REFERENCES cashier_accounts(id))");
        db.execSQL("CREATE TRIGGER cashier_account_no_delete BEFORE DELETE ON cashier_accounts BEGIN SELECT RAISE(ABORT,'Account audit cannot be deleted'); END");
        db.execSQL("CREATE TRIGGER cashier_account_no_replace BEFORE INSERT ON cashier_accounts WHEN EXISTS(SELECT 1 FROM cashier_accounts WHERE id=NEW.id OR username=NEW.username OR (role='OWNER' AND NEW.role='OWNER')) BEGIN SELECT RAISE(ABORT,'Account identity already exists'); END");
        db.execSQL("CREATE TRIGGER cashier_account_guard BEFORE UPDATE ON cashier_accounts WHEN NEW.id IS NOT OLD.id OR NEW.username IS NOT OLD.username OR NEW.role IS NOT OLD.role OR NEW.salt IS NOT OLD.salt OR NEW.password_hash IS NOT OLD.password_hash OR NEW.iterations IS NOT OLD.iterations OR NEW.created_ms IS NOT OLD.created_ms OR (OLD.active=0 AND NEW.active<>0) OR (NEW.active=OLD.active AND NEW.revision<>OLD.revision) OR (NEW.active<>OLD.active AND (OLD.role='OWNER' OR NEW.revision<>OLD.revision+1 OR EXISTS(SELECT 1 FROM cashier_shifts WHERE operator_id=OLD.id AND state='OPEN'))) BEGIN SELECT RAISE(ABORT,'Invalid account transition'); END");
        db.execSQL("CREATE TRIGGER cashier_session_initial BEFORE INSERT ON cashier_sessions WHEN NEW.revoked_ms IS NOT NULL OR EXISTS(SELECT 1 FROM cashier_sessions WHERE token_hash=NEW.token_hash) OR NOT EXISTS(SELECT 1 FROM cashier_accounts a WHERE a.id=NEW.account_id AND a.active=1 AND a.revision=NEW.account_revision) BEGIN SELECT RAISE(ABORT,'Invalid session issue'); END");
        db.execSQL("CREATE TRIGGER cashier_session_no_delete BEFORE DELETE ON cashier_sessions BEGIN SELECT RAISE(ABORT,'Session audit cannot be deleted'); END");
        db.execSQL("CREATE TRIGGER cashier_session_revoke BEFORE UPDATE ON cashier_sessions WHEN OLD.revoked_ms IS NOT NULL OR NEW.revoked_ms IS NULL OR NEW.token_hash IS NOT OLD.token_hash OR NEW.account_id IS NOT OLD.account_id OR NEW.account_revision IS NOT OLD.account_revision OR NEW.issued_ms IS NOT OLD.issued_ms OR NEW.expires_ms IS NOT OLD.expires_ms BEGIN SELECT RAISE(ABORT,'Invalid session transition'); END");
        db.execSQL("CREATE TRIGGER cashier_shift_initial BEFORE INSERT ON cashier_shifts WHEN NEW.state<>'OPEN' OR EXISTS(SELECT 1 FROM cashier_shifts WHERE id=NEW.id OR (operator_id=NEW.operator_id AND state='OPEN')) OR NOT EXISTS(SELECT 1 FROM cashier_accounts WHERE id=NEW.operator_id AND active=1) BEGIN SELECT RAISE(ABORT,'Invalid shift opening'); END");
        db.execSQL("CREATE TRIGGER cashier_shift_no_delete BEFORE DELETE ON cashier_shifts BEGIN SELECT RAISE(ABORT,'Shift audit cannot be deleted'); END");
        db.execSQL("CREATE TRIGGER cashier_shift_close BEFORE UPDATE ON cashier_shifts WHEN OLD.state<>'OPEN' OR NEW.state<>'CLOSED' OR NEW.id IS NOT OLD.id OR NEW.operator_id IS NOT OLD.operator_id OR NEW.opened_ms IS NOT OLD.opened_ms OR NEW.opening_cash IS NOT OLD.opening_cash OR EXISTS(SELECT 1 FROM cashier_shift_checkouts l JOIN checkout_requests r ON l.request_id=r.request_id WHERE l.shift_id=OLD.id AND r.state='PENDING') OR NEW.expected_cash<>OLD.opening_cash+COALESCE((SELECT SUM(c.paid) FROM cashier_shift_checkouts l JOIN checkout_cash c ON l.request_id=c.request_id JOIN checkout_requests r ON r.request_id=l.request_id WHERE l.shift_id=OLD.id AND r.state='APPLIED'),0) BEGIN SELECT RAISE(ABORT,'Invalid shift close or unresolved checkout'); END");
        db.execSQL("CREATE TRIGGER cashier_checkout_link_initial BEFORE INSERT ON cashier_shift_checkouts WHEN EXISTS(SELECT 1 FROM cashier_shift_checkouts WHERE request_id=NEW.request_id) OR NOT EXISTS(SELECT 1 FROM cashier_shifts s JOIN cashier_accounts a ON s.operator_id=a.id JOIN checkout_requests r ON r.request_id=NEW.request_id WHERE s.id=NEW.shift_id AND s.operator_id=NEW.operator_id AND s.state='OPEN' AND a.active=1 AND r.state='PENDING') BEGIN SELECT RAISE(ABORT,'Checkout requires open operator shift'); END");
        db.execSQL("CREATE TRIGGER cashier_checkout_link_no_update BEFORE UPDATE ON cashier_shift_checkouts BEGIN SELECT RAISE(ABORT,'Checkout operator audit is immutable'); END");
        db.execSQL("CREATE TRIGGER cashier_checkout_link_no_delete BEFORE DELETE ON cashier_shift_checkouts BEGIN SELECT RAISE(ABORT,'Checkout operator audit is immutable'); END");
        db.execSQL("CREATE TRIGGER cashier_checkout_sale_guard BEFORE INSERT ON checkout_sales WHEN EXISTS(SELECT 1 FROM cashier_accounts) AND NOT EXISTS(SELECT 1 FROM cashier_shift_checkouts l JOIN cashier_shifts s ON s.id=l.shift_id JOIN cashier_accounts a ON a.id=l.operator_id WHERE l.request_id=NEW.request_id AND s.state='OPEN' AND a.active=1 AND s.operator_id=l.operator_id) BEGIN SELECT RAISE(ABORT,'Checkout requires active operator shift before sale recording'); END");
        db.execSQL("CREATE TRIGGER cashier_checkout_apply_guard BEFORE UPDATE ON checkout_requests WHEN NEW.state='APPLIED' AND EXISTS(SELECT 1 FROM cashier_accounts) AND NOT EXISTS(SELECT 1 FROM cashier_shift_checkouts l JOIN cashier_shifts s ON s.id=l.shift_id JOIN cashier_accounts a ON a.id=l.operator_id WHERE l.request_id=NEW.request_id AND s.state='OPEN' AND a.active=1 AND s.operator_id=l.operator_id) BEGIN SELECT RAISE(ABORT,'Checkout requires active operator and open shift'); END");
    }
    private static String str(Cursor c,String n){return c.getString(c.getColumnIndexOrThrow(n));}
    private static long num(Cursor c,String n){return c.getLong(c.getColumnIndexOrThrow(n));}
    private static boolean exists(SQLiteDatabase db,String sql,String[] args){
        try(Cursor c=db.rawQuery(sql,args)){return c.moveToFirst();}
    }
    static boolean initialized(SQLiteDatabase db){
        return exists(db,"SELECT 1 FROM cashier_accounts LIMIT 1",null);
    }
    static void requireLegacyFixture(SQLiteDatabase db){
        if(initialized(db))throw new IllegalStateException("Use the authenticated checkout fixture API");
    }
    static final class Principal {
        final String id,username;
        final CashierSecurity.Role role;
        final long revision;
        Principal(String id,String username,CashierSecurity.Role role,long revision){this.id=id;this.username=username;this.role=role;this.revision=revision;}
    }
    private static final class Account {
        final Principal principal;
        final String salt,hash;
        final boolean active;
        final int failures;
        final long lockedUntil;
        Account(Cursor c){
            principal=new Principal(str(c,"id"),str(c,"username"),CashierSecurity.Role.valueOf(str(c,"role")),num(c,"revision"));
            salt=str(c,"salt");hash=str(c,"password_hash");active=num(c,"active")==1;
            failures=(int)num(c,"failures");lockedUntil=num(c,"locked_until");
            if(num(c,"iterations")!=CashierSecurity.ITERATIONS)throw new IllegalStateException("Unsupported credential version");
        }
    }
    private static Account account(SQLiteDatabase db,String field,String value){
        try(Cursor c=db.query("cashier_accounts",null,field+"=?",new String[]{value},null,null,null)){
            return c.moveToFirst()?new Account(c):null;
        }
    }
    static Principal bootstrap(SQLiteDatabase db,String username,char[] password,long now){
        if(initialized(db)||exists(db,"SELECT 1 FROM checkout_requests LIMIT 1",null))
            throw new IllegalStateException("Setup requires a new fixture with no prior checkout journal");
        return createAccount(db,username,password,CashierSecurity.Role.OWNER,now);
    }
    static Principal createCashier(SQLiteDatabase db,Principal owner,String username,char[] password,long now){
        if(owner.role!=CashierSecurity.Role.OWNER)throw new IllegalStateException("Owner authorization required");
        return createAccount(db,username,password,CashierSecurity.Role.CASHIER,now);
    }
    private static Principal createAccount(SQLiteDatabase db,String username,char[] password,CashierSecurity.Role role,long now){
        String normalized=CashierSecurity.username(username);
        if(account(db,"username",normalized)!=null)throw new IllegalArgumentException("Username unavailable");
        CashierSecurity.Credential credentials=CashierSecurity.createCredential(password);
        String id=UUID.randomUUID().toString();
        ContentValues v=new ContentValues();v.put("id",id);v.put("username",normalized);v.put("role",role.name());
        v.put("salt",credentials.saltHex);v.put("password_hash",credentials.hashHex);v.put("iterations",CashierSecurity.ITERATIONS);
        v.put("revision",1);v.put("active",1);v.put("failures",0);v.put("locked_until",0L);v.put("created_ms",now);
        db.insertOrThrow("cashier_accounts",null,v);return new Principal(id,normalized,role,1);
    }
    /** Returns null for any refused sign-in. Persist failed-attempt count before reporting denial. */
    static String signIn(SQLiteDatabase db,String username,char[] password,long now){
        String normalized;
        try{normalized=CashierSecurity.username(username);}catch(IllegalArgumentException ex){normalized="";}
        Account a=account(db,"username",normalized);
        boolean verified=CashierSecurity.verify(password,a==null?"00000000000000000000000000000000":a.salt,
            a==null?"0000000000000000000000000000000000000000000000000000000000000000":a.hash);
        if(a==null||!a.active||now<a.lockedUntil)return null;
        ContentValues update=new ContentValues();
        if(!verified){
            CashierSecurity.Failure next=CashierSecurity.failedAttempt(a.failures,a.lockedUntil,now);
            update.put("failures",next.count);update.put("locked_until",next.lockedUntil);
            if(db.update("cashier_accounts",update,"id=?",new String[]{a.principal.id})!=1)throw new IllegalStateException("Login state changed");
            return null;
        }
        update.put("failures",0);update.put("locked_until",0L);
        if(db.update("cashier_accounts",update,"id=?",new String[]{a.principal.id})!=1)throw new IllegalStateException("Login state changed");
        String token=CashierSecurity.newToken();
        ContentValues session=new ContentValues();session.put("token_hash",CashierSecurity.tokenHash(token));session.put("account_id",a.principal.id);
        session.put("account_revision",a.principal.revision);session.put("issued_ms",now);session.put("expires_ms",CashierSecurity.sessionExpiry(now));
        db.insertOrThrow("cashier_sessions",null,session);return token;
    }
    static Principal requireSession(SQLiteDatabase db,String token,long now){
        String hash=CashierSecurity.tokenHash(token);
        try(Cursor c=db.rawQuery("SELECT s.account_id,s.account_revision,s.issued_ms,s.expires_ms,s.revoked_ms,a.username,a.role,a.revision,a.active FROM cashier_sessions s JOIN cashier_accounts a ON a.id=s.account_id WHERE s.token_hash=?",new String[]{hash})){
            if(!c.moveToFirst()||c.getLong(1)!=c.getLong(7)||!CashierSecurity.validSession(c.getLong(2),c.getLong(3),now,!c.isNull(4),c.getInt(8)==1))
                throw new IllegalStateException("Sign in required");
            return new Principal(c.getString(0),c.getString(5),CashierSecurity.Role.valueOf(c.getString(6)),c.getLong(7));
        }
    }
    static void signOut(SQLiteDatabase db,String token,long now){
        requireSession(db,token,now);
        ContentValues v=new ContentValues();v.put("revoked_ms",now);
        if(db.update("cashier_sessions",v,"token_hash=? AND revoked_ms IS NULL",new String[]{CashierSecurity.tokenHash(token)})!=1)
            throw new IllegalStateException("Session changed");
    }
    static void disableCashier(SQLiteDatabase db,Principal owner,String id){
        if(owner.role!=CashierSecurity.Role.OWNER)throw new IllegalStateException("Owner authorization required");
        Account a=account(db,"id",id);
        if(a==null||!a.active||a.principal.role!=CashierSecurity.Role.CASHIER)throw new IllegalStateException("Active cashier required");
        if(exists(db,"SELECT 1 FROM cashier_shifts WHERE operator_id=? AND state='OPEN'",new String[]{id}))
            throw new IllegalStateException("Close the cashier shift before disabling the account");
        ContentValues v=new ContentValues();v.put("active",0);v.put("revision",Math.addExact(a.principal.revision,1));
        if(db.update("cashier_accounts",v,"id=? AND active=1",new String[]{id})!=1)throw new IllegalStateException("Account changed");
    }

    /** Verify the active operator's current credential; denial is returned to commit lockout state.
     * Session authorization is performed by the fixture transaction before this method is reached.
     */
    private static boolean confirmCredential(SQLiteDatabase db,Account a,Principal actor,char[] current,long now){
        if(a==null||!a.active||a.principal.revision!=actor.revision||now<a.lockedUntil)return false;
        boolean verified=CashierSecurity.verify(current,a.salt,a.hash);
        ContentValues v=new ContentValues();
        if(verified){v.put("failures",0);v.put("locked_until",0L);}
        else{
            CashierSecurity.Failure next=CashierSecurity.failedAttempt(a.failures,a.lockedUntil,now);
            v.put("failures",next.count);v.put("locked_until",next.lockedUntil);
        }
        if(db.update("cashier_accounts",v,"id=? AND revision=? AND active=1",new String[]{actor.id,Long.toString(actor.revision)})!=1)
            throw new IllegalStateException("Credential state changed");
        return verified;
    }
    private static void rotateCredential(SQLiteDatabase db,Account target,Principal actor,char[] replacement,String action,long now){
        if(CashierSecurity.verify(replacement,target.salt,target.hash))
            throw new IllegalArgumentException("Choose a different synthetic passphrase");
        CashierSecurity.Credential fresh=CashierSecurity.createCredential(replacement);
        CredentialJournal.append(db,target.principal,actor,action,now);
        ContentValues v=new ContentValues();v.put("salt",fresh.saltHex);v.put("password_hash",fresh.hashHex);
        v.put("revision",Math.addExact(target.principal.revision,1));v.put("failures",0);v.put("locked_until",0L);
        if(db.update("cashier_accounts",v,"id=? AND revision=? AND active=1",new String[]{target.principal.id,Long.toString(target.principal.revision)})!=1)
            throw new IllegalStateException("Credential state changed");
        // v8 trigger revokes every target session in the same transaction. No shift or checkout write.
    }
    static boolean changeOwnCredential(SQLiteDatabase db,Principal actor,char[] current,char[] replacement,long now){
        CashierSecurity.validatePassword(replacement);
        Account target=account(db,"id",actor.id);
        if(!confirmCredential(db,target,actor,current,now))return false;
        rotateCredential(db,target,actor,replacement,"SELF_CHANGE",now);return true;
    }
    static boolean resetCashierCredential(SQLiteDatabase db,Principal actor,char[] current,String username,char[] replacement,long now){
        if(actor.role!=CashierSecurity.Role.OWNER)throw new IllegalStateException("Owner authorization required");
        String normalized=CashierSecurity.username(username);CashierSecurity.validatePassword(replacement);
        if(!confirmCredential(db,account(db,"id",actor.id),actor,current,now))return false;
        Account target=account(db,"username",normalized);
        if(target==null||!target.active||target.principal.role!=CashierSecurity.Role.CASHIER)
            throw new IllegalStateException("Active cashier required");
        rotateCredential(db,target,actor,replacement,"OWNER_RESET",now);return true;
    }
    static final class Shift {
        final String id,operatorId,state;
        final long openingCash,openedMs;
        final Long expectedCash,countedCash,variance,closedMs;
        private Shift(Cursor c){
            id=str(c,"id");operatorId=str(c,"operator_id");state=str(c,"state");
            openingCash=num(c,"opening_cash");openedMs=num(c,"opened_ms");
            expectedCash=nullable(c,"expected_cash");countedCash=nullable(c,"counted_cash");variance=nullable(c,"variance");closedMs=nullable(c,"closed_ms");
        }
        private static Long nullable(Cursor c,String n){int i=c.getColumnIndexOrThrow(n);return c.isNull(i)?null:c.getLong(i);}
    }
    static Shift shift(SQLiteDatabase db,String id){
        try(Cursor c=db.query("cashier_shifts",null,"id=?",new String[]{id},null,null,null)){return c.moveToFirst()?new Shift(c):null;}
    }
    static Shift activeShift(SQLiteDatabase db,Principal operator){
        try(Cursor c=db.query("cashier_shifts",null,"operator_id=? AND state='OPEN'",new String[]{operator.id},null,null,null)){return c.moveToFirst()?new Shift(c):null;}
    }
    static Shift requireShift(SQLiteDatabase db,Principal operator,String id,boolean open){
        StockMath.validateRequestId(id);Shift s=shift(db,id);
        if(s==null||!s.operatorId.equals(operator.id)||(open&&!"OPEN".equals(s.state)))throw new IllegalStateException("Operator's open shift required");
        return s;
    }
    static Shift openShift(SQLiteDatabase db,Principal operator,String id,long opening,long now){
        StockMath.validateRequestId(id);ShiftMath.money(opening);
        Shift prior=shift(db,id);
        if(prior!=null){
            if(!operator.id.equals(prior.operatorId)||opening!=prior.openingCash)throw new IllegalStateException("Shift identity already used for different request");
            return prior;
        }
        if(exists(db,"SELECT 1 FROM cashier_shifts WHERE operator_id=? AND state='OPEN'",new String[]{operator.id}))
            throw new IllegalStateException("Operator already has an open shift");
        ContentValues v=new ContentValues();v.put("id",id);v.put("operator_id",operator.id);v.put("opened_ms",now);
        v.put("opening_cash",opening);v.put("state","OPEN");db.insertOrThrow("cashier_shifts",null,v);return shift(db,id);
    }
    static long expected(SQLiteDatabase db,Shift s){
        long result=s.openingCash;
        try(Cursor c=db.rawQuery("SELECT c.paid FROM cashier_shift_checkouts l JOIN checkout_cash c ON l.request_id=c.request_id JOIN checkout_requests r ON r.request_id=l.request_id WHERE l.shift_id=? AND r.state='APPLIED'",new String[]{s.id})){
            while(c.moveToNext())result=ShiftMath.addPayment(result,c.getLong(0));
        }
        return result;
    }
    static Shift closeShift(SQLiteDatabase db,Principal operator,String id,long counted,long now){
        ShiftMath.money(counted);Shift s=requireShift(db,operator,id,false);
        if("CLOSED".equals(s.state)){
            if(s.countedCash==null||s.countedCash!=counted)throw new IllegalStateException("Closed shift count differs");
            return s;
        }
        if(exists(db,"SELECT 1 FROM cashier_shift_checkouts l JOIN checkout_requests r ON r.request_id=l.request_id WHERE l.shift_id=? AND r.state='PENDING'",new String[]{id}))
            throw new IllegalStateException("Resolve pending checkout before closing shift");
        long expected=expected(db,s);
        ContentValues v=new ContentValues();v.put("state","CLOSED");v.put("closed_ms",now);
        v.put("expected_cash",expected);v.put("counted_cash",counted);v.put("variance",ShiftMath.variance(expected,counted));
        if(db.update("cashier_shifts",v,"id=? AND state='OPEN'",new String[]{id})!=1)throw new IllegalStateException("Shift changed");
        return shift(db,id);
    }
    static void link(SQLiteDatabase db,String request,Shift shift){
        ContentValues v=new ContentValues();v.put("request_id",request);v.put("shift_id",shift.id);v.put("operator_id",shift.operatorId);
        db.insertOrThrow("cashier_shift_checkouts",null,v);
    }
    static Shift requireCheckoutLink(SQLiteDatabase db,Principal operator,String request,boolean open){
        try(Cursor c=db.query("cashier_shift_checkouts",new String[]{"shift_id","operator_id"},"request_id=?",new String[]{request},null,null,null)){
            if(!c.moveToFirst()||!operator.id.equals(c.getString(1)))throw new IllegalStateException("Checkout belongs to another operator or unauthenticated fixture");
            return requireShift(db,operator,c.getString(0),open);
        }
    }
}
