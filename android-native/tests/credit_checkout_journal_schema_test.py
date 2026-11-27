"""Combined synthetic credit checkout/stock/debt/cash schema migration and synthetic SQL fixtures."""
import drawer_journal_schema_test as prior
from drawer_journal_schema_test import *
start=prior.checks
with sqlite3.connect(":memory:") as db:
    db.execute("PRAGMA foreign_keys=ON")
    db.execute("PRAGMA recursive_triggers=OFF")
    for _,sqls in stages:
        for sql in sqls: db.execute(sql)
    account(db,"owner","OWNER");account(db,"staff","CASHIER")
    db.execute("INSERT INTO catalog VALUES('service','SERVICE','Synthetic print','page',500,1,0)")
    db.execute("INSERT INTO catalog VALUES('paper','MATERIAL','Synthetic paper','sheet',100,1,0)")
    db.execute("INSERT INTO service_materials VALUES('service','paper',1000,'sheet')")
    db.execute("INSERT INTO recipe_history VALUES('service',1,'paper',1000,'sheet',1,1,1)")
    db.execute("INSERT INTO stock_movements VALUES('initial','paper',1,1,'sheet','Synthetic initial',10000,0,10000,1)")
    shift(db,"open","staff",1000)
    for sql in drawer+statements("RefundJournal.java")+statements("RecurringJournal.java")+statements("CustomerJournal.java")+statements("CustomerLinkJournal.java"): db.execute(sql)
    db.execute("INSERT INTO cashier_customer_versions VALUES('cust',1,'Synthetic customer','',1,'owner',1,2)")
    names=["catalog","stock_movements","cashier_shifts","cashier_drawer_effects","checkout_requests","checkout_sales","checkout_cash"]
    before=snapshot(db,names)
    for file in ("ReceivableJournal.java","CreditCashJournal.java","CreditCheckoutJournal.java"):
        for sql in re.findall(r'db\.execSQL\("([^"]+)"\);',(root/file).read_text()): db.execute(sql)
    check(snapshot(db,names)==before,"candidate migration no original data change")
    def stock():return db.execute("SELECT quantity,stock_revision FROM credit_checkout_current_stock WHERE id='paper'").fetchone()
    req="INSERT INTO credit_checkout_requests VALUES("+",".join("?"*18)+")"
    def proposal(id,paid=200,rate=500,total=500,service_rev=1,recipe_rev=1,rev=1,actor="staff",at=5):
        return (id,"cust",rev,"open",actor,1,"a"*64,"service",service_rev,0,recipe_rev,100,100,rate,total,paid,1,at,"PENDING",None,None,None)
    req="INSERT INTO credit_checkout_requests VALUES("+",".join("?"*22)+")"
    mat="INSERT INTO credit_checkout_materials VALUES(?,?,?,?,?,?,?,?,?,?)"
    def material(id,move,srev=1,before=10000,qty=1000):
        return (id,"paper",move,"sheet",1,srev,1000,qty,before,before-qty)
    def resolve_credit(id,state="APPLIED",at=7):
        db.execute("UPDATE credit_checkout_requests SET state=?,resolved_ms=?,resolved_revision=1,cancel_reason=? WHERE id=?",(state,at,None if state=="APPLIED" else "Synthetic cancel",id))
    def inputs(id):db.execute("INSERT INTO credit_checkout_inputs VALUES(?,1,1,1,NULL,NULL,NULL,'BILLABLE_UNIT')",(id,))
    def source_count():return db.execute("SELECT COUNT(*) FROM credit_receivable_sources").fetchone()[0]
    def state_credit(id):return db.execute("SELECT state FROM credit_checkout_requests WHERE id=?",(id,)).fetchone()[0]
    reject(db,req,proposal("bad",actor="owner"));reject(db,req,proposal("bad",paid=500))
    reject(db,"INSERT INTO credit_receivable_sources VALUES('fake','cust',1,'open','staff',1,?,500,200,6)",("a"*64,))
    db.execute(req,proposal("partial"))
    reject(db,req,proposal("second"))
    reject(db,mat,material("partial","bad",qty=999))
    reject(db,mat,material("partial","bad",srev=2))
    reject(db,"UPDATE credit_checkout_requests SET state='APPLIED',resolved_ms=7,resolved_revision=1 WHERE id='partial'")
    db.execute(mat,material("partial","movement1"))
    reject(db,"UPDATE credit_checkout_requests SET state='APPLIED',resolved_ms=7,resolved_revision=1 WHERE id='partial'")
    inputs("partial")
    reject(db,"UPDATE credit_checkout_inputs SET input_count=2 WHERE request_id='partial'")
    reject(db,"DELETE FROM credit_checkout_inputs WHERE request_id='partial'")
    reject(db,"INSERT INTO credit_checkout_inputs VALUES('partial',1,1,1,NULL,NULL,NULL,'BILLABLE_UNIT')")

    reject(db,mat,material("partial","movement2"))
    reject(db,"UPDATE credit_checkout_materials SET quantity_milli=999 WHERE request_id='partial'")
    check(stock()==(10000,1) and cash(db,"open")==1000 and source_count()==0,"staging no financial effects")
    reject(db,insert,params("block",sid="open",actor="staff",amount=100,before=1000))
    reject(db,"UPDATE cashier_shifts SET state='CLOSED',closed_ms=20,expected_cash=1000,counted_cash=1000,variance=0 WHERE id='open'")
    reject(db,"INSERT INTO credit_collection_requests VALUES('block','cust',1,'open','staff',1,100,1000,1100,6,'PENDING',NULL,NULL,NULL)")
    db.execute("CREATE TRIGGER synthetic_fault BEFORE INSERT ON cashier_cash_effects_v2 WHEN NEW.credit_id='partial' BEGIN SELECT RAISE(ABORT,'fault'); END")
    try: resolve_credit("partial")
    except sqlite3.IntegrityError: pass
    else: raise AssertionError("credit fault did not roll back")
    check(stock()==(10000,1) and cash(db,"open")==1000 and source_count()==0 and state_credit("partial")=="PENDING","stock/debt/cash rollback together")
    db.execute("DROP TRIGGER synthetic_fault");resolve_credit("partial")
    check(stock()==(9000,2) and cash(db,"open")==1200 and source_count()==1,"atomic partial sale")
    check(db.execute("SELECT input_count,pages,copies,basis FROM credit_checkout_inputs WHERE request_id='partial'").fetchone()==(1,1,1,"BILLABLE_UNIT"),"raw inputs persisted")
    check(db.execute("SELECT remaining FROM credit_receivable_open WHERE id='partial'").fetchone()==(300,),"partial debt")
    reject(db,"UPDATE credit_checkout_requests SET state='APPLIED' WHERE id='partial'")
    reject(db,"UPDATE stock_movements SET reason='altered' WHERE request_id='movement1'")
    reject(db,"DELETE FROM stock_movements WHERE request_id='movement1'")
    db.execute(req,proposal("unpaid",paid=0,at=8));inputs("unpaid");db.execute(mat,material("unpaid","movement2",srev=2,before=9000));resolve_credit("unpaid",at=9)
    check(stock()==(8000,3) and cash(db,"open")==1200 and source_count()==2,"unpaid consumes stock without cash")
    db.execute(req,proposal("stale",at=10));inputs("stale");db.execute(mat,material("stale","movement3",srev=3,before=8000))
    db.execute("INSERT INTO stock_movements VALUES('other-move','paper',1,4,'sheet','Synthetic competing adjustment',1000,8000,9000,11)")
    reject(db,"UPDATE credit_checkout_requests SET state='APPLIED',resolved_ms=12,resolved_revision=1 WHERE id='stale'")
    resolve_credit("stale","CANCELLED",12)
    check(stock()==(9000,4) and source_count()==2 and cash(db,"open")==1200,"stale cancel no sale")
    db.execute(req,proposal("price",rate=499,total=499,at=13));inputs("price");db.execute(mat,material("price","movement4",srev=4,before=9000))
    reject(db,"UPDATE credit_checkout_requests SET state='APPLIED',resolved_ms=14,resolved_revision=1 WHERE id='price'")
    resolve_credit("price","CANCELLED",14)
    db.execute(req,proposal("cancel",at=15));inputs("cancel");db.execute(mat,material("cancel","movement5",srev=4,before=9000));resolve_credit("cancel","CANCELLED",16)
    check(stock()==(9000,4) and cash(db,"open")==1200 and source_count()==2,"cancellation no stock or debt")
    close(db,"open",1200,1200)
    check(not db.execute("PRAGMA foreign_key_check").fetchall(),"foreign key integrity")
print(f"Credit checkout candidate SQLite: {prior.checks-start} checks PASS")
