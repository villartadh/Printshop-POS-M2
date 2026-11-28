import {createHash} from 'node:crypto';
import type {Transaction} from '../shared/types.ts';
// Canonicalize JSON property ordering, preserving array order and every submitted business field.
// Receipt branding is authoritative on the server; sync status is transport metadata.
export function transactionRequestDigest(payload:Transaction):string{
  const {syncStatus:_sync,receiptSnapshot:_snapshot,...request}=payload;
  const canonical=JSON.stringify(request,(_key,value)=>value&&typeof value==='object'&&!Array.isArray(value)?Object.fromEntries(Object.keys(value).sort().map(k=>[k,value[k]])):value);
  return createHash('sha256').update(canonical).digest('hex');
}
