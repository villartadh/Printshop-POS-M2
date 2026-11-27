"""Host SQL prototype only: credit/collection allocations. No production migration or drawer effects."""
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
    shift(db,"open","staff",1000);shift(db,"owner-open","owner",2000)
    for sql in drawer+statements("RefundJournal.java")+statements("RecurringJournal.java")+statements("CustomerJournal.java")+statements("CustomerLinkJournal.java"): db.execute(sql)
    db.execute("INSERT INTO cashier_customer_versions VALUES('cust',1,'Synthetic customer','',1,'owner',1,2)")
    db.execute("INSERT INTO cashier_customer_versions VALUES('other',1,'Other customer','',1,'owner',1,2)")
    prior_tables=["cashier_customer_versions","cashier_shifts","cashier_drawer_effects","checkout_sales","checkout_cash","stock_movements"]
    before=snapshot(db,prior_tables)
    for sql in statements("ReceivableJournal.java"): db.execute(sql)
    check(snapshot(db,prior_tables)==before,"candidate creates no legacy effects")
    check(db.execute("SELECT COUNT(*) FROM credit_receivable_sources").fetchone()==(0,),"no invented debt")
    source="INSERT INTO credit_receivable_sources VALUES(?,?,?,?,?,?,?,?,?,?)"
    def debt(id,customer="cust",rev=1,amount=100,paid=0,at=3,actor="staff",sid="open",ar=1):
        return (id,customer,rev,sid,actor,ar,"a"*64,amount,paid,at)
    reject(db,source,debt("bad",paid=100));reject(db,source,debt("bad",paid=-1))
    reject(db,source,debt("bad",rev=2));reject(db,source,debt("bad",actor="owner"))
    db.execute(source,debt("d1",amount=100,paid=20))
    db.execute(source,debt("d2",amount=200,paid=0))
    db.execute(source,debt("foreign",customer="other",amount=500))
    reject(db,source.replace("INSERT","INSERT OR REPLACE"),debt("d1",amount=500))
    reject(db,"UPDATE credit_receivable_sources SET paid=0 WHERE id='d1'")
    reject(db,"DELETE FROM credit_receivable_sources WHERE id='d1'")
    def remaining(id): return db.execute("SELECT remaining FROM credit_receivable_open WHERE id=?",(id,)).fetchone()[0]
    check(remaining("d1")==80 and remaining("d2")==200,"original debt exact")
    req="INSERT INTO credit_collection_requests VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
    def request(id,amount=100,customer="cust",rev=1,sid="open",actor="staff",ar=1,before=1000,at=6):
        return (id,customer,rev,sid,actor,ar,amount,before,before+amount,at,"PENDING",None,None,None)
    alloc="INSERT INTO credit_collection_allocations VALUES(?,?,?,?,?)"
    def resolution(id,state="APPLIED",rev=1,reason=None):
        db.execute("UPDATE credit_collection_requests SET state=?,resolved_ms=7,resolved_revision=?,cancel_reason=? WHERE id=?",(state,rev,reason,id))
    reject(db,req,request("bad",before=1001))
    db.execute(req,request("c1"))
    reject(db,source,debt("new-credit",at=6))
    reject(db,req,request("competing",sid="owner-open",actor="owner",before=2000))
    reject(db,req,request("another",customer="other"))
    reject(db,alloc,("c1","d2",200,100,100))
    reject(db,alloc,("c1","foreign",500,100,400))
    reject(db,alloc,("c1","d1",81,80,1))
    reject(db,alloc,("c1","d1",80,79,1))
    db.execute(alloc,("c1","d1",80,80,0))
    reject(db,alloc,("c1","d2",200,21,179))
    reject(db,"UPDATE credit_collection_requests SET state='APPLIED',resolved_ms=7,resolved_revision=1 WHERE id='c1'")
    db.execute(alloc,("c1","d2",200,20,180))
    check(remaining("d1")==80 and remaining("d2")==200,"pending no debt change")
    reject(db,alloc.replace("INSERT","INSERT OR REPLACE"),("c1","d1",80,80,0))
    reject(db,"UPDATE credit_collection_allocations SET amount=79 WHERE collection_id='c1' AND debt_id='d1'")
    sale_stage(db,"competing-checkout","open","staff",100)
    reject(db,"UPDATE credit_collection_requests SET state='APPLIED',resolved_ms=7,resolved_revision=1 WHERE id='c1'")
    cancel_sale(db,"competing-checkout")
    resolution("c1")
    check(remaining("d1")==0 and remaining("d2")==180,"applied exact allocations")
    reject(db,"UPDATE credit_collection_requests SET state='APPLIED' WHERE id='c1'")
    reject(db,"DELETE FROM credit_collection_requests WHERE id='c1'")
    reject(db,"DELETE FROM credit_collection_allocations WHERE collection_id='c1'")
    db.execute("INSERT INTO cashier_customer_versions VALUES('cust',2,'Archived customer','',0,'owner',1,8)")
    reject(db,source,debt("archived",rev=2,at=8))
    db.execute(req,request("cancel",amount=50,rev=2,at=8))
    db.execute(alloc,("cancel","d2",180,50,130))
    db.execute("UPDATE credit_collection_requests SET state='CANCELLED',resolved_ms=9,resolved_revision=1,cancel_reason='Synthetic cancellation' WHERE id='cancel'")
    check(remaining("d2")==180,"cancel never reduces debt")
    db.execute(req,request("over",amount=181,rev=2,at=8))
    db.execute(alloc,("over","d2",180,180,0))
    reject(db,"UPDATE credit_collection_requests SET state='APPLIED',resolved_ms=9,resolved_revision=1 WHERE id='over'")
    db.execute("UPDATE credit_collection_requests SET state='CANCELLED',resolved_ms=9,resolved_revision=1,cancel_reason='Overpayment refused' WHERE id='over'")
    check(remaining("d2")==180,"overpay no effect")
    # Atomic rollback of a completed candidate allocation resolution.
    db.commit()
    try:
        with db:
            db.execute(req,request("fault",amount=180,rev=2,at=8))
            db.execute(alloc,("fault","d2",180,180,0))
            db.execute("UPDATE credit_collection_requests SET state='APPLIED',resolved_ms=9,resolved_revision=1 WHERE id='fault'")
            raise RuntimeError("simulated integration fault")
    except RuntimeError: pass
    check(remaining("d2")==180 and db.execute("SELECT COUNT(*) FROM credit_collection_requests WHERE id='fault'").fetchone()==(0,),"all-or-nothing rollback")
    check(cash(db,"open")==1000 and cash(db,"owner-open")==2000,"prototype never invents actual drawer cash")
    check(not db.execute("PRAGMA foreign_key_check").fetchall(),"foreign keys")
print(f"Receivable journal candidate SQLite: {prior.checks-start} checks PASS")
