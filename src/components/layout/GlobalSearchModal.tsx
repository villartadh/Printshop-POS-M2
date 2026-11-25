import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowRight,
  ClipboardList,
  Package,
  Receipt,
  Search,
  Tag,
  User,
  X,
} from 'lucide-react';
import { useAppState } from '../../context/AppStateContext.tsx';
import { useApp } from '../../context/AppContext.tsx';
import type { ModuleId } from '../../shared/types.ts';
import { MODULE_META } from './navigationMeta.ts';

interface SearchEntry {
  id: string;
  category: 'Module' | 'Service' | 'Inventory' | 'Print Job' | 'Customer' | 'Receipt';
  title: string;
  subtitle: string;
  rightText?: string;
  targetModule: ModuleId;
}

interface GlobalSearchModalProps {
  open: boolean;
  onClose: () => void;
  onNavigate: (mod: ModuleId) => void;
}

export const GlobalSearchModal: React.FC<GlobalSearchModalProps> = ({
  open,
  onClose,
  onNavigate,
}) => {
  const { settings } = useAppState();
  const { services, inventory, printJobs, customers, transactions } = useApp();
  const [query, setQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (open) {
      setQuery('');
      setSelectedIndex(0);
      window.setTimeout(() => inputRef.current?.focus(), 10);
    }
  }, [open]);

  const currency = settings.taxAndCurrency.currencySymbol || '₱';
  const visibleSet = useMemo(
    () =>
      new Set(
        settings.moduleOrder.filter((m) => !settings.hiddenModules.includes(m)),
      ),
    [settings.hiddenModules, settings.moduleOrder],
  );

  const results = useMemo<SearchEntry[]>(() => {
    const q = query.trim().toLowerCase();
    const list: SearchEntry[] = [];

    // Modules
    for (const mod of settings.moduleOrder) {
      if (!visibleSet.has(mod)) continue;
      const meta = MODULE_META[mod];
      if (!q || meta.label.toLowerCase().includes(q) || meta.description.toLowerCase().includes(q)) {
        list.push({
          id: `mod-${mod}`,
          category: 'Module',
          title: meta.label,
          subtitle: meta.description,
          targetModule: mod,
        });
      }
    }

    if (!q) {
      // Show top quick services & active jobs when search opens empty
      for (const s of services.filter((item) => !item.isArchived).slice(0, 4)) {
        list.push({
          id: `srv-${s.id}`,
          category: 'Service',
          title: s.name,
          subtitle: `${s.sku} · ${s.unitLabel}`,
          rightText: `${currency}${s.basePrice.toFixed(2)}`,
          targetModule: 'pos',
        });
      }
      for (const j of printJobs.slice(0, 3)) {
        list.push({
          id: `job-${j.id}`,
          category: 'Print Job',
          title: `${j.jobOrderNumber} — ${j.title}`,
          subtitle: `${j.customerName} · ${j.productionStatus.replace(/_/g, ' ')}`,
          rightText: `${currency}${j.totalAmount.toFixed(2)}`,
          targetModule: 'jobs',
        });
      }
      return list.slice(0, 12);
    }

    for (const s of services) {
      if (s.isArchived) continue;
      if (
        s.name.toLowerCase().includes(q) ||
        s.sku.toLowerCase().includes(q) ||
        s.description.toLowerCase().includes(q)
      ) {
        list.push({
          id: `srv-${s.id}`,
          category: 'Service',
          title: s.name,
          subtitle: `${s.sku} · ${s.unitLabel}`,
          rightText: `${currency}${s.basePrice.toFixed(2)}`,
          targetModule: 'pos',
        });
      }
    }

    for (const j of printJobs) {
      if (
        j.jobOrderNumber.toLowerCase().includes(q) ||
        j.title.toLowerCase().includes(q) ||
        j.customerName.toLowerCase().includes(q)
      ) {
        list.push({
          id: `job-${j.id}`,
          category: 'Print Job',
          title: `${j.jobOrderNumber} — ${j.title}`,
          subtitle: `${j.customerName} · ${j.productionStatus.replace(/_/g, ' ')}`,
          rightText: `${currency}${j.totalAmount.toFixed(2)}`,
          targetModule: 'jobs',
        });
      }
    }

    for (const c of customers) {
      if (
        c.name.toLowerCase().includes(q) ||
        c.phone.toLowerCase().includes(q) ||
        c.email.toLowerCase().includes(q)
      ) {
        list.push({
          id: `cust-${c.id}`,
          category: 'Customer',
          title: c.name,
          subtitle: `${c.phone || c.email} · ${c.group}`,
          rightText: `${c.loyaltyPoints} pts`,
          targetModule: 'customers',
        });
      }
    }

    for (const t of transactions) {
      if (
        t.receiptNumber.toLowerCase().includes(q) ||
        t.customerName.toLowerCase().includes(q)
      ) {
        list.push({
          id: `txn-${t.id}`,
          category: 'Receipt',
          title: `${t.receiptNumber} · ${t.customerName}`,
          subtitle: `${t.items.length} item(s) · ${t.paymentStatus}`,
          rightText: `${currency}${t.totalAmount.toFixed(2)}`,
          targetModule: 'finance',
        });
      }
    }

    for (const item of inventory) {
      if (
        item.name.toLowerCase().includes(q) ||
        item.sku.toLowerCase().includes(q) ||
        item.category.toLowerCase().includes(q) ||
        (item.supplier || '').toLowerCase().includes(q)
      ) {
        list.push({
          id: `inv-${item.id}`,
          category: 'Inventory',
          title: item.name,
          subtitle: `${item.sku} · ${item.category} · Min ${item.reorderLevel} ${item.unit}`,
          rightText: `${item.quantityOnHand} ${item.unit}`,
          targetModule: 'inventory',
        });
      }
    }

    return list.slice(0, 14);
  }, [currency, customers, inventory, printJobs, query, services, settings.moduleOrder, transactions, visibleSet]);

  useEffect(() => {
    setSelectedIndex(0);
  }, [query]);

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
      } else if (e.key === 'ArrowDown') {
        e.preventDefault();
        setSelectedIndex((prev) => (results.length ? (prev + 1) % results.length : 0));
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        setSelectedIndex((prev) =>
          results.length ? (prev - 1 + results.length) % results.length : 0,
        );
      } else if (e.key === 'Enter' && results[selectedIndex]) {
        e.preventDefault();
        onNavigate(results[selectedIndex].targetModule);
        onClose();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose, onNavigate, open, results, selectedIndex]);

  if (!open) return null;

  const renderCategoryIcon = (cat: SearchEntry['category']) => {
    switch (cat) {
      case 'Service':
        return <Tag className="w-4 h-4 text-[var(--pos-primary)]" />;
      case 'Inventory':
        return <Package className="w-4 h-4 text-[var(--pos-primary)]" />;
      case 'Print Job':
        return <ClipboardList className="w-4 h-4 text-[var(--pos-info)]" />;
      case 'Customer':
        return <User className="w-4 h-4 text-[var(--pos-success)]" />;
      case 'Receipt':
        return <Receipt className="w-4 h-4 text-[var(--pos-warning)]" />;
      default:
        return <ArrowRight className="w-4 h-4 text-[var(--pos-text-muted)]" />;
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center pt-[10vh] px-4 bg-slate-950/50 backdrop-blur-[2px]"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Global workspace search"
        style={{ borderRadius: 'calc(var(--pos-radius) + 4px)' }}
        className="w-full max-w-xl bg-[var(--pos-surface)] border border-[var(--pos-border)] shadow-xl overflow-hidden"
      >
        <div className="flex items-center gap-2.5 px-4 h-12 border-b border-[var(--pos-border)]">
          <Search className="w-4 h-4 text-[var(--pos-text-muted)] shrink-0" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Jump to module, service, customer, job order, or receipt…"
            className="flex-1 bg-transparent text-sm text-[var(--pos-text)] placeholder:text-[var(--pos-text-muted)] focus:outline-none"
          />
          <button
            type="button"
            onClick={onClose}
            aria-label="Close search"
            className="h-7 w-7 rounded-md flex items-center justify-center text-[var(--pos-text-muted)] hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="max-h-[55vh] overflow-y-auto divide-y divide-[var(--pos-border)]">
          {results.length === 0 ? (
            <div className="py-10 px-4 text-center text-xs text-[var(--pos-text-muted)]">
              No matching modules, services, customers, print jobs, or receipts found.
            </div>
          ) : (
            results.map((item, idx) => {
              const highlighted = idx === selectedIndex;
              return (
                <button
                  key={item.id}
                  type="button"
                  onMouseEnter={() => setSelectedIndex(idx)}
                  onClick={() => {
                    onNavigate(item.targetModule);
                    onClose();
                  }}
                  className={`w-full px-4 py-2.5 text-left flex items-center justify-between gap-3 transition-colors cursor-pointer ${
                    highlighted
                      ? 'bg-[var(--pos-primary)]/10'
                      : 'hover:bg-slate-50 dark:hover:bg-slate-800/60'
                  }`}
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <span className="h-8 w-8 rounded-lg bg-[var(--pos-bg)] border border-[var(--pos-border)] flex items-center justify-center shrink-0">
                      {renderCategoryIcon(item.category)}
                    </span>
                    <div className="min-w-0">
                      <div className="text-sm font-medium text-[var(--pos-text)] truncate">
                        {item.title}
                      </div>
                      <div className="text-[11px] text-[var(--pos-text-muted)] truncate">
                        {item.category} · {item.subtitle}
                      </div>
                    </div>
                  </div>
                  {item.rightText && (
                    <span className="font-mono-num text-xs font-semibold text-[var(--pos-text)] shrink-0">
                      {item.rightText}
                    </span>
                  )}
                </button>
              );
            })
          )}
        </div>

        <div className="px-4 py-2 bg-[var(--pos-bg)] border-t border-[var(--pos-border)] flex items-center justify-between text-[11px] text-[var(--pos-text-muted)]">
          <span>↑↓ Navigate · Enter Open · Esc Close</span>
          <span className="font-mono-num">{results.length} results</span>
        </div>
      </div>
    </div>
  );
};
