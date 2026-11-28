// Monetary ledger values use integer centavos; catalog unit rates and quantities are separate.
export const MAX_LEDGER_AMOUNT=1e9;
export function isLedgerMoney(value:unknown):value is number{
 return typeof value==='number'&&Number.isFinite(value)&&value>=0&&value<=MAX_LEDGER_AMOUNT&&Math.abs(value*100-Math.round(value*100))<0.0001;
}
export function cents(value:unknown):number{
 if(!isLedgerMoney(value))throw Object.assign(new Error('Invalid monetary amount: use non-negative values with at most two decimal places.'),{statusCode:400});
 return Math.round(value*100);
}
export const fromCents=(value:number)=>value/100;
export function sumCents(values:unknown[]):number{
 const total=values.reduce<number>((sum,value)=>sum+cents(value),0);
 if(!Number.isSafeInteger(total)||total>MAX_LEDGER_AMOUNT*100)throw Object.assign(new Error('Monetary total exceeds supported limit.'),{statusCode:400});
 return total;
}
