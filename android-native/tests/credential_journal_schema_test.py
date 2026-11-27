"""Actual host SQLite v7->v8 credential audit and revocation invariants; not Android acceptance."""
import re
import sqlite3
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "app/src/main/java/ph/printshop/pos"


def statements(name):
    return re.findall(r'db\.execSQL\("((?:CREATE (?:TABLE|(?:UNIQUE )?INDEX|TRIGGER)|DROP TRIGGER) [^"]+)"\);',
                      (root / name).read_text())


base, checkout, cashier, credential = [
    statements(name) for name in ("CatalogStore.java", "CheckoutJournal.java", "CashierJournal.java", "CredentialJournal.java")
]
assert (len(base), len(checkout), len(cashier), len(credential)) == (12,22,20,7)
checks=0


def check(ok, label):
    global checks
    assert ok, label
    checks += 1


def reject(db, sql, args=()):
    try:
        db.execute(sql,args)
    except sqlite3.IntegrityError:
        check(True, sql)
    else:
        raise AssertionError("Unexpected acceptance: "+sql)


def account(db, aid, role):
    db.execute("INSERT INTO cashier_accounts VALUES(?,?,?,?,?,?,?,?,?,?,?)",
               (aid,aid,role,"a"*32,"b"*64,600000,1,1,0,0,1))


def audit(db, eid, target, actor, prior, action, at=5):
    db.execute("INSERT INTO cashier_credential_events VALUES(?,?,?,?,?,?,?)",
               (eid,target,actor,prior,prior+1,action,at))


def rotate(db, target, prior, salt, digest):
    assert db.execute("UPDATE cashier_accounts SET salt=?,password_hash=?,revision=?,failures=0,locked_until=0 WHERE id=? AND revision=?",
                      (salt*32,digest*64,prior+1,target,prior)).rowcount==1


for former in (1,2,3,4,5,6,7):
    with sqlite3.connect(":memory:") as db:
        db.execute("PRAGMA foreign_keys=ON")
        original={1:2,2:3,3:5,4:7,5:12,6:12,7:12}[former]
        for sql in base[:original]+(checkout if former>=6 else [])+(cashier if former==7 else []):
            db.execute(sql)
        db.execute("INSERT INTO catalog VALUES('s','SERVICE','Synthetic service','page',250,1,0)")
        db.execute("INSERT INTO catalog_history VALUES('s',1,'SERVICE','Synthetic service','page',250,0,'CREATE')")
        if former!=7:
            for sql in base[original:]+([] if former==6 else checkout)+cashier:
                db.execute(sql)
        account(db,"owner","OWNER");account(db,"cashier","CASHIER");account(db,"other","CASHIER")
        db.execute("INSERT INTO cashier_sessions VALUES(?,?,1,1,28800001,NULL)",("c"*64,"cashier"))
        db.execute("INSERT INTO cashier_sessions VALUES(?,?,1,10,28800010,NULL)",("d"*64,"cashier"))
        db.execute("INSERT INTO cashier_sessions VALUES(?,?,1,1,28800001,NULL)",("e"*64,"owner"))
        db.execute("INSERT INTO cashier_shifts VALUES('shift','cashier',1,1000,'OPEN',NULL,NULL,NULL,NULL)")
        db.execute("INSERT INTO checkout_requests VALUES("+",".join("?" for _ in range(30))+")",
                   ("pending",1,"a"*64,"s","Synthetic service","page",1,0,0,"PER_UNIT","NONE",2,1,1,None,None,None,200,2,250,250,500,0,600,100,0,1,"PENDING",None,None))
        db.execute("INSERT INTO cashier_shift_checkouts VALUES('pending','shift','cashier')")
        before=db.execute("SELECT * FROM cashier_accounts ORDER BY id").fetchall()
        sessions=db.execute("SELECT * FROM cashier_sessions ORDER BY token_hash").fetchall()
        shift=db.execute("SELECT * FROM cashier_shifts").fetchall()
        pending=db.execute("SELECT * FROM checkout_requests").fetchall()
        links=db.execute("SELECT * FROM cashier_shift_checkouts").fetchall()
        db.commit()
        with db:
            for sql in credential:db.execute(sql)
        check(db.execute("SELECT * FROM cashier_accounts ORDER BY id").fetchall()==before,"prior account rows retained")
        check(db.execute("SELECT * FROM cashier_sessions ORDER BY token_hash").fetchall()==sessions,"prior sessions retained")
        check(db.execute("SELECT COUNT(*) FROM cashier_credential_events").fetchone()==(0,),"no fabricated audit")
        reject(db,"UPDATE cashier_accounts SET salt=? WHERE id='cashier'",("f"*32,))
        reject(db,"UPDATE cashier_accounts SET salt=?,password_hash=?,revision=2 WHERE id='cashier'",("f"*32,"a"*64))
        reject(db,"UPDATE cashier_accounts SET revision=2 WHERE id='cashier'")
        reject(db,"INSERT INTO cashier_credential_events VALUES('wrong','owner','cashier',1,2,'OWNER_RESET',5)")
        reject(db,"INSERT INTO cashier_credential_events VALUES('wrong','cashier','other',1,2,'OWNER_RESET',5)")
        reject(db,"INSERT INTO cashier_credential_events VALUES('wrong','cashier','owner',1,2,'SELF_CHANGE',5)")
        reject(db,"INSERT INTO cashier_credential_events VALUES('wrong','cashier','owner',2,3,'OWNER_RESET',5)")
        reject(db,"INSERT INTO cashier_credential_events VALUES('wrong','cashier','owner',1,3,'OWNER_RESET',5)")
        reject(db,"INSERT INTO cashier_credential_events VALUES('wrong','cashier','owner',1,2,'OWNER_RESET',0)")
        db.commit()
        try:
            with db:
                audit(db,"rollback","cashier","owner",1,"OWNER_RESET")
                rotate(db,"cashier",1,"f","a")
                raise RuntimeError("synthetic lost transaction")
        except RuntimeError:
            check(db.execute("SELECT COUNT(*) FROM cashier_credential_events").fetchone()==(0,),"rollback audit")
            check(db.execute("SELECT * FROM cashier_accounts ORDER BY id").fetchall()==before,"rollback credential")
            check(db.execute("SELECT * FROM cashier_sessions ORDER BY token_hash").fetchall()==sessions,"rollback revocation")
        with db:
            audit(db,"reset","cashier","owner",1,"OWNER_RESET")
            reject(db,"UPDATE cashier_accounts SET salt=?,revision=2 WHERE id='cashier'",("f"*32,))
            reject(db,"UPDATE cashier_accounts SET salt=?,password_hash=?,revision=2,failures=1 WHERE id='cashier'",("f"*32,"a"*64))
            rotate(db,"cashier",1,"f","a")
        check(db.execute("SELECT revision,salt,password_hash,active FROM cashier_accounts WHERE id='cashier'").fetchone()==(2,"f"*32,"a"*64,1),"rotated exact identity")
        check(db.execute("SELECT revoked_ms FROM cashier_sessions WHERE account_id='cashier' ORDER BY issued_ms").fetchall()==[(5,),(10,)],"all sessions revoked even on clock rollback")
        check(db.execute("SELECT revoked_ms FROM cashier_sessions WHERE account_id='owner'").fetchone()==(None,),"owner session retained")
        reject(db,"INSERT INTO cashier_sessions VALUES(?,?,1,20,28800020,NULL)",("f"*64,"cashier"))
        db.execute("INSERT INTO cashier_sessions VALUES(?,?,2,20,28800020,NULL)",("f"*64,"cashier"))
        reject(db,"UPDATE cashier_sessions SET revoked_ms=NULL WHERE token_hash=?",("c"*64,))
        reject(db,"UPDATE cashier_credential_events SET actor_id='other' WHERE id='reset'")
        reject(db,"DELETE FROM cashier_credential_events WHERE id='reset'")
        reject(db,"INSERT OR REPLACE INTO cashier_credential_events VALUES('reset','cashier','owner',2,3,'OWNER_RESET',6)")
        reject(db,"UPDATE cashier_accounts SET username='new-cashier' WHERE id='cashier'")
        reject(db,"UPDATE cashier_accounts SET active=0,revision=3 WHERE id='cashier'")
        with db:
            audit(db,"self","cashier","cashier",2,"SELF_CHANGE",25)
            rotate(db,"cashier",2,"1","2")
        check(db.execute("SELECT revision FROM cashier_accounts WHERE id='cashier'").fetchone()==(3,),"own revision")
        check(db.execute("SELECT revoked_ms FROM cashier_sessions WHERE token_hash=?",("f"*64,)).fetchone()==(25,),"self change revoked new session")
        with db:
            audit(db,"owner-self","owner","owner",1,"SELF_CHANGE",25)
            rotate(db,"owner",1,"1","2")
        check(db.execute("SELECT revoked_ms FROM cashier_sessions WHERE account_id='owner'").fetchone()==(25,),"owner self change allowed")
        check(db.execute("SELECT * FROM cashier_shifts").fetchall()==shift,"open shift retained")
        check(db.execute("SELECT * FROM checkout_requests").fetchall()==pending,"pending immutable")
        check(db.execute("SELECT * FROM cashier_shift_checkouts").fetchall()==links,"operator identity retained")
        check(db.execute("SELECT COUNT(*) FROM checkout_sales").fetchone()==(0,),"no sale")
        check(db.execute("SELECT COUNT(*) FROM stock_movements").fetchone()==(0,),"no stock movement")
        check(db.execute("SELECT COUNT(*) FROM cashier_credential_events").fetchone()==(3,),"three audit rows")
        check(db.execute("PRAGMA foreign_key_check").fetchall()==[],"foreign key integrity")
print(f"CREDENTIAL_JOURNAL_SCHEMA_PASS: {checks} checks across v1-v7 fixtures")
