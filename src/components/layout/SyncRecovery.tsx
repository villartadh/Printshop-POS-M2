import React,{useState} from 'react';
import {useApp} from '../../context/AppContext.tsx';
import {Modal} from '../ui/Overlays.tsx';
import {Button} from '../ui/Primitives.tsx';
import type {OfflineMutation,Transaction} from '../../shared/types.ts';
import {formatCurrency} from '../../shared/pricingEngine.ts';
export function SyncRecovery({onClose}:{onClose:()=>void}){
 const {outbox,isOnline,isSyncing,syncNow,retrySyncConflict,refreshSyncQueue,syncError,settings}=useApp();
 const [confirmation,setConfirmation]=useState<string|null>(null);
 const [error,setError]=useState('');
 const [busy,setBusy]=useState(false);
 const records=[...outbox].sort((a,b)=>a.createdAt.localeCompare(b.createdAt)||a.id.localeCompare(b.id));
 const retry=async(m:OfflineMutation)=>{
   setBusy(true);setError('');
   try{await retrySyncConflict(m.id);setConfirmation(null);}catch(e){setError(e instanceof Error?e.message:'Retry failed');}finally{setBusy(false);}
 };
 const pending=records.filter(m=>m.status==='pending'||m.status==='syncing').length;
 return <Modal open title="Offline Record Recovery" subtitle="Preserved records for the signed-in operator" onClose={onClose} maxWidth="lg"
 footer={<div className="flex flex-wrap gap-2"><Button variant="outline" size="sm" onClick={()=>void refreshSyncQueue()}>Refresh Queue</Button><Button size="sm" disabled={!isOnline||busy||isSyncing||!pending} onClick={()=>void syncNow()}>Synchronize Pending</Button><Button variant="outline" size="sm" onClick={onClose}>Close</Button></div>}>
  <div className="space-y-3">
    <p className="text-sm">Do not collect payment again, clear browser data, or create replacement sales for these records. A retry uses the original mutation identity and payload. Records are removed only after confirmed acceptance or duplicate acknowledgment.</p>
    {!isOnline&&<p className="text-sm text-[var(--pos-warning)]">Offline — connect to retry records.</p>}
    {(syncError||error)&&<p role="alert" className="text-sm text-[var(--pos-error)]">{error||syncError}</p>}
    <p className="text-xs">{pending} pending · {records.length-pending} need reconciliation</p>
    {!records.length&&<p>No unresolved records for this operator.</p>}
    {records.map(m=>{
     const sale=m.type==='CREATE_TRANSACTION'?m.payload as Partial<Transaction>:undefined;
     const adjustment=m.type==='ADJUST_INVENTORY'?m.payload as {inventoryItemId?:string;quantityDelta?:number}:undefined;
     const conflict=m.status==='conflict'||m.status==='failed';
     return <section key={m.id} className="rounded-lg border border-[var(--pos-border)] p-3 space-y-2">
       <strong>{sale?'Sale / order '+(sale.receiptNumber||''):adjustment?'Stock adjustment':'Unsupported offline record'}</strong>
       <p className="text-xs">{conflict?'Needs reconciliation':'Awaiting confirmation'} · Attempts: {m.retryCount} · {new Date(m.createdAt).toLocaleString('en-PH',{timeZone:'Asia/Manila'})}</p>
       {sale&&typeof sale.totalAmount==='number'&&<p className="text-sm">Recorded total: {formatCurrency(sale.totalAmount,sale.receiptSnapshot?.taxAndCurrency??settings.taxAndCurrency)}</p>}
       {adjustment&&<p className="text-sm">Stock ID: {adjustment.inventoryItemId} · Quantity: {adjustment.quantityDelta}</p>}
       <p className="text-xs break-all">Mutation: {m.clientMutationId}</p>
       {m.errorMessage&&<p className="text-sm break-words text-[var(--pos-error)]">{m.errorMessage}</p>}
       {conflict&&['CREATE_TRANSACTION','ADJUST_INVENTORY'].includes(m.type)&&(
        confirmation===m.id?<div className="space-y-2"><p className="text-sm">Review the reported problem with a manager first. Retry will submit the unchanged record, not a replacement payment or edited order.</p><div className="flex gap-2"><Button size="sm" disabled={busy||isSyncing||!isOnline} onClick={()=>void retry(m)}>Confirm Retry</Button><Button variant="outline" size="sm" onClick={()=>setConfirmation(null)}>Cancel</Button></div></div>:
        <Button variant="outline" size="sm" disabled={busy||isSyncing||!isOnline} onClick={()=>setConfirmation(m.id)}>Retry Original Record</Button>
       )}
     </section>;
    })}
  </div>
 </Modal>;
}
