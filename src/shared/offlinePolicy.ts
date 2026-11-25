import type { OfflineMutation } from './types.ts';
export function mutationOwner(m: OfflineMutation): string | undefined {
  return m.ownerId || (m.type === 'CREATE_TRANSACTION' ? (m.payload as {cashierId?: string})?.cashierId : undefined);
}
export function pendingBatch(queue: OfflineMutation[], owner: string, limit = 50): OfflineMutation[] {
  return queue.filter(m => mutationOwner(m) === owner && (m.status === 'pending' || m.status === 'syncing'))
    .sort((a,b) => a.createdAt.localeCompare(b.createdAt) || a.id.localeCompare(b.id)).slice(0,limit);
}
export function validateSyncEnvelope(m: OfflineMutation, owner: string): void {
  if (!m || typeof m.clientMutationId !== 'string' || !m.clientMutationId || m.clientMutationId.length > 200) throw new Error('Invalid mutation ID');
  if (mutationOwner(m) !== owner) throw new Error('Mutation owner mismatch');
  if (m.type === 'CREATE_TRANSACTION') {
    const p = m.payload as {clientMutationId?: string; cashierId?: string};
    if (p?.clientMutationId !== m.clientMutationId || p.cashierId !== owner) throw new Error('Transaction identity mismatch');
  } else if (m.type !== 'ADJUST_INVENTORY') throw new Error('Unsupported offline mutation');
}

export type SyncResult = { clientMutationId: string; status: 'applied' | 'duplicate' | 'error'; error?: string };
export function validateSyncResults(queue: OfflineMutation[], response: unknown): SyncResult[] {
  const envelope = response as {results?: unknown; syncedAt?: unknown} | null;
  if (!envelope || !Array.isArray(envelope.results) || typeof envelope.syncedAt !== 'string' || !Number.isFinite(Date.parse(envelope.syncedAt))) throw new Error('Invalid synchronization acknowledgment');
  const expected = new Set(queue.map(m=>m.clientMutationId));
  if (expected.size !== queue.length || envelope.results.length !== queue.length) throw new Error('Incomplete or ambiguous synchronization acknowledgment');
  for (const raw of envelope.results) {
    const item = raw as SyncResult | null;
    if (!item || typeof item.clientMutationId !== 'string' || !expected.delete(item.clientMutationId) || !['applied','duplicate','error'].includes(item.status) || (item.error !== undefined && typeof item.error !== 'string')) throw new Error('Unrecognized or duplicate synchronization acknowledgment');
  }
  return envelope.results as SyncResult[];
}
