import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Download, Plus, Printer, Trash2 } from 'lucide-react';
import {PendingFinancialCancellation} from '../../components/layout/PendingFinancialCancellation.tsx';
import { useApp } from '../../context/AppContext.tsx';
import { formatCurrency } from '../../shared/pricingEngine.ts';
import { downloadCsv } from '../../services/hardwareService.ts';
import { apiRequest } from '../../services/apiClient.ts';
import type { ExpenseRecord, Transaction } from '../../shared/types.ts';
import { Button, Card, Input, Select } from '../../components/ui/Primitives.tsx';
import { DataTable } from '../../components/ui/DataTable.tsx';
import { BreakdownBarList } from '../../components/ui/Charts.tsx';
import { Modal } from '../../components/ui/Overlays.tsx';
import {readPendingExpense,reserveExpense,type PendingExpense} from '../../services/offlineDb.ts';

const businessDate = (date = new Date()) => new Intl.DateTimeFormat('en-CA', {timeZone:'Asia/Manila',year:'numeric',month:'2-digit',day:'2-digit'}).format(date);
type ReportData = {from:string;to:string;transactions:Transaction[];expenses:ExpenseRecord[]};

export const ExpensesReportsModule: React.FC = () => {
  const {
    settings,
    transactions,
    expenses,
    categories,
    services,
    customers,
    stockMovements,
    shifts,
    createExpense,
    deleteExpense,
    session,
    isOnline,
    notify,
  } = useApp();
  const taxCurr = settings.taxAndCurrency;
  const canDelete = session?.user.role === 'admin' || session?.user.role === 'manager';

  const [tab, setTab] = useState<
    'overview' | 'expenses' | 'by_service' | 'by_cashier' | 'refunds'
  >('overview');
  const [dateFrom, setDateFrom] = useState(
    businessDate(new Date(Date.now() - 30 * 86400000)),
  );
  const [dateTo, setDateTo] = useState(businessDate());
  const [addExpenseOpen, setAddExpenseOpen] = useState(false);
  const expenseSubmitting=useRef(false);
  const [savingExpense,setSavingExpense]=useState(false);
  const [expenseError,setExpenseError]=useState('');
  const [pendingExpense,setPendingExpense]=useState<PendingExpense|null>(null);
  const [expenseStorageReady,setExpenseStorageReady]=useState(false);
  const owner=session?.user.id;
  useEffect(()=>{let cancelled=false;setExpenseStorageReady(false);setPendingExpense(null);setExpenseError('');if(owner)readPendingExpense(owner).then(record=>{if(!cancelled){setPendingExpense(record);setExpenseStorageReady(true);}}).catch(()=>{if(!cancelled)setExpenseError('Expense recovery storage unavailable. Do not repeat unconfirmed expenses.');});return()=>{cancelled=true;};},[owner]);

  // Expense form
  const [expCategory, setExpCategory] = useState<ExpenseRecord['category']>('printing_materials');
  const [expDesc, setExpDesc] = useState('');
  const [expAmount, setExpAmount] = useState('');
  const [expVendor, setExpVendor] = useState('');
  const [expDate, setExpDate] = useState(businessDate());
  const submitExpense=async(existing?:PendingExpense)=>{
    if(expenseSubmitting.current||!owner||!expenseStorageReady||!isOnline||(!existing&&pendingExpense)||(existing&&existing.ownerId!==owner))return;
    expenseSubmitting.current=true;setSavingExpense(true);setExpenseError('');
    try{
      const record=existing??await reserveExpense(owner,{category:expCategory,description:expDesc,amount:Number(expAmount),vendor:expVendor,expenseDate:expDate});
      setPendingExpense(record);await createExpense(record);
      setPendingExpense(null);setExpDesc('');setExpAmount('');setExpVendor('');setAddExpenseOpen(false);
    }catch(error){setExpenseError(error instanceof Error?error.message:'Expense confirmation failed.');try{const pending=await readPendingExpense(owner);setPendingExpense(pending);if(pending)setAddExpenseOpen(false);}catch{setExpenseStorageReady(false);}}
    finally{expenseSubmitting.current=false;setSavingExpense(false);}
  };

  const [report,setReport] = useState<ReportData|null>(null);
  const [reportError,setReportError] = useState('');
  const [reportLoading,setReportLoading] = useState(true);
  const [reload,setReload] = useState(0);
  useEffect(() => {
    let active=true;
    setReport(null);setReportError('');setReportLoading(true);
    if (!dateFrom || !dateTo || dateFrom>dateTo) {
      setReportError('Select a valid reporting date range.');setReportLoading(false);return;
    }
    apiRequest<ReportData>('/api/reports/finance?'+new URLSearchParams({from:dateFrom,to:dateTo}))
      .then(data=>{if(active)setReport(data);})
      .catch(error=>{if(active)setReportError(error instanceof Error?error.message:'Unable to load report.');})
      .finally(()=>{if(active)setReportLoading(false);});
    return ()=>{active=false;};
  },[dateFrom,dateTo,reload,transactions,expenses]);
  const ready = !reportLoading && !reportError && report?.from===dateFrom && report?.to===dateTo;
  const rangedTxns = useMemo(()=>ready ? report!.transactions : [],[ready,report]);
  const rangedExpenses = useMemo(()=>ready ? report!.expenses : [],[ready,report]);

  const pnl = useMemo(() => {
    const active = rangedTxns.filter(
      (t) => t.paymentStatus !== 'voided' && t.paymentStatus !== 'refunded',
    );
    const revenue = active.reduce((s, t) => s + t.totalAmount, 0);
    const cashReceived = active.reduce((s, t) => s + t.amountPaid, 0);
    const receivables = customers.reduce((s, c) => s + c.outstandingBalance, 0);
    const estimatedMaterialCost = active.reduce(
      (s, t) => s + t.items.reduce((is, i) => is + (i.costTotal || 0), 0),
      0,
    );
    const operatingExpenses = rangedExpenses.reduce((s, e) => s + e.amount, 0);
    const taxTotal = active.reduce((s,t)=>s+t.taxAmount,0);
    const grossProfit = revenue - taxTotal - estimatedMaterialCost;
    const netProfit = grossProfit - operatingExpenses;

    const byCategory = new Map<string, number>();
    const byService = new Map<string, number>();
    const byCashier = new Map<string, number>();
    const byPayment = new Map<string, number>();

    for (const t of active) {
      byCashier.set(t.cashierName, (byCashier.get(t.cashierName) || 0) + t.totalAmount);
      for (const p of t.payments) {
        if (p.methodType !== 'credit_balance') byPayment.set(p.methodName, (byPayment.get(p.methodName) || 0) + p.amount);
      }
      const cashMethod = t.payments.find(p => p.methodType === 'cash');
      if (cashMethod) byPayment.set(cashMethod.methodName, (byPayment.get(cashMethod.methodName) || 0) - t.changeAmount);
      for (const i of t.items) {
        byService.set(i.serviceName, (byService.get(i.serviceName) || 0) + i.lineTotal);
        const srv = services.find((s) => s.id === i.serviceId);
        const catName =
          categories.find((c) => c.id === srv?.categoryId)?.name || 'Other / Custom';
        byCategory.set(catName, (byCategory.get(catName) || 0) + i.lineTotal);
      }
    }

    return {
      revenue,
      taxTotal,
      cashReceived,
      receivables,
      estimatedMaterialCost,
      operatingExpenses,
      grossProfit,
      netProfit,
      byCategory: Array.from(byCategory.entries()).map(([label, value]) => ({ label, value })),
      byService: Array.from(byService.entries())
        .sort((a, b) => b[1] - a[1])
        .map(([label, value]) => ({ label, value })),
      byCashier: Array.from(byCashier.entries()).map(([label, value]) => ({ label, value })),
      byPayment: Array.from(byPayment.entries()).map(([label, value]) => ({ label, value })),
    };
  }, [rangedTxns, rangedExpenses, customers, services, categories]);

  const handleExportCsv = () => {
    downloadCsv(
      `printshop-sales-${dateFrom}-to-${dateTo}.csv`,
      ['Receipt #', 'Date', 'Customer', 'Cashier', 'Status', 'Total', 'Paid', 'Balance'],
      rangedTxns.map((t) => [
        t.receiptNumber,
        t.createdAt,
        t.customerName,
        t.cashierName,
        t.paymentStatus,
        t.totalAmount,
        t.amountPaid,
        t.balanceDue,
      ]),
    );
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[var(--pos-text)]">
            Expenses, Profit & Reports
          </h1>
          <p className="text-xs text-[var(--pos-text-muted)]">
            Philippine sales-date view: billed revenue (including tax), collections on selected sales, current receivables and estimated profit excluding sales tax.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="outline" size="sm" disabled={!ready} onClick={handleExportCsv}>
            <Download className="w-3.5 h-3.5" />
            Export CSV
          </Button>
          <Button variant="outline" size="sm" disabled={!ready} onClick={() => window.print()}>
            <Printer className="w-3.5 h-3.5" />
            Print Report
          </Button>
          <Button size="sm" disabled={!expenseStorageReady||!isOnline||!!pendingExpense||savingExpense} onClick={() => {setExpenseError('');setAddExpenseOpen(true);}}>
            <Plus className="w-4 h-4" />
            Log Expense
          </Button>
        </div>
      </div>

      {!addExpenseOpen&&expenseError&&<p role="alert" className="text-sm text-[var(--pos-error)]">{expenseError}</p>}
      {pendingExpense&&pendingExpense.ownerId===owner&&<Card><div className="space-y-2">
        <h2 className="font-semibold">Unconfirmed expense</h2>
        <p className="text-sm">{pendingExpense.description} · {formatCurrency(pendingExpense.amount,taxCurr)} · {pendingExpense.expenseDate}</p>
        <p className="text-xs break-all">Reference: {pendingExpense.clientMutationId}</p>
        <p className="text-sm">Do not record the expense again. Confirm this original reference.</p>
        <Button size="sm" disabled={!isOnline||!expenseStorageReady||savingExpense} onClick={()=>void submitExpense(pendingExpense)}>Retry Original Expense</Button>
        <PendingFinancialCancellation key={pendingExpense.clientMutationId} request={{kind:'expense',record:pendingExpense}} disabled={!isOnline||!expenseStorageReady||savingExpense} onBusy={value=>{expenseSubmitting.current=value;setSavingExpense(value);}} onResolved={async()=>{setPendingExpense(await readPendingExpense(owner!));setAddExpenseOpen(false);setExpenseError('');}} />
      </div></Card>}
      {/* Date Filter & Tabs */}
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-wrap items-center gap-1 p-1 bg-slate-100 dark:bg-slate-800 rounded-lg">
          {[
            { id: 'overview', label: 'Profit & Loss' },
            { id: 'expenses', label: `Expenses (${rangedExpenses.length})` },
            { id: 'by_service', label: 'Service & Category' },
            { id: 'by_cashier', label: 'Cashiers & Shifts' },
            { id: 'refunds', label: 'Voids & Refunds' },
          ].map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => setTab(t.id as typeof tab)}
              className={`px-3 py-1.5 text-xs font-medium rounded-md cursor-pointer ${
                tab === t.id
                  ? 'bg-[var(--pos-surface)] text-[var(--pos-text)] shadow-xs'
                  : 'text-[var(--pos-text-muted)]'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>

        <div className="flex items-center gap-2">
          <Input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} />
          <span className="text-xs text-[var(--pos-text-muted)]">to</span>
          <Input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} />
        </div>
      </div>

      {!ready && <Card><p role="status" className="text-sm">{reportError || 'Loading complete financial report…'}</p>{reportError && <Button size="sm" onClick={()=>setReload(v=>v+1)}>Retry report</Button>}</Card>}
      {ready && <div id="printable-document" className="space-y-4">
        {tab === 'overview' && (
          <>
            <div className="grid grid-cols-2 lg:grid-cols-5 gap-3">
              <Card>
                <div className="text-xs text-[var(--pos-text-muted)]">Total Revenue</div>
                <div className="text-lg font-bold font-mono-num mt-1">
                  {formatCurrency(pnl.revenue, taxCurr)}
                </div>
              </Card>
              <Card>
                <div className="text-xs text-[var(--pos-text-muted)]">Collected on Selected Sales</div>
                <div className="text-lg font-bold font-mono-num text-[#16A34A] mt-1">
                  {formatCurrency(pnl.cashReceived, taxCurr)}
                </div>
              </Card>
              <Card>
                <div className="text-xs text-[var(--pos-text-muted)]">Current Receivables (all dates)</div>
                <div className="text-lg font-bold font-mono-num text-[#D97706] mt-1">
                  {formatCurrency(pnl.receivables, taxCurr)}
                </div>
              </Card>
              <Card>
                <div className="text-xs text-[var(--pos-text-muted)]">Operating Expenses</div>
                <div className="text-lg font-bold font-mono-num text-[#DC2626] mt-1">
                  {formatCurrency(pnl.operatingExpenses, taxCurr)}
                </div>
              </Card>
              <Card>
                <div className="text-xs text-[var(--pos-text-muted)]">Estimated Net Profit</div>
                <div
                  className={`text-lg font-bold font-mono-num mt-1 ${
                    pnl.netProfit >= 0 ? 'text-[#16A34A]' : 'text-[#DC2626]'
                  }`}
                >
                  {formatCurrency(pnl.netProfit, taxCurr)}
                </div>
              </Card>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              <Card>
                <p className="text-xs mb-2">Sales tax included in billed revenue: {formatCurrency(pnl.taxTotal,taxCurr)}</p>
                <h2 className="text-sm font-semibold mb-3">Sales by Payment Method</h2>
                <BreakdownBarList items={pnl.byPayment} taxCurrency={taxCurr} />
              </Card>
              <Card>
                <h2 className="text-sm font-semibold mb-3">Inventory Consumption Summary</h2>
                <p className="text-xs text-[var(--pos-text-muted)] mb-3">
                  Recent automatic & manual stock deductions ({stockMovements.length} movements)
                </p>
                <div className="space-y-2 max-h-48 overflow-y-auto">
                  {stockMovements
                    .filter((m) => m.quantityDelta < 0)
                    .slice(0, 6)
                    .map((m) => (
                      <div
                        key={m.id}
                        className="text-xs flex justify-between py-1 border-b border-[var(--pos-border)] last:border-0"
                      >
                        <span>
                          {m.itemName} · {m.movementType.replace(/_/g, ' ')}
                        </span>
                        <span className="font-mono-num text-[#DC2626]">{m.quantityDelta}</span>
                      </div>
                    ))}
                </div>
              </Card>
            </div>
          </>
        )}

        {tab === 'expenses' && (
          <DataTable
            rows={rangedExpenses}
            rowKey={(e) => e.id}
            columns={[
              {
                key: 'date',
                label: 'Date',
                render: (e) => <span className="font-mono-num text-xs">{e.expenseDate}</span>,
              },
              {
                key: 'desc',
                label: 'Description',
                render: (e) => (
                  <div>
                    <div className="text-sm font-medium">{e.description}</div>
                    <div className="text-[11px] text-[var(--pos-text-muted)]">
                      {e.category.replace(/_/g, ' ')} · {e.vendor || 'General'}
                    </div>
                  </div>
                ),
              },
              {
                key: 'amount',
                label: 'Amount',
                align: 'right',
                render: (e) => (
                  <span className="font-mono-num font-semibold text-[#DC2626]">
                    {formatCurrency(e.amount, taxCurr)}
                  </span>
                ),
              },
              {
                key: 'del',
                label: '',
                align: 'right',
                render: (e) =>
                  canDelete ? (
                    <button
                      type="button"
                      aria-label={'Void expense '+e.description}
                      onClick={async () => {
                        const reason=window.prompt('Void this expense? The original record will be retained. Enter reason:');
                        if(!reason?.trim())return;
                        try{await deleteExpense(e.id,reason.trim());}
                        catch(error){notify(error instanceof Error?error.message:'Expense void could not be confirmed. Refresh the report before retrying.','error');}
                      }}
                      className="p-1.5 text-[#DC2626] hover:bg-slate-100 dark:hover:bg-slate-800 rounded cursor-pointer"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  ) : null,
              },
            ]}
          />
        )}

        {tab === 'by_service' && (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <Card>
              <h2 className="text-sm font-semibold mb-3">Sales by Category</h2>
              <BreakdownBarList items={pnl.byCategory} taxCurrency={taxCurr} />
            </Card>
            <Card>
              <h2 className="text-sm font-semibold mb-3">Sales by Service</h2>
              <BreakdownBarList items={pnl.byService} taxCurrency={taxCurr} />
            </Card>
          </div>
        )}

        {tab === 'by_cashier' && (
          <div className="space-y-4">
            <Card>
              <h2 className="text-sm font-semibold mb-3">Sales by Cashier</h2>
              <BreakdownBarList items={pnl.byCashier} taxCurrency={taxCurr} />
            </Card>
            <DataTable
              rows={shifts}
              rowKey={(s) => s.id}
              columns={[
                { key: 'cashier', label: 'Cashier', render: (s) => s.cashierName },
                {
                  key: 'started',
                  label: 'Started',
                  render: (s) => new Date(s.startedAt).toLocaleString(),
                },
                {
                  key: 'sales',
                  label: 'Cash + E-Pay',
                  align: 'right',
                  render: (s) => (
                    <span className="font-mono-num">
                      {formatCurrency(s.cashSales + s.electronicSales, taxCurr)}
                    </span>
                  ),
                },
                {
                  key: 'diff',
                  label: 'Discrepancy',
                  align: 'right',
                  render: (s) => (
                    <span className="font-mono-num">
                      {s.discrepancy === null ? 'Open' : formatCurrency(s.discrepancy, taxCurr)}
                    </span>
                  ),
                },
              ]}
            />
          </div>
        )}

        {tab === 'refunds' && (
          <DataTable
            rows={rangedTxns.filter(
              (t) => t.paymentStatus === 'voided' || t.paymentStatus === 'refunded',
            )}
            rowKey={(t) => t.id}
            emptyTitle="No voids or refunds"
            emptyDescription="Voided or refunded transactions in the selected period appear here."
            columns={[
              { key: 'num', label: 'Receipt #', render: (t) => t.receiptNumber },
              { key: 'cust', label: 'Customer', render: (t) => t.customerName },
              { key: 'status', label: 'Status', render: (t) => t.paymentStatus },
              {
                key: 'reason',
                label: 'Reason',
                render: (t) => t.voidReason || t.refundReason || '—',
              },
              {
                key: 'amt',
                label: 'Amount',
                align: 'right',
                render: (t) => formatCurrency(t.totalAmount, taxCurr),
              },
            ]}
          />
        )}
      </div>}

      {/* Add Expense Modal */}
      <Modal
        open={addExpenseOpen}
        onClose={() => {if(!expenseSubmitting.current)setAddExpenseOpen(false);}}
        title="Record Operating Expense"
        maxWidth="sm"
      >
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            await submitExpense();
          }}
          className="space-y-3"
        >
          <Select
            label="Expense Category"
            value={expCategory}
            onChange={(e) => setExpCategory(e.target.value as ExpenseRecord['category'])}
            options={[
              { value: 'printing_materials', label: 'Printing Materials' },
              { value: 'ink_toner', label: 'Ink & Toner' },
              { value: 'electricity', label: 'Electricity' },
              { value: 'maintenance', label: 'Printer Maintenance' },
              { value: 'equipment', label: 'Equipment' },
              { value: 'labor', label: 'Labor / Payroll' },
              { value: 'rent', label: 'Rent' },
              { value: 'other', label: 'Other' },
            ]}
          />
          <Input
            label="Description"
            value={expDesc}
            onChange={(e) => setExpDesc(e.target.value)}
            required
          />
          <div className="grid grid-cols-2 gap-3">
            <Input
              label={`Amount (${taxCurr.currencySymbol})`}
              type="number"
              min="0.01"
              max="1000000000"
              step="0.01"
              value={expAmount}
              onChange={(e) => setExpAmount(e.target.value)}
              required
            />
            <Input
              label="Date"
              type="date"
              value={expDate}
              onChange={(e) => setExpDate(e.target.value)}
              required
            />
          </div>
          <Input label="Vendor" value={expVendor} onChange={(e) => setExpVendor(e.target.value)} />
          {expenseError&&<p role="alert" className="text-sm text-[var(--pos-error)]">{expenseError}</p>}
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" type="button" disabled={savingExpense} onClick={() => setAddExpenseOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={savingExpense}>Save Expense</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
