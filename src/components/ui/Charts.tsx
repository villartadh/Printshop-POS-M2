import React from 'react';
import { formatCurrency } from '../../shared/pricingEngine.ts';
import type { TaxAndCurrencyConfig } from '../../shared/types.ts';

export const SalesTrendChart: React.FC<{
  points: Array<{ label: string; value: number }>;
  taxCurrency: TaxAndCurrencyConfig;
}> = ({ points, taxCurrency }) => {
  if (points.length === 0) {
    return (
      <div className="h-44 flex items-center justify-center text-xs text-[var(--pos-text-muted)]">
        No sales trend data in selected window
      </div>
    );
  }

  const width = 520;
  const height = 160;
  const padX = 16;
  const padY = 18;
  const maxVal = Math.max(10, ...points.map((p) => p.value));
  const stepX = points.length > 1 ? (width - padX * 2) / (points.length - 1) : 0;

  const coords = points.map((p, idx) => {
    const x = points.length > 1 ? padX + idx * stepX : width / 2;
    const y = height - padY - (p.value / maxVal) * (height - padY * 2);
    return { x, y, ...p };
  });

  const linePath = coords
    .map((c, idx) => `${idx === 0 ? 'M' : 'L'} ${c.x.toFixed(1)} ${c.y.toFixed(1)}`)
    .join(' ');
  const areaPath = `${linePath} L ${coords[coords.length - 1].x.toFixed(1)} ${height - padY} L ${coords[0].x.toFixed(1)} ${height - padY} Z`;

  return (
    <div className="w-full">
      <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-44 overflow-visible">
        <defs>
          <linearGradient id="posTrendFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--pos-primary)" stopOpacity="0.22" />
            <stop offset="100%" stopColor="var(--pos-primary)" stopOpacity="0.01" />
          </linearGradient>
        </defs>
        {[0.25, 0.5, 0.75, 1].map((ratio) => {
          const y = height - padY - ratio * (height - padY * 2);
          return (
            <line
              key={ratio}
              x1={padX}
              x2={width - padX}
              y1={y}
              y2={y}
              stroke="var(--pos-border)"
              strokeDasharray="3 3"
              strokeWidth="1"
            />
          );
        })}
        {coords.length > 1 && <path d={areaPath} fill="url(#posTrendFill)" />}
        {coords.length > 1 && (
          <path
            d={linePath}
            fill="none"
            stroke="var(--pos-primary)"
            strokeWidth="2.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        )}
        {coords.map((c) => (
          <g key={c.label}>
            <circle
              cx={c.x}
              cy={c.y}
              r="3.5"
              fill="var(--pos-surface)"
              stroke="var(--pos-primary)"
              strokeWidth="2"
            >
              <title>{`${c.label}: ${formatCurrency(c.value, taxCurrency)}`}</title>
            </circle>
          </g>
        ))}
      </svg>
      <div className="flex items-center justify-between text-[11px] text-[var(--pos-text-muted)] px-2 tabular-nums">
        {points.map((p) => (
          <span key={p.label} className="truncate">
            {p.label}
          </span>
        ))}
      </div>
    </div>
  );
};

export const BreakdownBarList: React.FC<{
  items: Array<{ label: string; value: number; subtitle?: string; color?: string }>;
  taxCurrency: TaxAndCurrencyConfig;
}> = ({ items, taxCurrency }) => {
  if (items.length === 0) {
    return (
      <p className="text-xs text-[var(--pos-text-muted)] py-6 text-center">
        No breakdown records available yet.
      </p>
    );
  }
  const maxVal = Math.max(1, ...items.map((i) => i.value));
  return (
    <div className="space-y-3">
      {items.map((item) => {
        const pct = Math.min(100, Math.round((item.value / maxVal) * 100));
        return (
          <div key={item.label} className="space-y-1">
            <div className="flex items-center justify-between text-xs gap-2">
              <div className="min-w-0 truncate">
                <span className="font-medium text-[var(--pos-text)]">{item.label}</span>
                {item.subtitle && (
                  <span className="text-[var(--pos-text-muted)] ml-1.5">· {item.subtitle}</span>
                )}
              </div>
              <span className="font-mono-num font-semibold text-[var(--pos-text)] shrink-0">
                {formatCurrency(item.value, taxCurrency)}
              </span>
            </div>
            <div className="h-2 rounded-full bg-slate-100 dark:bg-slate-800 overflow-hidden">
              <div
                className="h-full rounded-full transition-all duration-200"
                style={{
                  width: `${pct}%`,
                  backgroundColor: item.color || 'var(--pos-primary)',
                }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
};
