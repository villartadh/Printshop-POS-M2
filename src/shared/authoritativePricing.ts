import type { AppSettings, CartItem, PrintService, Transaction } from './types.ts';
import { calculateServicePrice, calculateCartItemTotals, calculateOrderTotals, roundAmount } from './pricingEngine.ts';

const fail = (message: string): never => { throw Object.assign(new Error(message), { statusCode: 409 }); };
const finite = (n: number) => typeof n === 'number' && Number.isFinite(n) && n >= 0 && n <= 1e9;
const same = (a: number, b: number) => finite(a) && finite(b) && Math.abs(a-b) < 0.000001;
function discount(type: string, value: number) {
  if (!['none','fixed','percent'].includes(type) || !finite(value) || (type === 'percent' && value > 100) || (type === 'none' && value !== 0)) fail('Invalid discount.');
}
export function verifyCatalogPricing(t: Transaction, lookup: (id: string) => PrintService | undefined, settings: AppSettings, role: string): Transaction {
  const tax = settings.taxAndCurrency;
  if (!tax || ![0,1,2,3,4].includes(tax.decimalPlaces) || !finite(tax.taxRatePercent) || tax.taxRatePercent > 100 || !['none','nearest_0_25','nearest_1'].includes(tax.roundingMode)) fail('Invalid business tax settings.');
  const privileged = role === 'admin' || role === 'manager';
  const items = t.items.map((item): CartItem => {
    const c = item.config;
    if (!c || ![c.quantity,c.pages,c.copies].every(n=>Number.isSafeInteger(n)&&n>=1&&n<=100000) || ![c.width,c.height].every(n=>finite(n)&&n>0) || !['ft','in','cm','m'].includes(c.dimensionUnit) || !finite(c.designFee) || !finite(c.laborFee) || typeof c.isRush !== 'boolean' || !c.selectedAttributes || typeof c.selectedAttributes !== 'object' || Array.isArray(c.selectedAttributes)) fail('Invalid service configuration.');
    discount(item.discountType,item.discountValue);
    let canonical: CartItem;
    if (item.serviceId === 'custom' && item.isCustomItem === true) {
      if (!privileged || !finite(item.unitPrice) || item.unitPrice<=0 || !item.serviceName?.trim()) fail('Custom pricing requires a manager or administrator.');
      canonical = {...item, sku:'CUSTOM', pricingMethod:'fixed', unitLabel:'pc', effectiveQuantity:c.quantity, lineSubtotal:roundAmount(item.unitPrice*c.quantity,tax), costTotal:0, requiresProductionJob:false, consumables:[], displaySummary:'Custom Service'};
    } else {
      const s = lookup(item.serviceId);
      if (!s || s.isArchived || item.isCustomItem) return fail('Service unavailable. Refresh the cart.');
      if (c.isRush && !s.allowRushFee) fail('Rush pricing is not available.');
      if (!privileged && c.designFee < (s.defaultDesignFee || 0)) fail('Reducing the configured design fee requires a manager.');
      for (const key of Object.keys(c.selectedAttributes)) if (!s.attributeGroups.some(g=>g.key===key)) fail('Unknown service attribute.');
      for (const g of s.attributeGroups) if (g.options.length && !g.options.some(o=>o.id===c.selectedAttributes[g.key])) fail('Invalid service option.');
      const calc = calculateServicePrice(s,c,tax);
      canonical = {...item, sku:s.sku,serviceName:s.name,pricingMethod:s.pricingMethod,unitLabel:s.unitLabel,unitPrice:calc.unitPrice,effectiveQuantity:calc.effectiveQuantity,lineSubtotal:calc.lineSubtotal,costTotal:calc.estimatedCost,displaySummary:calc.displaySummary,requiresProductionJob:s.requiresProductionJob,consumables:s.consumables,isCustomItem:false};
    }
    canonical = {...canonical,...calculateCartItemTotals(canonical,tax)};
    for (const key of ['unitPrice','effectiveQuantity','lineSubtotal','lineDiscount','lineTotal'] as const) if (!same(item[key],canonical[key])) fail('Price or quantity changed. Rebuild the cart and retry.');
    if (!finite(canonical.costTotal) || !canonical.consumables.every(b=>finite(b.quantityPerUnit))) fail('Invalid catalog cost or consumables.');
    return canonical;
  });
  discount(t.orderDiscountType,t.orderDiscountValue);
  const totals = calculateOrderTotals(items,t.orderDiscountType,t.orderDiscountValue,tax);
  for (const key of ['subtotal','itemDiscountsTotal','orderDiscountAmount','taxAmount','totalAmount'] as const) if (!same(t[key],totals[key])) fail('Order pricing or tax changed. Rebuild the cart and retry.');
  const payments = t.payments.map(p=>{
    const method = settings.paymentMethods.find(m=>m.id===p.methodId && m.enabled);
    if (!method || method.type==='credit_balance' || method.type!==p.methodType || (method.requiresReference && !p.referenceNumber?.trim())) return fail('Invalid or disabled payment method.');
    if (!['verified','pending_verification'].includes(p.verificationStatus)) fail('Invalid verification status.');
    return {...p,methodName:method.name,methodType:method.type};
  });
  return {...t,...totals,items,payments};
}
