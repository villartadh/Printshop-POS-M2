import test from 'node:test';
import assert from 'node:assert/strict';
import {acceptanceEnv,verifyResponse,assertInstance} from '../scripts/acceptance.mjs';
test('acceptance environment isolates production data and secrets',()=>{const e=acceptanceEnv(4444,'test');assert.equal(e.HOST,'127.0.0.1');assert.equal(e.PORT,'4444');assert.equal(e.SEED_DEMO,'false');assert.equal(e.SESSION_SECRET,undefined);assert.equal(e.NODE_OPTIONS,undefined);});
test('acceptance rejects unexpected HTTP response',()=>{assert.throws(()=>verifyResponse({status:500},200));assert.deepEqual(verifyResponse({status:200,body:{ok:true}},200),{ok:true});});
test('acceptance verifies identity of test server',()=>{assert.throws(()=>assertInstance({status:'ok',instance:'other'},'test'));assert.doesNotThrow(()=>assertInstance({status:'ok',instance:'test'},'test'));});
