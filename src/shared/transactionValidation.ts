import type { Transaction } from './types.ts';
import {isLedgerMoney,cents,sumCents} from './money.ts';
export function validateTransaction(t:Transaction){
 const fail=(message:string):never=>{throw Object.assign(new Error(message),{statusCode:400});};
 if(!t||typeof t.clientMutationId!=='string'||!t.clientMutationId||t.clientMutationId.length>200||!['sale','held','quotation'].includes(t.type)||!Array.isArray(t.items)||!t.items.length||!Array.isArray(t.payments))fail('Invalid transaction structure.');
 if(![t.subtotal,t.itemDiscountsTotal,t.orderDiscountAmount,t.taxAmount,t.totalAmount,t.amountPaid,t.changeAmount,t.balanceDue].every(isLedgerMoney))fail('Invalid transaction amounts: use centavo precision.');
 const nonnegative=(n:unknown)=>typeof n==='number'&&Number.isFinite(n)&&n>=0&&n<=1e9;
 for(const i of t.items){
  if(!i||![i.lineSubtotal,i.lineDiscount,i.lineTotal].every(isLedgerMoney)||!nonnegative(i.effectiveQuantity)||i.effectiveQuantity<=0||!nonnegative(i.unitPrice)||!Array.isArray(i.consumables)||i.consumables.some(c=>!c||!nonnegative(c.quantityPerUnit)))fail('Invalid transaction item.');
  if(cents(i.lineSubtotal)-cents(i.lineDiscount)!==cents(i.lineTotal))fail('Invalid line totals.');
 }
 if(t.payments.some(p=>!p||!isLedgerMoney(p.amount)||p.methodType==='credit_balance'))fail('Invalid payment amount.');
 if(sumCents(t.items.map(i=>i.lineSubtotal))!==cents(t.subtotal)||sumCents(t.items.map(i=>i.lineDiscount))!==cents(t.itemDiscountsTotal))fail('Item totals do not match order.');
 const base=cents(t.subtotal)-cents(t.itemDiscountsTotal)-cents(t.orderDiscountAmount);
 const total=cents(t.totalAmount),tax=cents(t.taxAmount),paid=cents(t.amountPaid),due=cents(t.balanceDue),change=cents(t.changeAmount);
 if(base<0||!(base===total||base+tax===total)||tax>total)fail('Invalid order total.');
 if(paid+due!==total)fail('Payment and balance do not match total.');
 if(t.type==='sale'){
  if(sumCents(t.payments.map(p=>p.amount))-change!==paid)fail('Payment entries do not match paid amount.');
  if(change>sumCents(t.payments.filter(p=>p.methodType==='cash').map(p=>p.amount)))fail('Change cannot exceed cash tendered.');
  const status=due===0?'paid':paid>0?'partial':'unpaid';
  if(t.paymentStatus!==status)fail('Invalid payment status.');
 }else if(paid||change||t.payments.length||t.paymentStatus!=='unpaid')fail('Held orders and quotations cannot record payment.');
}
