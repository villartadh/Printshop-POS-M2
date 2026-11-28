import test from 'node:test';
import assert from 'node:assert/strict';
import {validatePurchaseCost,weightedPurchaseCost} from '../src/shared/purchaseCost.ts';
test('weighted purchase cost retains prior stock value and uses actual purchase quantity',()=>{
 assert.equal(weightedPurchaseCost(100,2,50,5),3);
 assert.equal(weightedPurchaseCost(0,2,50,5),5);
 assert.equal(weightedPurchaseCost(100,2,50,0),1.3333);
 assert.equal(weightedPurchaseCost(100,2,-10,undefined),2);
});
test('supplier cost validation is strict and restricted to authorized purchases',()=>{
 for(const cost of [-1,Infinity,NaN,'1',true,null,1.00001,1e10])assert.throws(()=>validatePurchaseCost(cost,'purchase','admin'));
 assert.throws(()=>validatePurchaseCost(1,'wastage','admin'));
 assert.throws(()=>validatePurchaseCost(1,'purchase','cashier'),/Only administrators/);
 assert.doesNotThrow(()=>validatePurchaseCost(0,'purchase','manager'));
 assert.doesNotThrow(()=>validatePurchaseCost(undefined,'adjustment','cashier'));
});
