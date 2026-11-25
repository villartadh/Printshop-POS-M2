import type {InventoryItem} from '../types/inventory.ts';
import {buildInventoryAlerts,extractSupplierInfo,getSuggestedRestockQuantity} from './inventoryModel.ts';
export type ReorderScope='critical'|'all';
export function reorderRows(items:InventoryItem[],scope:ReorderScope='all'){
  return buildInventoryAlerts(items).filter(a=>scope==='all'||a.severity!=='reorder').map(a=>{
    const supplier=extractSupplierInfo(a.item);
    const quantity=getSuggestedRestockQuantity(a.item);
    return {...a,supplier,quantity,estimatedCost:Number((quantity*a.item.unitCost).toFixed(2))};
  });
}
export type ReorderRow=ReturnType<typeof reorderRows>[number];
export function validOrderQuantity(n:number){return Number.isFinite(n)&&n>=0&&n<=1e9&&Math.abs(n-Number(n.toFixed(2)))<1e-9;}
export function groupReorderRows(rows:ReorderRow[]){
  const groups=new Map<string,{supplier:string;contact:string;rows:ReorderRow[]}>();
  for(const row of rows){
    const supplier=row.supplier.name||'Unassigned supplier',contact=row.supplier.contact||'';
    const key=JSON.stringify([supplier,contact]);
    if(!groups.has(key))groups.set(key,{supplier,contact,rows:[]});
    groups.get(key)!.rows.push(row);
  }
  return [...groups.values()].sort((a,b)=>a.supplier.localeCompare(b.supplier)||a.contact.localeCompare(b.contact));
}
// Prevent untrusted catalog text from being interpreted as spreadsheet formulas.
export function procurementCsvCell(value:string|number):string|number{
  return typeof value==='string'&&/^[\s\uFEFF]*[=+@-]|^[\t\r\n]/.test(value)?"'"+value:value;
}
