"""Actual SQLite v11 drawer migration, durable requests, cash-effect chain; not Android acceptance."""
import re
import sqlite3
from pathlib import Path

root=Path(__file__).resolve().parents[1]/"app/src/main/java/ph/printshop/pos"
def statements(name):
    return re.findall(r'db\.execSQL\("((?:CREATE (?:TABLE|(?:UNIQUE )?INDEX|TRIGGER|VIEW)|DROP TRIGGER|INSERT INTO) [^"]+)"\);',(root/name).read_text())
base,checkout,cashier,credential,staff,admin,drawer=[statements(n) for n in
    ("CatalogStore.java","CheckoutJournal.java","CashierJournal.java","CredentialJournal.java","StaffJournal.java","AdminJournal.java","DrawerJournal.java")]
assert (len(base),len(checkout),len(cashier),len(credential),len(staff),len(admin),len(drawer))==(12,22,20,7,6,22,29)
MAX=9999999999900000
checks=0
def check(ok,label):
    global checks
    assert ok,label
    checks+=1
def reject(db,sql,args=()):
    try: db.execute(sql,args)
    except sqlite3.IntegrityError: check(True,sql)
    else: raise AssertionError("Unexpected acceptance: "+sql)
def account(db,aid,role):
    db.execute("INSERT INTO cashier_accounts VALUES(?,?,?,?,?,?,?,?,?,?,?)",(aid,aid,role,"a"*32,"b"*64,600000,1,1,0,0,1))
def shift(db,sid,actor,opening):
    db.execute("INSERT INTO cashier_shifts VALUES(?,?,1,?,'OPEN',NULL,NULL,NULL,NULL)",(sid,actor,opening))
def sale_stage(db,req,sid,actor,amount):
    db.execute("INSERT INTO checkout_requests VALUES("+",".join("?"*30)+")",(req,1,"a"*64,"service","Synthetic service","page",1,0,0,
        "PER_UNIT","NONE",1,1,1,None,None,None,100,1,amount,amount,amount,0,amount,0,0,1,"PENDING",None,None))
    db.execute("INSERT INTO cashier_shift_checkouts VALUES(?,?,?)",(req,sid,actor))
def sale_apply(db,req,amount,at=5):
    db.execute("INSERT INTO checkout_sales VALUES(?,?)",(req,at))
    db.execute("INSERT INTO checkout_cash VALUES(?,?,?,0)",(req,amount,amount))
    db.execute("UPDATE checkout_requests SET state='APPLIED',resolved_ms=? WHERE request_id=?",(at,req))
def cancel_sale(db,req):
    db.execute("UPDATE checkout_requests SET state='CANCELLED',resolved_ms=5,cancel_reason='Synthetic cancellation' WHERE request_id=?",(req,))
def close(db,sid,expected,counted):
    db.execute("UPDATE cashier_shifts SET state='CLOSED',closed_ms=20,expected_cash=?,counted_cash=?,variance=? WHERE id=?",
               (expected,counted,counted-expected,sid))
def cash(db,sid):
    return db.execute("SELECT expected_cash FROM cashier_drawer_current WHERE shift_id=?",(sid,)).fetchone()[0]
def effects(db,sid):
    return db.execute("SELECT checkout_id,drawer_id,before_cash,after_cash FROM cashier_drawer_effects WHERE shift_id=? ORDER BY event_no",(sid,)).fetchall()
def params(req,sid="open",actor="owner",rev=1,kind="EXPENSE",amount=200,category="Synthetic supplies",reason="Synthetic drawer reason",before=1500,after=None,at=6):
    if after is None: after=before+(amount if kind=="CASH_IN" else -amount)
    return (req,sid,actor,rev,kind,amount,category,reason,before,after,at,"PENDING",None,None,None)
insert="INSERT INTO cashier_drawer_requests VALUES("+",".join("?"*15)+")"
def stage(db,req,**kw): db.execute(insert,params(req,**kw))
def bad(db,req,**kw): reject(db,insert,params(req,**kw))
def resolve(db,req,rev=1,apply=True,at=7,reason="Synthetic cancellation"):
    db.execute("UPDATE cashier_drawer_requests SET state=?,resolved_ms=?,resolved_revision=?,cancel_reason=? WHERE id=?",
               ("APPLIED" if apply else "CANCELLED",at,rev,None if apply else reason,req))
def state(db,req):
    return db.execute("SELECT state,resolved_revision,cancel_reason FROM cashier_drawer_requests WHERE id=?",(req,)).fetchone()
def snapshot(db,names):
    return {n:db.execute("SELECT * FROM "+n+" ORDER BY 1").fetchall() for n in names}
stages=[(1,base[:2]),(2,base[2:3]),(3,base[3:5]),(4,base[5:7]),(5,base[7:]),(6,checkout),(7,cashier),(8,credential),(9,staff),(10,admin)]
for former in range(1,11):
    with sqlite3.connect(":memory:") as db:
        db.execute("PRAGMA foreign_keys=ON")
        db.execute("PRAGMA recursive_triggers=OFF")
        for ver,sqls in stages:
            if ver<=former:
                for sql in sqls: db.execute(sql)
        db.execute("INSERT INTO catalog VALUES('service','SERVICE','Synthetic service','page',100,1,0)")
        db.execute("INSERT INTO catalog_history VALUES('service',1,'SERVICE','Synthetic service','page',100,0,'CREATE')")
        db.commit()
        with db:
            for ver,sqls in stages:
                if ver>former:
                    for sql in sqls: db.execute(sql)
        for aid,role in (("owner","OWNER"),("staff","CASHIER"),("old-disabled","CASHIER")): account(db,aid,role)
        db.execute("INSERT INTO cashier_disable_events VALUES('disabled-event','old-disabled','owner',1,1,2,'Synthetic disable',2)")
        db.execute("INSERT INTO catalog VALUES('other','SERVICE','Synthetic later item','page',100,1,0)")
        db.execute("INSERT INTO catalog_history VALUES('other',1,'SERVICE','Synthetic later item','page',100,0,'CREATE')")
        db.execute("INSERT INTO cashier_admin_events(actor_id,actor_revision,action,subject_id,revision,recorded_ms) VALUES('owner',1,'CATALOG_CREATE','other',1,2)")
        shift(db,"closed","owner",1000);sale_stage(db,"old-closed","closed","owner",500);sale_apply(db,"old-closed",500);close(db,"closed",1500,1450)
        shift(db,"open","owner",1000);sale_stage(db,"old-open","open","owner",500);sale_apply(db,"old-open",500)
        sale_stage(db,"pending-sale","open","owner",250)
        shift(db,"cashier","staff",500)
        names=["cashier_accounts","cashier_sessions","cashier_shifts","cashier_shift_checkouts","checkout_requests","checkout_sales","checkout_cash",
               "catalog","catalog_history","cashier_disable_events","cashier_admin_events","cashier_credential_events","stock_movements"]
        before=snapshot(db,names);db.commit()
        with db:
            for sql in drawer: db.execute(sql)
        check(snapshot(db,names)==before,"migration changed existing rows")
        check(cash(db,"closed")==1500 and cash(db,"open")==1500 and cash(db,"cashier")==500,"legacy opening projection")
        check(db.execute("SELECT checkout_id FROM cashier_drawer_legacy_sales ORDER BY 1").fetchall()==[("old-closed",),("old-open",)],"legacy exact provenance")
        check(db.execute("SELECT COUNT(*) FROM cashier_drawer_requests").fetchone()==(0,),"migration invented drawer requests")
        check(db.execute("SELECT COUNT(*) FROM cashier_drawer_effects").fetchone()==(0,),"migration invented cash effects")
        reject(db,"INSERT INTO cashier_drawer_effects(shift_id,checkout_id,before_cash,after_cash,recorded_ms) VALUES('open','old-open',1500,2000,5)")
        reject(db,"UPDATE cashier_drawer_anchors SET baseline_cash=0 WHERE shift_id='open'")
        reject(db,"DELETE FROM cashier_drawer_anchors WHERE shift_id='open'")
        reject(db,"INSERT OR REPLACE INTO cashier_drawer_anchors VALUES('open',1000)")
        reject(db,"DELETE FROM cashier_drawer_legacy_sales WHERE checkout_id='old-open'")
        reject(db,"INSERT OR REPLACE INTO cashier_drawer_legacy_sales VALUES('old-open')")
        bad(db,"blocked-by-sale")
        # Historical pending checkout can now apply as one new effect.
        sale_apply(db,"pending-sale",250)
        check(cash(db,"open")==1750 and effects(db,"open")==[("pending-sale",None,1500,1750)],"legacy pending recovery")
        bad(db,"stale")
        bad(db,"other-operator",actor="staff",before=1750)
        bad(db,"wrong-revision",rev=2,before=1750)
        bad(db,"closed-shift",sid="closed",before=1500)
        bad(db,"unknown-shift",sid="missing",before=1750)
        bad(db,"cashier-other-shift",sid="cashier",before=500)
        bad(db,"cashier-cash-in",sid="cashier",actor="staff",kind="CASH_IN",category=None,before=500)
        bad(db,"cashier-cash-out",sid="cashier",actor="staff",kind="CASH_OUT",category=None,before=500)
        bad(db,"zero",amount=0,before=1750)
        bad(db,"negative",amount=-1,before=1750)
        bad(db,"overdraw",amount=1751,before=1750)
        bad(db,"bad-after",before=1750,after=1700)
        bad(db,"blank-category",category="",before=1750)
        bad(db,"null-category",category=None,before=1750)
        bad(db,"long-category",category="x"*81,before=1750)
        bad(db,"control-category",category="bad\nreason",before=1750)
        bad(db,"category-on-adjustment",kind="CASH_IN",before=1750)
        bad(db,"blank-reason",reason="",before=1750)
        bad(db,"untrimmed-reason",reason=" leading",before=1750)
        bad(db,"control-reason",reason="bad\x00text",before=1750)
        stage(db,"expense",before=1750)
        check(cash(db,"open")==1750 and state(db,"expense")==("PENDING",None,None),"stage changed cash")
        bad(db,"competing",before=1750)
        reject(db,insert.replace("INSERT","INSERT OR REPLACE",1),params("expense",before=1750))
        reject(db,"UPDATE cashier_drawer_requests SET amount=1 WHERE id='expense'")
        reject(db,"DELETE FROM cashier_drawer_requests WHERE id='expense'")
        reject(db,"UPDATE cashier_drawer_requests SET state='APPLIED',resolved_ms=7,resolved_revision=2 WHERE id='expense'")
        reject(db,"UPDATE cashier_shifts SET state='CLOSED',closed_ms=20,expected_cash=1750,counted_cash=1750,variance=0 WHERE id='open'")
        db.commit()
        try:
            with db: sale_stage(db,"blocked-new","open","owner",100)
        except sqlite3.IntegrityError: check(True,"drawer blocked new checkout")
        else: raise AssertionError("checkout link bypass")
        check(db.execute("SELECT COUNT(*) FROM checkout_requests WHERE request_id='blocked-new'").fetchone()==(0,),"rolled back checkout link")
        db.execute("CREATE TRIGGER fault_drawer BEFORE INSERT ON cashier_drawer_effects BEGIN SELECT RAISE(ABORT,'synthetic effect fault'); END")
        reject(db,"UPDATE cashier_drawer_requests SET state='APPLIED',resolved_ms=7,resolved_revision=1 WHERE id='expense'")
        check(state(db,"expense")==("PENDING",None,None) and cash(db,"open")==1750,"drawer effect failure rollback")
        db.execute("DROP TRIGGER fault_drawer")
        resolve(db,"expense")
        check(cash(db,"open")==1550 and effects(db,"open")[-1]==(None,"expense",1750,1550),"expense effect")
        reject(db,"UPDATE cashier_drawer_requests SET state='APPLIED',resolved_ms=8,resolved_revision=1 WHERE id='expense'")
        reject(db,"INSERT OR REPLACE INTO cashier_drawer_requests SELECT * FROM cashier_drawer_requests WHERE id='expense'")
        reject(db,"DELETE FROM cashier_drawer_effects WHERE drawer_id='expense'")
        reject(db,"UPDATE cashier_drawer_effects SET after_cash=1 WHERE drawer_id='expense'")
        reject(db,"INSERT OR REPLACE INTO cashier_drawer_effects SELECT * FROM cashier_drawer_effects WHERE drawer_id='expense'")
        reject(db,"INSERT INTO cashier_drawer_effects(shift_id,drawer_id,before_cash,after_cash,recorded_ms) VALUES('open','expense',1550,1350,7)")
        sale_stage(db,"new-sale","open","owner",250);db.commit()
        db.execute("CREATE TRIGGER fault_sale_effect BEFORE INSERT ON cashier_drawer_effects BEGIN SELECT RAISE(ABORT,'checkout effect fault'); END")
        try:
            with db: sale_apply(db,"new-sale",250)
        except sqlite3.IntegrityError: check(True,"checkout effect fault")
        else: raise AssertionError("checkout effect fault accepted")
        check(cash(db,"open")==1550 and db.execute("SELECT state FROM checkout_requests WHERE request_id='new-sale'").fetchone()==("PENDING",),"checkout effect fault preserved pending and cash")
        check(db.execute("SELECT COUNT(*) FROM checkout_sales WHERE request_id='new-sale'").fetchone()==(0,) and db.execute("SELECT COUNT(*) FROM checkout_cash WHERE request_id='new-sale'").fetchone()==(0,),"checkout effect fault rolled back sale/payment")
        db.execute("DROP TRIGGER fault_sale_effect")
        sale_apply(db,"new-sale",250)
        check(cash(db,"open")==1800 and effects(db,"open")[-1]==("new-sale",None,1550,1800),"checkout after expense")
        stage(db,"cash-in",kind="CASH_IN",amount=500,category=None,before=1800);resolve(db,"cash-in")
        stage(db,"cash-out",kind="CASH_OUT",amount=300,category=None,before=2300);resolve(db,"cash-out")
        check(cash(db,"open")==2000,"cash in/out sign")
        stage(db,"cancelled",before=2000)
        reject(db,"UPDATE cashier_drawer_requests SET state='CANCELLED',resolved_ms=7,resolved_revision=1,cancel_reason='' WHERE id='cancelled'")
        resolve(db,"cancelled",apply=False)
        check(state(db,"cancelled")==("CANCELLED",1,"Synthetic cancellation") and cash(db,"open")==2000,"cancel no effect")
        reject(db,"INSERT INTO cashier_drawer_effects(shift_id,drawer_id,before_cash,after_cash,recorded_ms) VALUES('open','cancelled',2000,1800,7)")
        stage(db,"staff-expense",sid="cashier",actor="staff",amount=100,before=500);resolve(db,"staff-expense")
        check(cash(db,"cashier")==400,"cashier own expense")
        stage(db,"rotated",kind="CASH_IN",amount=100,category=None,before=2000)
        db.execute("INSERT INTO cashier_credential_events VALUES('rotation','owner','owner',1,2,'SELF_CHANGE',7)")
        db.execute("UPDATE cashier_accounts SET salt=?,password_hash=?,revision=2 WHERE id='owner'",("c"*32,"d"*64))
        reject(db,"UPDATE cashier_drawer_requests SET state='APPLIED',resolved_ms=8,resolved_revision=1 WHERE id='rotated'")
        resolve(db,"rotated",rev=2,at=8)
        check(cash(db,"open")==2100 and db.execute("SELECT stage_revision,resolved_revision FROM cashier_drawer_requests WHERE id='rotated'").fetchone()==(1,2),"credential revision audit")
        reject(db,"UPDATE cashier_shifts SET state='CLOSED',closed_ms=20,expected_cash=2000,counted_cash=2000,variance=0 WHERE id='open'")
        close(db,"open",2100,2050);close(db,"cashier",400,400)
        check(db.execute("SELECT expected_cash,counted_cash,variance FROM cashier_shifts WHERE id='open'").fetchone()==(2100,2050,-50),"close exact projected amount")
        check(db.execute("SELECT expected_cash,counted_cash,variance FROM cashier_shifts WHERE id='closed'").fetchone()==(1500,1450,-50),"legacy close unchanged")
        bad(db,"closed-new",rev=2,before=2100)
        check(db.execute("SELECT COUNT(*) FROM cashier_drawer_effects WHERE drawer_id='cancelled'").fetchone()==(0,),"cancel no effect")
        check(db.execute("SELECT COUNT(*) FROM cashier_drawer_effects WHERE shift_id='open'").fetchone()==(6,),"exact one effect per application")
        check(db.execute("SELECT COUNT(*) FROM cashier_disable_events").fetchone()==(1,) and db.execute("SELECT COUNT(*) FROM cashier_admin_events").fetchone()==(1,),"prior audits preserved")
        # New shift uses its opening value as anchor, not a historical aggregate.
        shift(db,"large","owner",0)
        stage(db,"max-in",sid="large",rev=2,kind="CASH_IN",amount=MAX,category=None,before=0);resolve(db,"max-in",rev=2)
        bad(db,"overflow",sid="large",rev=2,kind="CASH_IN",amount=1,category=None,before=MAX)
        # Raw checkout apply above cap must roll back payment and request in a writer transaction.
        sale_stage(db,"overflow-sale","large","owner",100);db.commit()
        try:
            with db: sale_apply(db,"overflow-sale",100)
        except sqlite3.IntegrityError: check(True,"checkout cap")
        else: raise AssertionError("checkout exceeded max drawer")
        check(db.execute("SELECT state FROM checkout_requests WHERE request_id='overflow-sale'").fetchone()==("PENDING",) and db.execute("SELECT COUNT(*) FROM checkout_sales WHERE request_id='overflow-sale'").fetchone()==(0,),"checkout overflow rollback")
        cancel_sale(db,"overflow-sale")
        stage(db,"max-out",sid="large",rev=2,kind="CASH_OUT",amount=MAX,category=None,before=MAX);resolve(db,"max-out",rev=2)
        check(cash(db,"large")==0,"max exact subtraction")
        if former==10:
            for i in range(930):
                req=f"large-in-{i}";stage(db,req,sid="large",rev=2,kind="CASH_IN",amount=MAX,category=None,before=0);resolve(db,req,rev=2)
                req=f"large-out-{i}";stage(db,req,sid="large",rev=2,kind="CASH_OUT",amount=MAX,category=None,before=MAX);resolve(db,req,rev=2)
            check(cash(db,"large")==0 and MAX*930>2**63-1,"running chain stays exact despite huge gross movements")
        close(db,"large",0,0)
        check(not db.execute("PRAGMA foreign_key_check").fetchall(),"foreign keys")
# Malformed legacy projection above the exact bound fails without rewriting prior data.
with sqlite3.connect(":memory:") as db:
    db.execute("PRAGMA foreign_keys=ON")
    for _,sqls in stages:
        for sql in sqls: db.execute(sql)
    db.execute("INSERT INTO catalog VALUES('service','SERVICE','Synthetic service','page',100,1,0)")
    account(db,"owner","OWNER");shift(db,"over","owner",MAX);sale_stage(db,"over-sale","over","owner",100);sale_apply(db,"over-sale",100)
    db.commit()
    before=snapshot(db,["cashier_shifts","checkout_requests","checkout_sales","checkout_cash"])
    db.execute("BEGIN")
    try:
        for sql in drawer: db.execute(sql)
    except sqlite3.IntegrityError: db.rollback();check(True,"invalid legacy anchor refused")
    else: raise AssertionError("invalid legacy anchor accepted")
    check(snapshot(db,list(before))==before,"failed migration preserved old data")
    check(db.execute("SELECT COUNT(*) FROM sqlite_master WHERE name LIKE 'cashier_drawer_%'").fetchone()==(0,),"failed migration did not strand new tables")
print(f"Drawer journal actual SQLite: {checks} checks PASS")
