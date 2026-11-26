"""Receipt-linked compensation against actual v1-v12 SQLite DDL, not Android acceptance."""
import drawer_journal_schema_test as prior_test
from drawer_journal_schema_test import *
refund=statements("RefundJournal.java")
assert len(refund)==7
start=prior_test.checks
for former in range(1,12):
    with sqlite3.connect(":memory:") as db:
        db.execute("PRAGMA foreign_keys=ON")
        db.execute("PRAGMA recursive_triggers=OFF")
        for version,sqls in stages:
            if version<=former:
                for sql in sqls: db.execute(sql)
        db.execute("INSERT INTO catalog VALUES('service','SERVICE','Synthetic service','page',100,1,0)")
        for version,sqls in stages:
            if version>former:
                for sql in sqls: db.execute(sql)
        account(db,"owner","OWNER");account(db,"staff","CASHIER")
        shift(db,"original","staff",0)
        sale_stage(db,"sale-a","original","staff",500);sale_apply(db,"sale-a",500)
        sale_stage(db,"sale-b","original","staff",300);sale_apply(db,"sale-b",300)
        close(db,"original",800,800)
        for sql in drawer: db.execute(sql)
        shift(db,"open","owner",1000)
        stage(db,"ordinary",kind="CASH_IN",category=None,amount=100,before=1000);resolve(db,"ordinary")
        names=["checkout_requests","checkout_cash","checkout_sales","checkout_stock_links","stock_movements","cashier_drawer_requests","cashier_drawer_effects","cashier_shifts"]
        old=snapshot(db,names)
        db.commit()
        for sql in refund: db.execute(sql)
        check(snapshot(db,names)==old,"v12 additive migration")
        check(db.execute("SELECT count(*) FROM cashier_refund_links").fetchone()==(0,),"no fabricated refunds")
        def link(req,sale,kind,paid,prior):
            db.execute("INSERT INTO cashier_refund_links VALUES(?,?,?,?,?)",(req,sale,kind,paid,prior))
        def compensation(req,sale,kind,paid,prior,amount,before):
            with db:
                stage(db,req,kind="CASH_OUT",category=None,amount=amount,before=before)
                link(req,sale,kind,paid,prior)
        def bad_comp(req,sale,kind,paid,prior,amount,before):
            db.commit()
            try: compensation(req,sale,kind,paid,prior,amount,before)
            except sqlite3.IntegrityError: check(True,"invalid compensation rejected")
            else: raise AssertionError("invalid compensation accepted")
            check(state(db,req) is None,"invalid link rolled back staging")
        bad_comp("over","sale-a","REFUND",500,0,501,1100)
        bad_comp("wrong","sale-a","REFUND",499,0,100,1100)
        bad_comp("partial-void","sale-a","VOID",500,0,100,1100)
        bad_comp("missing","missing","REFUND",500,0,100,1100)
        compensation("partial","sale-a","REFUND",500,0,200,1100)
        reject(db,"DELETE FROM cashier_refund_links WHERE drawer_id='partial'")
        reject(db,"UPDATE cashier_refund_links SET checkout_id='sale-b' WHERE drawer_id='partial'")
        reject(db,"INSERT OR REPLACE INTO cashier_refund_links VALUES('partial','sale-b','REFUND',300,0)")
        reject(db,"INSERT INTO cashier_refund_links VALUES('ordinary','sale-a','REFUND',500,0)")
        reject(db,"UPDATE cashier_shifts SET state='CLOSED',closed_ms=20,expected_cash=1100,counted_cash=1100,variance=0 WHERE id='open'")
        db.execute("CREATE TRIGGER refund_fault BEFORE INSERT ON cashier_drawer_effects BEGIN SELECT RAISE(ABORT,'fault'); END")
        reject(db,"UPDATE cashier_drawer_requests SET state='APPLIED',resolved_revision=1,resolved_ms=8 WHERE id='partial'")
        check(cash(db,"open")==1100 and state(db,"partial")[0]=="PENDING","fault rollback")
        db.execute("DROP TRIGGER refund_fault")
        resolve(db,"partial")
        check(cash(db,"open")==900,"partial exact cash")
        check(db.execute("SELECT compensated FROM cashier_refund_totals WHERE checkout_id='sale-a'").fetchone()==(200,),"partial total")
        bad_comp("stale","sale-a","REFUND",500,0,100,900)
        bad_comp("after-refund-void","sale-a","VOID",500,200,300,900)
        bad_comp("over-remaining","sale-a","REFUND",500,200,301,900)
        compensation("cancel","sale-a","REFUND",500,200,300,900);resolve(db,"cancel",apply=False)
        check(cash(db,"open")==900,"cancel no effect")
        compensation("final","sale-a","REFUND",500,200,300,900);resolve(db,"final")
        compensation("void","sale-b","VOID",300,0,300,600);resolve(db,"void")
        check(cash(db,"open")==300,"full compensation exact cash")
        check(db.execute("SELECT checkout_id,compensated FROM cashier_refund_totals ORDER BY 1").fetchall()==[("sale-a",500),("sale-b",300)],"cumulative exact cap")
        bad_comp("exhausted","sale-a","REFUND",500,500,1,300)
        check(snapshot(db,names[:5])=={n:old[n] for n in names[:5]},"original sale/payment/inventory untouched")
        check(db.execute("SELECT expected_cash,counted_cash,variance FROM cashier_shifts WHERE id='original'").fetchone()==(800,800,0),"original closed shift untouched")
        check(not db.execute("PRAGMA foreign_key_check").fetchall(),"foreign keys")
print(f"Refund journal actual SQLite: {prior_test.checks-start} additional checks PASS across 11 synthetic migration fixtures")
