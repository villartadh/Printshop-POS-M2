import React, {useEffect,useMemo,useState} from 'react';
import {Bell,AlertTriangle} from 'lucide-react';
import {useApp} from '../../context/AppContext.tsx';
import {buildInventoryAlerts} from '../../shared/inventoryModel.ts';
import {Modal} from '../ui/Overlays.tsx';
import {Button} from '../ui/Primitives.tsx';
import {ReorderRequestModal} from '../inventory/ReorderRequestModal.tsx';

const readKey=(id:string)=>'printshop_inventory_alert_reads_v1:'+id;
function loadReads(id:string):string[]{
  try {const value=JSON.parse(localStorage.getItem(readKey(id)) || '[]');return Array.isArray(value)?value.filter(v=>typeof v==='string'):[];} catch {return [];}
}
function AlertPanel({owner}:{owner:string}){
  const {inventory,inventoryState,loadingData,dataError,isOnline,setActiveModule,session}=useApp();
  const [open,setOpen]=useState(false);
  const [reorderOpen,setReorderOpen]=useState(false);
  const [read,setRead]=useState<string[]>(()=>loadReads(owner));
  const alerts=useMemo(()=>buildInventoryAlerts(inventory),[inventory]);
  const ready=Boolean(inventoryState.lastLoadedAt)&&!loadingData&&!dataError;
  const unread=alerts.filter(a=>!read.includes(a.signature)).length;
  const critical=alerts.filter(a=>a.severity!=='reorder').length;
  const saveReads=(next:string[])=>{setRead(next);try{localStorage.setItem(readKey(owner),JSON.stringify(next));}catch{}};
  useEffect(()=>{
    if(!ready||!isOnline)return;
    const active=new Set(alerts.map(a=>a.signature));
    const next=read.filter(s=>active.has(s));
    if(next.length!==read.length)saveReads(next);
  },[alerts,ready,isOnline,read]);
  return <>
    <button type="button" aria-label="Inventory notifications" title="Inventory notifications" aria-expanded={open} onClick={()=>setOpen(true)}
      className="relative h-9 w-9 rounded-lg border border-[var(--pos-border)] flex items-center justify-center shrink-0 text-[var(--pos-text-muted)]">
      <Bell className={`w-4 h-4 ${critical?'text-[var(--pos-error)]':''}`}/>
      {ready&&unread>0&&<span className="absolute -top-1 -right-1 min-w-4 px-1 rounded-full bg-red-600 text-white text-[10px] leading-4">{unread>99?'99+':unread}</span>}
    </button>
    {reorderOpen&&<ReorderRequestModal onClose={()=>setReorderOpen(false)}/>}
    <Modal open={open} onClose={()=>setOpen(false)} title="Inventory Notification Center" subtitle={ready?`${alerts.length} active alerts · ${unread} unread`:'Inventory status not yet available'} maxWidth="md"
      footer={<><Button variant="outline" size="sm" onClick={()=>setOpen(false)}>Close</Button>{['admin','manager'].includes(session?.user.role||'')&&<Button variant="outline" size="sm" disabled={!ready||!alerts.length} onClick={()=>{setOpen(false);setReorderOpen(true);}}>Create Reorder Request</Button>}<Button size="sm" onClick={()=>{setOpen(false);setActiveModule('inventory');}}>Open Inventory</Button></>}>
      <div className="space-y-3">
        {!isOnline&&<p className="text-xs text-[var(--pos-warning)]">Offline: showing last available local inventory data.</p>}
        {!ready?<p>Inventory data is not ready. Refresh the store records before relying on these alerts.</p>:<>
          {critical>=2&&<p role="status" className="flex gap-2 p-3 rounded-lg border border-red-500/40 text-[var(--pos-error)]"><AlertTriangle className="w-5 h-5 shrink-0"/>{critical} items currently have critical or exhausted available stock.</p>}
          {alerts.length===0?<p>No active inventory alerts.</p>:<>
            <div className="flex justify-between items-center"><span className="text-xs text-[var(--pos-text-muted)]">Based on available stock after reservations.</span><Button size="sm" variant="outline" disabled={!unread} onClick={()=>saveReads(alerts.map(a=>a.signature))}>Mark all read</Button></div>
            {alerts.map(a=><div key={a.signature} className="rounded-lg border border-[var(--pos-border)] p-3 space-y-1">
              <div className="flex justify-between items-start gap-2"><strong className="break-words min-w-0">{a.item.name}</strong><span className={a.severity==='reorder'?'text-[var(--pos-warning)]':'text-[var(--pos-error)]'}>{a.severity==='out'?'No available stock':a.severity==='critical'?'Critical shortage':'Reorder recommended'}</span></div>
              <div className="text-xs">Available: {a.available} {a.item.unit} · On hand: {a.item.quantityOnHand} · Reserved: {a.item.reservedQuantity ?? 0}</div>
              <div className="text-xs text-[var(--pos-text-muted)]">Reorder: {a.item.reorderLevel} · Critical minimum: {a.item.minimumThreshold || 'Disabled'}</div>
              <Button size="sm" variant="outline" disabled={read.includes(a.signature)} onClick={()=>saveReads([...read,a.signature])}>{read.includes(a.signature)?'Read':'Mark read'}</Button>
            </div>)}
          </>}
        </>}
        <p className="text-xs text-[var(--pos-text-muted)]">Read status is saved for this user on this terminal only. Reading an alert does not resolve a shortage.</p>
      </div>
    </Modal>
  </>;
}
export function NotificationCenter(){
  const {session}=useApp();
  return session?<AlertPanel key={session.user.id} owner={session.user.id}/>:null;
}
