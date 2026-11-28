"""Host-side SQLite DDL/atomicity checks, not Android persistence acceptance."""
import re
import sqlite3
from pathlib import Path

store = Path(__file__).resolve().parents[1] / "app/src/main/java/ph/printshop/pos/CatalogStore.java"
ddl = re.findall(r'db\.execSQL\("(CREATE (?:TABLE|UNIQUE INDEX|TRIGGER) [^"]+)"\);', store.read_text())
assert len(ddl) == 12, "Review migration fixtures after any schema change"
base, recovery = ddl[:7], ddl[7:]
checks = 0


def check(condition, message):
    global checks
    assert condition, message
    checks += 1


def reject(db, statement, args=()):
    try:
        db.execute(statement, args)
    except sqlite3.IntegrityError:
        check(True, "constraint rejected")
    else:
        raise AssertionError("invalid statement accepted: " + statement)


for former in (1, 2, 3, 4):
    with sqlite3.connect(":memory:") as db:
        db.execute("PRAGMA foreign_keys=ON")
        original = {1: 2, 2: 3, 3: 5, 4: 7}[former]
        for sql in base[:original]:
            db.execute(sql)
        db.execute("INSERT INTO catalog VALUES('m','MATERIAL','Synthetic','sheet',199,1,0)")
        db.execute("INSERT INTO catalog_history VALUES('m',1,'MATERIAL','Synthetic','sheet',199,0,'CREATE')")
        if former >= 2:
            db.execute("INSERT INTO stock_movements VALUES('old','m',1,1,'sheet','Old movement',1500,0,1500,1)")
        for sql in base[original:] + recovery:
            db.execute(sql)
        check(db.execute("SELECT centavos FROM catalog WHERE id='m'").fetchone() == (199,), "catalog preserved")
        check(db.execute("SELECT COUNT(*) FROM catalog_history").fetchone() == (1,), "history preserved")
        check(db.execute("SELECT COUNT(*) FROM stock_movements").fetchone() == (int(former >= 2),), "stock preserved")
        revision = int(former >= 2)
        db.execute("INSERT INTO stock_requests VALUES('request','m',1,?,500,'Synthetic pending',1,'PENDING',NULL)", (revision,))
        db.commit()
        reject(db, "INSERT INTO stock_requests VALUES('conflict','m',1,?,500,'Conflict',1,'PENDING',NULL)", (revision,))
        reject(db, "INSERT INTO stock_requests VALUES('bad','missing',1,0,500,'Invalid material',1,'PENDING',NULL)")
        reject(db, "INSERT INTO stock_requests VALUES('bad','m',1,0,0,'Zero',1,'CANCELLED',2)")
        reject(db, "INSERT INTO stock_requests VALUES('bad','m',1,0,500,'No resolution',1,'APPLIED',NULL)")
        reject(db, "UPDATE stock_requests SET resolved_ms=2 WHERE request_id='request'")
        reject(db, "UPDATE stock_requests SET reason='Changed' WHERE request_id='request'")
        reject(db, "UPDATE stock_requests SET state='APPLIED',resolved_ms=2 WHERE request_id='request'")
        reject(db, "DELETE FROM stock_requests WHERE request_id='request'")
        db.commit()
        before = 1500 if former >= 2 else 0
        try:
            with db:
                db.execute("INSERT INTO stock_movements VALUES('request','m',1,?,'sheet','Synthetic pending',500,?,?,2)", (revision+1,before,before+500))
                # Constraint failure after ledger insert must roll back both.
                db.execute("UPDATE stock_requests SET state='APPLIED' WHERE request_id='request'")
        except sqlite3.IntegrityError:
            pass
        else:
            raise AssertionError("missing resolution time accepted")
        check(db.execute("SELECT COUNT(*) FROM stock_movements WHERE request_id='request'").fetchone() == (0,), "ledger rollback")
        check(db.execute("SELECT state FROM stock_requests WHERE request_id='request'").fetchone() == ("PENDING",), "pending preserved")
        with db:
            db.execute("INSERT INTO stock_movements VALUES('request','m',1,?,'sheet','Synthetic pending',500,?,?,2)", (revision+1,before,before+500))
            db.execute("UPDATE stock_requests SET state='APPLIED',resolved_ms=2 WHERE request_id='request'")
        check(db.execute("SELECT after_quantity FROM stock_movements WHERE request_id='request'").fetchone() == (before+500,), "applied balance")
        reject(db, "UPDATE stock_requests SET state='CANCELLED',resolved_ms=4 WHERE request_id='request'")
        db.execute("INSERT INTO stock_requests VALUES('cancel','m',1,?,500,'Cancel',3,'PENDING',NULL)", (revision+1,))
        db.execute("UPDATE stock_requests SET state='CANCELLED',resolved_ms=4 WHERE request_id='cancel'")
        check(db.execute("SELECT COUNT(*) FROM stock_movements WHERE request_id='cancel'").fetchone() == (0,), "cancel did not move stock")
        reject(db, "INSERT INTO stock_requests VALUES('cancel','m',1,0,500,'Reuse cancelled identity',5,'PENDING',NULL)")
        reject(db, "UPDATE stock_requests SET state='PENDING',resolved_ms=NULL WHERE request_id='cancel'")
        check(db.execute("SELECT COUNT(*) FROM stock_requests").fetchone() == (2,), "audit rows preserved")
print(f"STOCK_REQUEST_SCHEMA_PASS: {checks} checks across v1-v4 fixtures")
