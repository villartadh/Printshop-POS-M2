import React, { useEffect, useState } from 'react';
import { CheckCircle2, CreditCard, Plus, Trash2 } from 'lucide-react';
import { useApp } from '../../context/AppContext.tsx';
import { formatCurrency } from '../../shared/pricingEngine.ts';
import type { PaymentEntry, PaymentMethodConfig } from '../../shared/types.ts';
import { Button, Input, Select } from '../../components/ui/Primitives.tsx';
import { BottomSheet, Modal } from '../../components/ui/Overlays.tsx';

interface CheckoutModalProps {
  open: boolean;
  onClose: () => void;
  totalAmount: number;
  customerName: string;
  isMobileLayout: boolean;
  onCompletePayment: (payments: PaymentEntry[], allowPartial: boolean) => Promise<void>;
}

export const CheckoutModal: React.FC<CheckoutModalProps> = ({
  open,
  onClose,
  totalAmount,
  customerName,
  isMobileLayout,
  onCompletePayment,
}) => {
  const { settings, notify } = useApp();
  const enabledMethods = settings.paymentMethods.filter((m) => m.enabled && m.type !== 'credit_balance');
  const taxCurr = settings.taxAndCurrency;

  const [selectedMethodId, setSelectedMethodId] = useState(enabledMethods[0]?.id || 'cash');
  const [tenderAmount, setTenderAmount] = useState<string>('');
  const [referenceNumber, setReferenceNumber] = useState('');
  const [operatorVerified, setOperatorVerified] = useState(false);
  const [splitPayments, setSplitPayments] = useState<PaymentEntry[]>([]);
  const [allowPartial, setAllowPartial] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError,setSubmitError]=useState('');

  useEffect(() => {
    if (open) {
      setSelectedMethodId(enabledMethods[0]?.id || 'cash');
      setTenderAmount(totalAmount.toFixed(taxCurr.decimalPlaces));
      setReferenceNumber('');
      setOperatorVerified(false);
      setSplitPayments([]);
      setAllowPartial(false);
      setSubmitError('');
    }
  }, [open, totalAmount, taxCurr.decimalPlaces]);

  if (!open) return null;

  const activeMethod: PaymentMethodConfig | undefined =
    enabledMethods.find((m) => m.id === selectedMethodId) || enabledMethods[0];

  const paidSoFar = splitPayments.reduce((s, p) => s + p.amount, 0);
  const remainingBeforeCurrent = Math.max(0, Number((totalAmount - paidSoFar).toFixed(2)));
  const currentInputVal = Math.max(0, Number(tenderAmount) || 0);
  const totalEntered = Number((paidSoFar + currentInputVal).toFixed(2));
  const changeAmount = Math.max(0, Number((totalEntered - totalAmount).toFixed(2)));
  const balanceRemaining = Math.max(0, Number((totalAmount - totalEntered).toFixed(2)));

  const buildPaymentEntry = (): PaymentEntry | null => {
    if (!activeMethod || currentInputVal <= 0) return null;
    if (activeMethod.requiresReference && !referenceNumber.trim()) {
      notify(`Please enter a reference number for ${activeMethod.name}.`, 'error');
      return null;
    }
    return {
      id: `pay_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
      methodId: activeMethod.id,
      methodName: activeMethod.name,
      methodType: activeMethod.type,
      amount: currentInputVal,
      referenceNumber: referenceNumber.trim(),
      verificationStatus: activeMethod.requiresVerification
        ? operatorVerified
          ? 'verified'
          : 'pending_verification'
        : 'verified',
      paidAt: new Date().toISOString(),
    };
  };

  const handleAddSplit = () => {
    const entry = buildPaymentEntry();
    if (!entry) return;
    const nextList = [...splitPayments, entry];
    setSplitPayments(nextList);
    const nextRem = Math.max(
      0,
      Number((totalAmount - nextList.reduce((s, p) => s + p.amount, 0)).toFixed(2)),
    );
    setTenderAmount(nextRem > 0 ? nextRem.toFixed(taxCurr.decimalPlaces) : '0.00');
    setReferenceNumber('');
    setOperatorVerified(false);
  };

  const handleSubmit = async () => {
    if(submitting)return;
    setSubmitError('');
    const finalPayments = [...splitPayments];
    if (currentInputVal > 0) {
      const entry = buildPaymentEntry();
      if (!entry) return;
      finalPayments.push(entry);
    }

    const finalPaid = finalPayments.reduce((s, p) => s + p.amount, 0);
    if (!allowPartial && finalPaid + 0.001 < totalAmount) {
      setSubmitError(`Amount received is short by ${formatCurrency(totalAmount - finalPaid, taxCurr)}. Enable partial payment or collect full amount.`);
      return;
    }
    if (allowPartial && finalPayments.length === 0) {
      // Allow zero-deposit partial / pay later if explicitly toggled
    }

    const cashTendered = finalPayments.filter(p => p.methodType === 'cash').reduce((sum, p) => sum + p.amount, 0);
    if (finalPaid - totalAmount > cashTendered + 0.01) {
      setSubmitError('Change cannot exceed cash tendered.');
      return;
    }
    setSubmitting(true);
    try {
      await onCompletePayment(finalPayments, allowPartial);
      onClose();
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Checkout failed.');
    } finally {
      setSubmitting(false);
    }
  };

  const content = (
    <div className="space-y-4">
      <div className="p-4 rounded-xl bg-[var(--pos-nav-bg)] text-white flex items-center justify-between">
        <div>
          <div className="text-xs text-teal-200">Total Amount Due · {customerName}</div>
          <div className="text-2xl font-bold font-mono-num mt-0.5">
            {formatCurrency(totalAmount, taxCurr)}
          </div>
        </div>
        {paidSoFar > 0 && (
          <div className="text-right">
            <div className="text-xs text-teal-200">Remaining</div>
            <div className="text-lg font-semibold font-mono-num">
              {formatCurrency(remainingBeforeCurrent, taxCurr)}
            </div>
          </div>
        )}
      </div>

      <div>
        <label className="block text-xs font-medium text-[var(--pos-text-muted)] mb-1.5">
          Payment Method
        </label>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
          {enabledMethods.map((m) => (
            <button
              key={m.id}
              type="button"
              onClick={() => {
                setSelectedMethodId(m.id);
                setOperatorVerified(false);
              }}
              style={{ borderRadius: 'var(--pos-radius)' }}
              className={`px-3 py-2.5 text-xs font-medium border text-left transition-colors cursor-pointer ${
                selectedMethodId === m.id
                  ? 'border-[var(--pos-primary)] bg-[var(--pos-primary)]/10 text-[var(--pos-text)] font-semibold'
                  : 'border-[var(--pos-border)] bg-[var(--pos-surface)] text-[var(--pos-text-muted)]'
              }`}
            >
              {m.name}
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <Input
          label={`Amount Tendered (${taxCurr.currencySymbol})`}
          type="number"
          step="0.01"
          min="0"
          value={tenderAmount}
          onChange={(e) => {setTenderAmount(e.target.value);setSubmitError('');}}
        />
        {activeMethod?.requiresReference && (
          <Input
            label="Reference / Approval #"
            placeholder="e.g. GCash Ref / Auth Code"
            value={referenceNumber}
            onChange={(e) => setReferenceNumber(e.target.value)}
          />
        )}
      </div>

      {activeMethod?.type === 'cash' && (
        <div className="flex flex-wrap gap-2">
          {[remainingBeforeCurrent, 100, 200, 500, 1000].map((amt, idx) => (
            <button
              key={`${amt}-${idx}`}
              type="button"
              onClick={() => setTenderAmount(amt.toFixed(taxCurr.decimalPlaces))}
              className="px-3 py-1.5 rounded-lg border border-[var(--pos-border)] text-xs font-mono-num font-medium hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
            >
              {idx === 0 ? 'Exact' : formatCurrency(amt, taxCurr)}
            </button>
          ))}
        </div>
      )}

      {activeMethod?.requiresVerification && (
        <label className="flex items-start gap-2.5 p-3 rounded-lg border border-[var(--pos-border)] bg-slate-50/60 dark:bg-slate-900/40 cursor-pointer">
          <input
            type="checkbox"
            checked={operatorVerified}
            onChange={(e) => setOperatorVerified(e.target.checked)}
            className="mt-0.5 h-4 w-4 accent-[var(--pos-primary)]"
          />
          <div className="text-xs">
            <div className="font-semibold text-[var(--pos-text)]">
              Confirm electronic payment received
            </div>
            <div className="text-[var(--pos-text-muted)]">
              Leave unchecked to record as pending verification until confirmed.
            </div>
          </div>
        </label>
      )}

      {splitPayments.length > 0 && (
        <div className="space-y-1.5 border border-[var(--pos-border)] rounded-lg p-3">
          <div className="text-xs font-semibold text-[var(--pos-text-muted)]">Split Payments</div>
          {splitPayments.map((p) => (
            <div key={p.id} className="flex items-center justify-between text-xs">
              <span>
                {p.methodName}
                {p.referenceNumber ? ` · ${p.referenceNumber}` : ''}
              </span>
              <div className="flex items-center gap-2">
                <span className="font-mono-num font-semibold">
                  {formatCurrency(p.amount, taxCurr)}
                </span>
                <button
                  type="button"
                  onClick={() => setSplitPayments((prev) => prev.filter((x) => x.id !== p.id))}
                  className="text-[#DC2626] hover:opacity-80 cursor-pointer"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
        <Button type="button" variant="outline" size="sm" onClick={handleAddSplit}>
          <Plus className="w-3.5 h-3.5" />
          Add Split Payment
        </Button>

        <label className="flex items-center gap-2 text-xs text-[var(--pos-text)] cursor-pointer">
          <input
            type="checkbox"
            checked={allowPartial}
            onChange={(e) => {setAllowPartial(e.target.checked);setSubmitError('');}}
            className="h-4 w-4 accent-[var(--pos-primary)]"
          />
          Allow partial / down payment
        </label>
      </div>

      <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-900/60 border border-[var(--pos-border)] flex items-center justify-between text-sm">
        {changeAmount > 0 ? (
          <>
            <span className="font-medium text-[var(--pos-text-muted)]">Change Due</span>
            <span className="font-mono-num font-bold text-[#16A34A]">
              {formatCurrency(changeAmount, taxCurr)}
            </span>
          </>
        ) : balanceRemaining > 0 ? (
          <>
            <span className="font-medium text-[var(--pos-text-muted)]">Remaining Balance</span>
            <span className="font-mono-num font-bold text-[#D97706]">
              {formatCurrency(balanceRemaining, taxCurr)}
            </span>
          </>
        ) : (
          <>
            <span className="font-medium text-[var(--pos-text-muted)]">Settlement Status</span>
            <span className="inline-flex items-center gap-1 font-semibold text-[#16A34A] text-xs">
              <CheckCircle2 className="w-4 h-4" />
              Settled in Full
            </span>
          </>
        )}
      </div>
    </div>
  );

  const footer = (
    <div className="w-full space-y-2">
      {submitError&&<p role="alert" className="text-sm text-[var(--pos-error)] break-words">{submitError}</p>}
      <div className="flex items-center justify-end gap-2.5">
      <Button variant="secondary" onClick={onClose}>
        Cancel
      </Button>
      <Button loading={submitting} onClick={() => void handleSubmit()}>
        <CreditCard className="w-4 h-4" />
        Complete Payment
      </Button>
      </div>
    </div>
  );

  if (isMobileLayout) {
    return (
      <BottomSheet
        open={open}
        onClose={onClose}
        title="Receive Payment"
        subtitle={customerName}
        footer={footer}
      >
        {content}
      </BottomSheet>
    );
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Receive Payment"
      subtitle={customerName}
      maxWidth="md"
      footer={footer}
    >
      {content}
    </Modal>
  );
};
