import { allowedJobStatuses } from '../../shared/jobWorkflow.ts';
import React, { useMemo, useState } from 'react';
import { Columns, List, Plus, Printer, Table as TableIcon } from 'lucide-react';
import { useApp } from '../../context/AppContext.tsx';
import { formatCurrency } from '../../shared/pricingEngine.ts';
import type { PrintJob, ProductionStatus } from '../../shared/types.ts';
import { Button, Card, Input, Select, StatusIndicator } from '../../components/ui/Primitives.tsx';
import { DataTable } from '../../components/ui/DataTable.tsx';
import { EmptyState, Modal } from '../../components/ui/Overlays.tsx';
import { ReceiptModal } from '../receipts/ReceiptModal.tsx';

const PRODUCTION_STATUSES: Array<{ value: ProductionStatus; label: string }> = [
  { value: 'quotation', label: 'Quotation' },
  { value: 'awaiting_approval', label: 'Awaiting Approval' },
  { value: 'pending', label: 'Pending' },
  { value: 'in_production', label: 'In Production' },
  { value: 'quality_checking', label: 'Quality Checking' },
  { value: 'ready_for_pickup', label: 'Ready for Pickup' },
  { value: 'completed', label: 'Completed' },
  { value: 'cancelled', label: 'Cancelled' },
];

export const PrintJobsModule: React.FC = () => {
  const { settings, printJobs, customers, createPrintJob, updatePrintJob, notify } = useApp();
  const taxCurr = settings.taxAndCurrency;

  const [viewMode, setViewMode] = useState<'kanban' | 'table' | 'list'>('table');
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [search, setSearch] = useState('');
  const [selectedJob, setSelectedJob] = useState<PrintJob | null>(null);
  const [receiptJob, setReceiptJob] = useState<PrintJob | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  // New job form
  const [title, setTitle] = useState('');
  const [customerId, setCustomerId] = useState('');
  const [customerName, setCustomerName] = useState('');
  const [specs, setSpecs] = useState('');
  const [instructions, setInstructions] = useState('');
  const [attachments, setAttachments] = useState('');
  const [totalAmount, setTotalAmount] = useState('500');
  const [deadline, setDeadline] = useState(
    new Date(Date.now() + 86400000).toISOString().slice(0, 16),
  );

  const filteredJobs = useMemo(() => {
    const q = search.trim().toLowerCase();
    return printJobs
      .filter((j) => (statusFilter === 'all' ? true : j.productionStatus === statusFilter))
      .filter((j) => {
        if (!q) return true;
        return (
          j.jobOrderNumber.toLowerCase().includes(q) ||
          j.title.toLowerCase().includes(q) ||
          j.customerName.toLowerCase().includes(q)
        );
      });
  }, [printJobs, statusFilter, search]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    const cust = customers.find((c) => c.id === customerId);
    try { await createPrintJob({
      title,
      customerId: cust?.id || null,
      customerName: cust?.name || customerName || 'Walk-in Customer',
      customerPhone: cust?.phone || '',
      specifications: specs,
      instructions,
      attachmentNames: attachments
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean),
      totalAmount: Number(totalAmount) || 0,
      depositAmount: 0,
      deadline: new Date(deadline).toISOString(),
    });
    setTitle('');
    setSpecs('');
    setInstructions('');
    setAttachments('');
    setCreateOpen(false);
    } catch (error) { notify((error as Error).message, 'error'); }
  };

  const statusTone = (s: ProductionStatus) => {
    if (s === 'completed' || s === 'ready_for_pickup') return 'success';
    if (s === 'cancelled') return 'error';
    if (s === 'in_production' || s === 'quality_checking') return 'info';
    return 'warning';
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[var(--pos-text)]">Print Job Production</h1>
          <p className="text-xs text-[var(--pos-text-muted)]">
            Track production progress independently from customer payment status
          </p>
        </div>

        <div className="flex items-center gap-2">
          <div className="flex items-center border border-[var(--pos-border)] rounded-lg p-0.5 bg-[var(--pos-surface)]">
            <button
              type="button"
              onClick={() => setViewMode('table')}
              className={`p-1.5 rounded-md cursor-pointer ${
                viewMode === 'table'
                  ? 'bg-slate-100 dark:bg-slate-800 text-[var(--pos-text)]'
                  : 'text-[var(--pos-text-muted)]'
              }`}
              title="Table View"
            >
              <TableIcon className="w-4 h-4" />
            </button>
            <button
              type="button"
              onClick={() => setViewMode('kanban')}
              className={`p-1.5 rounded-md cursor-pointer ${
                viewMode === 'kanban'
                  ? 'bg-slate-100 dark:bg-slate-800 text-[var(--pos-text)]'
                  : 'text-[var(--pos-text-muted)]'
              }`}
              title="Kanban Board"
            >
              <Columns className="w-4 h-4" />
            </button>
            <button
              type="button"
              onClick={() => setViewMode('list')}
              className={`p-1.5 rounded-md cursor-pointer ${
                viewMode === 'list'
                  ? 'bg-slate-100 dark:bg-slate-800 text-[var(--pos-text)]'
                  : 'text-[var(--pos-text-muted)]'
              }`}
              title="List View"
            >
              <List className="w-4 h-4" />
            </button>
          </div>
          <Button size="sm" onClick={() => setCreateOpen(true)}>
            <Plus className="w-4 h-4" />
            New Job Order
          </Button>
        </div>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex-1 min-w-[200px]">
          <Input
            placeholder="Search job #, title, or customer..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <div className="w-52">
          <Select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            options={[{ value: 'all', label: 'All Production Statuses' }, ...PRODUCTION_STATUSES]}
          />
        </div>
      </div>

      {/* Views */}
      {filteredJobs.length === 0 ? (
        <EmptyState
          title="No print jobs match"
          description="Create a new job order or run a production service checkout from the POS."
          actionLabel="Create Job Order"
          onAction={() => setCreateOpen(true)}
        />
      ) : viewMode === 'table' ? (
        <DataTable
          rows={filteredJobs}
          rowKey={(j) => j.id}
          onRowClick={(j) => setSelectedJob(j)}
          columns={[
            {
              key: 'jo',
              label: 'Job Order',
              render: (j) => (
                <div>
                  <div className="font-mono-num font-semibold text-xs">{j.jobOrderNumber}</div>
                  <div className="text-xs text-[var(--pos-text-muted)] truncate max-w-[200px]">
                    {j.title}
                  </div>
                </div>
              ),
            },
            {
              key: 'customer',
              label: 'Customer',
              render: (j) => (
                <div>
                  <div className="text-sm">{j.customerName}</div>
                  <div className="text-[11px] text-[var(--pos-text-muted)]">
                    Due {new Date(j.deadline).toLocaleDateString()}
                  </div>
                </div>
              ),
            },
            {
              key: 'prod',
              label: 'Production',
              render: (j) => (
                <StatusIndicator
                  tone={statusTone(j.productionStatus)}
                  label={j.productionStatus.replace(/_/g, ' ')}
                />
              ),
            },
            {
              key: 'pay',
              label: 'Payment',
              hideOnMobile: true,
              render: (j) => (
                <StatusIndicator
                  tone={
                    j.paymentStatus === 'paid'
                      ? 'success'
                      : j.paymentStatus === 'partial'
                        ? 'warning'
                        : 'error'
                  }
                  label={j.paymentStatus}
                />
              ),
            },
            {
              key: 'balance',
              label: 'Balance',
              align: 'right',
              render: (j) => (
                <span className="font-mono-num font-semibold">
                  {formatCurrency(j.remainingBalance, taxCurr)}
                </span>
              ),
            },
          ]}
        />
      ) : viewMode === 'list' ? (
        <div className="space-y-2.5">
          {filteredJobs.map((j) => (
            <Card
              key={j.id}
              onClick={() => setSelectedJob(j)}
              className="hover:border-[var(--pos-primary)] transition-colors cursor-pointer"
            >
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="text-sm font-semibold text-[var(--pos-text)]">
                    {j.jobOrderNumber} · {j.title}
                  </div>
                  <div className="text-xs text-[var(--pos-text-muted)] mt-0.5">
                    {j.customerName} · {j.specifications}
                  </div>
                </div>
                <div className="flex items-center gap-4">
                  <StatusIndicator
                    tone={statusTone(j.productionStatus)}
                    label={j.productionStatus.replace(/_/g, ' ')}
                  />
                  <span className="font-mono-num text-sm font-semibold">
                    {formatCurrency(j.totalAmount, taxCurr)}
                  </span>
                </div>
              </div>
            </Card>
          ))}
        </div>
      ) : (
        <div className="flex gap-3 overflow-x-auto pb-3">
          {PRODUCTION_STATUSES.filter((s) => s.value !== 'cancelled').map((col) => {
            const colJobs = filteredJobs.filter((j) => j.productionStatus === col.value);
            return (
              <div
                key={col.value}
                className="w-72 shrink-0 rounded-xl bg-slate-100/80 dark:bg-slate-900/60 border border-[var(--pos-border)] p-3 flex flex-col gap-2.5"
              >
                <div className="flex items-center justify-between text-xs font-semibold text-[var(--pos-text)]">
                  <span>{col.label}</span>
                  <span className="font-mono-num text-[var(--pos-text-muted)]">{colJobs.length}</span>
                </div>
                {colJobs.length === 0 ? (
                  <p className="text-[11px] text-[var(--pos-text-muted)] py-4 text-center">
                    No jobs
                  </p>
                ) : (
                  colJobs.map((j) => (
                    <button
                      key={j.id}
                      type="button"
                      onClick={() => setSelectedJob(j)}
                      style={{ borderRadius: 'var(--pos-radius)' }}
                      className="text-left p-3 bg-[var(--pos-surface)] border border-[var(--pos-border)] hover:border-[var(--pos-primary)] space-y-1.5 cursor-pointer"
                    >
                      <div className="flex items-center justify-between text-[11px] text-[var(--pos-text-muted)]">
                        <span className="font-mono-num font-semibold">{j.jobOrderNumber}</span>
                        <span>{j.paymentStatus}</span>
                      </div>
                      <div className="text-xs font-semibold text-[var(--pos-text)]">{j.title}</div>
                      <div className="text-[11px] text-[var(--pos-text-muted)] truncate">
                        {j.customerName}
                      </div>
                    </button>
                  ))
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Job Details & Status Update Modal */}
      <Modal
        open={Boolean(selectedJob)}
        onClose={() => setSelectedJob(null)}
        title={selectedJob ? `${selectedJob.jobOrderNumber} · ${selectedJob.title}` : ''}
        subtitle={selectedJob ? `Customer: ${selectedJob.customerName}` : ''}
        maxWidth="lg"
        footer={
          selectedJob && (
            <div className="w-full flex items-center justify-between gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setReceiptJob(selectedJob);
                }}
              >
                <Printer className="w-3.5 h-3.5" />
                Job Order Slip
              </Button>
              <Button size="sm" onClick={() => setSelectedJob(null)}>
                Close
              </Button>
            </div>
          )
        }
      >
        {selectedJob && (
          <div className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Select
                label="Production Status"
                value={selectedJob.productionStatus}
                onChange={async (e) => {
                  try { setSelectedJob(await updatePrintJob(selectedJob.id, { productionStatus: e.target.value as ProductionStatus })); }
                  catch (error) { notify((error as Error).message, 'error'); }
                }}
                options={PRODUCTION_STATUSES.filter(s => allowedJobStatuses(selectedJob.productionStatus, selectedJob.paymentStatus, selectedJob.remainingBalance, Boolean(selectedJob.transactionId), selectedJob.totalAmount).includes(s.value))}
              />
              <div className="text-xs">Payment: {selectedJob.paymentStatus}. Record payments through POS or Customers.</div>
            </div>

            <div className="p-3.5 rounded-lg border border-[var(--pos-border)] bg-slate-50/60 dark:bg-slate-900/40 space-y-1.5 text-xs">
              <div>
                <span className="font-semibold text-[var(--pos-text)]">Specifications: </span>
                <span className="text-[var(--pos-text-muted)]">
                  {selectedJob.specifications || 'Standard'}
                </span>
              </div>
              <div>
                <span className="font-semibold text-[var(--pos-text)]">Instructions: </span>
                <span className="text-[var(--pos-text-muted)]">
                  {selectedJob.instructions || 'None'}
                </span>
              </div>
              <div>
                <span className="font-semibold text-[var(--pos-text)]">Attachments: </span>
                <span className="text-[var(--pos-text-muted)]">
                  {selectedJob.attachmentNames.length > 0
                    ? selectedJob.attachmentNames.join(', ')
                    : 'No files linked'}
                </span>
              </div>
              <div className="pt-2 flex flex-wrap gap-4 font-mono-num">
                <span>Total: {formatCurrency(selectedJob.totalAmount, taxCurr)}</span>
                <span>Deposit: {formatCurrency(selectedJob.depositAmount, taxCurr)}</span>
                <span className="font-bold text-[#D97706]">
                  Balance: {formatCurrency(selectedJob.remainingBalance, taxCurr)}
                </span>
              </div>
            </div>

            <div className="space-y-2">
              <div className="text-xs font-semibold text-[var(--pos-text)]">Status History</div>
              <div className="space-y-1.5 max-h-40 overflow-y-auto">
                {selectedJob.history.map((h, idx) => (
                  <div
                    key={idx}
                    className="text-xs flex items-start justify-between gap-3 py-1.5 border-b border-[var(--pos-border)] last:border-0"
                  >
                    <div>
                      <span className="font-medium capitalize text-[var(--pos-text)]">
                        {h.status.replace(/_/g, ' ')}
                      </span>
                      <span className="text-[var(--pos-text-muted)]"> · {h.note}</span>
                    </div>
                    <span className="text-[11px] text-[var(--pos-text-muted)] shrink-0">
                      {h.changedBy} · {new Date(h.timestamp).toLocaleTimeString()}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </Modal>

      {/* Create New Print Job Modal */}
      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        title="New Print Job Order"
        maxWidth="md"
      >
        <form onSubmit={handleCreate} className="space-y-3.5">
          <Input
            label="Job Title"
            placeholder="e.g. 10x12ft Event Tarpaulin"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
          />
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Select
              label="Customer"
              value={customerId}
              onChange={(e) => setCustomerId(e.target.value)}
              options={[
                { value: '', label: 'Walk-in / Custom Name' },
                ...customers.map((c) => ({ value: c.id, label: c.name })),
              ]}
            />
            {!customerId && (
              <Input
                label="Customer Name"
                placeholder="Enter customer name"
                value={customerName}
                onChange={(e) => setCustomerName(e.target.value)}
                required
              />
            )}
          </div>
          <Input
            label="Specifications"
            placeholder="Size, stock, color mode, finishing..."
            value={specs}
            onChange={(e) => setSpecs(e.target.value)}
          />
          <Input
            label="Attachment Filenames (comma-separated)"
            placeholder="artwork-final.pdf, proof.png"
            value={attachments}
            onChange={(e) => setAttachments(e.target.value)}
          />
          <Input
            label="Production Instructions"
            value={instructions}
            onChange={(e) => setInstructions(e.target.value)}
          />
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <Input
              label={`Total Amount (${taxCurr.currencySymbol})`}
              type="number"
              min="0"
              step="0.01"
              value={totalAmount}
              onChange={(e) => setTotalAmount(e.target.value)}
            />
            <p className="text-xs">Manual jobs are quotations only. For paid production jobs, use POS checkout.</p>
            <Input
              label="Deadline"
              type="datetime-local"
              value={deadline}
              onChange={(e) => setDeadline(e.target.value)}
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" type="button" onClick={() => setCreateOpen(false)}>
              Cancel
            </Button>
            <Button type="submit">Create Job Order</Button>
          </div>
        </form>
      </Modal>

      <ReceiptModal
        open={Boolean(receiptJob)}
        onClose={() => setReceiptJob(null)}
        printJob={receiptJob}
      />
    </div>
  );
};
