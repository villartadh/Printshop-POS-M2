import test from 'node:test';
import assert from 'node:assert/strict';
import {isLedgerMoney,cents,sumCents,fromCents} from '../src/shared/money.ts';
import {validateTransaction} from '../src/shared/transactionValidation.ts';
const sale={clientMutationId:'id',type:'sale',items:[{lineSubtotal:10.1,lineDiscount:0,lineTotal:10.1,effectiveQuantity:1,unitPrice:10.1,consumables:[]}],subtotal:10.1,itemDiscountsTotal:0,orderDiscountAmount:0,taxAmount:0,totalAmount:10.1,amountPaid:10.1,changeAmount:0,balanceDue:0,paymentStatus:'paid',payments:[{methodType:'cash',amount:10.1}]};
test('integer centavos accept ordinary floating point representation but reject fractions and coercion',()=>{
 assert.equal(sumCents([0.1,0.2]),30);assert.equal(fromCents(cents(0.1+0.2)),0.3);
 for(const x of [null,'1',true,Infinity,NaN,-1,1.001,1e10])assert.equal(isLedgerMoney(x),false);
});
test('one-centavo discrepancies and fractional payment amounts are rejected',()=>{
 assert.doesNotThrow(()=>validateTransaction(sale));
 for(const patch of [{amountPaid:10.09},{balanceDue:0.01},{payments:[{methodType:'cash',amount:10.09}]},{payments:[{methodType:'cash',amount:10.101}]},{changeAmount:0.01}])assert.throws(()=>validateTransaction({...sale,...patch}));
});
test('mixed tender balances reconcile in exact centavos',()=>{
 assert.doesNotThrow(()=>validateTransaction({...sale,payments:[{methodType:'cash',amount:5.3},{methodType:'ewallet',amount:4.8}]}));
});
