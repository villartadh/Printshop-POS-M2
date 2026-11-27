"""Actual SQLite v14 customer revision migration and ownership, no cash changes."""
import drawer_journal_schema_test as prior
from drawer_journal_schema_test import *
customer=statements("CustomerJournal.java")
assert len(customer)==5
start=prior.checks
with sqlite3.connect(":memory:") as db:
    db.execute("PRAGMA foreign_keys=ON")
    db.execute("PRAGMA recursive_triggers=OFF")
    for _,sqls in stages:
        for sql in sqls: db.execute(sql)
    account(db,"owner","OWNER");account(db,"staff","CASHIER")
    db.execute("INSERT INTO catalog VALUES('service','SERVICE','Synthetic service','page',100,1,0)")
    shift(db,"open","owner",1000)
    sale_stage(db,"historic","open","owner",100);sale_apply(db,"historic",100)
    for sql in drawer+statements("RefundJournal.java")+statements("RecurringJournal.java"): db.execute(sql)
    db.execute("INSERT INTO cashier_recurring_versions VALUES(?,?,?,?,?,?,?,?,?,?,?)",("historical-schedule",1,"Synthetic rent","Rent",200,"MONTHLY","2024-01-31",1,"owner",1,2))
    names=["cashier_accounts","cashier_shifts","checkout_requests","checkout_sales","checkout_cash","cashier_drawer_effects","cashier_recurring_versions"]
    before=snapshot(db,names)
    for sql in customer: db.execute(sql)
    check(snapshot(db,names)==before,"migration preserves old accounts sales and financial history")
    check(db.execute("SELECT COUNT(*) FROM cashier_customer_versions").fetchone()==(0,),"no invented customers")
    insert="INSERT INTO cashier_customer_versions VALUES(?,?,?,?,?,?,?,?)"
    def row(id="cust-1",rev=1,name="Synthetic Alpha",contact="Example contact",active=1,actor="owner",ar=1,at=10):
        return (id,rev,name,contact,active,actor,ar,at)
    def save(**kw): db.execute(insert,row(**kw))
    def current(id="cust-1"): return db.execute("SELECT revision,name,contact,active FROM cashier_customer_current WHERE id=?",(id,)).fetchone()
    reject(db,insert,row(actor="staff"));reject(db,insert,row(ar=2))
    reject(db,insert,row(active=0));reject(db,insert,row(rev=2));reject(db,insert,row(name=""))
    reject(db,insert,row(contact="x"*121));reject(db,insert,row(name="Bad\nName"));reject(db,insert,row(name=" Leading"))
    reject(db,insert,row(at=0));reject(db,insert,row(rev=1.5));reject(db,insert,row(name="x"*81))
    save();check(current()==(1,"Synthetic Alpha","Example contact",1),"create customer")
    reject(db,insert.replace("INSERT","INSERT OR REPLACE"),row(name="overwrite"))
    reject(db,"UPDATE cashier_customer_versions SET contact='changed' WHERE id='cust-1'")
    reject(db,"DELETE FROM cashier_customer_versions WHERE id='cust-1'")
    reject(db,insert,row(rev=3));reject(db,insert,row(rev=2,at=9))
    save(rev=2,name="Synthetic Alpha Updated",contact="",at=11)
    check(current()==(2,"Synthetic Alpha Updated","",1),"revision updates current view")
    save(rev=3,name="Synthetic Alpha Updated",contact="",active=0,at=12)
    check(current()[3]==0,"archive retains identity")
    save(rev=4,name="Synthetic Alpha Updated",contact="",at=13)
    check(current()==(4,"Synthetic Alpha Updated","",1),"restore same identity")
    save(id="cust-2",name="Synthetic Alpha Updated",contact="")
    save(id="cust-3",name="Literal 100%_ test",contact="")
    for i in range(10): save(id=f"test-{i}",name=f"Synthetic Page {i}",contact="")
    query="SELECT id,revision,name,contact,active,actor_id,actor_revision,recorded_ms FROM cashier_customer_current WHERE active=? AND instr(lower(name),lower(?))>0 ORDER BY name COLLATE NOCASE,id LIMIT ? OFFSET ?"
    source=(root/"CustomerJournal.java").read_text()
    assert query.split(" FROM ",1)[1] in source
    def page(search="",offset=0,limit=9,active=1): return db.execute(query,(str(active),search,str(limit),str(offset))).fetchall()
    check([r[0] for r in page("ALPHA")]==["cust-1","cust-2"],"case insensitive and stable duplicate names")
    check([r[0] for r in page("%_")]==["cust-3"],"literal search not wildcard")
    check(page("missing")==[],"empty result")
    check(len(page("page"))==9 and len(page("page",8))==2,"lookahead pagination")
    check(page("page")[8]==page("page",8)[0],"stable page boundary")
    check(page(active=0)==[],"restored customer active only")
    save(rev=2,id="cust-2",name="Synthetic Alpha Updated",contact="",active=0,at=11)
    check([r[0] for r in page(active=0)]==["cust-2"],"archive filter")
    check(db.execute("SELECT revision FROM cashier_customer_versions WHERE id='cust-1' ORDER BY revision DESC").fetchall()==[(4,),(3,),(2,),(1,)],"immutable history")
    check(snapshot(db,names)==before,"customer writes do not alter money or prior sales")
    check(not db.execute("PRAGMA foreign_key_check").fetchall(),"foreign key integrity")
print(f"Customer journal actual SQLite: {prior.checks-start} checks PASS")
