"""Actual SQLite v9 staff disable atomicity and migration invariants; not Android acceptance."""
import re
import sqlite3
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "app/src/main/java/ph/printshop/pos"


def statements(name):
    return re.findall(r'db\.execSQL\("((?:CREATE (?:TABLE|(?:UNIQUE )?INDEX|TRIGGER)|DROP TRIGGER) [^"]+)"\);',
                      (root / name).read_text())


base, checkout, cashier, credential, staff = [
    statements(n) for n in ("CatalogStore.java", "CheckoutJournal.java", "CashierJournal.java",
                           "CredentialJournal.java", "StaffJournal.java")
]
assert (len(base), len(checkout), len(cashier), len(credential), len(staff)) == (12, 22, 20, 7, 6)
checks = 0


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
        raise AssertionError("Unexpected acceptance: " + sql)


def account(db, aid, role):
    db.execute("INSERT INTO cashier_accounts VALUES(?,?,?,?,?,?,?,?,?,?,?)",
               (aid, aid, role, "a"*32, "b"*64, 600000, 1, 1, 0, 0, 1))


def event(db, eid, target="staff", actor="owner", actor_rev=1, prior=2, nxt=3,
          reason="Synthetic training complete", at=5):
    db.execute("INSERT INTO cashier_disable_events VALUES(?,?,?,?,?,?,?,?)",
               (eid, target, actor, actor_rev, prior, nxt, reason, at))


def reject_event(db, eid, target="staff", actor="owner", actor_rev=1, prior=2, nxt=3,
                 reason="Synthetic training complete", at=5):
    reject(db, "INSERT INTO cashier_disable_events VALUES(?,?,?,?,?,?,?,?)",
           (eid, target, actor, actor_rev, prior, nxt, reason, at))


def stage(db, req, operator, shift):
    db.execute("INSERT INTO checkout_requests VALUES(" + ",".join("?" for _ in range(30)) + ")",
               (req, 1, "a"*64, "service", "Synthetic service", "page", 1, 0, 0,
                "PER_UNIT", "NONE", 2, 1, 1, None, None, None, 200, 2, 250, 250, 500, 0,
                600, 100, 0, 1, "PENDING", None, None))
    db.execute("INSERT INTO cashier_shift_checkouts VALUES(?,?,?)", (req, shift, operator))


def snapshot(db, names):
    return {n: db.execute("SELECT * FROM " + n + " ORDER BY 1").fetchall() for n in names}


for former in range(1, 9):
    with sqlite3.connect(":memory:") as db:
        db.execute("PRAGMA foreign_keys=ON")
        original = {1:2, 2:3, 3:5, 4:7, 5:12, 6:12, 7:12, 8:12}[former]
        for sql in base[:original] + (checkout if former>=6 else []) + (cashier if former>=7 else []) + (credential if former>=8 else []):
            db.execute(sql)
        db.execute("INSERT INTO catalog VALUES('service','SERVICE','Synthetic service','page',250,1,0)")
        db.execute("INSERT INTO catalog_history VALUES('service',1,'SERVICE','Synthetic service','page',250,0,'CREATE')")
        db.commit()
        with db:
            for sql in base[original:] + ([] if former>=6 else checkout) + ([] if former>=7 else cashier) + ([] if former>=8 else credential):
                db.execute(sql)
        # Populate representative v8 state. For former=8 this models existing v8 rows at upgrade time.
        for aid, role in (("owner","OWNER"), ("staff","CASHIER"), ("busy","CASHIER"),
                          ("other","CASHIER"), ("legacy","CASHIER")):
            account(db, aid, role)
        db.execute("INSERT INTO cashier_credential_events VALUES('prior-reset','staff','owner',1,2,'OWNER_RESET',2)")
        db.execute("UPDATE cashier_accounts SET salt=?,password_hash=?,revision=2 WHERE id='staff'", ("f"*32,"a"*64))
        db.execute("UPDATE cashier_accounts SET active=0,revision=2 WHERE id='legacy'")
        for token, aid, rev, issued in (("c"*64,"staff",2,3), ("d"*64,"staff",2,10),
                                        ("e"*64,"owner",1,2), ("f"*64,"busy",1,2)):
            db.execute("INSERT INTO cashier_sessions VALUES(?,?,?,?,?,NULL)", (token, aid, rev, issued, issued+28800000))
        db.execute("INSERT INTO cashier_shifts VALUES('history','staff',1,1000,'OPEN',NULL,NULL,NULL,NULL)")
        stage(db,"sale","staff","history")
        db.execute("INSERT INTO checkout_sales VALUES('sale',2)")
        db.execute("INSERT INTO checkout_cash VALUES('sale',500,600,100)")
        db.execute("UPDATE checkout_requests SET state='APPLIED',resolved_ms=2 WHERE request_id='sale'")
        db.execute("UPDATE cashier_shifts SET state='CLOSED',closed_ms=2,expected_cash=1500,counted_cash=1450,variance=-50 WHERE id='history'")
        db.execute("INSERT INTO cashier_shifts VALUES('open','busy',1,1000,'OPEN',NULL,NULL,NULL,NULL)")
        stage(db,"pending","busy","open")
        financial = ["cashier_shifts","cashier_shift_checkouts","checkout_requests","checkout_sales","checkout_cash",
                     "catalog","catalog_history","stock_movements","stock_requests"]
        immutable = financial + ["cashier_accounts","cashier_sessions","cashier_credential_events"]
        before = snapshot(db, immutable)
        db.commit()
        with db:
            for sql in staff:
                db.execute(sql)
        check(snapshot(db, immutable)==before, "prior rows preserved")
        check(db.execute("SELECT COUNT(*) FROM cashier_disable_events").fetchone()==(0,), "no fabricated audit")
        check(db.execute("SELECT active,revision FROM cashier_accounts WHERE id='legacy'").fetchone()==(0,2), "legacy disabled preserved")
        reject(db, "UPDATE cashier_accounts SET active=0,revision=3 WHERE id='staff'")
        reject(db, "UPDATE cashier_accounts SET active=0,revision=2 WHERE id='owner'")
        reject_event(db,"cashier-as-actor",actor="other")
        reject_event(db,"owner-as-target",target="owner",prior=1,nxt=2)
        reject_event(db,"busy",target="busy",prior=1,nxt=2)
        reject_event(db,"already-disabled",target="legacy")
        reject_event(db,"stale-target",prior=1,nxt=2)
        reject_event(db,"stale-actor",actor_rev=2)
        reject_event(db,"future-revision",nxt=4)
        reject_event(db,"blank",reason="")
        reject_event(db,"spaces",reason="  ")
        reject_event(db,"trim",reason=" leading")
        reject_event(db,"control",reason="bad\nreason")
        reject_event(db,"nul",reason="bad\x00reason")
        reject_event(db,"long",reason="x"*161)
        reject_event(db,"old",at=0)
        reject_event(db,"nonexistent",target="missing")
        reject_event(db,"self",target="other",actor="other",prior=1,nxt=2)
        check(snapshot(db, immutable)==before,"denials do not change old records")
        check(db.execute("SELECT COUNT(*) FROM cashier_disable_events").fetchone()==(0,),"denials leave no event")
        db.commit()
        # Inject failure at account transition; entire INSERT must roll back including event and revocation.
        db.execute("CREATE TRIGGER fault_disable BEFORE UPDATE ON cashier_accounts WHEN NEW.active=0 BEGIN SELECT RAISE(ABORT,'synthetic fault'); END")
        reject_event(db,"fault")
        check(snapshot(db, immutable)==before,"injected fault atomicity")
        check(db.execute("SELECT COUNT(*) FROM cashier_disable_events").fetchone()==(0,),"injected fault no stranded event")
        db.execute("DROP TRIGGER fault_disable")
        db.commit()
        try:
            with db:
                event(db,"rollback")
                raise RuntimeError("synthetic outer transaction failure")
        except RuntimeError:
            check(snapshot(db, immutable)==before,"rollback restored original account and sessions")
            check(db.execute("SELECT COUNT(*) FROM cashier_disable_events").fetchone()==(0,),"rollback restored event")
        with db:
            event(db,"disable")
        check(db.execute("SELECT active,revision,salt,password_hash FROM cashier_accounts WHERE id='staff'").fetchone()==(0,3,"f"*32,"a"*64),"exact identity and revision")
        check(db.execute("SELECT revoked_ms FROM cashier_sessions WHERE account_id='staff' ORDER BY issued_ms").fetchall()==[(5,),(10,)],"all sessions revoked with clock fence")
        check(db.execute("SELECT revoked_ms FROM cashier_sessions WHERE account_id='owner'").fetchone()==(None,),"owner session retained")
        check(db.execute("SELECT revoked_ms FROM cashier_sessions WHERE account_id='busy'").fetchone()==(None,),"unrelated session retained")
        check(db.execute("SELECT actor_id,actor_revision,prior_revision,next_revision,reason,disabled_ms FROM cashier_disable_events").fetchone()==("owner",1,2,3,"Synthetic training complete",5),"exact actor-linked event")
        check(snapshot(db,financial)=={n:before[n] for n in financial},"financial and stock records unchanged")
        check(db.execute("SELECT * FROM cashier_credential_events").fetchall()==before["cashier_credential_events"],"previous credential audit unchanged")
        reject(db,"UPDATE cashier_disable_events SET reason='changed' WHERE id='disable'")
        reject(db,"DELETE FROM cashier_disable_events WHERE id='disable'")
        reject(db,"INSERT OR REPLACE INTO cashier_disable_events VALUES('disable','staff','owner',1,3,4,'replace',6)")
        reject_event(db,"duplicate",prior=3,nxt=4)
        reject(db,"UPDATE cashier_accounts SET active=1,revision=4 WHERE id='staff'")
        reject(db,"DELETE FROM cashier_accounts WHERE id='staff'")
        reject(db,"INSERT INTO cashier_sessions VALUES(?,?,3,20,28800020,NULL)",("1"*64,"staff"))
        reject(db,"UPDATE cashier_sessions SET revoked_ms=NULL WHERE token_hash=?",("c"*64,))
        reject(db,"INSERT INTO cashier_shifts VALUES('new','staff',20,0,'OPEN',NULL,NULL,NULL,NULL)")
        reject(db,"INSERT INTO cashier_credential_events VALUES('reset-disabled','staff','owner',3,4,'OWNER_RESET',20)")
        # Existing v8 credential lifecycle continues to work for another active account.
        with db:
            db.execute("INSERT INTO cashier_credential_events VALUES('new-reset','other','owner',1,2,'OWNER_RESET',20)")
            db.execute("UPDATE cashier_accounts SET salt=?,password_hash=?,revision=2 WHERE id='other'", ("f"*32,"a"*64))
        check(db.execute("SELECT active,revision FROM cashier_accounts WHERE id='other'").fetchone()==(1,2),"credential lifecycle preserved")
        check(db.execute("SELECT COUNT(*) FROM cashier_disable_events").fetchone()==(1,),"exactly one disable")
        check(db.execute("PRAGMA foreign_key_check").fetchall()==[],"foreign keys intact")
print(f"STAFF_JOURNAL_SCHEMA_PASS: {checks} checks across v1-v8 fixtures")
