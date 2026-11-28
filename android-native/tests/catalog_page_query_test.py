"""Actual SQLite catalog browsing query; not Android/session acceptance."""
from pathlib import Path
import re
import sqlite3

root=Path(__file__).resolve().parents[1]/"app/src/main/java/ph/printshop/pos"
source=(root/"CatalogStore.java").read_text()
sql=re.search(r'db\.rawQuery\("(SELECT \* FROM catalog WHERE kind=\? AND archived=\? ORDER BY name COLLATE NOCASE,id LIMIT \? OFFSET \?)"',source).group(1)
ddl=re.search(r'db\.execSQL\("(CREATE TABLE catalog \([^"]+)"\);',source)
assert ddl
size=int(re.search(r'PAGE_SIZE=(\d+)',(root/"TrainingForms.java").read_text()).group(1))
checks=0
def check(ok):
    global checks
    assert ok
    checks+=1
with sqlite3.connect(":memory:") as db:
    db.execute(ddl.group(1))
    rows=[]
    for kind in ("SERVICE","MATERIAL"):
        for archived in (0,1):
            for i in range(19 if kind=="SERVICE" and not archived else 8 if kind=="MATERIAL" and not archived else 2):
                ident=f"{kind}-{archived}-{i:02}"
                name=["ALPHA","alpha","Beta","delta"][i%4]
                rows.append((ident,kind,name,"unit",100,1,archived))
    db.executemany("INSERT INTO catalog VALUES(?,?,?,?,?,?,?)",rows)
    def page(kind,archived,offset):
        # Android rawQuery binds all parameters as strings.
        result=db.execute(sql,(kind,str(archived),str(size+1),str(offset))).fetchall()
        return result[:size],len(result)>size
    seen=[]
    for offset,expected,more in ((0,8,True),(8,8,True),(16,3,False),(24,0,False)):
        entries,further=page("SERVICE",0,offset)
        check(len(entries)==expected and further==more)
        check(all(r[1]=="SERVICE" and r[6]==0 for r in entries))
        seen.extend(entries)
    check(len(seen)==len({r[0] for r in seen})==19)
    check(seen==sorted(seen,key=lambda r:(r[2].lower(),r[0])))
    for kind,archived,expected in (("SERVICE",1,2),("MATERIAL",0,8),("MATERIAL",1,2),("UNKNOWN",0,0)):
        entries,more=page(kind,archived,0)
        check(len(entries)==expected and not more)
        check(all(r[1]==kind and r[6]==archived for r in entries))
    db.execute("UPDATE catalog SET archived=1 WHERE id='SERVICE-0-00'")
    check(all(r[0]!="SERVICE-0-00" for r in page("SERVICE",0,0)[0]))
    check(any(r[0]=="SERVICE-0-00" for r in page("SERVICE",1,0)[0]))
print(f"Catalog page actual SQLite: {checks} checks PASS")
