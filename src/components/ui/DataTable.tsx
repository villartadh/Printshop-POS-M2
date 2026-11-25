import React from 'react';
import { ArrowDown, ArrowUp } from 'lucide-react';
import { EmptyState } from './Overlays.tsx';

export interface DataTableColumn<T> {
  key: string;
  label: React.ReactNode;
  align?: 'left' | 'right';
  sortable?: boolean;
  hideOnMobile?: boolean;
  headerClassName?: string;
  cellClassName?: string;
  stopRowClick?: boolean;
  render: (row: T) => React.ReactNode;
}

interface DataTableProps<T> {
  rows: T[];
  columns: DataTableColumn<T>[];
  rowKey: (row: T) => string;
  onRowClick?: (row: T) => void;
  sortKey?: string;
  sortDir?: 'asc' | 'desc';
  onSortChange?: (key: string) => void;
  emptyTitle?: string;
  emptyDescription?: string;
  emptyActionLabel?: string;
  onEmptyAction?: () => void;
  rowClassName?: (row: T) => string;
}

export function DataTable<T>({
  rows,
  columns,
  rowKey,
  onRowClick,
  sortKey,
  sortDir,
  onSortChange,
  emptyTitle = 'No records found',
  emptyDescription = 'Adjust your filters or add a new entry to get started.',
  emptyActionLabel,
  onEmptyAction,
  rowClassName,
}: DataTableProps<T>) {
  if (rows.length === 0) {
    return (
      <EmptyState
        title={emptyTitle}
        description={emptyDescription}
        actionLabel={emptyActionLabel}
        onAction={onEmptyAction}
      />
    );
  }

  return (
    <div
      style={{ borderRadius: 'var(--pos-radius)' }}
      className="w-full overflow-x-auto border border-[var(--pos-border)] bg-[var(--pos-surface)]"
    >
      <table className="w-full border-collapse text-left">
        <thead>
          <tr className="border-b border-[var(--pos-border)] bg-slate-50/80 dark:bg-slate-900/50">
            {columns.map((col) => (
              <th
                key={col.key}
                className={`px-3.5 py-2.5 text-xs font-semibold text-[var(--pos-text-muted)] whitespace-nowrap ${
                  col.align === 'right' ? 'text-right' : 'text-left'
                } ${col.hideOnMobile ? 'hidden md:table-cell' : ''} ${col.headerClassName || ''}`}
              >
                {col.sortable && onSortChange ? (
                  <button
                    type="button"
                    onClick={() => onSortChange(col.key)}
                    className="inline-flex items-center gap-1 hover:text-[var(--pos-text)] cursor-pointer"
                  >
                    <span>{col.label}</span>
                    {sortKey === col.key &&
                      (sortDir === 'asc' ? (
                        <ArrowUp className="w-3 h-3" />
                      ) : (
                        <ArrowDown className="w-3 h-3" />
                      ))}
                  </button>
                ) : (
                  col.label
                )}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-[var(--pos-border)]">
          {rows.map((row) => {
            const customRowCls = rowClassName ? rowClassName(row) : '';
            const hoverCls = customRowCls
              ? 'cursor-pointer transition-colors'
              : 'hover:bg-slate-50 dark:hover:bg-slate-800/50 cursor-pointer transition-colors';
            return (
              <tr
                key={rowKey(row)}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                className={`text-sm text-[var(--pos-text)] ${
                  onRowClick ? hoverCls : 'transition-colors'
                } ${customRowCls}`}
              >
              {columns.map((col) => (
                <td
                  key={col.key}
                  onClick={col.stopRowClick ? (e) => e.stopPropagation() : undefined}
                  className={`px-3.5 py-2.5 align-middle ${
                    col.align === 'right' ? 'text-right tabular-nums' : 'text-left'
                  } ${col.hideOnMobile ? 'hidden md:table-cell' : ''} ${col.cellClassName || ''}`}
                >
                  {col.render(row)}
                </td>
              ))}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
