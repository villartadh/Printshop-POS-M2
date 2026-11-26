import React, { useEffect,useRef,useState } from 'react';
import { ArrowDownLeft, ArrowUpRight, CheckCircle2, Play } from 'lucide-react';
import { useApp } from '../../context/AppContext.tsx';
import { formatCurrency } from '../../shared/pricingEngine.ts';
import { Button, Card, Input, Select, StatusIndicator } from '../../components/ui/Primitives.tsx';
import { DataTable } from '../../components/ui/DataTable.tsx';
import { Modal } from '../../components/ui/Overlays.tsx';
import {readPendingDrawerMovement,reserveDrawerMovement,type PendingDrawerMovement} from '../../services/offlineDb.ts';

export const ShiftsModule: React.FC = () => {
  const {
    settings,
    shifts,
    activeShift,
    startShift,
    recordCashMovement,
    closeShift,
    session,isOnline,
  } = useApp();
  const taxCurr = settings.taxAndCurrency;

  const [openingCash, setOpeningCash] = useState('1000');
  const [movementOpen, setMovementOpen] = useState(false);
  const [moveType, setMoveType] = useState<'cash_in' | 'cash_out'>('cash_out');
  const [moveAmount, setMoveAmount] = useState('');
  const [moveReason, setMoveReason] = useState('');
  const [pending,setPending]=useState<PendingDrawerMovement|null>(null);
  const [storageReady,setStorageReady]=useState(false);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState('');
  const lock=useRef(false);
  const owner=session?.user.id;
  useEffect(()=>{
    let cancelled=false;setStorageReady(false);setPending(null);setError('');
    if(owner)readPendingDrawerMovement(owner).then(record=>{if(!cancelled){setPending(record);setStorageReady(true);}}).catch(()=>{if(!cancelled)setError('Drawer recovery storage unavailable. Do not repeat cash adjustments.');});
    return ()=>{cancelled=true;};
  },[owner]);
  const submitMovement=async(existing?:PendingDrawerMovement)=>{
    if(lock.current||!owner||!storageReady||!isOnline||(!existing&&(!activeShift||pending))||(existing&&existing.ownerId!==owner))return;
    lock.current=true;setBusy(true);setError('');
    try{
      const record=existing??await reserveDrawerMovement(owner,activeShift!.id,moveType,Number(moveAmount),moveReason);
      setPending(record);await recordCashMovement(record);
      setPending(null);setMoveAmount('');setMoveReason('');setMovementOpen(false);
    }catch(e){setError(e instanceof Error?e.message:'Drawer adjustment unconfirmed.');try{setPending(await readPendingDrawerMovement(owner));}catch{setStorageReady(false);}}
    finally{lock.current=false;setBusy(false);}
  };

  const [closeOpen, setCloseOpen] = useState(false);
  const [actualCash, setActualCash] = useState('');
  const [reconciliationNote, setReconciliationNote] = useState('');

  const handleStart = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');try{await startShift(Number(openingCash));}catch(e){setError(e instanceof Error?e.message:'Shift could not be opened.');}
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[var(--pos-text)]">Cashier Shift Management</h1>
          <p className="text-xs text-[var(--pos-text-muted)]">
            Track opening drawer float, cash in/out, expected vs actual cash, and reconciliation
          </p>
        </div>
      </div>

      {error&&<p role="alert" className="text-sm text-[var(--pos-error)]">{error}</p>}
      {pending&&pending.ownerId===owner&&<Card><div className="space-y-2">
        <h2 className="font-semibold">Unconfirmed drawer adjustment</h2>
        <p className="text-sm">{pending.type==='cash_in'?'Cash In':'Cash Out'} · {formatCurrency(pending.amount,taxCurr)} · {pending.reason}</p>
        <p className="text-xs break-all">Reference: {pending.clientMutationId}</p>
        <p className="text-sm">Do not move the cash again. Confirm the original reference before further drawer adjustments or shift closure on this terminal.</p>
        <Button size="sm" disabled={!isOnline||busy||!storageReady} onClick={()=>void submitMovement(pending)}>Retry Original Drawer Adjustment</Button>
      </div></Card>}
      {activeShift ? (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <Card className="lg:col-span-2 space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div className="text-xs text-[var(--pos-text-muted)]">Active Shift</div>
                <div className="text-lg font-bold text-[var(--pos-text)]">
                  {activeShift.cashierName}
                </div>
                <div className="text-xs text-[var(--pos-text-muted)]">
                  Started {new Date(activeShift.startedAt).toLocaleString()}
                </div>
              </div>
              <StatusIndicator tone="success" label="Shift In Progress" />
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-2">
              <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-900/50 border border-[var(--pos-border)]">
                <div className="text-[11px] text-[var(--pos-text-muted)]">Opening Cash</div>
                <div className="font-mono-num font-bold mt-0.5">
                  {formatCurrency(activeShift.openingCash, taxCurr)}
                </div>
              </div>
              <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-900/50 border border-[var(--pos-border)]">
                <div className="text-[11px] text-[var(--pos-text-muted)]">Cash Sales</div>
                <div className="font-mono-num font-bold mt-0.5">
                  {formatCurrency(activeShift.cashSales, taxCurr)}
                </div>
              </div>
              <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-900/50 border border-[var(--pos-border)]">
                <div className="text-[11px] text-[var(--pos-text-muted)]">Electronic Sales</div>
                <div className="font-mono-num font-bold mt-0.5">
                  {formatCurrency(activeShift.electronicSales, taxCurr)}
                </div>
              </div>
              <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-900/50 border border-[var(--pos-border)]">
                <div className="text-[11px] text-[var(--pos-text-muted)]">Expected Drawer</div>
                <div className="font-mono-num font-bold text-[var(--pos-primary)] mt-0.5">
                  {formatCurrency(activeShift.expectedCash, taxCurr)}
                </div>
              </div>
            </div>

            <div className="flex flex-wrap gap-2.5 pt-2">
              <Button variant="outline" size="sm" disabled={!storageReady||!isOnline||!!pending||busy} onClick={() => {setError('');setMovementOpen(true);}}>
                <ArrowUpRight className="w-4 h-4" />
                Cash In / Cash Out
              </Button>
              <Button
                variant="danger"
                size="sm"
                disabled={!storageReady||!isOnline||!!pending||busy}
                onClick={() => {
                  setActualCash(String(activeShift.expectedCash));
                  setCloseOpen(true);
                }}
              >
                <CheckCircle2 className="w-4 h-4" />
                End & Reconcile Shift
              </Button>
            </div>
          </Card>

          <Card className="space-y-3">
            <h2 className="text-sm font-semibold">Drawer Cash Movements</h2>
            {activeShift.cashMovements.length === 0 ? (
              <p className="text-xs text-[var(--pos-text-muted)] py-4">
                No cash in/out adjustments recorded during this shift.
              </p>
            ) : (
              <div className="space-y-2 max-h-56 overflow-y-auto">
                {activeShift.cashMovements.map((m) => (
                  <div
                    key={m.id}
                    className="flex items-center justify-between text-xs py-1.5 border-b border-[var(--pos-border)] last:border-0"
                  >
                    <div className="flex items-center gap-2">
                      {m.type === 'cash_in' ? (
                        <ArrowDownLeft className="w-3.5 h-3.5 text-[#16A34A]" />
                      ) : (
                        <ArrowUpRight className="w-3.5 h-3.5 text-[#DC2626]" />
                      )}
                      <div>
                        <div className="font-medium">{m.reason}</div>
                        <div className="text-[11px] text-[var(--pos-text-muted)]">
                          {new Date(m.timestamp).toLocaleTimeString()}
                        </div>
                      </div>
                    </div>
                    <span className="font-mono-num font-semibold">
                      {m.type === 'cash_in' ? '+' : '-'}
                      {formatCurrency(m.amount, taxCurr)}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>
      ) : (
        <Card className="max-w-md">
          <h2 className="text-base font-semibold mb-1">Start a Cashier Shift</h2>
          <p className="text-xs text-[var(--pos-text-muted)] mb-4">
            Count your starting cash drawer float before accepting counter cash payments.
          </p>
          <form onSubmit={handleStart} className="space-y-3">
            <Input
              label={`Opening Cash Float (${taxCurr.currencySymbol})`}
              type="number"
              min="0"
              step="0.01"
              value={openingCash}
              onChange={(e) => setOpeningCash(e.target.value)}
              required
            />
            <Button type="submit" className="w-full">
              <Play className="w-4 h-4" />
              Open Cashier Shift
            </Button>
          </form>
        </Card>
      )}

      {/* Shift History */}
      <div className="space-y-2">
        <h2 className="text-sm font-semibold text-[var(--pos-text)]">Shift History</h2>
        <DataTable
          rows={shifts}
          rowKey={(s) => s.id}
          columns={[
            {
              key: 'cashier',
              label: 'Cashier',
              render: (s) => (
                <div>
                  <div className="font-medium text-sm">{s.cashierName}</div>
                  <div className="text-[11px] text-[var(--pos-text-muted)]">
                    {new Date(s.startedAt).toLocaleString()}
                  </div>
                </div>
              ),
            },
            {
              key: 'status',
              label: 'Status',
              render: (s) => (
                <StatusIndicator
                  tone={s.status === 'open' ? 'success' : 'neutral'}
                  label={s.status === 'open' ? 'Open' : 'Closed'}
                />
              ),
            },
            {
              key: 'expected',
              label: 'Expected Cash',
              align: 'right',
              render: (s) => (
                <span className="font-mono-num">{formatCurrency(s.expectedCash, taxCurr)}</span>
              ),
            },
            {
              key: 'actual',
              label: 'Actual Cash',
              align: 'right',
              hideOnMobile: true,
              render: (s) => (
                <span className="font-mono-num">
                  {s.actualCash !== null ? formatCurrency(s.actualCash, taxCurr) : '—'}
                </span>
              ),
            },
            {
              key: 'disc',
              label: 'Discrepancy',
              align: 'right',
              render: (s) =>
                s.discrepancy === null ? (
                  '—'
                ) : (
                  <span
                    className={`font-mono-num font-semibold ${
                      s.discrepancy === 0 ? 'text-[#16A34A]' : 'text-[#DC2626]'
                    }`}
                  >
                    {formatCurrency(s.discrepancy, taxCurr)}
                  </span>
                ),
            },
          ]}
        />
      </div>

      {/* Cash Movement Modal */}
      <Modal
        open={movementOpen}
        onClose={() => {if(!busy)setMovementOpen(false);}}
        title="Cash In / Cash Out"
        maxWidth="sm"
      >
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            await submitMovement();
          }}
          className="space-y-3"
        >
          {error&&<p role="alert" className="text-sm text-[var(--pos-error)]">{error}</p>}
          <Select
            disabled={busy||!!pending}
            label="Movement Type"
            value={moveType}
            onChange={(e) => setMoveType(e.target.value as 'cash_in' | 'cash_out')}
            options={[
              { value: 'cash_in', label: 'Cash In (Add to Drawer)' },
              { value: 'cash_out', label: 'Cash Out (Petty Cash / Drop)' },
            ]}
          />
          <Input
            label={`Amount (${taxCurr.currencySymbol})`}
            type="number"
            min="0.01"
            step="0.01"
            disabled={busy||!!pending}
            value={moveAmount}
            onChange={(e) => setMoveAmount(e.target.value)}
            required
          />
          <Input
            label="Reason"
            disabled={busy||!!pending}
            maxLength={500}
            placeholder="e.g. Safe drop or change replenishment"
            value={moveReason}
            onChange={(e) => setMoveReason(e.target.value)}
            required
          />
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" type="button" disabled={busy} onClick={() => setMovementOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={busy||!!pending||!storageReady||!isOnline}>Record</Button>
          </div>
        </form>
      </Modal>

      {/* Close Shift Modal */}
      <Modal
        open={closeOpen}
        onClose={() => setCloseOpen(false)}
        title="Close & Reconcile Shift"
        maxWidth="sm"
      >
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            if (!activeShift) return;
            setError('');try{await closeShift(activeShift.id,Number(actualCash),reconciliationNote);setCloseOpen(false);}catch(e){setError(e instanceof Error?e.message:'Shift closure failed.');}
          }}
          className="space-y-3"
        >
          <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-900/50 border border-[var(--pos-border)] text-xs flex justify-between">
            <span>Expected Drawer Cash:</span>
            <span className="font-mono-num font-bold">
              {formatCurrency(activeShift?.expectedCash || 0, taxCurr)}
            </span>
          </div>
          {error&&<p role="alert" className="text-sm text-[var(--pos-error)]">{error}</p>}
          <Input
            label={`Actual Counted Cash (${taxCurr.currencySymbol})`}
            type="number"
            min="0"
            step="0.01"
            value={actualCash}
            onChange={(e) => setActualCash(e.target.value)}
            required
          />
          <Input
            label="Reconciliation Note"
            placeholder="Explain any overage or shortage..."
            value={reconciliationNote}
            onChange={(e) => setReconciliationNote(e.target.value)}
          />
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" type="button" onClick={() => setCloseOpen(false)}>
              Cancel
            </Button>
            <Button variant="danger" type="submit">
              Close Shift
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
