import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Plus, Wallet } from 'lucide-react';
import { useApp } from '../../context/AppContext.tsx';
import { formatCurrency } from '../../shared/pricingEngine.ts';
import type { Customer, CustomerGroup } from '../../shared/types.ts';
import { Button, Card, Input, Select, StatusIndicator } from '../../components/ui/Primitives.tsx';
import { DataTable } from '../../components/ui/DataTable.tsx';
import { Modal } from '../../components/ui/Overlays.tsx';
import {readPendingCollection,reservePendingCollection,type PendingBalanceCollection} from '../../services/offlineDb.ts';

export const CustomersModule: React.FC = () => {
  const {
    settings,
    customers,
    transactions,
    printJobs,
    createCustomer,
    collectCustomerBalance,
    session,
    isOnline,
  } = useApp();
  const taxCurr = settings.taxAndCurrency;

  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState<Customer | null>(null);
  const [addOpen, setAddOpen] = useState(false);
  const [payAmount, setPayAmount] = useState('');
  const [collecting, setCollecting] = useState(false);
  const [pending,setPending]=useState<PendingBalanceCollection|null>(null);
  const [storageReady,setStorageReady]=useState(false);
  const [collectionError,setCollectionError]=useState('');
  const paymentLock=useRef(false);
  const owner=session?.user.id;
  useEffect(()=>{
    let cancelled=false;
    setStorageReady(false);setPending(null);setCollectionError('');
    if(owner)readPendingCollection(owner).then(record=>{if(!cancelled){setPending(record);setStorageReady(true);}}).catch(()=>{if(!cancelled)setCollectionError('Payment recovery storage is unavailable. Do not collect cash again.');});
    return ()=>{cancelled=true;};
  },[owner]);
  const submitCollection=async(existing?:PendingBalanceCollection)=>{
    if(paymentLock.current||!owner||!storageReady||!isOnline)return;
    if(existing&&existing.ownerId!==owner)return;
    if(!existing&&(!selected||pending))return;
    paymentLock.current=true;setCollecting(true);setCollectionError('');
    try{
      const record=existing??await reservePendingCollection(owner,selected!.id,Number(payAmount));
      setPending(record);
      await collectCustomerBalance(record);
      setPending(null);setSelected(null);
    }catch(err){
      setCollectionError(err instanceof Error?err.message:'Payment not confirmed.');
      try{setPending(await readPendingCollection(owner));}catch{setStorageReady(false);}
    }finally{paymentLock.current=false;setCollecting(false);}
  };

  // Add customer form
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [address, setAddress] = useState('');
  const [group, setGroup] = useState<CustomerGroup>('regular');
  const [discountPercent, setDiscountPercent] = useState('0');
  const [notes, setNotes] = useState('');

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return q
      ? customers.filter(
          (c) =>
            c.name.toLowerCase().includes(q) ||
            c.phone.toLowerCase().includes(q) ||
            c.email.toLowerCase().includes(q),
        )
      : customers;
  }, [customers, search]);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    await createCustomer({
      name,
      phone,
      email,
      address,
      group,
      discountPercent: Number(discountPercent) || 0,
      notes,
    });
    setName('');
    setPhone('');
    setEmail('');
    setAddress('');
    setNotes('');
    setAddOpen(false);
  };

  const selectedTxns = useMemo(
    () => (selected ? transactions.filter((t) => t.customerId === selected.id) : []),
    [selected, transactions],
  );
  const selectedJobs = useMemo(
    () => (selected ? printJobs.filter((j) => j.customerId === selected.id) : []),
    [selected, printJobs],
  );

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[var(--pos-text)]">Customers & Balances</h1>
          <p className="text-xs text-[var(--pos-text-muted)]">
            Manage client profiles, group discounts, loyalty points, and account receivables
          </p>
        </div>
        <Button size="sm" onClick={() => setAddOpen(true)}>
          <Plus className="w-4 h-4" />
          Add Customer
        </Button>
      </div>

      {collectionError&&<p role="alert" className="text-sm text-[var(--pos-error)]">{collectionError}</p>}
      {pending&&pending.ownerId===owner&&<Card><div className="space-y-2">
        <h2 className="font-semibold">Unconfirmed balance payment</h2>
        <p className="text-sm">Customer: {customers.find(c=>c.id===pending.customerId)?.name||pending.customerId} · {formatCurrency(pending.amount,taxCurr)}</p>
        <p className="text-xs break-all">Reference: {pending.clientMutationId}</p>
        <p className="text-sm">Do not collect cash again. Retry the original reference to confirm the previous payment. Other collections on this terminal are blocked until confirmation.</p>
        <Button size="sm" disabled={!isOnline||collecting||!storageReady} onClick={()=>void submitCollection(pending)}>Retry Original Balance Payment</Button>
      </div></Card>}
      <Input
        placeholder="Search customer name, phone, or email..."
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />

      <DataTable
        rows={filtered}
        rowKey={(c) => c.id}
        onRowClick={(c) => {
          setSelected(c);
          setPayAmount(String(c.outstandingBalance || ''));
        }}
        columns={[
          {
            key: 'name',
            label: 'Customer',
            render: (c) => (
              <div>
                <div className="text-sm font-semibold">{c.name}</div>
                <div className="text-[11px] text-[var(--pos-text-muted)]">
                  {c.group.toUpperCase()} · {c.phone || c.email || 'No contact'}
                </div>
              </div>
            ),
          },
          {
            key: 'points',
            label: 'Loyalty',
            hideOnMobile: true,
            align: 'right',
            render: (c) => <span className="font-mono-num text-xs">{c.loyaltyPoints} pts</span>,
          },
          {
            key: 'spent',
            label: 'Total Spent',
            align: 'right',
            hideOnMobile: true,
            render: (c) => (
              <span className="font-mono-num">{formatCurrency(c.totalSpent, taxCurr)}</span>
            ),
          },
          {
            key: 'balance',
            label: 'Balance Due',
            align: 'right',
            render: (c) =>
              c.outstandingBalance > 0 ? (
                <StatusIndicator
                  tone="warning"
                  label={formatCurrency(c.outstandingBalance, taxCurr)}
                />
              ) : (
                <span className="text-xs text-[var(--pos-text-muted)]">Settled</span>
              ),
          },
        ]}
      />

      {/* Customer Detail Modal */}
      <Modal
        open={Boolean(selected)}
        onClose={() => {if(!collecting)setSelected(null);}}
        title={selected?.name || ''}
        subtitle={
          selected ? `${selected.group.toUpperCase()} · ${selected.discountPercent}% Group Discount` : ''
        }
        maxWidth="lg"
      >
        {selected && (
          <div className="space-y-4">
            <div className="grid grid-cols-3 gap-3">
              <Card>
                <div className="text-xs text-[var(--pos-text-muted)]">Loyalty Points</div>
                <div className="text-lg font-bold font-mono-num mt-0.5">{selected.loyaltyPoints}</div>
              </Card>
              <Card>
                <div className="text-xs text-[var(--pos-text-muted)]">Total Spend</div>
                <div className="text-lg font-bold font-mono-num mt-0.5">
                  {formatCurrency(selected.totalSpent, taxCurr)}
                </div>
              </Card>
              <Card>
                <div className="text-xs text-[var(--pos-text-muted)]">Outstanding</div>
                <div className="text-lg font-bold font-mono-num text-[#D97706] mt-0.5">
                  {formatCurrency(selected.outstandingBalance, taxCurr)}
                </div>
              </Card>
            </div>

            {collectionError&&<p role="alert" className="text-sm text-[var(--pos-error)]">{collectionError}</p>}
            {pending&&<p className="text-sm">Close this detail and review the unconfirmed balance payment before collecting more cash.</p>}
            {selected.outstandingBalance > 0 && (
              <form
                onSubmit={async (e) => {
                  e.preventDefault();
                  if(Number(payAmount)<=0||Number(payAmount)>selected.outstandingBalance||collecting||pending||!isOnline||!storageReady)return;
                  await submitCollection();
                }}
                className="p-3.5 rounded-lg border border-[var(--pos-border)] bg-slate-50/60 dark:bg-slate-900/40 flex flex-wrap items-end gap-3"
              >
                <div className="flex-1 min-w-[160px]">
                  <Input
                    label={`Collect Cash Balance Payment (${taxCurr.currencySymbol})`}
                    type="number"
                    min="0.01"
                    step="0.01"
                    required
                    disabled={Boolean(pending)||collecting||!storageReady||!isOnline}
                    max={selected.outstandingBalance}
                    value={payAmount}
                    onChange={(e) => setPayAmount(e.target.value)}
                  />
                </div>
                <Button type="submit" disabled={collecting||Boolean(pending)||!storageReady||!isOnline}>
                  <Wallet className="w-4 h-4" />
                  Record Payment
                </Button>
              </form>
            )}

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <h3 className="text-xs font-semibold text-[var(--pos-text)] mb-2">
                  Recent Purchases ({selectedTxns.length})
                </h3>
                <div className="space-y-1.5 max-h-44 overflow-y-auto">
                  {selectedTxns.length === 0 ? (
                    <p className="text-xs text-[var(--pos-text-muted)]">No transactions yet.</p>
                  ) : (
                    selectedTxns.map((t) => (
                      <div
                        key={t.id}
                        className="text-xs p-2 rounded border border-[var(--pos-border)] flex justify-between"
                      >
                        <span>
                          {t.receiptNumber} · {new Date(t.createdAt).toLocaleDateString()}
                        </span>
                        <span className="font-mono-num font-semibold">
                          {formatCurrency(t.totalAmount, taxCurr)}
                        </span>
                      </div>
                    ))
                  )}
                </div>
              </div>
              <div>
                <h3 className="text-xs font-semibold text-[var(--pos-text)] mb-2">
                  Print Jobs ({selectedJobs.length})
                </h3>
                <div className="space-y-1.5 max-h-44 overflow-y-auto">
                  {selectedJobs.length === 0 ? (
                    <p className="text-xs text-[var(--pos-text-muted)]">No print jobs yet.</p>
                  ) : (
                    selectedJobs.map((j) => (
                      <div
                        key={j.id}
                        className="text-xs p-2 rounded border border-[var(--pos-border)] flex justify-between"
                      >
                        <span className="truncate">{j.title}</span>
                        <span className="capitalize text-[var(--pos-text-muted)] shrink-0 ml-2">
                          {j.productionStatus.replace(/_/g, ' ')}
                        </span>
                      </div>
                    ))
                  )}
                </div>
              </div>
            </div>
          </div>
        )}
      </Modal>

      {/* Add Customer Modal */}
      <Modal open={addOpen} onClose={() => setAddOpen(false)} title="New Customer Profile" maxWidth="md">
        <form onSubmit={handleAdd} className="space-y-3.5">
          <Input label="Customer / Company Name" value={name} onChange={(e) => setName(e.target.value)} required />
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Input label="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} />
            <Input label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <Input label="Address" value={address} onChange={(e) => setAddress(e.target.value)} />
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Select
              label="Customer Group"
              value={group}
              onChange={(e) => setGroup(e.target.value as CustomerGroup)}
              options={[
                { value: 'regular', label: 'Regular' },
                { value: 'vip', label: 'VIP' },
                { value: 'corporate', label: 'Corporate' },
                { value: 'student', label: 'Student' },
                { value: 'reseller', label: 'Reseller / Trade' },
              ]}
            />
            <Input
              label="Default Discount (%)"
              type="number"
              min="0"
              max="100"
              value={discountPercent}
              onChange={(e) => setDiscountPercent(e.target.value)}
            />
          </div>
          <Input label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} />
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" type="button" onClick={() => setAddOpen(false)}>
              Cancel
            </Button>
            <Button type="submit">Save Customer</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
