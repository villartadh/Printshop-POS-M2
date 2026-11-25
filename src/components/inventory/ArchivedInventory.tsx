import React,{useEffect,useState} from 'react';
import {useApp} from '../../context/AppContext.tsx';
import {apiRequest} from '../../services/apiClient.ts';
import {Modal} from '../ui/Overlays.tsx';
import {Button} from '../ui/Primitives.tsx';
import {InventoryHistory} from './InventoryHistory.tsx';
import type {InventoryItem} from '../../types/inventory.ts';
export function ArchivedInventory({onClose}:{onClose:()=>void}){
 const {isOnline,settings,loadInventory,notify}=useApp();
 const [items,setItems]=useState<InventoryItem[]>([]);
 const [error,setError]=useState('');
 const [busy,setBusy]=useState(false);
 const [loaded,setLoaded]=useState(false);
 const [revision,setRevision]=useState(0);
 const [selected,setSelected]=useState<string|null>(null);
 useEffect(()=>{
  if(!isOnline){setLoaded(false);return;}
  const controller=new AbortController();
  setLoaded(false);setError('');
  apiRequest<{items:InventoryItem[]}>('/api/inventory/archived',{signal:controller.signal}).then(data=>{if(!controller.signal.aborted){setItems(data.items);setLoaded(true);}}).catch(e=>{if(!controller.signal.aborted)setError(e instanceof Error?e.message:'Archive unavailable');});
  return ()=>controller.abort();
 },[isOnline,revision]);
 const restore=async(id:string)=>{
  setBusy(true);setError('');
  try{await apiRequest('/api/inventory/'+encodeURIComponent(id)+'/restore',{method:'POST'});await loadInventory();setSelected(null);setRevision(v=>v+1);notify('Inventory item restored.','success');}
  catch(e){setError(e instanceof Error?e.message:'Restore failed');}
  finally{setBusy(false);}
 };
 const detail=items.find(i=>i.id===selected);
 return <Modal open onClose={()=>{if(!busy)onClose();}} title="Archived Inventory" subtitle="Discontinued items and preserved history" maxWidth="xl">
  <div className="space-y-3">
  {!isOnline?<p>Connect to the server to review or restore archived items.</p>:error?<p role="alert">{error}</p>:null}
  {isOnline&&!loaded&&!error&&<p role="status">Loading archived inventory…</p>}
  {isOnline&&loaded&&<><Button size="sm" variant="outline" disabled={busy} onClick={()=>setRevision(v=>v+1)}>Refresh Archive</Button>
  {items.length===0&&<p>No archived inventory items.</p>}
  {items.map(item=><div key={item.id} className="border border-[var(--pos-border)] rounded-lg p-3 space-y-2">
    <strong>{item.name}</strong><p className="text-xs">{item.sku} · {item.category}</p>
    <p className="text-xs">Archived {new Date(item.archivedAt!).toLocaleString('en-PH',{timeZone:'Asia/Manila'})} · {item.archiveReason}</p>
    <div className="flex flex-wrap gap-2"><Button variant="outline" size="sm" disabled={busy} onClick={()=>setSelected(item.id===selected?null:item.id)}>{item.id===selected?'Hide History':'View History'}</Button><Button size="sm" disabled={busy} onClick={()=>void restore(item.id)}>Restore Item</Button></div>
  </div>)}
  {detail&&<InventoryHistory key={detail.id} item={detail} currency={settings.taxAndCurrency} online={isOnline}/>}
  </>}
  </div>
 </Modal>;
}
