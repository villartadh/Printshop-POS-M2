"""Combined host candidate: legacy effects, upfront credit cash, collections, rollback and reporting."""
import drawer_journal_schema_test as prior
from drawer_journal_schema_test import *
start=prior.checks
with sqlite3.connect(":memory:") as db:
    db.execute("PRAGMA foreign_keys=ON")
    db.execute("PRAGMA recursive_triggers=OFF")
    for _,sqls in stages:
        for sql in sqls: db.execute(sql)
    account(db,"owner","OWNER");account(db,"staff","CASHIER")
    db.execute("INSERT INTO catalog VALUES('service','SERVICE','Synthetic service','page',100,1,0)")
    shift(db,"old","owner",1000)
    sale_stage(db,"pre-anchor","old","owner",100);sale_apply(db,"pre-anchor",100)
    for sql in drawer+statements("RefundJournal.java")+statements("RecurringJournal.java")+statements("CustomerJournal.java")+statements("CustomerLinkJournal.java"): db.execute(sql)
    stage(db,"old-expense",sid="old",actor="owner",amount=50,before=1100);resolve(db,"old-expense")
    close(db,"old",1050,1050)
    shift(db,"open","staff",1000)
    sale_stage(db,"legacy-sale","open","staff",100);sale_apply(db,"legacy-sale",100)
    db.execute("INSERT INTO cashier_customer_versions VALUES('cust',1,'Synthetic customer','',1,'owner',1,2)")
    db.execute("INSERT INTO cashier_customer_versions VALUES('other',1,'Other customer','',1,'owner',1,2)")
    names=["cashier_shifts","cashier_drawer_effects","cashier_drawer_anchors","checkout_requests","checkout_sales","checkout_cash"]
    before=snapshot(db,names)
    for sql in statements("ReceivableJournal.java"): db.execute(sql)
    # Include DROP VIEW in candidate extraction (the generic prior helper handles only DROP TRIGGER).
    source=(root/"CreditCashJournal.java").read_text()
    for sql in re.findall(r'db\.execSQL\("([^"]+)"\);',source): db.execute(sql)
    check(snapshot(db,names)==before,"migration preserves original audit")
    check(cash(db,"old")==1050 and cash(db,"open")==1100,"legacy open/closed balances preserved")
    check(db.execute("SELECT legacy_event_no FROM cashier_cash_effects_v2 ORDER BY event_no").fetchall()==[(1,),(2,)],"mirror exact source order")
    source_sql="INSERT INTO credit_receivable_sources VALUES(?,?,?,?,?,?,?,?,?,?)"
    def credit(id,amount,paid,at=8,customer="cust",sid="open",actor="staff"):
        db.execute(source_sql,(id,customer,1,sid,actor,1,"a"*64,amount,paid,at))
    db.execute("CREATE TRIGGER synthetic_credit_fault BEFORE INSERT ON cashier_cash_effects_v2 WHEN NEW.credit_id='credit-fault' BEGIN SELECT RAISE(ABORT,'fault'); END")
    try: credit("credit-fault",500,200)
    except sqlite3.IntegrityError: pass
    else: raise AssertionError("credit effect failure did not abort")
    check(cash(db,"open")==1100 and db.execute("SELECT COUNT(*) FROM credit_receivable_sources WHERE id='credit-fault'").fetchone()==(0,),"credit source atomic cash rollback")
    db.execute("DROP TRIGGER synthetic_credit_fault")
    credit("partial",500,200)
    check(cash(db,"open")==1300,"partial adds only upfront cash")
    credit("unpaid",300,0,9)
    check(cash(db,"open")==1300,"unpaid adds no cash")
    check(db.execute("SELECT COUNT(*) FROM cashier_cash_effects_v2 WHERE credit_id='unpaid'").fetchone()==(0,),"zero cash effect absent")
    def remaining(id):return db.execute("SELECT remaining FROM credit_receivable_open WHERE id=?",(id,)).fetchone()[0]
    check(remaining("partial")==300 and remaining("unpaid")==300,"exact original debt")
    req="INSERT INTO credit_collection_requests VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
    def collection(id,amount,before=1300,at=10):
        db.execute(req,(id,"cust",1,"open","staff",1,amount,before,before+amount,at,"PENDING",None,None,None))
    def collect_alloc(id,debt,before,amount):
        db.execute("INSERT INTO credit_collection_allocations VALUES(?,?,?,?,?)",(id,debt,before,amount,before-amount))
    def finish(id,state="APPLIED",at=11):
        db.execute("UPDATE credit_collection_requests SET state=?,resolved_ms=?,resolved_revision=1,cancel_reason=? WHERE id=?",(state,at,None if state=="APPLIED" else "Synthetic cancel",id))
    collection("pending",400);collect_alloc("pending","partial",300,300);collect_alloc("pending","unpaid",300,100)
    check(cash(db,"open")==1300 and remaining("partial")==300,"stage has no cash or debt effect")
    reject(db,insert,params("blocked-drawer",sid="open",actor="staff",amount=100,before=1300))
    db.commit()
    try:
        with db: sale_stage(db,"blocked-checkout","open","staff",100)
    except sqlite3.IntegrityError: pass
    else: raise AssertionError("pending collection checkout allowed")
    check(db.execute("SELECT COUNT(*) FROM checkout_requests WHERE request_id='blocked-checkout'").fetchone()==(0,),"blocked checkout rollback")
    reject(db,"UPDATE cashier_shifts SET state='CLOSED',closed_ms=20,expected_cash=1300,counted_cash=1300,variance=0 WHERE id='open'")
    reject(db,source_sql,("blocked-credit","other",1,"open","staff",1,"a"*64,100,50,10))
    finish("pending")
    check(cash(db,"open")==1700 and remaining("partial")==0 and remaining("unpaid")==200,"collection cash and debt applied atomically")
    reject(db,"UPDATE credit_collection_requests SET state='APPLIED' WHERE id='pending'")
    reject(db,"INSERT INTO cashier_cash_effects_v2(shift_id,collection_id,before_cash,after_cash,recorded_ms) VALUES('open','pending',1700,2100,11)")
    collection("cancel",100,1700,12);collect_alloc("cancel","unpaid",200,100);finish("cancel","CANCELLED",13)
    check(cash(db,"open")==1700 and remaining("unpaid")==200,"cancel no effect")
    sale_stage(db,"later-sale","open","staff",100);sale_apply(db,"later-sale",100,14)
    check(cash(db,"open")==1800,"legacy sale continues chain after collection")
    stage(db,"later-expense",actor="staff",amount=25,before=1800,at=15);resolve(db,"later-expense",at=16)
    check(cash(db,"open")==1775,"legacy expense continues mixed chain")
    collection("fault",200,1775,17);collect_alloc("fault","unpaid",200,200)
    db.execute("CREATE TRIGGER synthetic_fault BEFORE INSERT ON cashier_cash_effects_v2 WHEN NEW.collection_id='fault' BEGIN SELECT RAISE(ABORT,'fault'); END")
    try: finish("fault",at=18)
    except sqlite3.IntegrityError: pass
    else: raise AssertionError("fault did not abort")
    check(cash(db,"open")==1775 and remaining("unpaid")==200 and db.execute("SELECT state FROM credit_collection_requests WHERE id='fault'").fetchone()==("PENDING",),"failure rolls back state and debt")
    db.execute("DROP TRIGGER synthetic_fault");finish("fault",at=18)
    check(cash(db,"open")==1975 and remaining("unpaid")==0,"same pending identity recoverable")
    rows=db.execute("SELECT kind,gross,cash_received,debt_created,debt_collected FROM credit_report_events ORDER BY recorded_ms").fetchall()
    check(sum(r[1] for r in rows)==800 and sum(r[2] for r in rows)==800 and sum(r[3] for r in rows)==600 and sum(r[4] for r in rows)==600,"separate credit sales and collection report totals")
    check(sum(r[1] for r in rows if r[0]=="COLLECTION")==0,"no duplicate revenue")
    chain=db.execute("SELECT before_cash,after_cash FROM cashier_cash_effects_v2 WHERE shift_id='open' ORDER BY event_no").fetchall()
    check(chain[0][0]==1000 and chain[-1][1]==1975 and all(a[1]==b[0] for a,b in zip(chain,chain[1:])),"continuous mixed cash chain")
    reject(db,"UPDATE cashier_cash_effects_v2 SET after_cash=0 WHERE shift_id='open'")
    reject(db,"DELETE FROM cashier_cash_effects_v2 WHERE shift_id='open'")
    shift(db,"cap","owner",MAX)
    reject(db,source_sql,("overflow","other",1,"cap","owner",1,"a"*64,100,50,19))
    check(cash(db,"cap")==MAX,"drawer cap enforced")
    close(db,"cap",MAX,MAX)
    close(db,"open",1975,1975)
    check(cash(db,"open")==1975 and db.execute("SELECT variance FROM cashier_shifts WHERE id='open'").fetchone()==(0,),"exact mixed shift close")
    check(not db.execute("PRAGMA foreign_key_check").fetchall(),"foreign keys")
print(f"Credit cash candidate SQLite: {prior.checks-start} checks PASS")
