import React,{useRef,useState} from 'react';
import {Button,Input} from '../ui/Primitives.tsx';
import {apiRequest} from '../../services/apiClient.ts';
import {acknowledgePendingCollection,acknowledgeDrawerMovement,acknowledgeExpense,type PendingBalanceCollection,type PendingDrawerMovement,type PendingExpense} from '../../services/offlineDb.ts';

type PendingRequest =
 | {kind:'collection';record:PendingBalanceCollection}
 | {kind:'drawer';record:PendingDrawerMovement}
 | {kind:'expense';record:PendingExpense};

export function PendingFinancialCancellation({request,disabled,onBusy,onResolved}:{request:PendingRequest;disabled:boolean;onBusy:(busy:boolean)=>void;onResolved:()=>Promise<void>}){
 const [open,setOpen]=useState(false),[confirmed,setConfirmed]=useState(false),[reason,setReason]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 const lock=useRef(false);
 const cancel=async()=>{
  if(lock.current||disabled||!confirmed||!reason.trim()||reason.trim().length>500)return;
  lock.current=true;setBusy(true);onBusy(true);setError('');
  try{
   const {kind,record}=request;
   const result=await apiRequest<{kind:unknown;clientMutationId:unknown;ownerId:unknown;status:unknown;reason:unknown;duplicate:unknown}>('/api/financial-requests/cancel',{
     method:'POST',body:JSON.stringify({kind,clientMutationId:record.clientMutationId,reason:reason.trim(),confirmedNoFundsMoved:true}),
   });
   if(result.kind!==kind||result.clientMutationId!==record.clientMutationId||result.ownerId!==record.ownerId||result.status!=='cancelled'||typeof result.reason!=='string'||!result.reason.trim()||typeof result.duplicate!=='boolean')
     throw new Error('Cancellation confirmation could not be verified. Preserve the original reference and try confirmation again.');
   // Compare-and-clear the original local reservation only after a verified durable cancellation.
   if(request.kind==='collection')await acknowledgePendingCollection(request.record);
   else if(request.kind==='drawer')await acknowledgeDrawerMovement(request.record);
   else await acknowledgeExpense(request.record);
   await onResolved();
   setOpen(false);setConfirmed(false);setReason('');
  }catch(e){setError(e instanceof Error?e.message:'Cancellation could not be confirmed. Preserve the original reference.');}
  finally{lock.current=false;setBusy(false);onBusy(false);}
 };
 return <div className="space-y-2">
  {!open?<Button size="sm" variant="outline" disabled={disabled} onClick={()=>setOpen(true)}>Reconcile Unapplied Request</Button>:
   <div className="border border-[var(--pos-border)] rounded-lg p-3 space-y-2">
    <p className="text-sm font-medium">Cancel an unapplied request only after manager review</p>
    <p className="text-xs">This does not reverse an applied expense, payment, or drawer adjustment. If any money changed hands or the outcome is uncertain, do not cancel. Retry the original reference or ask the manager to reconcile it. The server refuses cancellation if the reference already exists and permanently closes a confirmed unapplied reference against late retries.</p>
    <label className="flex gap-2 items-start text-sm"><input type="checkbox" checked={confirmed} disabled={busy||disabled} onChange={e=>setConfirmed(e.target.checked)}/>I reviewed this request with the manager and confirm no money moved.</label>
    <Input label="Reconciliation reason" value={reason} onChange={e=>setReason(e.target.value)} maxLength={500} disabled={busy||disabled} />
    {error&&<p role="alert" className="text-sm text-[var(--pos-error)]">{error}</p>}
    <div className="flex flex-wrap gap-2">
     <Button size="sm" disabled={busy||disabled||!confirmed||!reason.trim()} onClick={()=>void cancel()}>Confirm Unapplied Cancellation</Button>
     <Button size="sm" variant="outline" disabled={busy} onClick={()=>{setOpen(false);setConfirmed(false);setReason('');setError('');}}>Keep Original Request</Button>
    </div>
   </div>}
 </div>;
}
