import React, { useEffect } from 'react';
import { AlertTriangle, Inbox, X } from 'lucide-react';
import { Button } from './Primitives.tsx';

export const Modal: React.FC<{
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  maxWidth?: 'sm' | 'md' | 'lg' | 'xl';
  children: React.ReactNode;
  footer?: React.ReactNode;
}> = ({ open, onClose, title, subtitle, maxWidth = 'md', children, footer }) => {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;

  const widthCls =
    maxWidth === 'sm'
      ? 'max-w-md'
      : maxWidth === 'lg'
        ? 'max-w-2xl'
        : maxWidth === 'xl'
          ? 'max-w-4xl'
          : 'max-w-lg';

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 backdrop-blur-[2px] p-4"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        style={{ borderRadius: 'calc(var(--pos-radius) + 4px)' }}
        className={`w-full ${widthCls} max-h-[90dvh] flex flex-col bg-[var(--pos-surface)] border border-[var(--pos-border)] shadow-xl overflow-hidden`}
      >
        <div className="flex items-start justify-between gap-4 px-5 py-4 border-b border-[var(--pos-border)]">
          <div className="min-w-0">
            <h2 className="text-base font-semibold text-[var(--pos-text)] truncate">{title}</h2>
            {subtitle && (
              <p className="text-xs text-[var(--pos-text-muted)] mt-0.5 truncate">{subtitle}</p>
            )}
          </div>
          <button
            type="button"
            aria-label="Close dialog"
            onClick={onClose}
            className="h-9 w-9 rounded-lg flex items-center justify-center text-[var(--pos-text-muted)] hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors shrink-0 cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-4">{children}</div>
        {footer && (
          <div className="px-5 py-3.5 border-t border-[var(--pos-border)] bg-slate-50/70 dark:bg-slate-900/50 flex items-center justify-end gap-2.5">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
};

export const BottomSheet: React.FC<{
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
}> = ({ open, onClose, title, subtitle, children, footer }) => {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-slate-950/50 backdrop-blur-[2px] sm:p-4"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        className="w-full sm:max-w-lg max-h-[88dvh] flex flex-col bg-[var(--pos-surface)] border-t sm:border border-[var(--pos-border)] rounded-t-2xl sm:rounded-2xl shadow-xl overflow-hidden"
      >
        <div className="sm:hidden pt-2.5 pb-1 flex justify-center">
          <div className="w-10 h-1 rounded-full bg-slate-300 dark:bg-slate-700" />
        </div>
        <div className="flex items-start justify-between gap-3 px-4 py-3 border-b border-[var(--pos-border)]">
          <div className="min-w-0">
            <h2 className="text-base font-semibold text-[var(--pos-text)] truncate">{title}</h2>
            {subtitle && (
              <p className="text-xs text-[var(--pos-text-muted)] mt-0.5 truncate">{subtitle}</p>
            )}
          </div>
          <button
            type="button"
            aria-label="Close sheet"
            onClick={onClose}
            className="h-9 w-9 rounded-lg flex items-center justify-center text-[var(--pos-text-muted)] hover:bg-slate-100 dark:hover:bg-slate-800 shrink-0 cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-4 py-4">{children}</div>
        {footer && (
          <div className="px-4 py-3 border-t border-[var(--pos-border)] bg-slate-50/70 dark:bg-slate-900/50">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
};

export const EmptyState: React.FC<{
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
  icon?: React.ReactNode;
}> = ({ title, description, actionLabel, onAction, icon }) => (
  <div className="flex flex-col items-center justify-center text-center py-12 px-6 border border-dashed border-[var(--pos-border)] rounded-xl bg-[var(--pos-surface)]/50">
    <div className="h-11 w-11 rounded-xl bg-slate-100 dark:bg-slate-800 text-[var(--pos-text-muted)] flex items-center justify-center mb-3">
      {icon || <Inbox className="w-5 h-5" />}
    </div>
    <h3 className="text-sm font-semibold text-[var(--pos-text)]">{title}</h3>
    <p className="text-xs text-[var(--pos-text-muted)] max-w-sm mt-1">{description}</p>
    {actionLabel && onAction && (
      <Button size="sm" className="mt-4" onClick={onAction}>
        {actionLabel}
      </Button>
    )}
  </div>
);

export const LoadingState: React.FC<{ rows?: number }> = ({ rows = 4 }) => (
  <div className="space-y-3">
    {Array.from({ length: rows }).map((_, i) => (
      <div
        key={i}
        className="h-12 rounded-lg bg-slate-200/70 dark:bg-slate-800/70 animate-pulse"
      />
    ))}
  </div>
);

export const ErrorState: React.FC<{
  title?: string;
  message: string;
  onRetry?: () => void;
}> = ({ title = 'Something went wrong', message, onRetry }) => (
  <div className="flex flex-col items-center justify-center text-center py-10 px-6 border border-[#DC2626]/30 rounded-xl bg-[#DC2626]/5">
    <AlertTriangle className="w-8 h-8 text-[#DC2626] mb-2" />
    <h3 className="text-sm font-semibold text-[var(--pos-text)]">{title}</h3>
    <p className="text-xs text-[var(--pos-text-muted)] max-w-md mt-1">{message}</p>
    {onRetry && (
      <Button variant="outline" size="sm" className="mt-4" onClick={onRetry}>
        Try Again
      </Button>
    )}
  </div>
);
