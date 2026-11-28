import test from 'node:test';
import assert from 'node:assert/strict';
import {isBelowMinimumThreshold,normalizeInventoryItem,validateInventoryItemInput,buildInventoryAlerts,getInventoryStockStatus,getSuggestedRestockQuantity} from '../src/shared/inventoryModel.ts';
test('critical threshold is based on unreserved available stock',()=>{
 const item={quantityOnHand:100,reservedQuantity:60,minimumThreshold:50};
 assert.equal(isBelowMinimumThreshold(item),true);
 assert.equal(isBelowMinimumThreshold({...item,reservedQuantity:50}),false);
 assert.equal(isBelowMinimumThreshold({...item,minimumThreshold:0}),false);
});
test('legacy inventory has a disabled critical threshold',()=>assert.equal(normalizeInventoryItem({id:'legacy',name:'Paper'}).minimumThreshold,0));
test('critical threshold validation rejects invalid values and reversed thresholds',()=>{
 const item={name:'Paper',unit:'sheet',reorderLevel:100};
 for(const minimumThreshold of [-1,101,NaN,Infinity,'50',null]) assert.equal(validateInventoryItemInput({...item,minimumThreshold}).valid,false);
 assert.equal(validateInventoryItemInput({...item,minimumThreshold:50}).valid,true);
 assert.equal(validateInventoryItemInput({...item,minimumThreshold:0}).valid,true);
});

test('alerts prioritize exhausted then critical then reorder available stock',()=>{
 const item=(id,stock,reserved)=>normalizeInventoryItem({id,name:id,quantityOnHand:stock,reservedQuantity:reserved,reorderLevel:50,minimumThreshold:20,reorderQuantity:10});
 const alerts=buildInventoryAlerts([item('healthy',100,0),item('reorder',100,60),item('critical',100,90),item('out',100,100)]);
 assert.deepEqual(alerts.map(a=>[a.item.id,a.severity,a.available]),[['out','out',0],['critical','critical',10],['reorder','reorder',40]]);
 assert.equal(getInventoryStockStatus(item('out',100,100)),'out_of_stock');
 assert.equal(getSuggestedRestockQuantity(item('out',100,100)),50);
 assert.notEqual(alerts[1].signature,buildInventoryAlerts([item('critical',100,100)])[0].signature);
});
