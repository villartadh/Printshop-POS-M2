import React, { useEffect, useMemo, useState } from 'react';
import { useApp } from '../../context/AppContext.tsx';
import {
  calculateServicePrice,
  formatCurrency,
  getDefaultServiceSelection,
} from '../../shared/pricingEngine.ts';
import type { PrintService, ServiceConfigSelection } from '../../shared/types.ts';
import { Button, Input, QuantityStepper, Select } from '../../components/ui/Primitives.tsx';
import { BottomSheet, Modal } from '../../components/ui/Overlays.tsx';

interface ServiceConfiguratorProps {
  open: boolean;
  service: PrintService | null;
  onClose: () => void;
  onConfirm: (service: PrintService, config: ServiceConfigSelection) => void;
  isMobileLayout: boolean;
}

export const ServiceConfigurator: React.FC<ServiceConfiguratorProps> = ({
  open,
  service,
  onClose,
  onConfirm,
  isMobileLayout,
}) => {
  const { settings } = useApp();
  const [config, setConfig] = useState<ServiceConfigSelection | null>(null);

  useEffect(() => {
    if (service && open) {
      setConfig(getDefaultServiceSelection(service));
    }
  }, [service, open]);

  const calc = useMemo(() => {
    if (!service || !config) return null;
    return calculateServicePrice(service, config, settings.taxAndCurrency);
  }, [service, config, settings.taxAndCurrency]);

  if (!open || !service || !config || !calc) return null;

  const content = (
    <div className="space-y-4">
      {service.allowPagesAndCopies ? (
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-medium text-[var(--pos-text-muted)] mb-1.5">
              Pages
            </label>
            <QuantityStepper
              value={config.pages}
              onChange={(pages) => setConfig({ ...config, pages })}
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-[var(--pos-text-muted)] mb-1.5">
              Copies
            </label>
            <QuantityStepper
              value={config.copies}
              onChange={(copies) => setConfig({ ...config, copies })}
            />
          </div>
        </div>
      ) : (
        <div className="flex items-center justify-between gap-4">
          <span className="text-sm font-medium text-[var(--pos-text)]">Quantity</span>
          <QuantityStepper
            value={config.quantity}
            onChange={(quantity) => setConfig({ ...config, quantity })}
          />
        </div>
      )}

      {service.allowCustomDimensions && (
        <div className="grid grid-cols-3 gap-2.5">
          <Input
            label="Width"
            type="number"
            step="0.1"
            min="0.1"
            value={config.width}
            onChange={(e) => setConfig({ ...config, width: Math.max(0.1, Number(e.target.value) || 0.1) })}
          />
          <Input
            label="Height"
            type="number"
            step="0.1"
            min="0.1"
            value={config.height}
            onChange={(e) => setConfig({ ...config, height: Math.max(0.1, Number(e.target.value) || 0.1) })}
          />
          <Select
            label="Unit"
            value={config.dimensionUnit}
            onChange={(e) =>
              setConfig({
                ...config,
                dimensionUnit: e.target.value as ServiceConfigSelection['dimensionUnit'],
              })
            }
            options={[
              { value: 'ft', label: 'Feet (ft)' },
              { value: 'in', label: 'Inches (in)' },
              { value: 'cm', label: 'cm' },
              { value: 'm', label: 'Meters (m)' },
            ]}
          />
        </div>
      )}

      {service.attributeGroups.map((group) => (
        <div key={group.key}>
          <label className="block text-xs font-medium text-[var(--pos-text-muted)] mb-1.5">
            {group.label}
          </label>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
            {group.options.map((opt) => {
              const active = config.selectedAttributes[group.key] === opt.id;
              return (
                <button
                  key={opt.id}
                  type="button"
                  onClick={() =>
                    setConfig({
                      ...config,
                      selectedAttributes: {
                        ...config.selectedAttributes,
                        [group.key]: opt.id,
                      },
                    })
                  }
                  style={{ borderRadius: 'var(--pos-radius)' }}
                  className={`px-3 py-2 text-left border text-xs transition-colors cursor-pointer ${
                    active
                      ? 'border-[var(--pos-primary)] bg-[var(--pos-primary)]/10 text-[var(--pos-text)] font-semibold'
                      : 'border-[var(--pos-border)] bg-[var(--pos-surface)] text-[var(--pos-text-muted)] hover:border-slate-300'
                  }`}
                >
                  <div className="truncate">{opt.label}</div>
                  {opt.priceDelta !== 0 && (
                    <div className="text-[11px] font-mono-num text-[var(--pos-primary)] mt-0.5">
                      {opt.priceDelta > 0 ? '+' : ''}
                      {formatCurrency(opt.priceDelta, settings.taxAndCurrency)}
                    </div>
                  )}
                </button>
              );
            })}
          </div>
        </div>
      ))}

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <Input
          label={`Design Fee (${settings.taxAndCurrency.currencySymbol})`}
          type="number"
          min="0"
          step="1"
          value={config.designFee}
          onChange={(e) => setConfig({ ...config, designFee: Math.max(0, Number(e.target.value) || 0) })}
        />
        <Input
          label={`Additional Labor (${settings.taxAndCurrency.currencySymbol})`}
          type="number"
          min="0"
          step="1"
          value={config.laborFee}
          onChange={(e) => setConfig({ ...config, laborFee: Math.max(0, Number(e.target.value) || 0) })}
        />
      </div>

      {service.allowRushFee && (
        <label className="flex items-center justify-between gap-3 p-3 rounded-lg border border-[var(--pos-border)] cursor-pointer">
          <div>
            <div className="text-sm font-medium text-[var(--pos-text)]">Rush Production Service</div>
            <div className="text-xs text-[var(--pos-text-muted)]">
              Adds {service.rushFeePercent}% priority surcharge
            </div>
          </div>
          <input
            type="checkbox"
            checked={config.isRush}
            onChange={(e) => setConfig({ ...config, isRush: e.target.checked })}
            className="h-4 w-4 accent-[var(--pos-primary)]"
          />
        </label>
      )}

      <Input
        label="Job Instructions / Finishing Notes"
        placeholder="e.g. Borderless, matte finish, 4 eyelets..."
        value={config.notes}
        onChange={(e) => setConfig({ ...config, notes: e.target.value })}
      />

      <div className="p-3.5 rounded-xl bg-slate-50 dark:bg-slate-900/60 border border-[var(--pos-border)] space-y-1.5 text-xs">
        <div className="flex justify-between text-[var(--pos-text-muted)]">
          <span>
            Computed Rate ({calc.effectiveQuantity} {service.unitLabel})
          </span>
          <span className="font-mono-num">
            {formatCurrency(calc.unitPrice, settings.taxAndCurrency)} / {service.unitLabel}
          </span>
        </div>
        {calc.rushAmount > 0 && (
          <div className="flex justify-between text-[var(--pos-text-muted)]">
            <span>Rush Surcharge</span>
            <span className="font-mono-num">
              {formatCurrency(calc.rushAmount, settings.taxAndCurrency)}
            </span>
          </div>
        )}
        {calc.extraFees > 0 && (
          <div className="flex justify-between text-[var(--pos-text-muted)]">
            <span>Design & Labor Fees</span>
            <span className="font-mono-num">
              {formatCurrency(calc.extraFees, settings.taxAndCurrency)}
            </span>
          </div>
        )}
        <div className="flex justify-between items-center pt-1.5 border-t border-[var(--pos-border)] text-sm font-bold text-[var(--pos-text)]">
          <span>Line Total</span>
          <span className="font-mono-num text-[var(--pos-primary)]">
            {formatCurrency(calc.lineSubtotal, settings.taxAndCurrency)}
          </span>
        </div>
      </div>
    </div>
  );

  const footer = (
    <div className="w-full flex items-center justify-end gap-2.5">
      <Button variant="secondary" onClick={onClose}>
        Cancel
      </Button>
      <Button
        onClick={() => {
          onConfirm(service, config);
          onClose();
        }}
      >
        Add to Order · {formatCurrency(calc.lineSubtotal, settings.taxAndCurrency)}
      </Button>
    </div>
  );

  if (isMobileLayout) {
    return (
      <BottomSheet
        open={open}
        onClose={onClose}
        title={service.name}
        subtitle={`${service.sku} · Configure print specifications`}
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
      title={service.name}
      subtitle={`${service.sku} · Configure print specifications`}
      maxWidth="md"
      footer={footer}
    >
      {content}
    </Modal>
  );
};
