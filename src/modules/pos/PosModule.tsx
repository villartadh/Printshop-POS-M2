import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Camera,
  FileText,
  Grid,
  List,
  PauseCircle,
  Percent,
  Plus,
  Printer,
  RotateCcw,
  Search,
  ShoppingCart,
  Star,
  Trash2,
  UserPlus,
} from 'lucide-react';
import { useApp } from '../../context/AppContext.tsx';
import {
  calculateCartItemTotals,
  calculateOrderTotals,
  roundAmount,
  calculateServicePrice,
  formatCurrency,
  getDefaultServiceSelection,
} from '../../shared/pricingEngine.ts';
import type {
  CartItem,
  PaymentEntry,
  PrintService,
  ServiceConfigSelection,
  Transaction,
} from '../../shared/types.ts';
import { Button, Input, QuantityStepper, Select } from '../../components/ui/Primitives.tsx';
import { BottomSheet, EmptyState, Modal } from '../../components/ui/Overlays.tsx';
import { ServiceIcon } from '../../components/ui/ServiceIcon.tsx';
import { ServiceConfigurator } from './ServiceConfigurator.tsx';
import { CheckoutModal } from './CheckoutModal.tsx';
import { ReceiptModal } from '../receipts/ReceiptModal.tsx';

interface PosModuleProps {
  layoutTier: 'desktop' | 'tablet' | 'mobile';
}

export const PosModule: React.FC<PosModuleProps> = ({ layoutTier }) => {
  const {
    settings,
    categories,
    services,
    customers,
    transactions,
    session,
    activeShift,
    isOnline,
    submitTransaction,
    createCustomer,
    notify,
  } = useApp();

  const taxCurr = settings.taxAndCurrency;
  const searchInputRef = useRef<HTMLInputElement>(null);

  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategoryId, setSelectedCategoryId] = useState<string>('all');
  const [quickFilter, setQuickFilter] = useState<'all' | 'favorites' | 'recent'>('all');
  const [viewMode, setViewMode] = useState<'grid' | 'list'>(settings.posLayout.defaultView);

  const [sourceOrderId, setSourceOrderId] = useState<string | undefined>();
  const [cart, setCart] = useState<CartItem[]>([]);
  const [selectedCustomerId, setSelectedCustomerId] = useState<string>('');
  const [orderDiscountType, setOrderDiscountType] = useState<'none' | 'percent' | 'fixed'>('none');
  const [orderDiscountValue, setOrderDiscountValue] = useState<number>(0);
  const [orderNotes, setOrderNotes] = useState('');

  // Dialogs & sheets
  const [configService, setConfigService] = useState<PrintService | null>(null);
  const [checkoutOpen, setCheckoutOpen] = useState(false);
  const [mobileCartOpen, setMobileCartOpen] = useState(false);
  const [customItemOpen, setCustomItemOpen] = useState(false);
  const [discountModalOpen, setDiscountModalOpen] = useState(false);
  const [recallModalOpen, setRecallModalOpen] = useState(false);
  const [quickCustomerOpen, setQuickCustomerOpen] = useState(false);
  const [scannerOpen, setScannerOpen] = useState(false);
  const [receiptTxn, setReceiptTxn] = useState<Transaction | null>(null);

  // Custom service form
  const [customName, setCustomName] = useState('');
  const [customPrice, setCustomPrice] = useState('50');
  const [customQty, setCustomQty] = useState('1');

  // Quick customer form
  const [newCustName, setNewCustName] = useState('');
  const [newCustPhone, setNewCustPhone] = useState('');

  const selectedCustomer = customers.find((c) => c.id === selectedCustomerId) || null;
  const heldOrders = useMemo(
    () => transactions.filter((t) => (t.type === 'held' || t.type === 'quotation') && !t.convertedSaleId && t.syncStatus === 'synced'),
    [transactions],
  );

  // Apply customer group discount automatically when selecting customer
  useEffect(() => {
    if (selectedCustomer && selectedCustomer.discountPercent > 0 && orderDiscountType === 'none') {
      setOrderDiscountType('percent');
      setOrderDiscountValue(selectedCustomer.discountPercent);
    }
  }, [selectedCustomer]);

  const orderTotals = useMemo(
    () => calculateOrderTotals(cart, orderDiscountType, orderDiscountValue, taxCurr),
    [cart, orderDiscountType, orderDiscountValue, taxCurr],
  );

  const recentServiceIds = useMemo(() => {
    const ids = new Set<string>();
    for (const txn of transactions.slice(0, 15)) {
      for (const item of txn.items) {
        ids.add(item.serviceId);
      }
    }
    return ids;
  }, [transactions]);

  const visibleServices = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    const list = services
      .filter((s) => !s.isArchived)
      .filter((s) => (selectedCategoryId === 'all' ? true : s.categoryId === selectedCategoryId))
      .filter((s) => {
        if (quickFilter === 'favorites') return s.isFavorite;
        if (quickFilter === 'recent') return recentServiceIds.has(s.id);
        return true;
      })
      .filter((s) => {
        if (!q) return true;
        return (
          s.name.toLowerCase().includes(q) ||
          s.sku.toLowerCase().includes(q) ||
          s.description.toLowerCase().includes(q)
        );
      });

    if (settings.posLayout.sortBy === 'price_asc') {
      return [...list].sort((a, b) => a.basePrice - b.basePrice);
    }
    if (settings.posLayout.sortBy === 'price_desc') {
      return [...list].sort((a, b) => b.basePrice - a.basePrice);
    }
    if (settings.posLayout.sortBy === 'name') {
      return [...list].sort((a, b) => a.name.localeCompare(b.name));
    }
    return list;
  }, [services, selectedCategoryId, quickFilter, searchQuery, recentServiceIds, settings.posLayout.sortBy]);

  const addConfiguredServiceToCart = (
    service: PrintService,
    config: ServiceConfigSelection,
  ) => {
    const calc = calculateServicePrice(service, config, taxCurr);
    const baseItem: Omit<CartItem, 'lineDiscount' | 'lineTotal'> = {
      id: `ci_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
      serviceId: service.id,
      sku: service.sku,
      serviceName: service.name,
      pricingMethod: service.pricingMethod,
      unitLabel: service.unitLabel,
      unitPrice: calc.unitPrice,
      effectiveQuantity: calc.effectiveQuantity,
      displaySummary: calc.displaySummary,
      config,
      discountType: 'none',
      discountValue: 0,
      lineSubtotal: calc.lineSubtotal,
      costTotal: calc.estimatedCost,
      requiresProductionJob: service.requiresProductionJob,
      consumables: service.consumables,
    };
    const totals = calculateCartItemTotals(baseItem, taxCurr);
    setCart((prev) => [...prev, { ...baseItem, ...totals }]);
  };

  const handleQuickAddService = (service: PrintService) => {
    if (service.requiresConfiguration) {
      setConfigService(service);
    } else {
      addConfiguredServiceToCart(service, getDefaultServiceSelection(service));
    }
  };

  const handleUpdateCartQuantity = (itemId: string, nextQty: number) => {
    setCart((prev) =>
      prev.map((item) => {
        if (item.id !== itemId) return item;
        const config = { ...item.config, quantity: nextQty };
        const service = services.find(s => s.id === item.serviceId && !s.isArchived);
        if (!service && !item.isCustomItem) return item;
        const calc = service ? calculateServicePrice(service, config, taxCurr) : null;
        const updated = {
          ...item, config,
          effectiveQuantity: calc?.effectiveQuantity ?? nextQty,
          unitPrice: calc?.unitPrice ?? item.unitPrice,
          lineSubtotal: calc?.lineSubtotal ?? roundAmount(item.unitPrice * nextQty, taxCurr),
          costTotal: calc?.estimatedCost ?? 0,
          displaySummary: calc?.displaySummary ?? item.displaySummary,
        };
        const totals = calculateCartItemTotals(updated, taxCurr);
        return { ...updated, ...totals };
      }),
    );
  };

  const handleSearchKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && searchQuery.trim()) {
      const exactSku = services.find(
        (s) => !s.isArchived && s.sku.toLowerCase() === searchQuery.trim().toLowerCase(),
      );
      if (exactSku) {
        e.preventDefault();
        handleQuickAddService(exactSku);
        setSearchQuery('');
      }
    }
  };

  // Keyboard shortcuts for Desktop POS productivity (F4, F7, F8, F9, F10, F12)
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'F4') {
        e.preventDefault();
        searchInputRef.current?.focus();
      } else if (e.key === 'F7') {
        e.preventDefault();
        setCustomItemOpen(true);
      } else if (e.key === 'F8') {
        e.preventDefault();
        setDiscountModalOpen(true);
      } else if (e.key === 'F9') {
        e.preventDefault();
        if (cart.length > 0) void handleSaveOrder('held');
      } else if (e.key === 'F10') {
        e.preventDefault();
        setRecallModalOpen(true);
      } else if (e.key === 'F12') {
        e.preventDefault();
        if (cart.length > 0) setCheckoutOpen(true);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [cart.length]);

  const handleSaveOrder = async (type: 'held' | 'quotation') => {
    if (cart.length === 0 || !session) return;
    if (sourceOrderId) { notify('Complete or clear the recalled order before saving another.', 'error'); return; }
    const txn: Transaction = {
      id: `txn_${Date.now()}`,
      receiptNumber: `${type === 'quotation' ? 'QT' : 'HLD'}-${`${Date.now()}-${Math.random().toString(36).slice(2, 8)}`}`,
      clientMutationId: `mut_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
      type,
      customerId: selectedCustomer?.id || null,
      customerName: selectedCustomer?.name || 'Walk-in Customer',
      cashierId: session.user.id,
      cashierName: session.user.fullName,
      shiftId: activeShift?.id || null,
      items: cart,
      subtotal: orderTotals.subtotal,
      itemDiscountsTotal: orderTotals.itemDiscountsTotal,
      orderDiscountType,
      orderDiscountValue,
      orderDiscountAmount: orderTotals.orderDiscountAmount,
      taxAmount: orderTotals.taxAmount,
      totalAmount: orderTotals.totalAmount,
      amountPaid: 0,
      changeAmount: 0,
      balanceDue: orderTotals.totalAmount,
      paymentStatus: 'unpaid',
      payments: [],
      notes: orderNotes,
      syncStatus: 'synced',
      createdAt: new Date().toISOString(),
    };
    let res: Awaited<ReturnType<typeof submitTransaction>>;
    try {
      res = await submitTransaction(txn);
    } catch (error) {
      notify(error instanceof Error ? error.message : 'Could not save the order.', 'error');
      return;
    }
    setCart([]);
    setSourceOrderId(undefined);
    setOrderNotes('');
    setOrderDiscountType('none');
    setOrderDiscountValue(0);
    setMobileCartOpen(false);
    if (type === 'quotation') {
      setReceiptTxn(res.transaction);
      notify('Quotation saved.', 'success');
    } else {
      notify('Order placed on hold.', 'info');
    }
  };

  const handleCompletePayment = async (payments: PaymentEntry[], allowPartial: boolean) => {
    if (!session || cart.length === 0) return;
    if (sourceOrderId && !isOnline) throw new Error('Connect to the server before completing a recalled order.');
    const totalPaidRaw = payments.reduce((s, p) => s + p.amount, 0);
    const changeAmount = Math.max(0, Number((totalPaidRaw - orderTotals.totalAmount).toFixed(2)));
    const appliedPaid = Math.min(orderTotals.totalAmount, totalPaidRaw);
    const balanceDue = Math.max(0, Number((orderTotals.totalAmount - appliedPaid).toFixed(2)));
    if (balanceDue > 0 && !selectedCustomer) {
      throw new Error('Select a registered customer before recording a balance due.');
    }
    const paymentStatus: Transaction['paymentStatus'] =
      balanceDue <= 0 ? 'paid' : appliedPaid > 0 ? 'partial' : allowPartial ? 'unpaid' : 'paid';

    const txn: Transaction = {
      id: `txn_${Date.now()}`,
      receiptNumber: `OR-${`${Date.now()}-${Math.random().toString(36).slice(2, 8)}`}`,
      clientMutationId: `mut_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
      type: 'sale',
      sourceOrderId,
      customerId: selectedCustomer?.id || null,
      customerName: selectedCustomer?.name || 'Walk-in Customer',
      cashierId: session.user.id,
      cashierName: session.user.fullName,
      shiftId: activeShift?.id || null,
      items: cart,
      subtotal: orderTotals.subtotal,
      itemDiscountsTotal: orderTotals.itemDiscountsTotal,
      orderDiscountType,
      orderDiscountValue,
      orderDiscountAmount: orderTotals.orderDiscountAmount,
      taxAmount: orderTotals.taxAmount,
      totalAmount: orderTotals.totalAmount,
      amountPaid: appliedPaid,
      changeAmount,
      balanceDue,
      paymentStatus,
      payments,
      notes: orderNotes,
      syncStatus: 'synced',
      createdAt: new Date().toISOString(),
    };

    const res = await submitTransaction(txn);
    setCart([]);
    setSourceOrderId(undefined);
    setOrderNotes('');
    setOrderDiscountType('none');
    setOrderDiscountValue(0);
    setMobileCartOpen(false);
    setReceiptTxn(res.transaction);
    notify(res.queuedOffline ? `Sale ${res.transaction.receiptNumber} queued; server confirmation pending.` : `Sale ${res.transaction.receiptNumber} completed.`, res.queuedOffline ? 'info' : 'success');
  };

  const handleAddCustomItem = (e: React.FormEvent) => {
    e.preventDefault();
    if (!session || !["admin", "manager"].includes(session.user.role)) { notify("Custom pricing requires a manager or administrator.", "error"); return; }
    const price = Math.max(0, Number(customPrice) || 0);
    const qty = Math.max(1, Number(customQty) || 1);
    if (!customName.trim() || price <= 0) return;
    const lineSubtotal = roundAmount(price * qty, taxCurr);
    const item: CartItem = {
      id: `ci_custom_${Date.now()}`,
      serviceId: 'custom',
      sku: 'CUSTOM',
      serviceName: customName.trim(),
      pricingMethod: 'fixed',
      unitLabel: 'pc',
      unitPrice: price,
      effectiveQuantity: qty,
      displaySummary: 'Custom Service',
      config: {
        quantity: qty,
        pages: 1,
        copies: 1,
        width: 1,
        height: 1,
        dimensionUnit: 'ft',
        selectedAttributes: {},
        isRush: false,
        designFee: 0,
        laborFee: 0,
        notes: '',
      },
      discountType: 'none',
      discountValue: 0,
      lineSubtotal,
      lineDiscount: 0,
      lineTotal: lineSubtotal,
      costTotal: 0,
      requiresProductionJob: false,
      consumables: [],
      isCustomItem: true,
    };
    setCart((prev) => [...prev, item]);
    setCustomName('');
    setCustomPrice('50');
    setCustomQty('1');
    setCustomItemOpen(false);
  };

  const handleRecallOrder = async (held: Transaction) => {
    if (cart.length) { notify('Clear or finish the current cart before recalling an order.', 'error'); return; }
    setSourceOrderId(held.id);
    setCart(held.items);
    setSelectedCustomerId(held.customerId || '');
    setOrderDiscountType(held.orderDiscountType);
    setOrderDiscountValue(held.orderDiscountValue);
    setOrderNotes(held.notes);
    setRecallModalOpen(false);
    notify(`Recalled order ${held.receiptNumber}.`, 'info');
  };

  const gridColsCls =
    layoutTier !== 'desktop' || settings.posLayout.gridColumnsDesktop === 2
      ? 'grid-cols-2'
      : settings.posLayout.gridColumnsDesktop === 4
        ? 'grid-cols-2 xl:grid-cols-4'
        : 'grid-cols-2 lg:grid-cols-3';

  const cartPanelContent = (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between gap-2 pb-3 border-b border-[var(--pos-border)]">
        <div>
          <h3 className="text-sm font-semibold text-[var(--pos-text)]">
            Current order ({cart.length})
          </h3>
          <p className="text-[11px] text-[var(--pos-text-muted)]">
            {activeShift ? `Shift open · ${activeShift.cashierName}` : 'No active cash shift'}
          </p>
        </div>
        {cart.length > 0 && (
          <button
            type="button"
            onClick={() => { setCart([]); setSourceOrderId(undefined); }}
            className="text-xs font-medium text-[#DC2626] hover:underline cursor-pointer"
          >
            Clear
          </button>
        )}
      </div>

      {/* Customer Selector */}
      <div className="py-3 border-b border-[var(--pos-border)] flex items-center gap-2">
        <div className="flex-1 min-w-0">
          <Select
            value={selectedCustomerId}
            onChange={(e) => setSelectedCustomerId(e.target.value)}
            options={[
              { value: '', label: 'Walk-in customer' },
              ...customers.map((c) => ({
                value: c.id,
                label: `${c.name}${c.discountPercent > 0 ? ` (${c.discountPercent}% off)` : ''}`,
              })),
            ]}
          />
        </div>
        <button
          type="button"
          title="Quick add customer"
          onClick={() => setQuickCustomerOpen(true)}
          style={{ borderRadius: 'var(--pos-radius)' }}
          className="h-10 w-10 border border-[var(--pos-border)] flex items-center justify-center text-[var(--pos-text-muted)] hover:text-[var(--pos-text)] hover:bg-slate-50 dark:hover:bg-slate-800 shrink-0 cursor-pointer"
        >
          <UserPlus className="w-4 h-4" />
        </button>
      </div>

      {/* Cart Items */}
      <div className="flex-1 overflow-y-auto py-3 space-y-2.5 min-h-[160px]">
        {cart.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center text-center py-8 px-4">
            <ShoppingCart className="w-8 h-8 text-[var(--pos-text-muted)]/40 mb-2" />
            <p className="text-xs font-medium text-[var(--pos-text-muted)]">
              Tap a printing service to add it to the order
            </p>
          </div>
        ) : (
          cart.map((item) => (
            <div
              key={item.id}
              className="p-2.5 rounded-lg border border-[var(--pos-border)] bg-[var(--pos-bg)]/50 space-y-2"
            >
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <div className="text-xs font-semibold text-[var(--pos-text)] truncate">
                    {item.serviceName}
                  </div>
                  <div className="text-[11px] text-[var(--pos-text-muted)] truncate">
                    {item.displaySummary}
                  </div>
                </div>
                <div className="text-xs font-mono-num font-bold text-[var(--pos-text)] shrink-0">
                  {formatCurrency(item.lineTotal, taxCurr)}
                </div>
              </div>
              <div className="flex items-center justify-between gap-2">
                {services.find(s => s.id === item.serviceId)?.allowPagesAndCopies ? <span className="text-xs">{item.config.pages}p × {item.config.copies}c</span> : <QuantityStepper
                  compact
                  value={item.config.quantity}
                  onChange={(next) => handleUpdateCartQuantity(item.id, next)}
                />}
                <button
                  type="button"
                  onClick={() => setCart((prev) => prev.filter((x) => x.id !== item.id))}
                  className="h-8 w-8 rounded-lg flex items-center justify-center text-[var(--pos-text-muted)] hover:text-[#DC2626] hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
                  aria-label="Remove item"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          ))
        )}
      </div>

      {/* Totals & Actions */}
      <div className="pt-3 border-t border-[var(--pos-border)] space-y-2.5">
        <Input
          placeholder="Order note / job deadline..."
          value={orderNotes}
          onChange={(e) => setOrderNotes(e.target.value)}
        />

        <div className="space-y-1 text-xs">
          <div className="flex justify-between text-[var(--pos-text-muted)]">
            <span>Subtotal</span>
            <span className="font-mono-num">{formatCurrency(orderTotals.subtotal, taxCurr)}</span>
          </div>
          <div className="flex justify-between items-center text-[var(--pos-text-muted)]">
            <button
              type="button"
              onClick={() => setDiscountModalOpen(true)}
              className="inline-flex items-center gap-1 hover:text-[var(--pos-primary)] cursor-pointer"
            >
              <span>Discount</span>
              <Percent className="w-3 h-3" />
            </button>
            <span className="font-mono-num text-[#16A34A]">
              -{formatCurrency(orderTotals.orderDiscountAmount + orderTotals.itemDiscountsTotal, taxCurr)}
            </span>
          </div>
          {taxCurr.taxEnabled && (
            <div className="flex justify-between text-[var(--pos-text-muted)]">
              <span>
                {taxCurr.taxName} ({taxCurr.taxRatePercent}%)
              </span>
              <span className="font-mono-num">{formatCurrency(orderTotals.taxAmount, taxCurr)}</span>
            </div>
          )}
          <div className="flex justify-between items-center pt-1.5 border-t border-[var(--pos-border)]">
            <span className="text-sm font-semibold text-[var(--pos-text)]">Total amount</span>
            <span className="text-lg font-bold font-mono-num text-[var(--pos-text)]">
              {formatCurrency(orderTotals.totalAmount, taxCurr)}
            </span>
          </div>
        </div>

        <Button
          size="lg"
          className="w-full"
          disabled={cart.length === 0}
          onClick={() => setCheckoutOpen(true)}
        >
          Receive payment {layoutTier === 'desktop' ? '(F12)' : ''}
        </Button>

        <div className="grid grid-cols-3 gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={cart.length === 0}
            onClick={() => void handleSaveOrder('held')}
          >
            <PauseCircle className="w-3.5 h-3.5" />
            Hold
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={cart.length === 0}
            onClick={() => void handleSaveOrder('quotation')}
          >
            <FileText className="w-3.5 h-3.5" />
            Quote
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={transactions.filter((t) => t.type === 'sale').length === 0}
            onClick={() => {
              const latest = transactions.find((t) => t.type === 'sale');
              if (latest) setReceiptTxn(latest);
            }}
          >
            <Printer className="w-3.5 h-3.5" />
            Receipt
          </Button>
        </div>
      </div>
    </div>
  );

  return (
    <div className={`flex gap-4 min-h-0 ${layoutTier === 'mobile' ? 'flex-col min-h-full' : 'flex-row h-full'}`}>
      {/* Left / Main Catalog Pane */}
      <div className="flex-1 min-w-0 min-h-0 flex flex-col gap-3">
        {/* Search & Shortcut Toolbar */}
        <div className="flex flex-wrap items-center gap-2">
          <div className="flex-1 min-w-[200px]">
            <Input
              ref={searchInputRef}
              leftIcon={<Search className="w-4 h-4" />}
              placeholder={
                layoutTier === 'desktop'
                  ? 'Search services, SKU, or scan barcode... (F4)'
                  : 'Search services...'
              }
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onKeyDown={handleSearchKeyDown}
            />
          </div>

          <Button
            variant="outline"
            size="md"
            onClick={() => setScannerOpen(true)}
            title="Barcode lookup / camera scan"
          >
            <Camera className="w-4 h-4" />
          </Button>

          <Button
            variant="outline"
            size="md"
            onClick={() => setCustomItemOpen(true)}
            title="Add custom printing service (F7)"
          >
            <Plus className="w-4 h-4" />
            <span className="hidden sm:inline">Custom</span>
          </Button>

          <Button
            variant="outline"
            size="md"
            onClick={() => setRecallModalOpen(true)}
            title="Recall held order (F10)"
          >
            <RotateCcw className="w-4 h-4" />
            <span className="hidden sm:inline">Recall ({heldOrders.length})</span>
          </Button>

          <div className="hidden sm:flex items-center border border-[var(--pos-border)] rounded-lg p-0.5 bg-[var(--pos-surface)]">
            <button
              type="button"
              onClick={() => setViewMode('grid')}
              className={`p-1.5 rounded-md cursor-pointer ${
                viewMode === 'grid'
                  ? 'bg-slate-100 dark:bg-slate-800 text-[var(--pos-text)]'
                  : 'text-[var(--pos-text-muted)]'
              }`}
              aria-label="Grid view"
            >
              <Grid className="w-4 h-4" />
            </button>
            <button
              type="button"
              onClick={() => setViewMode('list')}
              className={`p-1.5 rounded-md cursor-pointer ${
                viewMode === 'list'
                  ? 'bg-slate-100 dark:bg-slate-800 text-[var(--pos-text)]'
                  : 'text-[var(--pos-text-muted)]'
              }`}
              aria-label="List view"
            >
              <List className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Category Strip + Favorites / Recent Filter */}
        <div className="flex items-center justify-between gap-2 overflow-x-auto pb-1">
          <div className="flex items-center gap-1.5 shrink-0">
            <button
              type="button"
              onClick={() => setSelectedCategoryId('all')}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-colors cursor-pointer ${
                selectedCategoryId === 'all'
                  ? 'bg-[var(--pos-primary)] text-white'
                  : 'bg-[var(--pos-surface)] border border-[var(--pos-border)] text-[var(--pos-text-muted)] hover:text-[var(--pos-text)]'
              }`}
            >
              All
            </button>
            {categories.map((cat) => (
              <button
                key={cat.id}
                type="button"
                onClick={() => setSelectedCategoryId(cat.id)}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-colors cursor-pointer ${
                  selectedCategoryId === cat.id
                    ? 'bg-[var(--pos-primary)] text-white'
                    : 'bg-[var(--pos-surface)] border border-[var(--pos-border)] text-[var(--pos-text-muted)] hover:text-[var(--pos-text)]'
                }`}
              >
                {cat.name}
              </button>
            ))}
          </div>

          {settings.posLayout.showQuickItemsBar && (
            <div className="flex items-center gap-1 p-0.5 bg-slate-100 dark:bg-slate-800 rounded-lg shrink-0">
              {(['all', 'favorites', 'recent'] as const).map((tab) => (
                <button
                  key={tab}
                  type="button"
                  onClick={() => setQuickFilter(tab)}
                  className={`px-2.5 py-1 text-xs font-medium rounded-md capitalize cursor-pointer ${
                    quickFilter === tab
                      ? 'bg-[var(--pos-surface)] text-[var(--pos-text)] shadow-xs'
                      : 'text-[var(--pos-text-muted)]'
                  }`}
                >
                  {tab}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Service Grid / List */}
        <div className="flex-1 min-h-0 overflow-y-auto pr-0.5 pb-2">
          {visibleServices.length === 0 ? (
            <EmptyState
              title="No matching printing services"
              description="Clear the search filter or add a new printing service in the Services module."
              actionLabel="Add Custom Service"
              onAction={() => setCustomItemOpen(true)}
            />
          ) : viewMode === 'grid' ? (
            <div className={`grid ${gridColsCls} gap-3`}>
              {visibleServices.map((srv) => {
                const catName =
                  categories.find((c) => c.id === srv.categoryId)?.name || 'Service';
                return (
                  <button
                    key={srv.id}
                    type="button"
                    onClick={() => handleQuickAddService(srv)}
                    style={{ borderRadius: 'var(--pos-radius)' }}
                    className="group text-left bg-[var(--pos-surface)] border border-[var(--pos-border)] hover:border-[var(--pos-primary)] p-3 transition-colors flex flex-col justify-between gap-2.5 cursor-pointer"
                  >
                    <div className="w-full h-14 rounded-lg bg-[var(--pos-primary)]/10 text-[var(--pos-primary)] flex items-center justify-center relative">
                      <ServiceIcon name={srv.iconName} className="w-6 h-6" />
                      {srv.isFavorite && (
                        <Star className="w-3.5 h-3.5 absolute top-2 right-2 fill-current opacity-70" />
                      )}
                    </div>
                    <div className="min-w-0 w-full">
                      <div className="text-sm font-semibold text-[var(--pos-text)] truncate">
                        {srv.name}
                      </div>
                      {settings.posLayout.showCategoryOnCard && (
                        <div className="text-[11px] text-[var(--pos-text-muted)] truncate mt-0.5">
                          {catName} · {srv.sku}
                        </div>
                      )}
                    </div>
                    {settings.posLayout.showPriceOnCard && (
                      <div className="flex items-center justify-between gap-1 w-full pt-0.5">
                        <span className="min-w-0 break-words text-xs font-mono-num font-semibold text-[var(--pos-primary)]">
                          {formatCurrency(srv.basePrice, taxCurr)}
                          {settings.posLayout.showUnitOnCard ? `/${srv.unitLabel}` : ''}
                        </span>
                        <span className="h-6 w-6 shrink-0 rounded-full border border-[var(--pos-primary)]/40 text-[var(--pos-primary)] flex items-center justify-center group-hover:bg-[var(--pos-primary)] group-hover:text-white transition-colors">
                          <Plus className="w-3.5 h-3.5" />
                        </span>
                      </div>
                    )}
                  </button>
                );
              })}
            </div>
          ) : (
            <div className="space-y-2">
              {visibleServices.map((srv) => (
                <button
                  key={srv.id}
                  type="button"
                  onClick={() => handleQuickAddService(srv)}
                  style={{ borderRadius: 'var(--pos-radius)' }}
                  className="w-full text-left bg-[var(--pos-surface)] border border-[var(--pos-border)] hover:border-[var(--pos-primary)] px-3.5 py-2.5 flex items-center justify-between gap-3 cursor-pointer"
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="h-9 w-9 rounded-lg bg-[var(--pos-primary)]/10 text-[var(--pos-primary)] flex items-center justify-center shrink-0">
                      <ServiceIcon name={srv.iconName} className="w-4 h-4" />
                    </div>
                    <div className="min-w-0">
                      <div className="text-sm font-semibold text-[var(--pos-text)] truncate">
                        {srv.name}
                      </div>
                      <div className="text-xs text-[var(--pos-text-muted)] truncate">
                        {srv.sku} · {srv.description || srv.pricingMethod.replace(/_/g, ' ')}
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center gap-3 shrink-0">
                    <span className="text-sm font-mono-num font-semibold text-[var(--pos-primary)]">
                      {formatCurrency(srv.basePrice, taxCurr)}/{srv.unitLabel}
                    </span>
                    <Plus className="w-4 h-4 text-[var(--pos-primary)]" />
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Desktop & Tablet Landscape Persistent Right Cart Panel */}
      {layoutTier !== 'mobile' && (
        <aside
          style={{
            width:
              layoutTier === 'tablet'
                ? '310px'
                : `${Math.max(300, Math.min(440, settings.posLayout.cartPanelWidthPx))}px`,
            borderRadius: 'var(--pos-radius)',
          }}
          className="shrink-0 bg-[var(--pos-surface)] border border-[var(--pos-border)] p-4 flex flex-col max-h-[calc(100dvh-110px)]"
        >
          {cartPanelContent}
        </aside>
      )}

      {/* Mobile Sticky Cart Summary Bar (strictly compact above bottom nav) */}
      {layoutTier === 'mobile' && (
        <div className="absolute pos-mobile-cart left-0 right-0 z-30 px-3 pb-2 pointer-events-none">
          <button
            type="button"
            onClick={() => setMobileCartOpen(true)}
            style={{ borderRadius: 'var(--pos-radius)' }}
            className="pointer-events-auto w-full h-12 px-4 bg-[var(--pos-primary)] hover:bg-[var(--pos-primary-hover)] text-white shadow-lg flex items-center justify-between text-sm font-semibold cursor-pointer"
          >
            <span className="inline-flex items-center gap-2">
              <ShoppingCart className="w-4 h-4" />
              <span>View cart · {cart.length} item(s)</span>
            </span>
            <span className="font-mono-num">
              {formatCurrency(orderTotals.totalAmount, taxCurr)}
            </span>
          </button>
        </div>
      )}

      {/* Mobile Cart Sheet */}
      <BottomSheet
        open={mobileCartOpen}
        onClose={() => setMobileCartOpen(false)}
        title="Current Order"
        subtitle={`${cart.length} item(s)`}
      >
        {cartPanelContent}
      </BottomSheet>

      {/* Dynamic Service Configurator */}
      <ServiceConfigurator
        open={Boolean(configService)}
        service={configService}
        onClose={() => setConfigService(null)}
        onConfirm={addConfiguredServiceToCart}
        isMobileLayout={layoutTier === 'mobile'}
      />

      {/* Checkout Modal */}
      <CheckoutModal
        open={checkoutOpen}
        onClose={() => setCheckoutOpen(false)}
        totalAmount={orderTotals.totalAmount}
        customerName={selectedCustomer?.name || 'Walk-in Customer'}
        isMobileLayout={layoutTier === 'mobile'}
        onCompletePayment={handleCompletePayment}
      />

      {/* Custom Service Item Modal */}
      <Modal
        open={customItemOpen}
        onClose={() => setCustomItemOpen(false)}
        title="Add Custom Service"
        subtitle="One-off print job or manual line item"
        maxWidth="sm"
      >
        <form onSubmit={handleAddCustomItem} className="space-y-3.5">
          <Input
            label="Service / Item Description"
            placeholder="e.g. Custom Die-Cut Vinyl Sticker"
            value={customName}
            onChange={(e) => setCustomName(e.target.value)}
            required
          />
          <div className="grid grid-cols-2 gap-3">
            <Input
              label={`Unit Price (${taxCurr.currencySymbol})`}
              type="number"
              min="1"
              step="0.01"
              value={customPrice}
              onChange={(e) => setCustomPrice(e.target.value)}
              required
            />
            <Input
              label="Quantity"
              type="number"
              min="1"
              value={customQty}
              onChange={(e) => setCustomQty(e.target.value)}
              required
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" type="button" onClick={() => setCustomItemOpen(false)}>
              Cancel
            </Button>
            <Button type="submit">Add to Order</Button>
          </div>
        </form>
      </Modal>

      {/* Order Discount Modal */}
      <Modal
        open={discountModalOpen}
        onClose={() => setDiscountModalOpen(false)}
        title="Order Discount"
        maxWidth="sm"
        footer={
          <Button size="sm" onClick={() => setDiscountModalOpen(false)}>
            Apply
          </Button>
        }
      >
        <div className="space-y-3">
          <Select
            label="Discount Type"
            value={orderDiscountType}
            onChange={(e) => { setOrderDiscountType(e.target.value as typeof orderDiscountType); if (e.target.value === "none") setOrderDiscountValue(0); }}
            options={[
              { value: 'none', label: 'No Discount' },
              { value: 'percent', label: 'Percentage (%)' },
              { value: 'fixed', label: `Fixed Amount (${taxCurr.currencySymbol})` },
            ]}
          />
          {orderDiscountType !== 'none' && (
            <Input
              label={orderDiscountType === 'percent' ? 'Discount (%)' : 'Discount Amount'}
              type="number"
              min="0"
              step="0.01"
              value={orderDiscountValue}
              onChange={(e) => setOrderDiscountValue(Math.max(0, Number(e.target.value) || 0))}
            />
          )}
        </div>
      </Modal>

      {/* Recall Held Orders & Quotations */}
      <Modal
        open={recallModalOpen}
        onClose={() => setRecallModalOpen(false)}
        title="Recall Held Orders & Quotations"
        maxWidth="md"
      >
        {heldOrders.length === 0 ? (
          <EmptyState
            title="No saved orders"
            description="Unconverted held orders and quotations appear here."
          />
        ) : (
          <div className="space-y-2.5">
            {heldOrders.map((h) => (
              <div
                key={h.id}
                className="p-3 rounded-lg border border-[var(--pos-border)] flex items-center justify-between gap-3"
              >
                <div>
                  <div className="text-sm font-semibold text-[var(--pos-text)]">
                    {h.receiptNumber} · {h.customerName} · {h.type}
                  </div>
                  <div className="text-xs text-[var(--pos-text-muted)]">
                    {h.items.length} item(s) · {formatCurrency(h.totalAmount, taxCurr)}
                  </div>
                </div>
                <Button size="sm" onClick={() => void handleRecallOrder(h)}>
                  Recall
                </Button>
              </div>
            ))}
          </div>
        )}
      </Modal>

      {/* Quick Add Customer */}
      <Modal
        open={quickCustomerOpen}
        onClose={() => setQuickCustomerOpen(false)}
        title="Quick Add Customer"
        maxWidth="sm"
      >
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            if (!newCustName.trim()) return;
            const created = await createCustomer({ name: newCustName, phone: newCustPhone });
            setSelectedCustomerId(created.id);
            setNewCustName('');
            setNewCustPhone('');
            setQuickCustomerOpen(false);
          }}
          className="space-y-3"
        >
          <Input
            label="Customer Name"
            value={newCustName}
            onChange={(e) => setNewCustName(e.target.value)}
            required
          />
          <Input
            label="Phone Number"
            value={newCustPhone}
            onChange={(e) => setNewCustPhone(e.target.value)}
          />
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" type="button" onClick={() => setQuickCustomerOpen(false)}>
              Cancel
            </Button>
            <Button type="submit">Save & Select</Button>
          </div>
        </form>
      </Modal>

      {/* Barcode / SKU Scanner Lookup */}
      <Modal
        open={scannerOpen}
        onClose={() => setScannerOpen(false)}
        title="SKU & Barcode Lookup"
        subtitle="Scan with a USB/Bluetooth barcode wedge or enter SKU manually"
        maxWidth="sm"
      >
        <div className="space-y-3">
          <Input
            label="Barcode / SKU"
            placeholder="e.g. PRT-BW-01"
            autoFocus
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                handleSearchKeyDown(e);
                setScannerOpen(false);
              }
            }}
          />
          <p className="text-xs text-[var(--pos-text-muted)]">
            {typeof window !== 'undefined' && 'BarcodeDetector' in window
              ? 'Native BarcodeDetector API is available on this device.'
              : 'Hardware keyboard-wedge scanners work directly in the search field; press Enter after scanning.'}
          </p>
        </div>
      </Modal>

      {/* Receipt Preview */}
      <ReceiptModal
        open={Boolean(receiptTxn)}
        onClose={() => setReceiptTxn(null)}
        transaction={receiptTxn}
      />
    </div>
  );
};
