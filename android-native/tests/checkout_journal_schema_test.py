"""Host SQLite DDL/atomicity fixture tests, not Android functional acceptance."""
import re
import sqlite3
import tempfile
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "app/src/main/java/ph/printshop/pos"


def statements(filename):
    return re.findall(r'db\.execSQL\("(CREATE (?:TABLE|(?:UNIQUE )?INDEX|TRIGGER) [^"]+)"\);',
                      (root / filename).read_text())


base = statements("CatalogStore.java")
journal = statements("CheckoutJournal.java")
assert len(base) == 12 and len(journal) == 22, "Review schema fixture coverage"
checks = 0
COLUMNS = ("request_id,fingerprint_version,fingerprint,service_id,service_name,service_unit,service_revision,"
           "pricing_revision,recipe_revision,method,basis,input_count,pages,copies,width,height,dimension_unit,"
           "billable_hundredths,produced_items,base_rate,rate,total,material_cost,received,change_due,material_count,"
           "created_ms,state,resolved_ms,cancel_reason")
INSERT = f"INSERT INTO checkout_requests ({COLUMNS}) VALUES ({','.join('?' for _ in range(len(COLUMNS.split(','))))})"


def check(ok, label):
    global checks
    assert ok, label
    checks += 1


def reject(db, sql, args=()):
    try:
        db.execute(sql, args)
    except sqlite3.IntegrityError:
        check(True, sql)
    else:
        raise AssertionError("Invalid mutation accepted: " + sql)


def request(id, **changes):
    row = [id, 1, "a"*64, "s", "Synthetic service", "page", 1, 1, 1, "PER_PAGE", "BILLABLE_UNIT",
           1, 5, 2, None, None, None, 1000, 2, 250, 225, 2250, 995, 2500, 250, 1, 1, "PENDING", None, None]
    for key, value in changes.items():
        row[COLUMNS.split(",").index(key)] = value
    return row


def stage(db, id):
    db.execute(INSERT, request(id))
    db.execute("INSERT INTO checkout_materials VALUES(?,?,?,?,?,?,?,?,?,?,?)",
               (id,"m","Synthetic paper","sheet",1,1,500,5000,5000,0,199))


def effect(db, id, movement):
    db.execute("INSERT INTO checkout_sales VALUES(?,2)", (id,))
    db.execute("INSERT INTO checkout_cash VALUES(?,2250,2500,250)", (id,))
    db.execute("INSERT INTO stock_movements VALUES(?,?,?,?,?,?,?,?,?,?)",
               (movement,"m",1,2,"sheet","Preview checkout "+id,-5000,5000,0,2))
    db.execute("INSERT INTO checkout_stock_links VALUES(?,?,?)", (id,"m",movement))
    db.execute("UPDATE checkout_requests SET state='APPLIED',resolved_ms=2 WHERE request_id=?", (id,))


def connect(file):
    db = sqlite3.connect(file)
    db.execute("PRAGMA foreign_keys=ON")
    return db


def count(db, table, id):
    return db.execute(f"SELECT COUNT(*) FROM {table} WHERE request_id=?", (id,)).fetchone()[0]


for former in (1, 2, 3, 4, 5):
    with tempfile.TemporaryDirectory() as folder:
        file = Path(folder) / "checkout.db"
        db = connect(file)
        original = {1: 2, 2: 3, 3: 5, 4: 7, 5: 12}[former]
        for sql in base[:original]:
            db.execute(sql)
        db.execute("INSERT INTO catalog VALUES('m','MATERIAL','Synthetic paper','sheet',199,1,0)")
        db.execute("INSERT INTO catalog VALUES('s','SERVICE','Synthetic service','page',250,1,0)")
        db.execute("INSERT INTO catalog_history VALUES('m',1,'MATERIAL','Synthetic paper','sheet',199,0,'CREATE')")
        if former >= 2:
            db.execute("INSERT INTO stock_movements VALUES('old','m',1,1,'sheet','Old movement',5000,0,5000,1)")
        if former >= 3:
            db.execute("INSERT INTO service_materials VALUES('s','m',500,'sheet')")
            db.execute("INSERT INTO recipe_history VALUES('s',1,'m',500,'sheet',1,1,1)")
        if former >= 4:
            db.execute("INSERT INTO pricing_profile_versions VALUES('s',1,'PER_PAGE',1,1,'page',1)")
            db.execute("INSERT INTO pricing_tier_versions VALUES('s',1,1000,225)")
        if former == 5:
            db.execute("INSERT INTO stock_requests VALUES('legacy-pending','m',1,1,-1,'Old pending',1,'PENDING',NULL)")
        db.commit()
        with db:
            for sql in base[original:]+journal:
                db.execute(sql)
        check(db.execute("SELECT COUNT(*) FROM catalog").fetchone()[0]==2,"catalog preserved")
        check(db.execute("SELECT COUNT(*) FROM catalog_history").fetchone()[0]==1,"history preserved")
        check(db.execute("SELECT COUNT(*) FROM stock_movements").fetchone()[0]==int(former>=2),"stock preserved")
        check(db.execute("SELECT COUNT(*) FROM recipe_history").fetchone()[0]==int(former>=3),"recipes preserved")
        check(db.execute("SELECT COUNT(*) FROM pricing_tier_versions").fetchone()[0]==int(former>=4),"pricing preserved")
        check(db.execute("SELECT COUNT(*) FROM stock_requests").fetchone()[0]==int(former>=5),"pending inventory preserved")
        if former<2:
            db.execute("INSERT INTO stock_movements VALUES('old','m',1,1,'sheet','Old movement',5000,0,5000,1)")
        if former==5:
            db.execute("UPDATE stock_requests SET state='CANCELLED',resolved_ms=2 WHERE request_id='legacy-pending'")
        reject(db,INSERT,request("bad",state="APPLIED",resolved_ms=2))
        reject(db,INSERT,request("bad",total=2251))
        reject(db,INSERT,request("bad",change_due=251))
        reject(db,INSERT,request("bad",fingerprint="oops"))
        reject(db,INSERT,request("bad",received=2249))
        reject(db,INSERT,request("bad",received=2500.5,change_due=250.5))
        reject(db,INSERT,request("bad",material_cost=995.5))
        stage(db,"r");stage(db,"other")
        reject(db,INSERT,request("r"))
        reject(db,INSERT.replace("INSERT INTO","INSERT OR REPLACE INTO"),request("r"))
        reject(db,"UPDATE checkout_requests SET fingerprint=? WHERE request_id='r'",("b"*64,))
        reject(db,"DELETE FROM checkout_requests WHERE request_id='r'")
        reject(db,"UPDATE checkout_materials SET after_milli=1 WHERE request_id='r'")
        reject(db,"UPDATE checkout_requests SET state='APPLIED',resolved_ms=2 WHERE request_id='r'")
        reject(db,"INSERT INTO checkout_stock_links VALUES('r','m','old')")
        check(count(db,"checkout_sales","r")==0 and count(db,"checkout_cash","r")==0,"stage nonmutating")
        db.commit();db.close()
        db=connect(file)
        check(db.execute("SELECT state,received FROM checkout_requests WHERE request_id='r'").fetchone()==("PENDING",2500),"pending reopened")
        # Error injected after sale/cash/movement/link writes, before terminal commit.
        db.execute("CREATE TRIGGER fixture_abort BEFORE UPDATE ON checkout_requests WHEN NEW.state='APPLIED' BEGIN SELECT RAISE(ABORT,'synthetic rollback'); END")
        try:
            with db:
                effect(db,"r","movement")
        except sqlite3.IntegrityError:
            check(True,"injected failure")
        else:
            raise AssertionError("injected failure did not roll back")
        check(count(db,"checkout_sales","r")==0 and count(db,"checkout_cash","r")==0 and count(db,"checkout_stock_links","r")==0,"atomic financial rollback")
        check(count(db,"stock_movements","movement")==0,"atomic stock rollback")
        check(db.execute("SELECT state FROM checkout_requests WHERE request_id='r'").fetchone()==("PENDING",),"retry preserved")
        db.execute("DROP TRIGGER fixture_abort")
        with db:
            effect(db,"r","movement")
        check(count(db,"checkout_sales","r")==1 and count(db,"checkout_cash","r")==1 and count(db,"checkout_stock_links","r")==1,"exact sale/payment/link")
        check(db.execute("SELECT after_quantity FROM stock_movements WHERE request_id='movement'").fetchone()==(0,),"exact deduction")
        reject(db,"UPDATE checkout_requests SET state='PENDING',resolved_ms=NULL WHERE request_id='r'")
        reject(db,"UPDATE checkout_requests SET state='CANCELLED',resolved_ms=5,cancel_reason='Invalid' WHERE request_id='r'")
        reject(db,"UPDATE checkout_cash SET paid=1 WHERE request_id='r'")
        reject(db,"INSERT OR REPLACE INTO checkout_cash VALUES('r',2250,2500,250)")
        reject(db,INSERT.replace("INSERT INTO","INSERT OR REPLACE INTO"),request("r"))
        reject(db,"DELETE FROM checkout_sales WHERE request_id='r'")
        reject(db,"UPDATE stock_movements SET reason='Tampered' WHERE request_id='movement'")
        reject(db,"INSERT INTO checkout_sales VALUES('r',3)")
        reject(db,"INSERT INTO checkout_stock_links VALUES('other','m','movement')")
        # Competing stale snapshot cannot append the same stock revision.
        db.commit()
        try:
            with db:
                effect(db,"other","competing")
        except sqlite3.IntegrityError:
            check(True,"duplicate stock revision rejected")
        else:
            raise AssertionError("competing checkout oversold stock")
        check(count(db,"checkout_sales","other")==0 and count(db,"checkout_cash","other")==0,"competing rollback")
        db.execute("UPDATE checkout_requests SET state='CANCELLED',resolved_ms=3,cancel_reason='Synthetic cancellation' WHERE request_id='other'")
        reject(db,"INSERT INTO checkout_sales VALUES('other',4)")
        reject(db,"UPDATE checkout_requests SET state='PENDING',resolved_ms=NULL,cancel_reason=NULL WHERE request_id='other'")
        db.commit();db.close()
        db=connect(file)
        check(db.execute("SELECT state,total,received,change_due FROM checkout_requests WHERE request_id='r'").fetchone()==("APPLIED",2250,2500,250),"historical replay data")
        check(db.execute("SELECT state,cancel_reason FROM checkout_requests WHERE request_id='other'").fetchone()==("CANCELLED","Synthetic cancellation"),"cancel audit reopened")
        check(db.execute("PRAGMA foreign_key_check").fetchall()==[],"foreign key integrity")
        db.close()
print(f"CHECKOUT_JOURNAL_SCHEMA_PASS: {checks} checks across v1-v5 fixtures")
