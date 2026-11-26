import type { DiagnosticLogEntry, OfflineMutation } from '../shared/types.ts';

import {validateSyncResults,retainSyncFailure,requeueConflict,sameQueuedVersion} from '../shared/offlinePolicy.ts';

const DB_NAME = 'printshop_pos_offline_v1';
const DB_VERSION = 1;

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('IndexedDB unavailable'));
      return;
    }
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains('kv')) {
        db.createObjectStore('kv');
      }
      if (!db.objectStoreNames.contains('outbox')) {
        db.createObjectStore('outbox', { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains('diagnostics')) {
        db.createObjectStore('diagnostics', { keyPath: 'id' });
      }
    };
    req.onsuccess = () => {
      const db = req.result;
      db.onversionchange = () => db.close();
      resolve(db);
    };
    req.onerror = () => reject(req.error || new Error('Failed to open IndexedDB'));
  });
}

export async function saveOfflineSnapshot<T>(key: string, value: T): Promise<void> {
  try {
    const db = await openDb();
    try {
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction('kv', 'readwrite');
      tx.objectStore('kv').put(value, key);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error || new Error("Storage transaction aborted"));
    });
    } finally { db.close(); }
  } catch {
    // Graceful degradation when private browsing blocks IndexedDB
  }
}

export async function loadOfflineSnapshot<T>(key: string): Promise<T | null> {
  try {
    const db = await openDb();
    try {
    return await new Promise<T | null>((resolve, reject) => {
      const tx = db.transaction('kv', 'readonly');
      const req = tx.objectStore('kv').get(key);
      req.onsuccess = () => resolve((req.result as T) ?? null);
      req.onerror = () => reject(req.error);
    });
    } finally { db.close(); }
  } catch {
    return null;
  }
}

export async function enqueueOfflineMutation(mutation: OfflineMutation): Promise<void> {
  const db = await openDb();
    try {
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction('outbox', 'readwrite');
    tx.objectStore('outbox').put(mutation);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error || new Error("Storage transaction aborted"));
  });
    } finally { db.close(); }
}

export async function getOutboxMutations(): Promise<OfflineMutation[]> {
  const db = await openDb();
  try {
    return await new Promise<OfflineMutation[]>((resolve, reject) => {
      const tx = db.transaction('outbox', 'readonly');
      const req = tx.objectStore('outbox').getAll();
      req.onsuccess = () => resolve((req.result as OfflineMutation[]) || []);
      req.onerror = () => reject(req.error);
      tx.onabort = () => reject(tx.error || new Error('Queue read aborted'));
    });
  } finally { db.close(); }
}

export async function removeOutboxMutation(id: string): Promise<void> {
  const db = await openDb();
    try {
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction('outbox', 'readwrite');
    tx.objectStore('outbox').delete(id);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error || new Error("Storage transaction aborted"));
  });
    } finally { db.close(); }
}

export async function updateOutboxMutation(mutation: OfflineMutation): Promise<void> {
  await enqueueOfflineMutation(mutation);
}

export async function appendDiagnosticLog(entry: Omit<DiagnosticLogEntry, 'id' | 'timestamp'>): Promise<void> {
  try {
    const db = await openDb();
    try {
    const record: DiagnosticLogEntry = {
      id: `diag_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
      timestamp: new Date().toISOString(),
      ...entry,
    };
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction('diagnostics', 'readwrite');
      tx.objectStore('diagnostics').put(record);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error || new Error("Storage transaction aborted"));
    });
    } finally { db.close(); }
  } catch {
    // Ignore storage quota errors
  }
}

export async function getDiagnosticLogs(): Promise<DiagnosticLogEntry[]> {
  try {
    const db = await openDb();
    try {
    const all = await new Promise<DiagnosticLogEntry[]>((resolve, reject) => {
      const tx = db.transaction('diagnostics', 'readonly');
      const req = tx.objectStore('diagnostics').getAll();
      req.onsuccess = () => resolve((req.result as DiagnosticLogEntry[]) || []);
      req.onerror = () => reject(req.error);
    });
    return all.sort((a, b) => b.timestamp.localeCompare(a.timestamp)).slice(0, 100);
    } finally { db.close(); }
  } catch {
    return [];
  }
}

/** Apply a fully validated acknowledgment in one transaction; never recreate missing or changed records. */
export async function commitSyncAcknowledgments(queue:OfflineMutation[],response:unknown):Promise<void>{
 const results=validateSyncResults(queue,response);
 const byId=new Map(queue.map(m=>[m.clientMutationId,m]));
 const db=await openDb();
 try{
  await new Promise<void>((resolve,reject)=>{
   const tx=db.transaction('outbox','readwrite'),store=tx.objectStore('outbox');
   tx.oncomplete=()=>resolve();
   tx.onerror=()=>reject(tx.error||new Error('Queue acknowledgment failed'));
   tx.onabort=()=>reject(tx.error||new Error('Queue acknowledgment aborted'));
   for(const result of results){
    const submitted=byId.get(result.clientMutationId)!;
    const get=store.get(submitted.id);
    get.onsuccess=()=>{
     const current=get.result as OfflineMutation|undefined;
     if(!sameQueuedVersion(current,submitted))return;
     if(result.status==='applied'||result.status==='duplicate')store.delete(submitted.id);
     else store.put(retainSyncFailure(submitted,result));
    };
   }
  });
 }finally{db.close();}
}
export async function retryOutboxConflict(id:string,owner:string):Promise<void>{
 const db=await openDb();
 try{
  await new Promise<void>((resolve,reject)=>{
   const tx=db.transaction('outbox','readwrite'),store=tx.objectStore('outbox');
   let failure:unknown;
   tx.oncomplete=()=>resolve();
   tx.onerror=()=>reject(failure||tx.error||new Error('Queue retry failed'));
   tx.onabort=()=>reject(failure||tx.error||new Error('Queue retry aborted'));
   const get=store.get(id);
   get.onsuccess=()=>{
    try{if(!get.result)throw new Error('Offline record no longer exists. Refresh the queue.');
      store.put(requeueConflict(get.result as OfflineMutation,owner));
    }catch(e){failure=e;tx.abort();}
   };
  });
 }finally{db.close();}
}

export interface PendingBalanceCollection {ownerId:string;customerId:string;amount:number;clientMutationId:string;createdAt:string}
const collectionKey=(owner:string)=>'pending_balance_collection:'+owner;
export async function readPendingCollection(owner:string):Promise<PendingBalanceCollection|null>{
 const db=await openDb();
 try{return await new Promise((resolve,reject)=>{
  const tx=db.transaction('kv','readonly'),req=tx.objectStore('kv').get(collectionKey(owner));
  req.onsuccess=()=>resolve(req.result??null);
  req.onerror=()=>reject(req.error);
  tx.onabort=()=>reject(tx.error||new Error('Collection read aborted'));
 });}finally{db.close();}
}
export async function reservePendingCollection(owner:string,customerId:string,amount:number):Promise<PendingBalanceCollection>{
 if(!owner||!customerId||!Number.isFinite(amount)||amount<=0||amount>1e9||Math.abs(amount-Math.round(amount*100)/100)>0.000001)throw new Error('Enter a positive cash amount in cents.');
 const candidate={ownerId:owner,customerId,amount,clientMutationId:crypto.randomUUID?.()??Array.from(crypto.getRandomValues(new Uint8Array(16)),b=>b.toString(16).padStart(2,'0')).join(''),createdAt:new Date().toISOString()};
 const db=await openDb();
 try{return await new Promise((resolve,reject)=>{
  const tx=db.transaction('kv','readwrite'),store=tx.objectStore('kv');
  let result=candidate;let failure:unknown;
  tx.oncomplete=()=>resolve(result);
  tx.onerror=()=>reject(failure||tx.error||new Error('Collection reservation failed'));
  tx.onabort=()=>reject(failure||tx.error||new Error('Collection reservation aborted'));
  const req=store.get(collectionKey(owner));
  req.onsuccess=()=>{
   const existing=req.result as PendingBalanceCollection|undefined;
   if(existing){failure=new Error('Resolve the existing payment reference before recording another collection.');tx.abort();return;}
   store.put(candidate,collectionKey(owner));
  };
 });}finally{db.close();}
}
export async function acknowledgePendingCollection(record:PendingBalanceCollection):Promise<void>{
 const db=await openDb();
 try{await new Promise<void>((resolve,reject)=>{
  const tx=db.transaction('kv','readwrite'),store=tx.objectStore('kv');
  tx.oncomplete=()=>resolve();
  tx.onerror=()=>reject(tx.error||new Error('Collection acknowledgment failed'));
  tx.onabort=()=>reject(tx.error||new Error('Collection acknowledgment aborted'));
  const req=store.get(collectionKey(record.ownerId));
  req.onsuccess=()=>{if(req.result?.clientMutationId===record.clientMutationId)store.delete(collectionKey(record.ownerId));};
 });}finally{db.close();}
}

export interface PendingDrawerMovement {ownerId:string;shiftId:string;type:'cash_in'|'cash_out';amount:number;reason:string;clientMutationId:string;createdAt:string}
const drawerKey=(owner:string)=>'pending_drawer_movement:'+owner;
export async function readPendingDrawerMovement(owner:string):Promise<PendingDrawerMovement|null>{
 const db=await openDb();
 try{return await new Promise((resolve,reject)=>{
  const tx=db.transaction('kv','readonly'),req=tx.objectStore('kv').get(drawerKey(owner));
  req.onsuccess=()=>resolve(req.result??null);req.onerror=()=>reject(req.error);
  tx.onabort=()=>reject(tx.error||new Error('Drawer recovery read aborted'));
 });}finally{db.close();}
}
export async function reserveDrawerMovement(owner:string,shiftId:string,type:PendingDrawerMovement['type'],amount:number,reason:string):Promise<PendingDrawerMovement>{
 if(!owner||!shiftId||!['cash_in','cash_out'].includes(type)||!Number.isFinite(amount)||amount<=0||amount>1e9||Math.abs(amount*100-Math.round(amount*100))>=0.0001||!reason.trim()||reason.trim().length>500)throw new Error('Enter a valid cash adjustment and reason.');
 const candidate={ownerId:owner,shiftId,type,amount,reason:reason.trim(),clientMutationId:crypto.randomUUID?.()??Array.from(crypto.getRandomValues(new Uint8Array(16)),b=>b.toString(16).padStart(2,'0')).join(''),createdAt:new Date().toISOString()};
 const db=await openDb();
 try{return await new Promise((resolve,reject)=>{
  const tx=db.transaction('kv','readwrite'),store=tx.objectStore('kv');let failure:unknown;
  tx.oncomplete=()=>resolve(candidate);tx.onerror=()=>reject(failure||tx.error||new Error('Drawer reservation failed'));tx.onabort=()=>reject(failure||tx.error||new Error('Drawer reservation aborted'));
  const req=store.get(drawerKey(owner));req.onsuccess=()=>{if(req.result){failure=new Error('Confirm the previous drawer adjustment before recording another.');tx.abort();return;}store.put(candidate,drawerKey(owner));};
 });}finally{db.close();}
}
export async function acknowledgeDrawerMovement(record:PendingDrawerMovement):Promise<void>{
 const db=await openDb();
 try{await new Promise<void>((resolve,reject)=>{
  const tx=db.transaction('kv','readwrite'),store=tx.objectStore('kv');
  tx.oncomplete=()=>resolve();tx.onerror=()=>reject(tx.error||new Error('Drawer acknowledgment failed'));tx.onabort=()=>reject(tx.error||new Error('Drawer acknowledgment aborted'));
  const req=store.get(drawerKey(record.ownerId));req.onsuccess=()=>{if(req.result?.clientMutationId===record.clientMutationId)store.delete(drawerKey(record.ownerId));};
 });}finally{db.close();}
}
