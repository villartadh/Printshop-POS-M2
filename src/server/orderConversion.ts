import type { Transaction } from '../shared/types.ts';
import type { db as database } from './db.ts';
// Must execute inside the same transaction as sale insertion and stock movements.
export function recordOrderConversion(db: typeof database, payload: Pick<Transaction,'type'|'sourceOrderId'|'customerId'>, txId: string) {
      if (payload.type !== 'sale') throw Object.assign(new Error('Only sales can convert saved orders.'), {statusCode:400});
      const source = db.prepare("SELECT * FROM transactions WHERE id=? AND type IN ('held','quotation')").get(payload.sourceOrderId) as Record<string,unknown> | undefined;
      if (!source || db.prepare('SELECT 1 FROM order_conversions WHERE source_id=?').get(payload.sourceOrderId)) throw Object.assign(new Error('Saved order unavailable or already converted.'), {statusCode:409});
      if ((source.customer_id || null) !== (payload.customerId || null)) throw Object.assign(new Error('Saved order customer must match.'), {statusCode:409});
      db.prepare('INSERT INTO order_conversions VALUES (?,?,?)').run(payload.sourceOrderId,txId,new Date().toISOString());
}
