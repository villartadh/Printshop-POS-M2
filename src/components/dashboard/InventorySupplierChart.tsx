import React, { useMemo, useState } from 'react';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { formatCurrency } from '../../shared/pricingEngine.ts';
import { extractSupplierInfo } from '../../shared/inventoryModel.ts';
import type { InventoryItem } from '../../types/inventory.ts';
import type { TaxAndCurrencyConfig } from '../../shared/types.ts';
import { Button } from '../ui/Primitives.tsx';

const SUPPLIER_SLICE_COLORS = [
  '#0D9488',
  '#2563EB',
  '#D97706',
  '#4F46E5',
  '#16A34A',
  '#0891B2',
];
const OTHER_SUPPLIER_COLOR = '#64748B';
const UNASSIGNED_SUPPLIER_COLOR = '#94A3B8';

export interface SupplierDistributionSlice {
  supplier: string;
  value: number;
  itemCount: number;
  percentage: number;
  color: string;
}

export interface InventorySupplierChartProps {
  items: InventoryItem[];
  taxCurrency: TaxAndCurrencyConfig;
  onManageInventory?: () => void;
}

interface CustomTooltipProps {
  active?: boolean;
  payload?: Array<{
    payload: SupplierDistributionSlice;
  }>;
  taxCurrency: TaxAndCurrencyConfig;
}

const SupplierPieTooltip: React.FC<CustomTooltipProps> = ({
  active,
  payload,
  taxCurrency,
}) => {
  if (!active || !payload || payload.length === 0) return null;
  const entry = payload[0]?.payload;
  if (!entry) return null;

  return (
    <div
      style={{ borderRadius: 'var(--pos-radius)' }}
      className="px-3 py-2 border border-[var(--pos-border)] bg-[var(--pos-surface)] shadow-md text-xs"
    >
      <div className="flex items-center gap-2 font-semibold text-[var(--pos-text)]">
        <span
          className="w-2.5 h-2.5 rounded-[2px] shrink-0"
          style={{ backgroundColor: entry.color }}
        />
        <span>{entry.supplier}</span>
      </div>
      <div className="mt-1 font-mono-num font-semibold text-[var(--pos-text)]">
        {formatCurrency(entry.value, taxCurrency)}
      </div>
      <div className="mt-0.5 text-[11px] text-[var(--pos-text-muted)] font-mono-num">
        {entry.percentage.toFixed(1)}% share · {entry.itemCount}{' '}
        {entry.itemCount === 1 ? 'SKU' : 'SKUs'}
      </div>
    </div>
  );
};

export const InventorySupplierChart: React.FC<InventorySupplierChartProps> = ({
  items,
  taxCurrency,
  onManageInventory,
}) => {
  const [activeIndex, setActiveIndex] = useState<number | null>(null);

  const { slices, totalValue, supplierCount, trackedSkuCount } = useMemo(() => {
    const grouped = new Map<string, { supplier: string; value: number; itemCount: number }>();

    for (const item of items) {
      const sup = extractSupplierInfo(item);
      const supplierName = sup.name.trim() || 'Unassigned';
      const stockQty = Math.max(0, Number(item.currentStock ?? item.quantityOnHand) || 0);
      const unitCost = Math.max(0, Number(item.unitCost) || 0);
      const stockValue = stockQty * unitCost;

      const key = supplierName.toLowerCase();
      const current = grouped.get(key) || {
        supplier: supplierName,
        value: 0,
        itemCount: 0,
      };
      current.value += stockValue;
      current.itemCount += 1;
      grouped.set(key, current);
    }

    const positiveEntries = Array.from(grouped.values())
      .map((entry) => ({
        ...entry,
        value: Number(entry.value.toFixed(2)),
      }))
      .filter((entry) => entry.value > 0)
      .sort((a, b) => {
        if (b.value !== a.value) return b.value - a.value;
        return a.supplier.localeCompare(b.supplier);
      });

    const total = Number(
      positiveEntries.reduce((sum, entry) => sum + entry.value, 0).toFixed(2),
    );

    const maxNamedSlices = 6;
    const rawSlices =
      positiveEntries.length > maxNamedSlices + 1
        ? [
            ...positiveEntries.slice(0, maxNamedSlices),
            positiveEntries.slice(maxNamedSlices).reduce(
              (acc, entry) => ({
                supplier: 'Other Suppliers',
                value: Number((acc.value + entry.value).toFixed(2)),
                itemCount: acc.itemCount + entry.itemCount,
              }),
              { supplier: 'Other Suppliers', value: 0, itemCount: 0 },
            ),
          ]
        : positiveEntries;

    let colorIdx = 0;
    const formattedSlices: SupplierDistributionSlice[] = rawSlices.map((entry) => {
      let color: string;
      if (entry.supplier === 'Other Suppliers') {
        color = OTHER_SUPPLIER_COLOR;
      } else if (entry.supplier === 'Unassigned') {
        color = UNASSIGNED_SUPPLIER_COLOR;
      } else {
        color = SUPPLIER_SLICE_COLORS[colorIdx % SUPPLIER_SLICE_COLORS.length];
        colorIdx += 1;
      }
      return {
        supplier: entry.supplier,
        value: entry.value,
        itemCount: entry.itemCount,
        percentage: total > 0 ? (entry.value / total) * 100 : 0,
        color,
      };
    });

    return {
      slices: formattedSlices,
      totalValue: total,
      supplierCount: positiveEntries.length,
      trackedSkuCount: items.length,
    };
  }, [items]);

  const activeSlice =
    activeIndex !== null && slices[activeIndex] ? slices[activeIndex] : null;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 className="text-sm font-semibold text-[var(--pos-text)]">
            Inventory Value by Supplier
          </h2>
          <p className="text-xs text-[var(--pos-text-muted)] mt-0.5 font-mono-num">
            {supplierCount} active {supplierCount === 1 ? 'supplier' : 'suppliers'} ·{' '}
            {trackedSkuCount} tracked {trackedSkuCount === 1 ? 'SKU' : 'SKUs'} · Total{' '}
            {formatCurrency(totalValue, taxCurrency)}
          </p>
        </div>
        {onManageInventory && (
          <Button variant="ghost" size="sm" onClick={onManageInventory}>
            Manage Stock
          </Button>
        )}
      </div>

      {slices.length === 0 ? (
        <div className="py-10 text-center space-y-2">
          <p className="text-sm font-medium text-[var(--pos-text)]">
            No stocked inventory valuation available
          </p>
          <p className="text-xs text-[var(--pos-text-muted)]">
            Add inventory items with positive stock counts and unit costs to view supplier
            distribution.
          </p>
          {onManageInventory && (
            <div className="pt-1">
              <Button variant="outline" size="sm" onClick={onManageInventory}>
                Open Inventory
              </Button>
            </div>
          )}
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 items-center">
          <div className="lg:col-span-5 relative h-[250px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={slices}
                  dataKey="value"
                  nameKey="supplier"
                  cx="50%"
                  cy="50%"
                  innerRadius={62}
                  outerRadius={96}
                  paddingAngle={slices.length > 1 ? 2 : 0}
                  stroke="var(--pos-surface)"
                  strokeWidth={2}
                  onMouseEnter={(_, idx) => setActiveIndex(idx)}
                  onMouseLeave={() => setActiveIndex(null)}
                >
                  {slices.map((entry, idx) => (
                    <Cell
                      key={entry.supplier}
                      fill={entry.color}
                      fillOpacity={activeIndex === null || activeIndex === idx ? 1 : 0.4}
                      className="transition-opacity duration-150 outline-none cursor-pointer"
                    />
                  ))}
                </Pie>
                <Tooltip
                  content={<SupplierPieTooltip taxCurrency={taxCurrency} />}
                />
              </PieChart>
            </ResponsiveContainer>

            <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center text-center px-6">
              <span className="text-[11px] font-medium text-[var(--pos-text-muted)] truncate max-w-[112px]">
                {activeSlice ? activeSlice.supplier : 'Total Value'}
              </span>
              <span className="text-sm font-bold font-mono-num text-[var(--pos-text)] mt-0.5">
                {formatCurrency(activeSlice ? activeSlice.value : totalValue, taxCurrency)}
              </span>
              {activeSlice && (
                <span className="text-[11px] font-mono-num text-[var(--pos-text-muted)]">
                  {activeSlice.percentage.toFixed(1)}%
                </span>
              )}
            </div>
          </div>

          <div className="lg:col-span-7 divide-y divide-[var(--pos-border)]">
            {slices.map((entry, idx) => {
              const isHighlighted = activeIndex === idx;
              return (
                <div
                  key={entry.supplier}
                  onMouseEnter={() => setActiveIndex(idx)}
                  onMouseLeave={() => setActiveIndex(null)}
                  className={`py-2.5 px-2 flex items-center justify-between gap-3 transition-colors duration-150 ${
                    isHighlighted ? 'bg-slate-50 dark:bg-slate-800/50' : ''
                  }`}
                >
                  <div className="flex items-center gap-2.5 min-w-0">
                    <span
                      className="w-2.5 h-2.5 rounded-[2px] shrink-0"
                      style={{ backgroundColor: entry.color }}
                    />
                    <div className="min-w-0">
                      <div className="text-sm font-medium text-[var(--pos-text)] truncate">
                        {entry.supplier}
                      </div>
                      <div className="text-[11px] text-[var(--pos-text-muted)] font-mono-num">
                        {entry.itemCount} {entry.itemCount === 1 ? 'SKU' : 'SKUs'}
                      </div>
                    </div>
                  </div>

                  <div className="text-right shrink-0">
                    <div className="text-sm font-semibold font-mono-num text-[var(--pos-text)]">
                      {formatCurrency(entry.value, taxCurrency)}
                    </div>
                    <div className="text-[11px] font-mono-num text-[var(--pos-text-muted)]">
                      {entry.percentage.toFixed(1)}%
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};
