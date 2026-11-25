import type { Transaction } from './types.ts';
export function validateTransaction(t: Transaction) {
  const fail = (message: string): never => { throw Object.assign(new Error(message), { statusCode: 400 }); };
  if (!t || typeof t.clientMutationId !== 'string' || !t.clientMutationId || t.clientMutationId.length>200 || !['sale','held','quotation'].includes(t.type) || !Array.isArray(t.items) || !t.items.length || !Array.isArray(t.payments)) fail('Invalid transaction structure.');
  const nonnegative = (n: number) => typeof n === 'number' && Number.isFinite(n) && n >= 0;
  if (![t.subtotal,t.itemDiscountsTotal,t.orderDiscountAmount,t.taxAmount,t.totalAmount,t.amountPaid,t.changeAmount,t.balanceDue].every(nonnegative)) fail('Invalid transaction amounts.');
  for (const i of t.items) {
    if (![i.lineSubtotal,i.lineDiscount,i.lineTotal,i.effectiveQuantity,i.unitPrice].every(nonnegative) || i.effectiveQuantity <= 0 || !Array.isArray(i.consumables) || i.consumables.some(c => !nonnegative(c.quantityPerUnit))) fail('Invalid transaction item.');
  }
  if (t.payments.some(p => !nonnegative(p.amount) || p.methodType === 'credit_balance')) fail('Invalid payment amount.');
  const equal = (a: number,b: number) => Math.abs(a-b) < 0.021;
  if (t.items.some(i => i.lineDiscount > i.lineSubtotal || !equal(i.lineSubtotal-i.lineDiscount,i.lineTotal))) fail('Invalid line totals.');
  if (!equal(t.items.reduce((sum,i)=>sum+i.lineSubtotal,0),t.subtotal) || !equal(t.items.reduce((sum,i)=>sum+i.lineDiscount,0),t.itemDiscountsTotal)) fail('Item totals do not match order.');
  const base=t.subtotal-t.itemDiscountsTotal-t.orderDiscountAmount;
  if (base < -0.01 || !(equal(base,t.totalAmount) || equal(base+t.taxAmount,t.totalAmount)) || t.taxAmount > t.totalAmount+0.01) fail('Invalid order total.');
  if (!equal(t.amountPaid+t.balanceDue,t.totalAmount)) fail('Payment and balance do not match total.');
  if (t.type === 'sale') {
    const paid = t.payments.reduce((sum,p)=>sum+p.amount,0);
    if (!equal(paid-t.changeAmount,t.amountPaid)) fail('Payment entries do not match paid amount.');
    const cash = t.payments.filter(p=>p.methodType==='cash').reduce((sum,p)=>sum+p.amount,0);
    if (t.changeAmount > cash+0.01) fail('Change cannot exceed cash tendered.');
    const status = t.balanceDue <= 0 ? 'paid' : t.amountPaid > 0 ? 'partial' : 'unpaid';
    if (t.paymentStatus !== status) fail('Invalid payment status.');
  } else if (t.amountPaid || t.changeAmount || t.payments.length || t.paymentStatus !== 'unpaid') fail('Held orders and quotations cannot record payment.');
}
