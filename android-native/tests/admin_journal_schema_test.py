"""Actual SQLite v10 administrative audit/source integrity; not installed Android evidence."""
import re
import sqlite3
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "app/src/main/java/ph/printshop/pos"
def statements(name):
    return re.findall(r'db\.execSQL\("((?:CREATE (?:TABLE|(?:UNIQUE )?INDEX|TRIGGER)|DROP TRIGGER) [^"]+)"\);',
                      (root / name).read_text())
base, checkout, cashier, credential, staff, admin = [
    statements(n) for n in ("CatalogStore.java", "CheckoutJournal.java", "CashierJournal.java",
                           "CredentialJournal.java", "StaffJournal.java", "AdminJournal.java")]
assert (len(base),len(checkout),len(cashier),len(credential),len(staff),len(admin)) == (12,22,20,7,6,22)
checks = 0
def check(ok, label):
    global checks
    assert ok, label
    checks += 1
def reject(db, sql, args=()):
    try: db.execute(sql,args)
    except sqlite3.IntegrityError: check(True,sql)
    else: raise AssertionError("Unexpected acceptance: "+sql)
def account(db, aid, role):
    db.execute("INSERT INTO cashier_accounts VALUES(?,?,?,?,?,?,?,?,?,?,?)",
               (aid,aid,role,"a"*32,"b"*64,600000,1,1,0,0,1))
def event(db, action, subject, revision, request=None, actor="owner", actor_rev=1, at=10):
    db.execute("INSERT INTO cashier_admin_events(actor_id,actor_revision,action,subject_id,revision,request_id,recorded_ms) VALUES(?,?,?,?,?,?,?)",
               (actor,actor_rev,action,subject,revision,request,at))
def bad(db, action, subject, rev, request=None, actor="owner", actor_rev=1, at=10):
    reject(db,"INSERT INTO cashier_admin_events(actor_id,actor_revision,action,subject_id,revision,request_id,recorded_ms) VALUES(?,?,?,?,?,?,?)",
           (actor,actor_rev,action,subject,rev,request,at))
def history(db, revision, action, subject="service"):
    db.execute("INSERT INTO catalog_history SELECT id,?,kind,name,unit,centavos,archived,? FROM catalog WHERE id=?",
               (revision,action,subject))
def stage(db, req, rev):
    db.execute("INSERT INTO stock_requests VALUES(?,?,?,?,?,?,?,?,?)",
               (req,"paper",1,rev,1000,"Synthetic count",2,"PENDING",None))
def apply(db, req, rev, before):
    db.execute("INSERT INTO stock_movements VALUES(?,?,?,?,?,?,?,?,?,?)",
               (req,"paper",1,rev+1,"sheet","Synthetic count",1000,before,before+1000,3))
    db.execute("UPDATE stock_requests SET state='APPLIED',resolved_ms=3 WHERE request_id=?",(req,))
def cancel(db,req):
    db.execute("UPDATE stock_requests SET state='CANCELLED',resolved_ms=3 WHERE request_id=?",(req,))
def snap(db,names):
    return {n:db.execute("SELECT * FROM "+n+" ORDER BY 1").fetchall() for n in names}

for former in range(1,10):
    with sqlite3.connect(":memory:") as db:
        db.execute("PRAGMA foreign_keys=ON")
        db.execute("PRAGMA recursive_triggers=OFF")
        stages=[(1,base[:2]),(2,base[2:3]),(3,base[3:5]),(4,base[5:7]),(5,base[7:]),(6,checkout),(7,cashier),(8,credential),(9,staff)]
        for ver,sqls in stages:
            if ver<=former:
                for sql in sqls: db.execute(sql)
        db.execute("INSERT INTO catalog VALUES('service','SERVICE','Synthetic printing','page',250,1,0)")
        db.execute("INSERT INTO catalog VALUES('paper','MATERIAL','Synthetic paper','sheet',100,1,0)")
        history(db,1,"CREATE"); history(db,1,"CREATE","paper")
        db.commit()
        with db:
            for ver,sqls in stages:
                if ver>former:
                    for sql in sqls: db.execute(sql)
        account(db,"owner","OWNER"); account(db,"staff","CASHIER"); account(db,"disabled","CASHIER")
        db.execute("INSERT INTO cashier_disable_events VALUES('old-disable','disabled','owner',1,1,2,'Synthetic historical disable',2)")
        db.execute("INSERT INTO cashier_shifts VALUES('closed','staff',1,500,'OPEN',NULL,NULL,NULL,NULL)")
        db.execute("UPDATE cashier_shifts SET state='CLOSED',closed_ms=2,expected_cash=500,counted_cash=500,variance=0 WHERE id='closed'")
        stage(db,"legacy",0)
        names=["catalog","catalog_history","stock_requests","stock_movements","service_materials","recipe_history",
               "pricing_profile_versions","pricing_tier_versions","cashier_accounts","cashier_disable_events",
               "cashier_shifts","cashier_sessions","checkout_requests","checkout_sales","checkout_cash"]
        before=snap(db,names); db.commit()
        with db:
            for sql in admin: db.execute(sql)
        check(snap(db,names)==before,"migration preserved old rows")
        check(db.execute("SELECT COUNT(*) FROM cashier_admin_events").fetchone()==(0,),"no fabricated audit")
        # A pre-v10 pending request can be resolved with only a new apply event.
        apply(db,"legacy",0,0); event(db,"STOCK_APPLY","paper",1,"legacy")
        bad(db,"STOCK_STAGE","paper",0,"legacy")
        bad(db,"STOCK_CANCEL","paper",0,"legacy")
        bad(db,"STOCK_APPLY","paper",1,"legacy")
        bad(db,"STOCK_APPLY","service",1,"legacy")
        bad(db,"STOCK_APPLY","paper",2,"legacy")
        # Current owner and exact source revision/action required.
        db.execute("UPDATE catalog SET revision=2,name='Synthetic changed' WHERE id='service'"); history(db,2,"EDIT")
        bad(db,"CATALOG_EDIT","service",2,actor="staff")
        bad(db,"CATALOG_EDIT","service",2,actor="disabled")
        bad(db,"CATALOG_EDIT","service",2,actor="missing")
        bad(db,"CATALOG_EDIT","service",2,actor_rev=2)
        bad(db,"CATALOG_EDIT","service",2,at=0)
        bad(db,"CATALOG_ARCHIVE","service",2)
        bad(db,"CATALOG_EDIT","service",90)
        bad(db,"CATALOG_EDIT","service",0)
        bad(db,"CATALOG_EDIT","service",2,"legacy")
        bad(db,"UNKNOWN","service",2)
        event(db,"CATALOG_EDIT","service",2)
        bad(db,"CATALOG_EDIT","service",2)
        db.execute("UPDATE catalog SET revision=3,archived=1 WHERE id='service'"); history(db,3,"ARCHIVE")
        event(db,"CATALOG_ARCHIVE","service",3)
        db.execute("UPDATE catalog SET revision=4,archived=0 WHERE id='service'"); history(db,4,"RESTORE")
        event(db,"CATALOG_RESTORE","service",4)
        db.execute("INSERT INTO catalog VALUES('new','MATERIAL','Synthetic new','sheet',100,1,0)")
        history(db,1,"CREATE","new"); event(db,"CATALOG_CREATE","new",1)
        bad(db,"PRICING_SAVE","service",1)
        db.execute("INSERT INTO pricing_profile_versions VALUES('service',1,'TIERED_BULK',0,4,'page',3)")
        db.execute("INSERT INTO pricing_tier_versions VALUES('service',1,100,250)")
        bad(db,"PRICING_SAVE","service",1,at=2)
        event(db,"PRICING_SAVE","service",1)
        bad(db,"RECIPE_SAVE","service",1)
        db.execute("INSERT INTO service_materials VALUES('service','paper',1000,'sheet')")
        db.execute("INSERT INTO recipe_history VALUES('service',1,'paper',1000,'sheet',1,4,3)")
        event(db,"RECIPE_SAVE","service",1)
        stage(db,"addition",1)
        bad(db,"STOCK_STAGE","paper",2,"addition")
        bad(db,"STOCK_APPLY","paper",2,"addition")
        bad(db,"STOCK_CANCEL","paper",1,"addition")
        bad(db,"STOCK_STAGE","paper",1,None)
        event(db,"STOCK_STAGE","paper",1,"addition")
        bad(db,"STOCK_STAGE","paper",1,"addition")
        reject(db,"INSERT OR REPLACE INTO stock_requests VALUES('replacement','paper',1,1,2000,'Synthetic replacement',2,'PENDING',NULL)")
        reject(db,"INSERT OR REPLACE INTO stock_requests SELECT * FROM stock_requests WHERE request_id='addition'")
        apply(db,"addition",1,1000); event(db,"STOCK_APPLY","paper",2,"addition")
        bad(db,"STOCK_STAGE","paper",1,"addition")
        stage(db,"cancel",2);event(db,"STOCK_STAGE","paper",2,"cancel");cancel(db,"cancel")
        event(db,"STOCK_CANCEL","paper",2,"cancel")
        bad(db,"STOCK_CANCEL","paper",2,"cancel")
        bad(db,"STOCK_APPLY","paper",3,"cancel")
        check(db.execute("SELECT COUNT(*) FROM cashier_admin_events").fetchone()==(11,),"all nine action kinds and no duplicate")
        check(db.execute("SELECT COUNT(DISTINCT action) FROM cashier_admin_events").fetchone()==(9,),"nine action types")
        # Actual list SQL, text-bound integers as Android rawQuery does.
        query=re.search(r'db\.rawQuery\("([^"]+)"', (root/"AdminJournal.java").read_text()).group(1)
        first=db.execute(query,("0","0","5")).fetchall()
        older=db.execute(query,(str(first[-1][0]),str(first[-1][0]),"50")).fetchall()
        check(len(first)==5 and len(older)==6 and first[-1][0]>older[0][0],"keyset pagination")
        check(first[0][4]=="STOCK_CANCEL" and first[0][10]=="Synthetic count","stock audit join")
        frozen=snap(db,names+["cashier_admin_events"])
        for sql in [
            "UPDATE cashier_admin_events SET actor_revision=2 WHERE event_no=1",
            "DELETE FROM cashier_admin_events WHERE event_no=1",
            "INSERT OR REPLACE INTO cashier_admin_events SELECT * FROM cashier_admin_events WHERE event_no=1",
            "UPDATE catalog_history SET name='tampered' WHERE id='service' AND revision=2",
            "DELETE FROM catalog_history WHERE id='service' AND revision=2",
            "INSERT OR REPLACE INTO catalog_history SELECT * FROM catalog_history WHERE id='service' AND revision=2",
            "UPDATE OR REPLACE catalog_history SET revision=2 WHERE id='service' AND revision=1",
            "UPDATE pricing_profile_versions SET method='FIXED' WHERE service_id='service'",
            "DELETE FROM pricing_profile_versions WHERE service_id='service'",
            "INSERT OR REPLACE INTO pricing_profile_versions SELECT * FROM pricing_profile_versions",
            "UPDATE pricing_tier_versions SET rate_centavos=1",
            "DELETE FROM pricing_tier_versions",
            "INSERT INTO pricing_tier_versions VALUES('service',1,200,1)",
            "INSERT OR REPLACE INTO pricing_tier_versions SELECT * FROM pricing_tier_versions",
            "UPDATE recipe_history SET quantity_milli=1",
            "DELETE FROM recipe_history",
            "INSERT OR REPLACE INTO recipe_history SELECT * FROM recipe_history",
            "UPDATE stock_movements SET reason='tampered' WHERE request_id='addition'",
            "DELETE FROM stock_movements WHERE request_id='addition'",
            "INSERT OR REPLACE INTO stock_movements SELECT * FROM stock_movements WHERE request_id='addition'",
            "INSERT OR REPLACE INTO stock_movements VALUES('replacement','paper',1,2,'sheet','Synthetic count',1000,1000,2000,3)",
            "INSERT INTO stock_movements VALUES('cancel','paper',1,3,'sheet','Synthetic count',1000,2000,3000,3)",
            "INSERT OR REPLACE INTO stock_requests VALUES('cancel','paper',1,2,1000,'Synthetic count',2,'PENDING',NULL)",
        ]: reject(db,sql)
        check(snap(db,names+["cashier_admin_events"])==frozen,"tampering preserved source and audit")
        db.commit()
        # Application transaction performs source mutation then audit; fault must roll back both.
        db.execute("CREATE TRIGGER fault_admin BEFORE INSERT ON cashier_admin_events BEGIN SELECT RAISE(ABORT,'synthetic fault'); END")
        try:
            with db:
                db.execute("UPDATE catalog SET revision=5 WHERE id='service'")
                history(db,5,"EDIT")
                event(db,"CATALOG_EDIT","service",5)
        except sqlite3.IntegrityError: check(True,"injected fault")
        else: raise AssertionError("fault accepted")
        check(snap(db,names+["cashier_admin_events"])==frozen,"transaction rollback")
        db.execute("DROP TRIGGER fault_admin")
        # New credential revision applies only to new events, not historical ones.
        db.execute("INSERT INTO cashier_credential_events VALUES('rotation','owner','owner',1,2,'SELF_CHANGE',4)")
        db.execute("UPDATE cashier_accounts SET salt=?,password_hash=?,revision=2 WHERE id='owner'",("c"*32,"d"*64))
        stage(db,"rotated",2)
        bad(db,"STOCK_STAGE","paper",2,"rotated")
        event(db,"STOCK_STAGE","paper",2,"rotated",actor_rev=2)
        cancel(db,"rotated");event(db,"STOCK_CANCEL","paper",2,"rotated",actor_rev=2)
        check(db.execute("SELECT actor_revision,COUNT(*) FROM cashier_admin_events GROUP BY actor_revision ORDER BY actor_revision").fetchall()==[(1,11),(2,2)],"historical actor revision unchanged")
        check(db.execute("SELECT COUNT(*) FROM cashier_disable_events").fetchone()==(1,),"v9 event preserved")
        check(not db.execute("PRAGMA foreign_key_check").fetchall(),"foreign keys")
print(f"Admin journal actual SQLite: {checks} checks PASS")
