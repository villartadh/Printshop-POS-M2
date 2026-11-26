import type Database from 'better-sqlite3';
import { randomUUID } from 'node:crypto';
import {isLedgerMoney,cents,fromCents,sumCents} from '../shared/money.ts';
const money = (n: number) => Math.round(n * 100) / 100;
const fail = (message: string, statusCode = 409): never => { throw Object.assign(new Error(message), { statusCode }); };
export function paymentSplit(payments: {methodType:string;amount:number}[], change: number) {
  if(!Array.isArray(payments)||!isLedgerMoney(change)||payments.some(p=>!p||!isLedgerMoney(p.amount)||p.methodType==='credit_balance'))fail('Unsupported or invalid payment entries.');
  const cash=fromCents(sumCents(payments.filter(p=>p.methodType==='cash').map(p=>p.amount))-cents(change));
  const electronic=fromCents(sumCents(payments.filter(p=>p.methodType!=='cash').map(p=>p.amount)));
  if (cash < 0) fail('Invalid cash change.');
  return {cash,electronic};
}
// Caller must run within a database transaction. Original sale and closed shifts stay immutable except status.
export function reverseSale(db: Database.Database, id: string, kind: 'voided'|'refunded', reason: string, actorId: string, actorName: string) {
  if (!reason.trim()) fail('A reason is required.',400);
  const sale = db.prepare('SELECT * FROM transactions WHERE id=?').get(id) as any;
  if (!sale) fail('Transaction not found.',404);
  if (sale.type !== 'sale' || ['voided','refunded'].includes(sale.payment_status) || sale.is_demo) fail('Transaction cannot be reversed.');
  const paid = Number(sale.amount_paid), due = Number(sale.balance_due);
  if (!isLedgerMoney(paid)||!isLedgerMoney(due))fail('Original amounts require manual reconciliation.');
  if (kind === 'voided' && paid !== 0) fail('Paid or partially paid sales must be refunded, not voided.');
  if (kind === 'refunded' && paid <= 0) fail('Unpaid sales must be voided.');
  const {cash,electronic} = paymentSplit(JSON.parse(sale.payments_json),Number(sale.change_amount));
  if(cents(cash)+cents(electronic)!==cents(paid))fail('Original payment records do not reconcile.');
  if (db.prepare('SELECT 1 FROM balance_collections WHERE transaction_id=?').get(id)) fail('Sale has later collections; manual reconciliation is required before reversal.');
  let shift: any;
  if (paid > 0) {
    shift = db.prepare("SELECT * FROM cashier_shifts WHERE cashier_id=? AND status='open' ORDER BY started_at DESC LIMIT 1").get(actorId);
    if (!shift) fail('Open your own cashier shift before recording a refund.');
    if (Number(shift.expected_cash)+0.001 < cash) fail('Insufficient expected cash for refund.');
  }
  if (sale.customer_id) {
    const customer = db.prepare('SELECT * FROM customers WHERE id=?').get(sale.customer_id) as any;
    if (!customer || customer.outstanding_balance+0.001 < due || customer.total_spent+0.001 < paid) fail('Customer ledger needs reconciliation before reversal.');
    db.prepare('UPDATE customers SET outstanding_balance=ROUND(outstanding_balance-?,2), total_spent=ROUND(total_spent-?,2), loyalty_points=MAX(0,loyalty_points-?) WHERE id=?').run(due,paid,Math.floor(paid/10),sale.customer_id);
  }
  const now = new Date().toISOString();
  if (shift) db.prepare('UPDATE cashier_shifts SET refunds_total=ROUND(refunds_total+?,2), expected_cash=ROUND(expected_cash-?,2) WHERE id=?').run(paid,cash,shift.id);
  db.prepare('INSERT INTO transaction_reversals (transaction_id,kind,reason,actor_id,shift_id,cash_amount,electronic_amount,cancelled_receivable,created_at) VALUES (?,?,?,?,?,?,?,?,?)').run(id,kind,reason,actorId,shift?.id??null,cash,electronic,due,now);
  db.prepare(`UPDATE transactions SET payment_status=?, ${kind==='voided'?'void_reason':'refund_reason'}=? WHERE id=?`).run(kind,reason,id);
  const jobs = db.prepare('SELECT * FROM print_jobs WHERE transaction_id=?').all(id) as any[];
  for (const job of jobs) {
    const status = ['pending','awaiting_approval','quotation'].includes(job.production_status)?'cancelled':job.production_status;
    const history = JSON.parse(job.history_json);
    history.push({status,changedBy:actorName,note:`Sale ${kind}: ${reason}. Stock not automatically returned.`,timestamp:now});
    db.prepare('UPDATE print_jobs SET payment_status=?, production_status=?, remaining_balance=0, history_json=?, updated_at=? WHERE id=?').run(kind,status,JSON.stringify(history),now,job.id);
  }
  return {cash,electronic,due};
}
export function collectBalance(db: Database.Database, customerId: string, amount: number, actorId: string) {
  if (!Number.isFinite(amount) || amount <= 0 || Math.abs(amount-money(amount))>0.000001) fail('Enter a positive amount in cents.',400);
  const customer = db.prepare('SELECT * FROM customers WHERE id=?').get(customerId) as any;
  if (!customer) fail('Customer not found.',404);
  const shift = db.prepare("SELECT * FROM cashier_shifts WHERE cashier_id=? AND status='open' ORDER BY started_at DESC LIMIT 1").get(actorId) as any;
  if (!shift) fail('Open your own cashier shift to collect cash.');
  const sales = db.prepare("SELECT * FROM transactions WHERE customer_id=? AND type='sale' AND is_demo=0 AND payment_status IN ('unpaid','partial') AND balance_due>0 ORDER BY created_at,id").all(customerId) as any[];
  if(!isLedgerMoney(customer.outstanding_balance)||sales.some(t=>!isLedgerMoney(t.balance_due)||!isLedgerMoney(t.amount_paid)))fail('Customer ledger requires manual reconciliation.');
  const totalDue=fromCents(sumCents(sales.map(t=>t.balance_due)));
  if(cents(totalDue)!==cents(customer.outstanding_balance)||cents(amount)>cents(totalDue))fail('Amount exceeds balance or customer ledger needs reconciliation.');
  let left=amount; const now=new Date().toISOString();
  for (const sale of sales) {
    if (left<=0) break;
    const applied=money(Math.min(left,Number(sale.balance_due))), due=money(Number(sale.balance_due)-applied), paid=money(Number(sale.amount_paid)+applied);
    const status=due===0?'paid':'partial'; const collectionId=randomUUID();
    const payments=JSON.parse(sale.payments_json);
    payments.push({id:collectionId,methodId:'cash',methodName:'Cash',methodType:'cash',amount:applied,referenceNumber:'',verificationStatus:'verified',paidAt:now});
    db.prepare('INSERT INTO balance_collections (id,transaction_id,customer_id,shift_id,amount,actor_id,created_at) VALUES (?,?,?,?,?,?,?)').run(collectionId,sale.id,customerId,shift.id,applied,actorId,now);
    db.prepare('UPDATE transactions SET amount_paid=?,balance_due=?,payment_status=?,payments_json=? WHERE id=?').run(paid,due,status,JSON.stringify(payments),sale.id);
    db.prepare('UPDATE print_jobs SET deposit_amount=?,remaining_balance=?,payment_status=?,updated_at=? WHERE transaction_id=?').run(paid,due,status,now,sale.id);
    left=money(left-applied);
  }
  if (left!==0) fail('Collection allocation failed.');
  db.prepare('UPDATE customers SET outstanding_balance=ROUND(outstanding_balance-?,2),total_spent=ROUND(total_spent+?,2) WHERE id=?').run(amount,amount,customerId);
  db.prepare('UPDATE cashier_shifts SET cash_sales=ROUND(cash_sales+?,2),expected_cash=ROUND(expected_cash+?,2) WHERE id=?').run(amount,amount,shift.id);
  return money(totalDue-amount);
}
