import type {StockMovementType} from '../types/inventory.ts';
export function validatePurchaseCost(cost:unknown,type:StockMovementType,role:string):void{
  if(cost===undefined)return;
  if(!['admin','manager'].includes(role))throw Object.assign(new Error('Only administrators and managers can record purchase prices.'),{statusCode:403});
  if(type!=='purchase')throw new Error('Supplier purchase price is only valid for a purchase movement.');
  if(typeof cost!=='number'||!Number.isFinite(cost)||cost<0||cost>1e9||Math.abs(cost-Number(cost.toFixed(4)))>1e-9)throw new Error('Purchase price must be a finite non-negative number with up to four decimal places.');
}
export function weightedPurchaseCost(stock:number,currentCost:number,quantity:number,purchasePrice:number|undefined):number{
  if(purchasePrice===undefined)return currentCost;
  if(!Number.isFinite(stock)||stock<0||!Number.isFinite(currentCost)||currentCost<0||!Number.isFinite(quantity)||quantity<=0)throw new Error('Invalid stock cost basis.');
  const total=stock+quantity;
  if(!Number.isFinite(total))throw new Error('Invalid stock cost basis.');
  const result=Number((stock/total*currentCost+quantity/total*purchasePrice).toFixed(4));
  if(!Number.isFinite(result))throw new Error('Invalid calculated stock cost.');
  return result;
}
