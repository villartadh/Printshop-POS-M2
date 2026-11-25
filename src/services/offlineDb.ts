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
