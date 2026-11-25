import test from 'node:test';
import assert from 'node:assert/strict';
import {isBelowMinimumThreshold,normalizeInventoryItem,validateInventoryItemInput} from '../src/shared/inventoryModel.ts';
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
