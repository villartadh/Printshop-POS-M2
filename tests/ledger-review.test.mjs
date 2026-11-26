import test from 'node:test';
import assert from 'node:assert/strict';
import {reconcileLedger} from '../scripts/ledger-review.mjs';
const sale={id:'s',customer_id:'c',total_amount:40,amount_paid:25,balance_due:15,change_amount:0,payment_status:'partial',payments_json:JSON.stringify([{id:'initial',methodType:'cash',amount:15},{id:'collect',methodType:'cash',amount:10}])};
const fixture=()=>({sales:[{...sale}],customers:[{id:'c',outstanding_balance:15}],collections:[{id:'collect',transaction_id:'s',customer_id:'c',amount:10}],reversals:[]});
test('non-fiscal ledger review reconciles collections and outstanding balances in centavos',()=>assert.equal(reconcileLedger(fixture()).status,'PASS'));
test('ledger review identifies corrupted financial amounts and collection references without changing records',()=>{
 const f=fixture();f.sales[0].amount_paid=26;f.collections[0].amount=11;f.customers[0].outstanding_balance=14;
 const before=JSON.stringify(f);const result=reconcileLedger(f);
 assert.equal(result.status,'REVIEW_REQUIRED');
 assert.deepEqual(result.findings.map(x=>x.code).sort(),['COLLECTION_PAYMENT_MISMATCH','CUSTOMER_BALANCE_MISMATCH','PAYMENT_TOTAL_MISMATCH','SALE_TOTAL_MISMATCH'].sort());
 assert.equal(JSON.stringify(f),before);
});
test('ledger review excludes reversed receivables and detects missing reversal records',()=>{
 const f=fixture();f.collections=[];f.sales[0].payment_status='refunded';f.customers[0].outstanding_balance=0;
 assert.equal(reconcileLedger(f).findings[0].code,'REVERSAL_MISMATCH');
 f.reversals=[{transaction_id:'s',kind:'refunded',cash_amount:25,electronic_amount:0,cancelled_receivable:15}];
 assert.equal(reconcileLedger(f).status,'PASS');
});
