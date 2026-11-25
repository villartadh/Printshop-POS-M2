import type { ProductionStatus } from './types.ts';
const transitions: Record<ProductionStatus, ProductionStatus[]> = {
 quotation:['awaiting_approval','cancelled'], awaiting_approval:['quotation','pending','cancelled'], pending:['in_production','cancelled'], in_production:['quality_checking'], quality_checking:['in_production','ready_for_pickup'], ready_for_pickup:['quality_checking','completed'], completed:[], cancelled:[]
};
export function allowedJobStatuses(current: ProductionStatus, payment: string, balance: number, linked: boolean, total: number): ProductionStatus[] {
 return [current,...(transitions[current] || []).filter(next => {
  if (next==='cancelled') return !linked && (payment==='unpaid'||total===0);
  if (payment==='refunded'||payment==='voided') return false;
  if (next==='completed') return Number.isFinite(balance)&&balance===0&&payment==='paid'&&(linked||total===0);
  if (next==='pending' && !linked && total>0) return false;
  return true;
 })];
}
export function validateJobPatch(current: ProductionStatus, payment: string, balance: number, linked: boolean, total: number, patch: Record<string, unknown>) {
 if ('paymentStatus' in patch || 'depositAmount' in patch) throw new Error('Payments must be recorded through POS or customer balance collection.');
 if ('productionStatus' in patch && !allowedJobStatuses(current,payment,balance,linked,total).includes(patch.productionStatus as ProductionStatus)) throw new Error('Invalid production transition or outstanding payment.');
}
