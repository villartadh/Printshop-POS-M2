import React,{useEffect,useState} from 'react';
import {LineChart,Line,XAxis,YAxis,Tooltip,ResponsiveContainer,CartesianGrid} from 'recharts';
import {apiRequest} from '../../services/apiClient.ts';
import {formatCurrency} from '../../shared/pricingEngine.ts';
import {STOCK_MOVEMENT_LABELS} from '../../shared/inventoryModel.ts';
import type {InventoryItem,StockMovement} from '../../types/inventory.ts';
import type {TaxAndCurrencyConfig} from '../../shared/types.ts';
import {Button} from '../ui/Primitives.tsx';
import {DataTable} from '../ui/DataTable.tsx';
type History={snapshot:number;total:number;offset:number;limit:number;movements:StockMovement[];summary:{count:number;minimum:number|null;maximum:number|null};purchasePoints:StockMovement[]};
const date=(stamp:string)=>new Date(stamp).toLocaleString('en-PH',{timeZone:'Asia/Manila'});
export function InventoryHistory({item,currency,online}:{item:InventoryItem;currency:TaxAndCurrencyConfig;online:boolean}){
 const [history,setHistory]=useState<History|null>(null);
 const [loading,setLoading]=useState(false);
 const [error,setError]=useState('');
 const [query,setQuery]=useState<{offset:number;snapshot?:number;revision:number}>({offset:0,revision:0});
 useEffect(()=>{
   if(!online){setHistory(null);setLoading(false);return;}
   const controller=new AbortController();
   setLoading(true);setError('');
   const params=new URLSearchParams({offset:String(query.offset),limit:'50'});
   if(query.snapshot!==undefined)params.set('snapshot',String(query.snapshot));
   apiRequest<History>('/api/inventory/'+encodeURIComponent(item.id)+'/history?'+params,{signal:controller.signal}).then(h=>{if(!controller.signal.aborted)setHistory(h);}).catch(e=>{if(!controller.signal.aborted){setHistory(null);setError(e instanceof Error?e.message:'History unavailable');}}).finally(()=>{if(!controller.signal.aborted)setLoading(false);});
   return ()=>controller.abort();
 },[item.id,online,query]);
 const money=(n:number)=>formatCurrency(n,{...currency,decimalPlaces:Math.max(currency.decimalPlaces,4)});
 const refresh=()=>setQuery(q=>({offset:0,revision:q.revision+1}));
 const points=(history?.purchasePoints??[]).map(m=>({id:m.id,date:date(m.createdAt),price:m.purchaseUnitCost,reference:m.reference}));
 return <section className="space-y-3" aria-label="Complete inventory history">
   <div className="flex justify-between gap-2 items-center"><h3 className="text-sm font-semibold">Recorded Stock & Purchase History</h3><Button size="sm" variant="outline" disabled={!online||loading} onClick={refresh}>Refresh History</Button></div>
   {!online?<p className="text-sm">Full history requires a server connection. Pending offline movements are not included.</p>:loading?<p role="status">Loading inventory history…</p>:error?<p role="alert">{error}</p>:history&&<>
     <div className="rounded-lg border border-[var(--pos-border)] p-3 space-y-2">
       <h4 className="font-semibold text-sm">Recorded supplier purchase prices</h4>
       <p className="text-xs text-[var(--pos-text-muted)]">Only explicitly recorded supplier prices are charted, in server recording order. Opening stock, unknown prices and provisional offline movements are excluded. Currency uses current display settings.</p>
       {history.summary.count===0?<p className="text-sm">No explicit supplier prices recorded yet.</p>:<>
         <div className="grid grid-cols-2 gap-2 text-xs"><span>Recorded purchases: {history.summary.count}</span><span>Latest recorded price: {money(points[points.length-1].price!)}</span><span>Minimum: {money(history.summary.minimum!)}</span><span>Maximum: {money(history.summary.maximum!)}</span></div>
         <p className="text-xs">Showing {points.length} of {history.summary.count} priced purchases. All stock movements are accessible below.</p>
         {points.length===1?<p className="text-sm">{points[0].date} · {money(points[0].price!)}</p>:<div className="h-52 w-full min-w-0" role="img" aria-label="Recorded supplier purchase price trend"><ResponsiveContainer width="100%" height="100%"><LineChart data={points}><CartesianGrid strokeDasharray="3 3" opacity={0.3}/><XAxis dataKey="date" tick={{fontSize:10}} minTickGap={45}/><YAxis tick={{fontSize:10}} domain={['auto','auto']}/><Tooltip formatter={v=>money(Number(v))}/><Line type="linear" dataKey="price" name="Supplier price" stroke="var(--pos-primary)" dot={points.length<=20} isAnimationActive={false}/></LineChart></ResponsiveContainer></div>}
       </>}
     </div>
     <div className="flex flex-wrap items-center justify-between gap-2"><span className="text-xs">Showing {history.total?history.offset+1:0}–{Math.min(history.offset+history.movements.length,history.total)} of {history.total} recorded movements</span><div className="flex gap-2"><Button size="sm" variant="outline" disabled={history.offset===0} onClick={()=>setQuery(q=>({...q,offset:Math.max(0,history.offset-history.limit),snapshot:history.snapshot}))}>Previous</Button><Button size="sm" variant="outline" disabled={history.offset+history.limit>=history.total} onClick={()=>setQuery(q=>({...q,offset:history.offset+history.limit,snapshot:history.snapshot}))}>Next</Button></div></div>
     <DataTable rows={history.movements} rowKey={m=>m.id} emptyTitle="No recorded stock movements" emptyDescription="Accepted movements will appear here." columns={[
       {key:'movement',label:'Movement',render:m=><div><strong>{STOCK_MOVEMENT_LABELS[m.movementType]}</strong><div className="text-xs">{m.performedBy} · {m.reference||'No reference'}</div><div className="text-xs break-words">{m.note}</div></div>},
       {key:'date',label:'Recorded (PH)',render:m=><span className="text-xs">{date(m.createdAt)}</span>},
       {key:'delta',label:'Change',align:'right',render:m=>m.quantityDelta},
       {key:'balance',label:'Balance',align:'right',render:m=>m.balanceAfter},
       {key:'price',label:'Supplier price',align:'right',render:m=>m.purchaseUnitCost===undefined?'—':money(m.purchaseUnitCost)}
     ]}/>
   </>}
 </section>;
}
