import test from 'node:test';
import assert from 'node:assert/strict';
import {transactionRequestDigest} from '../src/server/transactionIdentity.ts';
const original={clientMutationId:'x',items:[{quantity:2,serviceId:'s'}],payments:[{methodId:'cash',amount:20}],totalAmount:20,syncStatus:'synced'};
test('transaction identity ignores only transport and authoritative receipt metadata',()=>{
 const {items,...rest}=original;
 assert.equal(transactionRequestDigest({...rest,items:[{serviceId:'s',quantity:2}],syncStatus:'pending_sync',receiptSnapshot:{branding:{businessName:'other'}}}),transactionRequestDigest(original));
 for(const changed of [{...original,payments:[{methodId:'cash',amount:30}]},{...original,items:[{quantity:3,serviceId:'s'}]},{...original,customerId:'other'},{...original,totalAmount:21}])assert.notEqual(transactionRequestDigest(changed),transactionRequestDigest(original));
});
test('transaction identity preserves array ordering and nested data',()=>{
 const a={...original,items:[{quantity:1},{quantity:2}]};
 assert.notEqual(transactionRequestDigest(a),transactionRequestDigest({...a,items:[...a.items].reverse()}));
});
