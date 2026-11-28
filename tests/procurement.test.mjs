import test from 'node:test';
import assert from 'node:assert/strict';
import {normalizeInventoryItem} from '../src/shared/inventoryModel.ts';
import {reorderRows,groupReorderRows,validOrderQuantity,procurementCsvCell} from '../src/shared/procurement.ts';
const item=(id,stock,reserved,supplier='Paper Co')=>normalizeInventoryItem({id,name:id,quantityOnHand:stock,reservedQuantity:reserved,reorderLevel:50,minimumThreshold:20,reorderQuantity:30,unitCost:2,supplier});
test('procurement uses available stock, excludes healthy and covers reorder shortfall',()=>{
 const rows=reorderRows([item('critical',100,90),item('reorder',100,60),item('healthy',100,0)]);
 assert.deepEqual(rows.map(r=>[r.item.id,r.available,r.quantity,r.estimatedCost]),[['critical',10,40,80],['reorder',40,30,60]]);
 assert.equal(reorderRows(rows.map(r=>r.item),'critical').length,1);
});
test('supplier groups preserve unassigned materials',()=>assert.deepEqual(groupReorderRows(reorderRows([item('a',0,0),item('b',0,0),item('c',0,0,'')])).map(g=>g.rows.length),[2,1]));
test('draft quantities and spreadsheet text are validated',()=>{
 for(const n of [-1,NaN,Infinity,0.001,1e10])assert.equal(validOrderQuantity(n),false);
 assert.equal(validOrderQuantity(1.25),true);
 for(const s of ['=1+1',' +1','@SUM(A1)','-cmd','\tdata'])assert.equal(procurementCsvCell(s),"'"+s);
 assert.equal(procurementCsvCell(-2),-2);assert.equal(procurementCsvCell('Paper'), 'Paper');
});
