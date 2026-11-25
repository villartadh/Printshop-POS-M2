import React,{useMemo,useState} from 'react';
import {useApp} from '../../context/AppContext.tsx';
import {Modal} from '../ui/Overlays.tsx';
import {Button} from '../ui/Primitives.tsx';
import {reorderRows,groupReorderRows,validOrderQuantity,procurementCsvCell,type ReorderScope} from '../../shared/procurement.ts';
import {formatCurrency} from '../../shared/pricingEngine.ts';
import {escapeHtml} from '../../shared/printDocument.ts';
import {openPrintPreview} from '../../services/printService.ts';
import {downloadCsv} from '../../services/hardwareService.ts';

export function ReorderRequestModal({onClose}:{onClose:()=>void}){
  const {inventory,inventoryState,loadingData,dataError,isOnline,settings,notify,session}=useApp();
  const [scope,setScope]=useState<ReorderScope>('all');
  const [excluded,setExcluded]=useState<string[]>([]);
  const [overrides,setOverrides]=useState<Record<string,string>>({});
  const rows=useMemo(()=>reorderRows(inventory,scope),[inventory,scope]);
  const selected=rows.filter(r=>!excluded.includes(r.item.id)).map(r=>{
    const quantity=overrides[r.item.id]===undefined?r.quantity:Number(overrides[r.item.id]);
    return {...r,quantity,estimatedCost:Number((quantity*r.item.unitCost).toFixed(2))};
  });
  const valid=selected.every(r=>validOrderQuantity(r.quantity)&&Number.isFinite(r.estimatedCost));
  const included=selected.filter(r=>r.quantity>0);
  const groups=groupReorderRows(included);
  const total=included.reduce((sum,r)=>sum+r.estimatedCost,0);
  const ready=Boolean(inventoryState.lastLoadedAt)&&!loadingData&&!dataError&&!inventoryState.error;
  const allowed=['admin','manager'].includes(session?.user.role||'');
  const canExport=allowed&&ready&&valid&&included.length>0&&Number.isFinite(total);
  const money=(n:number)=>formatCurrency(n,settings.taxAndCurrency);
  const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Manila',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
  const reference='Reorder-request-'+date;
  const lines=()=>['DRAFT REORDER REQUEST — NOT A PURCHASE ORDER',settings.branding.businessName,date,'Estimates only; verify stock and supplier prices before ordering.',...groups.flatMap(g=>['',g.supplier+(g.contact?' · '+g.contact:''),...g.rows.map(r=>r.item.name+' ('+r.item.sku+'): '+r.quantity+' '+r.item.unit+' · '+money(r.estimatedCost))]),'Estimated total: '+money(total)].join('\n');
  const exportCsv=()=>{if(!canExport)return;downloadCsv(reference+'.csv',['Document status','Supplier','Contact','Supplier SKU','Item','SKU','Unit','On hand','Reserved','Available','Reorder level','Critical minimum','Order quantity','Unit cost estimate','Total estimate','Currency'],groups.flatMap(g=>g.rows.map(r=>['Draft estimate',g.supplier,g.contact,r.supplier.supplierSku||'',r.item.name,r.item.sku,r.item.unit,r.item.quantityOnHand,r.item.reservedQuantity??0,r.available,r.item.reorderLevel,r.item.minimumThreshold??0,r.quantity,r.item.unitCost,r.estimatedCost,settings.taxAndCurrency.currencyCode].map(procurementCsvCell))));};
  const print=()=>{if(!canExport)return;try{openPrintPreview(reference,'<div class="font-bold">DRAFT REORDER REQUEST — NOT A PURCHASE ORDER</div>'+lines().split('\n').slice(1).map(l=>'<div>'+escapeHtml(l)+'</div>').join(''),'A4',80);}catch(e){notify(e instanceof Error?e.message:'Unable to open print preview','error');}};
  const copy=async()=>{if(!canExport)return;try{await navigator.clipboard.writeText(lines());notify('Draft reorder request copied.','success');}catch{notify('Clipboard unavailable. Use CSV export instead.','error');}};
  return <Modal open onClose={onClose} title="Supplier Reorder Request" subtitle="Draft procurement estimate · No stock or expense changes" maxWidth="xl"
    footer={<div className="flex flex-wrap gap-2"><Button variant="outline" size="sm" onClick={onClose}>Close</Button><Button variant="outline" size="sm" disabled={!canExport} onClick={()=>void copy()}>Copy Request</Button><Button variant="outline" size="sm" disabled={!canExport} onClick={exportCsv}>Export CSV</Button><Button size="sm" disabled={!canExport} onClick={print}>Print Request</Button></div>}>
    {!allowed?<p>Administrator or manager access required.</p>:<div className="space-y-4">
      <p className="text-sm">Recommendations use available stock after reservations. Quantities and supplier prices are estimates; no order is submitted or saved.</p>
      {!isOnline&&<p className="text-xs text-[var(--pos-warning)]">Offline draft: verify current stock before ordering.</p>}
      {!ready&&<p role="alert">Inventory is not ready. Refresh before exporting.</p>}
      <label className="flex items-center gap-2 text-sm">Scope<select aria-label="Reorder scope" value={scope} onChange={e=>{setScope(e.target.value as ReorderScope);setExcluded([]);setOverrides({});}} className="border rounded p-2 bg-[var(--pos-surface)]"><option value="all">All low-stock items</option><option value="critical">Critical and exhausted only</option></select></label>
      <div className="flex gap-2"><Button size="sm" variant="outline" onClick={()=>setExcluded([])}>Select all</Button><Button size="sm" variant="outline" onClick={()=>setExcluded(rows.map(r=>r.item.id))}>Clear selection</Button></div>
      {rows.length===0&&<p>No items match this reorder scope.</p>}
      {groupReorderRows(rows).map(g=><section key={JSON.stringify([g.supplier,g.contact])} className="border rounded-lg border-[var(--pos-border)] p-3 space-y-2">
        <h3 className="font-semibold">{g.supplier}</h3>{g.contact&&<p className="text-xs">{g.contact}</p>}
        {g.rows.map(r=>{const value=overrides[r.item.id]??String(r.quantity);return <div key={r.item.id} className="border-t border-[var(--pos-border)] py-2 flex flex-wrap items-center gap-3">
          <label className="flex items-center gap-2 flex-1 min-w-0"><input type="checkbox" checked={!excluded.includes(r.item.id)} onChange={e=>setExcluded(prev=>e.target.checked?prev.filter(id=>id!==r.item.id):[...prev,r.item.id])}/><span><strong>{r.item.name}</strong><span className="block text-xs">Available {r.available} {r.item.unit} · Reserved {r.item.reservedQuantity??0} · Reorder {r.item.reorderLevel} · {money(r.item.unitCost)}/{r.item.unit}</span>{r.supplier.supplierSku&&<span className="block text-xs">Supplier SKU: {r.supplier.supplierSku}</span>}</span></label>
          <label className="text-xs">Order quantity<input aria-label={'Order quantity '+r.item.name} type="number" min="0" max="1000000000" step="0.01" value={value} onChange={e=>setOverrides(prev=>({...prev,[r.item.id]:e.target.value}))} className="block w-28 border rounded p-2 bg-[var(--pos-surface)]"/></label>
          <span className="text-sm font-semibold">{validOrderQuantity(Number(value))?money(Number(value)*r.item.unitCost):'Invalid quantity'}</span>
        </div>;})}
      </section>)}
      <div className="border-t pt-3 flex flex-wrap justify-between gap-2"><span>{included.length} selected items · {groups.length} supplier groups</span><strong aria-label="Estimated procurement total">Estimated total: {valid&&Number.isFinite(total)?money(total):'Invalid quantity'}</strong></div>
    </div>}
  </Modal>;
}
