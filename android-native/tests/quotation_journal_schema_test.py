"""SQLite v17 saved quote audit and preservation from synthetic v16 fixture."""
from drawer_journal_schema_test import *
import tempfile
from contextlib import closing
with tempfile.TemporaryDirectory() as temp, closing(sqlite3.connect(str(Path(temp)/"quote.db"))) as db, db:
    path=str(Path(temp)/"quote.db")
    db.execute("PRAGMA foreign_keys=ON")
    for _,sqls in stages:
        for sql in sqls: db.execute(sql)
    account(db,"owner","OWNER");account(db,"staff","CASHIER");account(db,"other","CASHIER")
    db.execute("INSERT INTO catalog VALUES('service','SERVICE','Synthetic print','page',500,1,0)")
    db.execute("INSERT INTO catalog_history VALUES('service',1,'SERVICE','Synthetic print','page',500,0,'CREATE')")
    db.execute("INSERT INTO catalog VALUES('paper','MATERIAL','Synthetic paper','sheet',100,1,0)")
    db.execute("INSERT INTO stock_movements VALUES('initial','paper',1,1,'sheet','Synthetic initial',10000,0,10000,1)")
    shift(db,"open","staff",1000)
    for sql in drawer+statements("RefundJournal.java")+statements("RecurringJournal.java")+statements("CustomerJournal.java")+statements("CustomerLinkJournal.java"): db.execute(sql)
    db.execute("INSERT INTO cashier_customer_versions VALUES('cust',1,'Synthetic customer','',1,'owner',1,2)")
    for file in ("ReceivableJournal.java","CreditCashJournal.java","CreditCheckoutJournal.java"):
        for sql in re.findall(r'db\.execSQL\("([^"]+)"\);',(root/file).read_text()): db.execute(sql)
    names=["catalog","catalog_history","stock_movements","cashier_shifts","cashier_cash_effects_v2","checkout_requests","checkout_sales","credit_checkout_requests","credit_receivable_sources","cashier_customer_versions"]
    before=snapshot(db,names)
    for sql in statements("QuotationJournal.java"): db.execute(sql)
    check(snapshot(db,names)==before,"v17 migration preserves original data")
    def quote(id,actor="staff",rev=1,customer="cust",cr=1,service="service",sr=1,total=500,convertible=1):
        return (id,actor,rev,customer,cr,service,sr,total,"canonical-v1",0,1,convertible,5)
    # Schema has 13 columns.
    insert="INSERT INTO saved_quotations VALUES("+",".join("?"*13)+")"
    reject(db,insert,quote("unknown",actor="missing"))
    reject(db,insert,quote("stale",rev=2))
    reject(db,insert,quote("stale-service",sr=2))
    reject(db,insert,quote("stale-customer",cr=2))
    reject(db,insert,quote("half-customer",cr=None))
    reject(db,insert,quote("bad-total",total=-1))
    reject(db,insert,quote("zero-convertible",total=0))
    db.execute(insert,quote("q1"))
    db.execute(insert,quote("q2",customer=None,cr=None,total=0,convertible=0))
    reject(db,insert,quote("q1"))
    reject(db,"UPDATE saved_quotations SET total_centavos=1 WHERE id='q1'")
    reject(db,"DELETE FROM saved_quotations WHERE id='q1'")
    cancel="INSERT INTO saved_quotation_cancellations VALUES(?,?,?,?,?)"
    reject(db,cancel,("q1","other",1,"wrong actor",7))
    reject(db,cancel,("q1","staff",2,"stale revision",7))
    reject(db,cancel,("q1","staff",1,"",7))
    reject(db,cancel,("q1","staff",1,"backdated",4))
    db.execute(cancel,("q1","owner",1,"Synthetic cancellation",7))
    reject(db,cancel,("q1","owner",1,"duplicate",8))
    reject(db,"UPDATE saved_quotation_cancellations SET reason='changed'")
    reject(db,"DELETE FROM saved_quotation_cancellations")
    check(snapshot(db,names)==before,"quotation save/cancel has no financial or inventory effects")
    db.commit()
    with closing(sqlite3.connect(path)) as reopened:
        check(reopened.execute("SELECT total_centavos,snapshot,stock_sufficient,stock_pending FROM saved_quotations WHERE id='q1'").fetchone()==(500,"canonical-v1",0,1),"saved review survives reopen")
        check(reopened.execute("SELECT actor_id,reason FROM saved_quotation_cancellations WHERE quotation_id='q1'").fetchone()==("owner","Synthetic cancellation"),"cancellation survives reopen")
        check(reopened.execute("PRAGMA foreign_key_check").fetchall()==[],"foreign keys intact")
    db.execute("INSERT INTO cashier_customer_versions VALUES('cust',2,'Renamed customer','',1,'owner',1,9)")
    reject(db,insert,quote("old-customer"))
    check(db.execute("SELECT customer_revision FROM saved_quotations WHERE id='q1'").fetchone()==(1,),"historical customer revision frozen")
print("QUOTATION_JOURNAL_SCHEMA_PASS")
