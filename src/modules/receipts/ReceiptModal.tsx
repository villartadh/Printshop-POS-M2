import React, { useEffect, useRef, useState } from 'react';
import { Copy, Download, Printer } from 'lucide-react';
import { useApp } from '../../context/AppContext.tsx';
import { formatCurrency } from '../../shared/pricingEngine.ts';
import type { PrintJob, ReceiptTemplateConfig, Transaction } from '../../shared/types.ts';
import { Button, Select } from '../../components/ui/Primitives.tsx';
import { paperLayout, documentKind, PILOT_DOCUMENT_NOTICE } from '../../shared/printDocument.ts';
import { openPrintPreview, downloadPrintDocument } from '../../services/printService.ts';
import { Modal } from '../../components/ui/Overlays.tsx';

interface ReceiptModalProps {
  open: boolean;
  onClose: () => void;
  transaction?: Transaction | null;
  printJob?: PrintJob | null;
  reprint?: boolean;
}

export const ReceiptModal: React.FC<ReceiptModalProps> = ({
  open,
  onClose,
  transaction,
  printJob,
  reprint = false,
}) => {
  const { settings, notify, session, voidTransaction, refundTransaction } = useApp();
  const documentRef = useRef<HTMLDivElement>(null);
  const [reversing, setReversing] = useState(false);
  const [paperSize, setPaperSize] = useState<ReceiptTemplateConfig['defaultPaperSize']>(
    settings.receiptTemplate.defaultPaperSize,
  );
  const [docType, setDocType] = useState<ReceiptTemplateConfig['defaultDocumentType']>(
    documentKind(transaction?.type, Boolean(printJob)),
  );
  const [copyMode, setCopyMode] = useState<'customer' | 'internal'>('customer');

  const docReference =
    transaction?.receiptNumber || printJob?.jobOrderNumber || 'DOC-PREVIEW';
  const [previewOpened, setPreviewOpened] = useState(false);
  useEffect(() => {
    if (open) {
      setPaperSize(settings.receiptTemplate.defaultPaperSize);
      setDocType(transaction?.type === 'sale' && ['sales_receipt','invoice','payment_acknowledgment'].includes(settings.receiptTemplate.defaultDocumentType) ? settings.receiptTemplate.defaultDocumentType : documentKind(transaction?.type, Boolean(printJob)));
      setCopyMode('customer');
      setPreviewOpened(false);
    }
  }, [open, docReference, transaction?.type, Boolean(printJob), settings.receiptTemplate.defaultPaperSize, settings.receiptTemplate.defaultDocumentType]);
  const allowedTypes = transaction?.type === 'sale' ? ['sales_receipt','invoice','payment_acknowledgment'] : [documentKind(transaction?.type, Boolean(printJob))];
  const effectiveType = allowedTypes.includes(docType) ? docType : documentKind(transaction?.type, Boolean(printJob));

  if (!open || (!transaction && !printJob)) return null;

  const docTitleMap: Record<ReceiptTemplateConfig['defaultDocumentType'], string> = {
    sales_receipt: 'SALES SLIP — NOT A TAX INVOICE',
    invoice: 'SERVICE INVOICE DRAFT — NOT A TAX INVOICE',
    quotation: 'PRINTING QUOTATION',
    job_order_slip: 'JOB ORDER PRODUCTION SLIP',
    payment_acknowledgment: 'PAYMENT ACKNOWLEDGMENT',
  };

  const widthPx = Math.round(paperLayout(paperSize, settings.receiptTemplate.customWidthMm).width * 96 / 25.4);
  const isWide = widthPx >= 500;
  const branding = transaction?.receiptSnapshot?.branding ?? settings.branding;
  const taxCurr = transaction?.receiptSnapshot?.taxAndCurrency ?? settings.taxAndCurrency;
  const legacyIdentity = Boolean(transaction && !transaction.receiptSnapshot && transaction.syncStatus === 'synced');

  const handlePrint = () => {
    const el = documentRef.current;
    if (!el) return;
    try { openPrintPreview(docReference, el.innerHTML, paperSize, settings.receiptTemplate.customWidthMm); setPreviewOpened(true); }
    catch (err) { notify(err instanceof Error ? err.message : String(err), 'error'); }
  };

  const handleCopySummary = async () => {
    const total = transaction ? transaction.totalAmount : printJob?.totalAmount || 0;
    const customer = transaction?.customerName || printJob?.customerName || 'Customer';
    const text = `${PILOT_DOCUMENT_NOTICE}\n${branding.businessName}\n${transaction?.type === 'held' ? 'HELD ORDER — NOT A SALE' : docTitleMap[effectiveType]} #${docReference}\nCustomer: ${customer}\nTotal: ${formatCurrency(total, taxCurr)}`;
    const status = transaction ? [legacyIdentity ? 'HISTORICAL IDENTITY UNAVAILABLE — CURRENT BUSINESS SETTINGS USED' : '',transaction.syncStatus !== 'synced' ? 'PROVISIONAL — SERVER CONFIRMATION PENDING' : '', ['voided','refunded'].includes(transaction.paymentStatus) ? transaction.paymentStatus.toUpperCase()+' — ORIGINAL SALE RECORD' : '', reprint || previewOpened ? 'REPRINT / COPY' : ''].filter(Boolean).join('\n') : '';
    try {
      await navigator.clipboard.writeText(status ? status+'\n'+text : text);
      notify('Receipt summary copied to clipboard.', 'success');
    } catch {
      notify('Unable to copy automatically in this browser.', 'error');
    }
  };

  const handleDownloadHtml = () => {
    const el = documentRef.current;
    if (!el) return;
    try { downloadPrintDocument(docReference, el.innerHTML, paperSize, settings.receiptTemplate.customWidthMm); }
    catch (err) { notify(err instanceof Error ? err.message : String(err), 'error'); }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Document & Receipt Preview"
      subtitle={`${docReference} · ${paperSize} layout`}
      maxWidth="xl"
      footer={
        <div className="w-full flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => void handleCopySummary()}>
              <Copy className="w-3.5 h-3.5" />
              Share Summary
            </Button>
            <Button variant="outline" size="sm" onClick={handleDownloadHtml}>
              <Download className="w-3.5 h-3.5" />
              Export File
            </Button>
          </div>
          <div className="flex items-center gap-2">
            {transaction?.type === 'sale' && !['voided','refunded'].includes(transaction.paymentStatus) && ['admin','manager'].includes(session?.user.role || '') && (
              <Button variant="outline" size="sm" disabled={reversing} onClick={async () => {
                const refund = transaction.amountPaid > 0;
                const reason = window.prompt(refund ? 'Confirm original-method refund externally, then enter reason. Stock is not returned automatically.' : 'Enter void reason. Stock is not returned automatically.');
                if (!reason?.trim()) return;
                setReversing(true);
                try { await (refund ? refundTransaction : voidTransaction)(transaction.id,reason); onClose(); }
                catch (err) { notify(err instanceof Error ? err.message : String(err),'error'); }
                finally { setReversing(false); }
              }}>{transaction.amountPaid > 0 ? 'Record Refund' : 'Void Unpaid Sale'}</Button>
            )}
            <Button variant="secondary" size="sm" onClick={onClose}>
              Close
            </Button>
            <Button size="sm" onClick={handlePrint}>
              <Printer className="w-3.5 h-3.5" />
              Print / Save as PDF
            </Button>
          </div>
        </div>
      }
    >
      <div className="space-y-4">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <Select
            label="Document Type"
            value={effectiveType}
            onChange={(e) => setDocType(e.target.value as typeof docType)}
            options={[
              { value: 'sales_receipt', label: 'Sales Receipt' },
              { value: 'invoice', label: 'Non-tax invoice draft' },
              { value: 'quotation', label: 'Quotation' },
              { value: 'job_order_slip', label: 'Job Order Slip' },
              { value: 'payment_acknowledgment', label: 'Payment Acknowledgment' },
            ].filter(option => allowedTypes.includes(option.value))}
          />
          <Select
            label="Paper Format"
            value={paperSize}
            onChange={(e) => setPaperSize(e.target.value as typeof paperSize)}
            options={[
              { value: '58mm', label: '58mm Thermal' },
              { value: '80mm', label: '80mm Thermal' },
              { value: 'A4', label: 'A4 Sheet' },
              { value: 'Letter', label: 'US Letter' },
              { value: 'Custom', label: `Custom (${settings.receiptTemplate.customWidthMm}mm)` },
            ]}
          />
          <Select
            label="Copy Mode"
            value={copyMode}
            onChange={(e) => setCopyMode(e.target.value as 'customer' | 'internal')}
            options={[
              { value: 'customer', label: 'Customer Copy' },
              { value: 'internal', label: 'Internal Production Copy' },
            ]}
          />
        </div>

        <div className="bg-slate-100 dark:bg-slate-900/70 p-4 rounded-xl flex justify-center overflow-x-auto border border-[var(--pos-border)]">
          <div
            ref={documentRef}
            id="printable-document"
            style={{ width: `${widthPx}px` }}
            className={`bg-white text-slate-900 border border-slate-300 shadow-sm ${
              isWide ? 'p-8 text-xs' : 'p-4 text-[11px]'
            } font-mono leading-relaxed`}
          >
            <div className="text-center border-b border-dashed border-slate-300 pb-3 mb-3">
              {branding.showLogoOnReceipt && branding.logoDataUrl && (
                <img
                  src={branding.logoDataUrl}
                  alt={branding.businessName}
                  className="h-10 mx-auto mb-2 object-contain"
                  referrerPolicy="no-referrer"
                />
              )}
              <div className="font-bold text-sm">{branding.businessName}</div>
              <div className="text-slate-600">{branding.address}</div>
              <div className="text-slate-600">
                {branding.phone} · {branding.email}
              </div>
              {branding.taxRegistrationNumber && (
                <div className="text-slate-600">
                  {branding.taxIdLabel}: {branding.taxRegistrationNumber}
                </div>
              )}
              <div className="mt-2 font-bold tracking-wide text-slate-900">
                {transaction?.type === 'held' ? 'HELD ORDER — NOT A SALE' : docTitleMap[effectiveType]}
              </div>
              <div className="font-bold border border-slate-700 mt-2 p-1">{PILOT_DOCUMENT_NOTICE}</div>
              {legacyIdentity && <div className="font-bold border border-slate-700 mt-2 p-1">HISTORICAL IDENTITY UNAVAILABLE — CURRENT BUSINESS SETTINGS USED</div>}
              {transaction && transaction.syncStatus !== "synced" && <div className="font-bold border border-slate-700 mt-2 p-1">PROVISIONAL — SERVER CONFIRMATION PENDING</div>}
              {transaction && ['voided','refunded'].includes(transaction.paymentStatus) && <div className="font-bold border border-slate-700 mt-2 p-1">{transaction.paymentStatus.toUpperCase()} — ORIGINAL SALE RECORD</div>}
              <div className="text-[10px] text-slate-500">
                {copyMode === 'internal' ? 'INTERNAL PRODUCTION COPY' : 'CUSTOMER COPY'}
                {(reprint || previewOpened) && ' · REPRINT / COPY'}
              </div>
            </div>

            <div className="space-y-1 border-b border-dashed border-slate-300 pb-2.5 mb-2.5">
              <div className="flex justify-between">
                <span>Ref #:</span>
                <span className="font-bold">{docReference}</span>
              </div>
              <div className="flex justify-between">
                <span>Date:</span>
                <span>
                  {new Date(
                    transaction?.createdAt || printJob?.createdAt || Date.now(),
                  ).toLocaleString('en-PH', { timeZone: 'Asia/Manila' })}
                </span>
              </div>
              <div className="flex justify-between">
                <span>Customer:</span>
                <span>{transaction?.customerName || printJob?.customerName}</span>
              </div>
              {transaction && (
                <div className="flex justify-between">
                  <span>Cashier:</span>
                  <span>{transaction.cashierName}</span>
                </div>
              )}
              {printJob && (
                <div className="flex justify-between">
                  <span>Job Status:</span>
                  <span className="uppercase">{printJob.productionStatus.replace(/_/g, ' ')}</span>
                </div>
              )}
            </div>

            {transaction ? (
              <>
                <div className="border-b border-dashed border-slate-300 pb-2.5 mb-2.5 space-y-2">
                  {transaction.items.map((item) => (
                    <div key={item.id}>
                      <div className="flex justify-between font-semibold">
                        <span>{item.serviceName}</span>
                        <span>{formatCurrency(item.lineTotal, taxCurr)}</span>
                      </div>
                      <div className="text-[10px] text-slate-600 flex justify-between">
                        <span>
                          {item.effectiveQuantity} {item.unitLabel} ×{' '}
                          {formatCurrency(item.unitPrice, taxCurr)}
                        </span>
                        {item.lineDiscount > 0 && (
                          <span>-{formatCurrency(item.lineDiscount, taxCurr)}</span>
                        )}
                      </div>
                      {item.displaySummary && (
                        <div className="text-[10px] text-slate-500">{item.displaySummary}</div>
                      )}
                      {copyMode === 'internal' && item.config.notes && (
                        <div className="text-[10px] font-semibold text-slate-800">
                          Note: {item.config.notes}
                        </div>
                      )}
                    </div>
                  ))}
                </div>

                <div className="space-y-1 border-b border-dashed border-slate-300 pb-2.5 mb-2.5">
                  <div className="flex justify-between">
                    <span>Subtotal</span>
                    <span>{formatCurrency(transaction.subtotal, taxCurr)}</span>
                  </div>
                  {transaction.itemDiscountsTotal + transaction.orderDiscountAmount > 0 && (
                    <div className="flex justify-between">
                      <span>Discounts</span>
                      <span>
                        -
                        {formatCurrency(
                          transaction.itemDiscountsTotal + transaction.orderDiscountAmount,
                          taxCurr,
                        )}
                      </span>
                    </div>
                  )}
                  {transaction.taxAmount > 0 && (
                    <div className="flex justify-between">
                      <span>
                        Tax (recorded amount)
                      </span>
                      <span>{formatCurrency(transaction.taxAmount, taxCurr)}</span>
                    </div>
                  )}
                  <div className="flex justify-between font-bold text-sm pt-1">
                    <span>TOTAL</span>
                    <span>{formatCurrency(transaction.totalAmount, taxCurr)}</span>
                  </div>
                  <div className="flex justify-between">
                    <span>Amount Paid</span>
                    <span>{formatCurrency(transaction.amountPaid, taxCurr)}</span>
                  </div>
                  {transaction.changeAmount > 0 && (
                    <div className="flex justify-between">
                      <span>Change</span>
                      <span>{formatCurrency(transaction.changeAmount, taxCurr)}</span>
                    </div>
                  )}
                  {transaction.balanceDue > 0 && (
                    <div className="flex justify-between font-bold">
                      <span>Remaining Balance</span>
                      <span>{formatCurrency(transaction.balanceDue, taxCurr)}</span>
                    </div>
                  )}
                </div>

                {transaction.payments.length > 0 && (
                  <div className="space-y-1 border-b border-dashed border-slate-300 pb-2.5 mb-2.5">
                    <div className="font-semibold">Payments</div>
                    {transaction.payments.map((p) => (
                      <div key={p.id} className="flex justify-between text-[10px]">
                        <span>
                          {p.methodName}
                          {p.referenceNumber ? ` (${p.referenceNumber})` : ''} ·{' '}
                          {p.verificationStatus === 'verified' ? 'Verified' : 'Pending Verification'}
                        </span>
                        <span>{formatCurrency(p.amount, taxCurr)}</span>
                      </div>
                    ))}
                  </div>
                )}
              </>
            ) : (
              printJob && (
                <div className="space-y-2 border-b border-dashed border-slate-300 pb-2.5 mb-2.5">
                  <div className="font-semibold">{printJob.title}</div>
                  <div className="text-[10px] text-slate-600">{printJob.specifications}</div>
                  {printJob.instructions && (
                    <div className="text-[10px] text-slate-700">
                      Instructions: {printJob.instructions}
                    </div>
                  )}
                  <div className="flex justify-between font-bold pt-1">
                    <span>Total Amount</span>
                    <span>{formatCurrency(printJob.totalAmount, taxCurr)}</span>
                  </div>
                  <div className="flex justify-between">
                    <span>Deposit Paid</span>
                    <span>{formatCurrency(printJob.depositAmount, taxCurr)}</span>
                  </div>
                  <div className="flex justify-between font-bold">
                    <span>Balance Due</span>
                    <span>{formatCurrency(printJob.remainingBalance, taxCurr)}</span>
                  </div>
                </div>
              )
            )}

            <div className="text-center space-y-2 pt-1">
              {branding.showQrOnReceipt && <div>Reference: {docReference}</div>}
              <div className="font-semibold">{branding.receiptHeader}</div>
              <div className="text-[10px] text-slate-600">{branding.receiptFooter}</div>
            </div>
          </div>
        </div>
      </div>
    </Modal>
  );
};
