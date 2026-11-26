"""Exercise actual report SQL against immutable v12 fixture sources."""
import drawer_journal_schema_test as prior
from drawer_journal_schema_test import *
source=(root/"ReportJournal.java").read_text()
events=re.search(r'String EVENTS="([^"]+)"',source).group(1)
closes=re.search(r'String CLOSES="([^"]+)"',source).group(1)
count=prior.checks
with sqlite3.connect(":memory:") as db:
    db.execute("PRAGMA foreign_keys=ON")
    for _,sqls in stages:
        for sql in sqls: db.execute(sql)
    db.execute("INSERT INTO catalog VALUES('service','SERVICE','Synthetic service','page',100,1,0)")
    account(db,"owner","OWNER");account(db,"staff","CASHIER")
    shift(db,"original","staff",0)
    sale_stage(db,"sale-a","original","staff",500);sale_apply(db,"sale-a",500,at=5)
    sale_stage(db,"sale-b","original","staff",300);sale_apply(db,"sale-b",300,at=6)
    close(db,"original",800,790)
    for sql in drawer+statements("RefundJournal.java"): db.execute(sql)
    shift(db,"open","owner",1000)
    def activity(req,kind,amount,before,at,apply=True):
        stage(db,req,kind=kind,category="Synthetic supplies" if kind=="EXPENSE" else None,amount=amount,before=before,at=at)
        resolve(db,req,at=at,apply=apply)
    def compensate(req,original,kind,paid,amount,before,at,apply=True):
        stage(db,req,kind="CASH_OUT",category=None,amount=amount,before=before,at=at)
        db.execute("INSERT INTO cashier_refund_links VALUES(?,?,?,?,?)",(req,original,kind,paid,0))
        resolve(db,req,at=at,apply=apply)
    activity("float","CASH_IN",100,1000,7)
    compensate("partial","sale-a","REFUND",500,200,1100,8)
    compensate("void","sale-b","VOID",300,300,900,9)
    activity("supplies","EXPENSE",100,600,10)
    activity("withdraw","CASH_OUT",100,500,11)
    activity("cancel","CASH_OUT",100,400,12,False)
    stage(db,"pending",kind="EXPENSE",amount=100,before=400,at=13)
    def report(a,b):
        return db.execute(events,(a,b,a,b)).fetchall()
    def totals(a,b):
        t={}
        for k,amt,*_ in report(a,b): t[k]=t.get(k,0)+amt
        return t
    names=["checkout_requests","checkout_sales","checkout_cash","stock_movements","cashier_shifts","cashier_drawer_requests","cashier_drawer_effects","cashier_refund_links"]
    original=snapshot(db,names)
    check(totals(0,5)=={},"exclusive end")
    check(totals(5,6)=={"SALE":500},"inclusive start")
    check(totals(5,7)=={"SALE":800},"legacy sales counted once")
    check(totals(7,14)=={"CASH_IN":100,"REFUND":200,"VOID":300,"EXPENSE":100,"CASH_OUT":100},"resolution dates and mutually exclusive classifications")
    check(totals(8,10)=={"REFUND":200,"VOID":300},"compensation-only negative net")
    check(totals(12,15)=={},"cancelled/pending excluded")
    check(totals(0,100)=={"SALE":800,"CASH_IN":100,"REFUND":200,"VOID":300,"EXPENSE":100,"CASH_OUT":100},"total exact gross and cash components")
    rows=report(0,100)
    check(len(rows)==7 and len({r[5] for r in rows})==7,"one source event each")
    check(all(r[2:4]==("service","Synthetic service") for r in rows if r[0] in ("SALE","REFUND","VOID")),"immutable service attribution")
    check(all(r[2] is None and r[3] is None for r in rows if r[0] in ("CASH_IN","CASH_OUT","EXPENSE")),"no service allocation to adjustments")
    check(db.execute(closes,(0,20)).fetchall()==[],"close boundary exclusive")
    check(db.execute(closes,(20,21)).fetchall()==[(800,790,-10)],"closed shifts reconciled separately")
    check(snapshot(db,names)==original,"report read only")
    check(cash(db,"open")==400,"cash chain unchanged")
    resolve(db,"pending",at=14,apply=False)
    close(db,"open",400,405)
    check(sorted(db.execute(closes,(20,21)).fetchall())==[(400,405,5),(800,790,-10)],"signed variance preservation")
    # Stream individual bounded values; do not invoke SQLite SUM on an unbounded report.
    shift(db,"large","owner",0)
    for i in range(930):
        stage(db,f"in-{i}",sid="large",kind="CASH_IN",category=None,amount=MAX,before=0,at=21);resolve(db,f"in-{i}",at=22)
        stage(db,f"out-{i}",sid="large",kind="CASH_OUT",category=None,amount=MAX,before=MAX,at=22);resolve(db,f"out-{i}",at=23)
    check(totals(21,24)=={"CASH_IN":MAX*930,"CASH_OUT":MAX*930} and MAX*930>2**63-1,"large gross exact without SQL SUM")
    check(cash(db,"large")==0,"large chain unchanged")
print(f"Report journal actual SQLite: {prior.checks-count} checks PASS")
