import React, { useState } from 'react';
import { ArrowDownLeft, ArrowUpRight, CheckCircle2, Play } from 'lucide-react';
import { useApp } from '../../context/AppContext.tsx';
import { formatCurrency } from '../../shared/pricingEngine.ts';
import { Button, Card, Input, Select, StatusIndicator } from '../../components/ui/Primitives.tsx';
import { DataTable } from '../../components/ui/DataTable.tsx';
import { Modal } from '../../components/ui/Overlays.tsx';

export const ShiftsModule: React.FC = () => {
  const {
    settings,
    shifts,
    activeShift,
    startShift,
    recordCashMovement,
    closeShift,
  } = useApp();
  const taxCurr = settings.taxAndCurrency;

  const [openingCash, setOpeningCash] = useState('1000');
  const [movementOpen, setMovementOpen] = useState(false);
  const [moveType, setMoveType] = useState<'cash_in' | 'cash_out'>('cash_out');
  const [moveAmount, setMoveAmount] = useState('');
  const [moveReason, setMoveReason] = useState('');

  const [closeOpen, setCloseOpen] = useState(false);
  const [actualCash, setActualCash] = useState('');
  const [reconciliationNote, setReconciliationNote] = useState('');

  const handleStart = async (e: React.FormEvent) => {
    e.preventDefault();
    await startShift(Math.max(0, Number(openingCash) || 0));
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
              <Button variant="outline" size="sm" onClick={() => setMovementOpen(true)}>
                <ArrowUpRight className="w-4 h-4" />
                Cash In / Cash Out
              </Button>
              <Button
                variant="danger"
                size="sm"
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
        onClose={() => setMovementOpen(false)}
        title="Cash In / Cash Out"
        maxWidth="sm"
      >
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            if (!activeShift) return;
            await recordCashMovement(activeShift.id, moveType, Number(moveAmount) || 0, moveReason);
            setMoveAmount('');
            setMoveReason('');
            setMovementOpen(false);
          }}
          className="space-y-3"
        >
          <Select
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
            min="1"
            step="0.01"
            value={moveAmount}
            onChange={(e) => setMoveAmount(e.target.value)}
            required
          />
          <Input
            label="Reason"
            placeholder="e.g. Safe drop or change replenishment"
            value={moveReason}
            onChange={(e) => setMoveReason(e.target.value)}
            required
          />
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" type="button" onClick={() => setMovementOpen(false)}>
              Cancel
            </Button>
            <Button type="submit">Record</Button>
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
            await closeShift(activeShift.id, Number(actualCash) || 0, reconciliationNote);
            setCloseOpen(false);
          }}
          className="space-y-3"
        >
          <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-900/50 border border-[var(--pos-border)] text-xs flex justify-between">
            <span>Expected Drawer Cash:</span>
            <span className="font-mono-num font-bold">
              {formatCurrency(activeShift?.expectedCash || 0, taxCurr)}
            </span>
          </div>
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
