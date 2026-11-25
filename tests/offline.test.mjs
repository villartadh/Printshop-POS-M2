import test from 'node:test';
import assert from 'node:assert/strict';
import {pendingBatch,mutationOwner,validateSyncEnvelope,validateSyncResults} from '../src/shared/offlinePolicy.ts';
const m=(id,owner='a',status='pending')=>({id,ownerId:owner,clientMutationId:id,type:'ADJUST_INVENTORY',status,createdAt:id});
test('queue isolates cashier and quarantines conflicts',()=>assert.deepEqual(pendingBatch([m('1','b'),m('2','a','conflict'),m('3')],'a').map(x=>x.id),['3']));
test('queue sorts and bounds batches',()=>assert.deepEqual(pendingBatch([m('3'),m('1'),m('2')],'a',2).map(x=>x.id),['1','2']));
test('interrupted syncing records are recoverable',()=>assert.equal(pendingBatch([m('1','a','syncing')],'a').length,1));
test('legacy unowned stock is quarantined',()=>assert.equal(pendingBatch([{...m('1'),ownerId:undefined}],'a').length,0));
test('legacy sale ownership is recovered',()=>assert.equal(mutationOwner({...m('1'),ownerId:undefined,type:'CREATE_TRANSACTION',payload:{cashierId:'a'}}),'a'));
test('server rejects foreign owner',()=>assert.throws(()=>validateSyncEnvelope(m('1','b'),'a'),/owner/));
test('server rejects mismatched sale identity',()=>assert.throws(()=>validateSyncEnvelope({...m('1'),type:'CREATE_TRANSACTION',payload:{cashierId:'a',clientMutationId:'2'}},'a'),/identity/));
test('server accepts matching sale identity',()=>assert.doesNotThrow(()=>validateSyncEnvelope({...m('1'),type:'CREATE_TRANSACTION',payload:{cashierId:'a',clientMutationId:'1'}},'a')));
test('server rejects unsupported mutation and null',()=>{assert.throws(()=>validateSyncEnvelope({...m('1'),type:'CREATE_EXPENSE'},'a'));assert.throws(()=>validateSyncEnvelope(null,'a'));});

const ack=(results)=>({results,syncedAt:'2026-11-25T00:00:00.000Z'});
const result=(id,status='applied')=>({clientMutationId:id,status});
test('complete mixed batch acknowledgment accepted regardless of ordering',()=>assert.deepEqual(validateSyncResults([m('1'),m('2'),m('3')],ack([result('3','error'),result('1'),result('2','duplicate')])).map(x=>x.status),['error','applied','duplicate']));
test('missing acknowledgment rejects entire response',()=>assert.throws(()=>validateSyncResults([m('1'),m('2')],ack([result('1')]))));
test('duplicate or foreign acknowledgment rejects entire response',()=>{for(const results of [[result('1'),result('1')],[result('1'),result('3')]])assert.throws(()=>validateSyncResults([m('1'),m('2')],ack(results)))});
test('unknown status or invalid envelope rejects entire response',()=>{for(const response of [null,{},ack([result('1','retry')]),{...ack([result('1')]),syncedAt:'invalid'},ack([null])])assert.throws(()=>validateSyncResults([m('1')],response))});

import {isRetryableSyncError,retainSyncFailure} from '../src/shared/offlinePolicy.ts';
test('only known database contention is automatically retryable',()=>{for(const e of [{code:'SQLITE_BUSY'},{code:'SQLITE_LOCKED_SHAREDCACHE'},{code:'ERR_SQLITE_ERROR',errcode:261}])assert.equal(isRetryableSyncError(e),true);for(const e of [null,new Error('busy'),{statusCode:409},{code:'ERR_SQLITE_ERROR',errcode:19}])assert.equal(isRetryableSyncError(e),false);});
test('retry preserves mutation identity and payload; business conflict remains quarantined',()=>{const original={...m('1'),retryCount:2,payload:{quantityDelta:4}};const retry=retainSyncFailure(original,{...result('1','error'),retryable:true});assert.deepEqual(retry,{...original,status:'pending',retryCount:3,errorMessage:'Sync conflict'});assert.equal(retainSyncFailure(original,result('1','error')).status,'conflict');});
test('retryable acknowledgment metadata is validated',()=>{assert.equal(validateSyncResults([m('1')],ack([{...result('1','error'),retryable:true}]))[0].retryable,true);for(const x of [{...result('1'),retryable:true},{...result('1','error'),retryable:'true'}])assert.throws(()=>validateSyncResults([m('1')],ack([x])));});

import {requeueConflict,sameQueuedVersion} from '../src/shared/offlinePolicy.ts';
test('manual retry preserves original identity, payload and attempt count',()=>{
 const original={...m('1','a','conflict'),retryCount:2,payload:{inventoryItemId:'paper',quantityDelta:4},errorMessage:'Insufficient stock'};
 const retry=requeueConflict(original,'a');
 assert.deepEqual(retry,{...original,status:'pending',errorMessage:undefined});
 assert.throws(()=>requeueConflict(original,'b'),/owner/);
 assert.throws(()=>requeueConflict(retry,'a'),/Only unresolved/);
 assert.throws(()=>requeueConflict({...original,type:'CREATE_EXPENSE'},'a'),/Unsupported/);
});
test('stale acknowledgments cannot recreate or overwrite newer queue records',()=>{
 const original={...m('1'),retryCount:0,payload:{quantityDelta:4}};
 assert.equal(sameQueuedVersion({...original},original),true);
 assert.equal(sameQueuedVersion(undefined,original),false);
 assert.equal(sameQueuedVersion({...original,retryCount:1},original),false);
 assert.equal(sameQueuedVersion({...original,status:'conflict'},original),false);
});
