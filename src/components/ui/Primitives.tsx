import React from 'react';
import {
  AlertCircle,
  CheckCircle2,
  Clock,
  Info,
  XCircle,
} from 'lucide-react';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';
  size?: 'sm' | 'md' | 'lg';
  loading?: boolean;
}

export const Button: React.FC<ButtonProps> = ({
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled,
  className = '',
  children,
  ...rest
}) => {
  const sizeCls =
    size === 'sm'
      ? 'min-h-[34px] px-3 py-1.5 text-xs'
      : size === 'lg'
        ? 'min-h-[46px] px-5 py-2.5 text-sm'
        : 'min-h-[40px] px-4 py-2 text-sm';

  const variantCls =
    variant === 'primary'
      ? 'bg-[var(--pos-primary)] hover:bg-[var(--pos-primary-hover)] text-white shadow-xs'
      : variant === 'secondary'
        ? 'bg-slate-100 hover:bg-slate-200 text-slate-900 dark:bg-slate-800 dark:hover:bg-slate-700 dark:text-slate-100'
        : variant === 'outline'
          ? 'border border-[var(--pos-border)] bg-[var(--pos-surface)] hover:bg-slate-50 dark:hover:bg-slate-800/70 text-[var(--pos-text)]'
          : variant === 'danger'
            ? 'bg-[#DC2626] hover:bg-[#B91C1C] text-white'
            : 'hover:bg-slate-100 dark:hover:bg-slate-800 text-[var(--pos-text)]';

  return (
    <button
      disabled={disabled || loading}
      style={{ borderRadius: 'var(--pos-radius)' }}
      className={`pos-btn-target inline-flex items-center justify-center gap-2 font-medium whitespace-nowrap shrink-0 transition-colors duration-150 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--pos-primary)] disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer ${sizeCls} ${variantCls} ${className}`}
      {...rest}
    >
      {loading && (
        <span className="h-3.5 w-3.5 rounded-full border-2 border-current border-t-transparent animate-spin" />
      )}
      {children}
    </button>
  );
};

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  hint?: string;
  error?: string;
  leftIcon?: React.ReactNode;
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ label, hint, error, leftIcon, className = '', id, ...rest }, ref) => {
    const inputId = id || (label ? `inp_${label.toLowerCase().replace(/\W+/g, '_')}` : undefined);
    return (
      <div className="w-full">
        {label && (
          <label
            htmlFor={inputId}
            className="block text-xs font-medium text-[var(--pos-text-muted)] mb-1.5"
          >
            {label}
          </label>
        )}
        <div className="relative flex items-center">
          {leftIcon && (
            <span className="absolute left-3 text-[var(--pos-text-muted)] pointer-events-none flex items-center">
              {leftIcon}
            </span>
          )}
          <input
            ref={ref}
            id={inputId}
            style={{ borderRadius: 'var(--pos-radius)' }}
            className={`w-full min-h-[40px] bg-[var(--pos-surface)] border border-[var(--pos-border)] text-sm text-[var(--pos-text)] placeholder:text-[var(--pos-text-muted)]/70 focus:outline-none focus:border-[var(--pos-primary)] focus:ring-2 focus:ring-[var(--pos-primary)]/15 transition-colors ${
              leftIcon ? 'pl-9 pr-3' : 'px-3'
            } py-2 ${error ? 'border-[#DC2626]' : ''} ${className}`}
            {...rest}
          />
        </div>
        {error && <p className="mt-1 text-xs text-[#DC2626]">{error}</p>}
        {!error && hint && <p className="mt-1 text-xs text-[var(--pos-text-muted)]">{hint}</p>}
      </div>
    );
  },
);
Input.displayName = 'Input';

interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  options: Array<{ value: string; label: string }>;
}

export const Select: React.FC<SelectProps> = ({
  label,
  options,
  className = '',
  id,
  ...rest
}) => {
  const selectId = id || (label ? `sel_${label.toLowerCase().replace(/\W+/g, '_')}` : undefined);
  return (
    <div className="w-full">
      {label && (
        <label
          htmlFor={selectId}
          className="block text-xs font-medium text-[var(--pos-text-muted)] mb-1.5"
        >
          {label}
        </label>
      )}
      <select
        id={selectId}
        style={{ borderRadius: 'var(--pos-radius)' }}
        className={`w-full min-h-[40px] bg-[var(--pos-surface)] border border-[var(--pos-border)] px-3 py-2 text-sm text-[var(--pos-text)] focus:outline-none focus:border-[var(--pos-primary)] focus:ring-2 focus:ring-[var(--pos-primary)]/15 transition-colors ${className}`}
        {...rest}
      >
        {options.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
    </div>
  );
};

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  padded?: boolean;
}

export const Card: React.FC<CardProps> = ({
  padded = true,
  className = '',
  children,
  ...rest
}) => {
  return (
    <div
      style={{ borderRadius: 'var(--pos-radius)' }}
      className={`bg-[var(--pos-surface)] border border-[var(--pos-border)] ${
        padded ? 'pos-card-pad' : ''
      } ${className}`}
      {...rest}
    >
      {children}
    </div>
  );
};

export type StatusTone = 'success' | 'warning' | 'error' | 'info' | 'neutral';

export const StatusIndicator: React.FC<{
  tone: StatusTone;
  label: string;
}> = ({ tone, label }) => {
  const icon =
    tone === 'success' ? (
      <CheckCircle2 className="w-3.5 h-3.5 shrink-0" />
    ) : tone === 'warning' ? (
      <Clock className="w-3.5 h-3.5 shrink-0" />
    ) : tone === 'error' ? (
      <XCircle className="w-3.5 h-3.5 shrink-0" />
    ) : tone === 'info' ? (
      <Info className="w-3.5 h-3.5 shrink-0" />
    ) : (
      <AlertCircle className="w-3.5 h-3.5 shrink-0" />
    );

  const colorCls =
    tone === 'success'
      ? 'text-[#16A34A] dark:text-emerald-400'
      : tone === 'warning'
        ? 'text-[#D97706] dark:text-amber-400'
        : tone === 'error'
          ? 'text-[#DC2626] dark:text-red-400'
          : tone === 'info'
            ? 'text-[#2563EB] dark:text-blue-400'
            : 'text-[var(--pos-text-muted)]';

  return (
    <span className={`inline-flex items-center gap-1.5 text-xs font-medium whitespace-nowrap ${colorCls}`}>
      {icon}
      <span>{label}</span>
    </span>
  );
};

export const QuantityStepper: React.FC<{
  value: number;
  onChange: (next: number) => void;
  min?: number;
  max?: number;
  compact?: boolean;
}> = ({ value, onChange, min = 1, max = 99999, compact = false }) => {
  const btnSize = compact ? 'h-8 w-8' : 'h-10 w-10';
  return (
    <div
      style={{ borderRadius: 'var(--pos-radius)' }}
      className="inline-flex items-center border border-[var(--pos-border)] bg-[var(--pos-surface)] overflow-hidden"
    >
      <button
        type="button"
        aria-label="Decrease quantity"
        onClick={() => onChange(Math.max(min, value - 1))}
        className={`${btnSize} flex items-center justify-center text-sm font-semibold text-[var(--pos-text)] hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer`}
      >
        −
      </button>
      <input
        type="number"
        min={min}
        max={max}
        value={value}
        onChange={(e) => {
          const parsed = parseInt(e.target.value, 10);
          if (!Number.isNaN(parsed)) {
            onChange(Math.min(max, Math.max(min, parsed)));
          }
        }}
        className="w-12 text-center text-sm font-semibold tabular-nums bg-transparent text-[var(--pos-text)] focus:outline-none"
      />
      <button
        type="button"
        aria-label="Increase quantity"
        onClick={() => onChange(Math.min(max, value + 1))}
        className={`${btnSize} flex items-center justify-center text-sm font-semibold text-[var(--pos-text)] hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer`}
      >
        +
      </button>
    </div>
  );
};
