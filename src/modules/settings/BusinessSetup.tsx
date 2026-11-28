import React, { useState } from 'react';
import { useApp } from '../../context/AppContext.tsx';
import type { AppSettings } from '../../shared/types.ts';

export function BusinessSetup({onClose}:{onClose:()=>void}) {
  const {settings,saveSettings,services,setActiveModule,isOnline}=useApp();
  const [draft,setDraft]=useState<AppSettings>(()=>structuredClone(settings));
  const [step,setStep]=useState(0);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState('');
  const titles=['Business profile','Regional and receipt settings','Payment methods','Service catalog and backups'];
  const inputClass='w-full rounded-md border border-[var(--pos-border)] bg-[var(--pos-bg)] p-2 text-sm';
  const patch=(key:'businessName'|'address'|'phone'|'email'|'storeId',value:string)=>setDraft(prev=>({...prev,branding:{...prev.branding,[key]:value}}));
  const validProfile=Boolean(draft.branding.businessName.trim()&&draft.branding.address.trim());
  const validPayment=draft.paymentMethods.some(m=>m.enabled);
  async function save() {
    if(busy||!validProfile||!validPayment)return;
    setBusy(true);setError('');
    try {
      await saveSettings(draft);
      onClose();
    } catch(e) {setError(e instanceof Error?e.message:'Unable to save configuration.');}
    finally {setBusy(false);}
  }
  return <section aria-label="Guided business setup" className="mb-4 rounded-xl border border-[var(--pos-border)] bg-[var(--pos-surface)] p-4 space-y-4">
    <div className="flex justify-between items-start gap-2">
      <div><p className="text-xs text-[var(--pos-text-muted)]">Business setup · Step {step+1} of {titles.length}</p><h2 className="text-lg font-bold">{titles[step]}</h2></div>
      <button type="button" disabled={busy} onClick={onClose} className="text-sm underline">Close setup</button>
    </div>
    <div className="flex gap-1" aria-hidden="true">{titles.map((t,i)=><div key={t} className={`h-1 flex-1 rounded ${i<=step?'bg-[var(--pos-primary)]':'bg-slate-300'}`}/>)}</div>
    {step===0&&<div className="grid gap-3 sm:grid-cols-2">
      {([['businessName','Business name (required)'],['address','Business address (required)'],['phone','Contact number'],['email','Email address'],['storeId','Store ID']] as const).map(([key,label])=><label key={key} className="space-y-1 text-sm"><span>{label}</span><input className={inputClass} value={draft.branding[key]} onChange={e=>patch(key,e.target.value)}/></label>)}
    </div>}
    {step===1&&<div className="space-y-3 text-sm">
      <p>Philippine peso (PHP) · Asia/Manila business date for expenses. Tax settings are not proof of BIR registration.</p>
      <label className="block space-y-1">Receipt paper size<select className={inputClass} value={draft.receiptTemplate.defaultPaperSize} onChange={e=>setDraft(p=>({...p,receiptTemplate:{...p.receiptTemplate,defaultPaperSize:e.target.value as AppSettings['receiptTemplate']['defaultPaperSize']}}))}>{['58mm','80mm','A4','Letter','Custom'].map(v=><option key={v}>{v}</option>)}</select></label>
      {draft.receiptTemplate.defaultPaperSize==='Custom'&&<label className="block">Custom width (mm)<input type="number" min={30} max={500} className={inputClass} value={draft.receiptTemplate.customWidthMm} onChange={e=>setDraft(p=>({...p,receiptTemplate:{...p.receiptTemplate,customWidthMm:Number(e.target.value)}}))}/></label>}
      <p>Review tax and registration details in Settings before using customer documents. Physical printer selection is handled by the operating system.</p>
    </div>}
    {step===2&&<div className="space-y-2 text-sm"><p>Enable only payment methods your shop actually accepts. Electronic payment verification remains manual.</p>{draft.paymentMethods.map(m=><label key={m.id} className="flex items-center gap-2"><input type="checkbox" checked={m.enabled} onChange={e=>setDraft(p=>({...p,paymentMethods:p.paymentMethods.map(x=>x.id===m.id?{...x,enabled:e.target.checked}:x)}))}/>{m.name}</label>)}</div>}
    {step===3&&<div className="space-y-3 text-sm">
      <p>{services.filter(s=>!s.isArchived).length} active services in your catalog. Add actual prices for printing, photocopying, binding, ID lace and other services in Services & Pricing.</p>
      <p>Windows distribution: use BACKUP.cmd to create a verified database backup and RESTORE-POS.cmd for recovery while the POS is stopped. Keep an additional copy outside the shop computer.</p>
      <p>Save the profile first, then configure catalog and review backup procedures. This setup does not certify hardware or tax compliance.</p>
    </div>}
    {error&&<p role="alert" className="text-sm text-red-600">{error}</p>}
    {!isOnline&&<p className="text-sm">Connect to the POS server to save business configuration.</p>}
    <div className="flex flex-wrap justify-between gap-2">
      <button type="button" className="rounded border border-[var(--pos-border)] px-3 py-2 text-sm disabled:opacity-50" disabled={step===0||busy} onClick={()=>setStep(s=>s-1)}>Back</button>
      {step<3?<button type="button" disabled={(step===0&&!validProfile)||(step===2&&!validPayment)} className="rounded bg-[var(--pos-primary)] px-3 py-2 text-sm text-white disabled:opacity-50" onClick={()=>setStep(s=>s+1)}>Continue</button>:<button type="button" disabled={busy||!isOnline||!validProfile||!validPayment} className="rounded bg-[var(--pos-primary)] px-3 py-2 text-sm text-white disabled:opacity-50" onClick={()=>void save()}>{busy?'Saving…':'Save business setup'}</button>}
    </div>
  </section>;
}
