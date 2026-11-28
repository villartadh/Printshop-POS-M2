"""Host SQLite DDL/authorization and shift invariants, not installed Android acceptance."""
import re
import sqlite3
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "app/src/main/java/ph/printshop/pos"


def statements(name):
    return re.findall(r'db\.execSQL\("(CREATE (?:TABLE|(?:UNIQUE )?INDEX|TRIGGER) [^"]+)"\);',
                      (root / name).read_text())


base, checkout, cashier = [statements(name) for name in ("CatalogStore.java","CheckoutJournal.java","CashierJournal.java")]
assert len(base)==12 and len(checkout)==22 and len(cashier)==20
checks=0


def check(ok, label):
    global checks
    assert ok,label
    checks+=1


def reject(db, sql, args=()):
    try:
        db.execute(sql,args)
    except sqlite3.IntegrityError:
        check(True,sql)
    else:
        raise AssertionError("Unexpected acceptance: "+sql)


def add_account(db,id,name,role):
    db.execute("INSERT INTO cashier_accounts VALUES(?,?,?,?,?,?,?,?,?,?,?)",
               (id,name,role,"a"*32,"b"*64,600000,1,1,0,0,1))


def stage(db,id):
    db.execute("INSERT INTO checkout_requests VALUES("+",".join("?" for _ in range(30))+")",
               (id,1,"a"*64,"s","Synthetic service","page",1,0,0,"PER_UNIT","NONE",2,1,1,None,None,None,200,2,250,250,500,0,600,100,0,1,"PENDING",None,None))


def sale(db,id):
    db.execute("INSERT INTO checkout_sales VALUES(?,2)",(id,))
    db.execute("INSERT INTO checkout_cash VALUES(?,500,600,100)",(id,))
    db.execute("UPDATE checkout_requests SET state='APPLIED',resolved_ms=2 WHERE request_id=?",(id,))


for former in (1,2,3,4,5,6):
    with sqlite3.connect(":memory:") as db:
        db.execute("PRAGMA foreign_keys=ON")
        original={1:2,2:3,3:5,4:7,5:12,6:12}[former]
        for sql in base[:original]+(checkout if former==6 else []):
            db.execute(sql)
        db.execute("INSERT INTO catalog VALUES('s','SERVICE','Synthetic service','page',250,1,0)")
        db.execute("INSERT INTO catalog_history VALUES('s',1,'SERVICE','Synthetic service','page',250,0,'CREATE')")
        if former==6:
            stage(db,"historic")
            sale(db,"historic")
        for sql in base[original:]+([] if former==6 else checkout)+cashier:
            db.execute(sql)
        check(db.execute("SELECT COUNT(*) FROM catalog").fetchone()==(1,),"catalog retained")
        check(db.execute("SELECT COUNT(*) FROM checkout_sales").fetchone()==(int(former==6),),"v6 sale retained")
        check(db.execute("SELECT COUNT(*) FROM cashier_accounts").fetchone()==(0,),"no default credentials")
        add_account(db,"owner","owner-one","OWNER")
        add_account(db,"cashier","cashier-one","CASHIER")
        add_account(db,"staff","cashier-two","CASHIER")
        reject(db,"INSERT INTO cashier_accounts VALUES('owner2','owner-two','OWNER',?,?,600000,1,1,0,0,1)",("a"*32,"b"*64))
        reject(db,"INSERT OR REPLACE INTO cashier_accounts VALUES('cashier','cashier-one','CASHIER',?,?,600000,1,1,0,0,1)",("a"*32,"c"*64))
        reject(db,"UPDATE cashier_accounts SET role='OWNER' WHERE id='cashier'")
        reject(db,"UPDATE cashier_accounts SET failures=0.5 WHERE id='cashier'")
        reject(db,"UPDATE cashier_accounts SET active=0,revision=2 WHERE id='owner'")
        reject(db,"DELETE FROM cashier_accounts WHERE id='cashier'")
        db.execute("INSERT INTO cashier_sessions VALUES(?,?,1,1,28800001,NULL)",("c"*64,"cashier"))
        reject(db,"INSERT INTO cashier_sessions VALUES(?,?,1,1,28800002,NULL)",("d"*64,"cashier"))
        reject(db,"UPDATE cashier_sessions SET expires_ms=9 WHERE token_hash=?",("c"*64,))
        db.execute("UPDATE cashier_sessions SET revoked_ms=2 WHERE token_hash=?",("c"*64,))
        reject(db,"UPDATE cashier_sessions SET revoked_ms=NULL WHERE token_hash=?",("c"*64,))
        reject(db,"DELETE FROM cashier_sessions WHERE token_hash=?",("c"*64,))
        db.execute("INSERT INTO cashier_shifts VALUES('shift','cashier',1,1000,'OPEN',NULL,NULL,NULL,NULL)")
        db.execute("INSERT INTO cashier_shifts VALUES('other-shift','staff',1,2000,'OPEN',NULL,NULL,NULL,NULL)")
        reject(db,"UPDATE cashier_shifts SET state='CLOSED',closed_ms=2 WHERE id='other-shift'")
        reject(db,"UPDATE cashier_shifts SET state='CLOSED',closed_ms=2,expected_cash=2000,counted_cash=2000.5,variance=0.5 WHERE id='other-shift'")
        reject(db,"INSERT INTO cashier_shifts VALUES('duplicate','cashier',1,1000,'OPEN',NULL,NULL,NULL,NULL)")
        reject(db,"UPDATE cashier_accounts SET active=0,revision=2 WHERE id='cashier'")
        stage(db,"r");stage(db,"pending");stage(db,"unlinked")
        reject(db,"INSERT INTO cashier_shift_checkouts VALUES('r','other-shift','cashier')")
        db.execute("INSERT INTO cashier_shift_checkouts VALUES('r','shift','cashier')")
        db.execute("INSERT INTO cashier_shift_checkouts VALUES('pending','shift','cashier')")
        reject(db,"UPDATE cashier_shift_checkouts SET shift_id='other-shift' WHERE request_id='r'")
        reject(db,"DELETE FROM cashier_shift_checkouts WHERE request_id='r'")
        reject(db,"UPDATE cashier_shifts SET state='CLOSED',closed_ms=2,expected_cash=1000,counted_cash=1000,variance=0 WHERE id='shift'")
        db.commit()
        try:
            with db:
                sale(db,"unlinked")
        except sqlite3.IntegrityError:
            check(True,"unlinked sale rolled back")
        else:
            raise AssertionError("Unlinked sale accepted")
        check(db.execute("SELECT COUNT(*) FROM checkout_sales WHERE request_id='unlinked'").fetchone()==(0,),"unlinked sale absent")
        with db:
            sale(db,"r")
        reject(db,"UPDATE cashier_shifts SET state='CLOSED',closed_ms=2,expected_cash=1500,counted_cash=1500,variance=0 WHERE id='shift'")
        db.execute("UPDATE checkout_requests SET state='CANCELLED',resolved_ms=3,cancel_reason='Synthetic cancelled' WHERE request_id='pending'")
        reject(db,"UPDATE cashier_shifts SET state='CLOSED',closed_ms=3,expected_cash=1600,counted_cash=1500,variance=-100 WHERE id='shift'")
        db.execute("UPDATE cashier_shifts SET state='CLOSED',closed_ms=3,expected_cash=1500,counted_cash=1450,variance=-50 WHERE id='shift'")
        check(db.execute("SELECT expected_cash,counted_cash,variance FROM cashier_shifts WHERE id='shift'").fetchone()==(1500,1450,-50),"net cash not tender")
        reject(db,"UPDATE cashier_shifts SET state='OPEN',closed_ms=NULL,expected_cash=NULL,counted_cash=NULL,variance=NULL WHERE id='shift'")
        reject(db,"INSERT INTO cashier_shift_checkouts VALUES('unlinked','shift','cashier')")
        reject(db,"DELETE FROM cashier_shifts WHERE id='shift'")
        db.execute("UPDATE cashier_accounts SET active=0,revision=2 WHERE id='cashier'")
        reject(db,"INSERT INTO cashier_sessions VALUES(?,?,2,2,28800002,NULL)",("e"*64,"cashier"))
        reject(db,"INSERT INTO cashier_shifts VALUES('new','cashier',3,0,'OPEN',NULL,NULL,NULL,NULL)")
        reject(db,"UPDATE cashier_accounts SET active=1,revision=3 WHERE id='cashier'")
        check(db.execute("PRAGMA foreign_key_check").fetchall()==[],"foreign key integrity")
print(f"CASHIER_JOURNAL_SCHEMA_PASS: {checks} checks across v1-v6 fixtures")
