import React, { useMemo, useState } from 'react';
import { Archive, Edit3, Plus, Star, Trash2 } from 'lucide-react';
import { useApp } from '../../context/AppContext.tsx';
import { formatCurrency } from '../../shared/pricingEngine.ts';
import type { PricingMethod, PrintService } from '../../shared/types.ts';
import { Button, Card, Input, Select, StatusIndicator } from '../../components/ui/Primitives.tsx';
import { DataTable } from '../../components/ui/DataTable.tsx';
import { Modal } from '../../components/ui/Overlays.tsx';
import { AVAILABLE_SERVICE_ICONS, ServiceIcon } from '../../components/ui/ServiceIcon.tsx';

const PRICING_METHODS: Array<{ value: PricingMethod; label: string }> = [
  { value: 'per_page', label: 'Per Page' },
  { value: 'per_sheet', label: 'Per Sheet' },
  { value: 'per_copy', label: 'Per Copy' },
  { value: 'per_piece', label: 'Per Piece' },
  { value: 'per_set', label: 'Per Set' },
  { value: 'per_sqft', label: 'Per Square Foot' },
  { value: 'per_linear_ft', label: 'Per Linear Foot' },
  { value: 'per_meter', label: 'Per Meter' },
  { value: 'per_hour', label: 'Per Hour' },
  { value: 'fixed', label: 'Fixed Service Price' },
  { value: 'tiered_bulk', label: 'Bulk Tiered Pricing' },
  { value: 'formula', label: 'Formula-Based Pricing' },
];

export const ServicesModule: React.FC = () => {
  const {
    settings,
    categories,
    services,
    inventory,
    createCategory,
    deleteCategory,
    saveService,
    deleteService,
    session,
  } = useApp();

  const taxCurr = settings.taxAndCurrency;
  const canEdit = session?.user.role === 'admin' || session?.user.role === 'manager';

  const [tab, setTab] = useState<'services' | 'categories'>('services');
  const [search, setSearch] = useState('');
  const [showArchived, setShowArchived] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [newCategoryName, setNewCategoryName] = useState('');

  // Service Form State
  const [editingId, setEditingId] = useState<string | undefined>(undefined);
  const [name, setName] = useState('');
  const [sku, setSku] = useState('');
  const [categoryId, setCategoryId] = useState(categories[0]?.id || '');
  const [description, setDescription] = useState('');
  const [iconName, setIconName] = useState('Printer');
  const [pricingMethod, setPricingMethod] = useState<PricingMethod>('per_page');
  const [basePrice, setBasePrice] = useState('5');
  const [unitLabel, setUnitLabel] = useState('page');
  const [costEstimate, setCostEstimate] = useState('1');
  const [formulaExpression, setFormulaExpression] = useState('');
  const [requiresConfiguration, setRequiresConfiguration] = useState(true);
  const [requiresProductionJob, setRequiresProductionJob] = useState(false);
  const [allowCustomDimensions, setAllowCustomDimensions] = useState(false);
  const [allowPagesAndCopies, setAllowPagesAndCopies] = useState(true);
  const [allowRushFee, setAllowRushFee] = useState(true);
  const [rushFeePercent, setRushFeePercent] = useState('25');
  const [isFavorite, setIsFavorite] = useState(false);
  const [consumableId, setConsumableId] = useState('');
  const [consumableQty, setConsumableQty] = useState('1');

  const openNewEditor = () => {
    setEditingId(undefined);
    setName('');
    setSku(`SRV-${Date.now().toString().slice(-4)}`);
    setCategoryId(categories[0]?.id || '');
    setDescription('');
    setIconName('Printer');
    setPricingMethod('per_page');
    setBasePrice('5');
    setUnitLabel('page');
    setCostEstimate('1');
    setFormulaExpression('');
    setRequiresConfiguration(true);
    setRequiresProductionJob(false);
    setAllowCustomDimensions(false);
    setAllowPagesAndCopies(true);
    setAllowRushFee(true);
    setRushFeePercent('25');
    setIsFavorite(false);
    setConsumableId('');
    setConsumableQty('1');
    setEditorOpen(true);
  };

  const openEditEditor = (s: PrintService) => {
    setEditingId(s.id);
    setName(s.name);
    setSku(s.sku);
    setCategoryId(s.categoryId);
    setDescription(s.description);
    setIconName(s.iconName);
    setPricingMethod(s.pricingMethod);
    setBasePrice(String(s.basePrice));
    setUnitLabel(s.unitLabel);
    setCostEstimate(String(s.costEstimate));
    setFormulaExpression(s.formulaExpression || '');
    setRequiresConfiguration(s.requiresConfiguration);
    setRequiresProductionJob(s.requiresProductionJob);
    setAllowCustomDimensions(s.allowCustomDimensions);
    setAllowPagesAndCopies(s.allowPagesAndCopies);
    setAllowRushFee(s.allowRushFee);
    setRushFeePercent(String(s.rushFeePercent));
    setIsFavorite(s.isFavorite);
    setConsumableId(s.consumables[0]?.inventoryItemId || '');
    setConsumableQty(String(s.consumables[0]?.quantityPerUnit || 1));
    setEditorOpen(true);
  };

  const handleSaveService = async (e: React.FormEvent) => {
    e.preventDefault();
    const existing = services.find((s) => s.id === editingId);
    await saveService({
      id: editingId,
      name,
      sku,
      categoryId,
      description,
      iconName,
      pricingMethod,
      basePrice: Number(basePrice) || 0,
      unitLabel,
      costEstimate: Number(costEstimate) || 0,
      formulaExpression,
      requiresConfiguration,
      requiresProductionJob,
      allowCustomDimensions,
      allowPagesAndCopies,
      allowRushFee,
      rushFeePercent: Number(rushFeePercent) || 25,
      defaultDesignFee: existing?.defaultDesignFee || 0,
      bulkTiers: existing?.bulkTiers || [],
      attributeGroups: existing?.attributeGroups || [],
      consumables: consumableId
        ? [{ inventoryItemId: consumableId, quantityPerUnit: Number(consumableQty) || 1 }]
        : [],
      isFavorite,
      isArchived: existing?.isArchived || false,
    });
    setEditorOpen(false);
  };

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return services
      .filter((s) => (showArchived ? true : !s.isArchived))
      .filter(
        (s) =>
          !q ||
          s.name.toLowerCase().includes(q) ||
          s.sku.toLowerCase().includes(q) ||
          s.description.toLowerCase().includes(q),
      );
  }, [services, showArchived, search]);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[var(--pos-text)]">Printing Services & Pricing</h1>
          <p className="text-xs text-[var(--pos-text-muted)]">
            Configure service pricing rules, bulk tiers, formulas, and material consumption
          </p>
        </div>

        <div className="flex items-center gap-2">
          <div className="flex items-center gap-1 p-1 bg-slate-100 dark:bg-slate-800 rounded-lg">
            <button
              type="button"
              onClick={() => setTab('services')}
              className={`px-3 py-1 text-xs font-medium rounded-md cursor-pointer ${
                tab === 'services'
                  ? 'bg-[var(--pos-surface)] text-[var(--pos-text)] shadow-xs'
                  : 'text-[var(--pos-text-muted)]'
              }`}
            >
              Services ({services.length})
            </button>
            <button
              type="button"
              onClick={() => setTab('categories')}
              className={`px-3 py-1 text-xs font-medium rounded-md cursor-pointer ${
                tab === 'categories'
                  ? 'bg-[var(--pos-surface)] text-[var(--pos-text)] shadow-xs'
                  : 'text-[var(--pos-text-muted)]'
              }`}
            >
              Categories ({categories.length})
            </button>
          </div>
          {canEdit && tab === 'services' && (
            <Button size="sm" onClick={openNewEditor}>
              <Plus className="w-4 h-4" />
              Add Service
            </Button>
          )}
        </div>
      </div>

      {tab === 'services' ? (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex-1 min-w-[220px]">
              <Input
                placeholder="Search service name or SKU..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
            <label className="flex items-center gap-2 text-xs text-[var(--pos-text-muted)] cursor-pointer">
              <input
                type="checkbox"
                checked={showArchived}
                onChange={(e) => setShowArchived(e.target.checked)}
                className="h-4 w-4 accent-[var(--pos-primary)]"
              />
              Show archived services
            </label>
          </div>

          <DataTable
            rows={filtered}
            rowKey={(s) => s.id}
            columns={[
              {
                key: 'name',
                label: 'Service',
                render: (s) => (
                  <div className="flex items-center gap-2.5">
                    <div className="h-8 w-8 rounded-lg bg-[var(--pos-primary)]/10 text-[var(--pos-primary)] flex items-center justify-center shrink-0">
                      <ServiceIcon name={s.iconName} className="w-4 h-4" />
                    </div>
                    <div>
                      <div className="text-sm font-semibold text-[var(--pos-text)] flex items-center gap-1.5">
                        <span>{s.name}</span>
                        {s.isFavorite && <Star className="w-3 h-3 text-[#D97706] fill-current" />}
                      </div>
                      <div className="text-[11px] text-[var(--pos-text-muted)]">
                        {s.sku} · {categories.find((c) => c.id === s.categoryId)?.name || 'Uncategorized'}
                      </div>
                    </div>
                  </div>
                ),
              },
              {
                key: 'method',
                label: 'Pricing Model',
                hideOnMobile: true,
                render: (s) => (
                  <span className="text-xs capitalize text-[var(--pos-text-muted)]">
                    {s.pricingMethod.replace(/_/g, ' ')}
                  </span>
                ),
              },
              {
                key: 'price',
                label: 'Base Rate',
                align: 'right',
                render: (s) => (
                  <span className="font-mono-num font-semibold">
                    {formatCurrency(s.basePrice, taxCurr)} / {s.unitLabel}
                  </span>
                ),
              },
              {
                key: 'status',
                label: 'Status',
                hideOnMobile: true,
                render: (s) => (
                  <StatusIndicator
                    tone={s.isArchived ? 'neutral' : 'success'}
                    label={s.isArchived ? 'Archived' : 'Active'}
                  />
                ),
              },
              {
                key: 'actions',
                label: 'Actions',
                align: 'right',
                render: (s) =>
                  canEdit ? (
                    <div className="inline-flex items-center gap-1">
                      <button
                        type="button"
                        onClick={() => openEditEditor(s)}
                        className="p-1.5 rounded hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
                        title="Edit"
                      >
                        <Edit3 className="w-4 h-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() =>
                          void saveService({ ...s, isArchived: !s.isArchived })
                        }
                        className="p-1.5 rounded hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
                        title={s.isArchived ? 'Restore' : 'Archive'}
                      >
                        <Archive className="w-4 h-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => void deleteService(s.id)}
                        className="p-1.5 rounded text-[#DC2626] hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
                        title="Delete"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  ) : (
                    <span className="text-xs text-[var(--pos-text-muted)]">View only</span>
                  ),
              },
            ]}
          />
        </>
      ) : (
        <Card className="space-y-4">
          {canEdit && (
            <form
              onSubmit={async (e) => {
                e.preventDefault();
                if (!newCategoryName.trim()) return;
                await createCategory(newCategoryName);
                setNewCategoryName('');
              }}
              className="flex gap-2"
            >
              <Input
                placeholder="New category name (e.g. Sublimation)"
                value={newCategoryName}
                onChange={(e) => setNewCategoryName(e.target.value)}
              />
              <Button type="submit">Add Category</Button>
            </form>
          )}
          <div className="divide-y divide-[var(--pos-border)]">
            {categories.map((c) => (
              <div key={c.id} className="py-2.5 flex items-center justify-between">
                <div>
                  <span className="text-sm font-medium text-[var(--pos-text)]">{c.name}</span>
                  <span className="text-xs text-[var(--pos-text-muted)] ml-2">
                    · {services.filter((s) => s.categoryId === c.id).length} service(s)
                  </span>
                </div>
                {canEdit && (
                  <button
                    type="button"
                    onClick={() => void deleteCategory(c.id)}
                    className="p-1.5 rounded text-[#DC2626] hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                )}
              </div>
            ))}
          </div>
        </Card>
      )}

      {/* Service Editor Modal */}
      <Modal
        open={editorOpen}
        onClose={() => setEditorOpen(false)}
        title={editingId ? 'Edit Printing Service' : 'New Printing Service'}
        maxWidth="lg"
      >
        <form onSubmit={handleSaveService} className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Input
              label="Service Name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
            <Input label="SKU / Barcode" value={sku} onChange={(e) => setSku(e.target.value)} required />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <Select
              label="Category"
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
              options={categories.map((c) => ({ value: c.id, label: c.name }))}
            />
            <Select
              label="Pricing Method"
              value={pricingMethod}
              onChange={(e) => setPricingMethod(e.target.value as PricingMethod)}
              options={PRICING_METHODS}
            />
            <Select
              label="Icon"
              value={iconName}
              onChange={(e) => setIconName(e.target.value)}
              options={AVAILABLE_SERVICE_ICONS.map((ic) => ({ value: ic, label: ic }))}
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <Input
              label={`Base Price (${taxCurr.currencySymbol})`}
              type="number"
              min="0"
              step="0.01"
              value={basePrice}
              onChange={(e) => setBasePrice(e.target.value)}
              required
            />
            <Input
              label="Unit Label"
              placeholder="page, sq ft, pc"
              value={unitLabel}
              onChange={(e) => setUnitLabel(e.target.value)}
              required
            />
            <Input
              label={`Estimated Cost (${taxCurr.currencySymbol})`}
              type="number"
              min="0"
              step="0.01"
              value={costEstimate}
              onChange={(e) => setCostEstimate(e.target.value)}
            />
          </div>

          {pricingMethod === 'formula' && (
            <Input
              label="Custom Pricing Formula"
              placeholder="e.g. base * areaSqFt * copies + designFee"
              hint="Available variables: base, qty, pages, copies, areaSqFt, linearFt, linearMeters, attrDelta, attrMult, designFee, laborFee"
              value={formulaExpression}
              onChange={(e) => setFormulaExpression(e.target.value)}
            />
          )}

          <Input
            label="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Select
              label="Consumable Deduction Binding (Optional)"
              value={consumableId}
              onChange={(e) => setConsumableId(e.target.value)}
              options={[
                { value: '', label: 'None' },
                ...inventory.map((i) => ({ value: i.id, label: `${i.name} (${i.unit})` })),
              ]}
            />
            {consumableId && (
              <Input
                label="Quantity Consumed per Unit"
                type="number"
                min="0.1"
                step="0.1"
                value={consumableQty}
                onChange={(e) => setConsumableQty(e.target.value)}
              />
            )}
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5 text-xs">
            {[
              { label: 'Show Configurator on Tap', checked: requiresConfiguration, set: setRequiresConfiguration },
              { label: 'Create Production Job', checked: requiresProductionJob, set: setRequiresProductionJob },
              { label: 'Custom Width × Height', checked: allowCustomDimensions, set: setAllowCustomDimensions },
              { label: 'Pages & Copies Controls', checked: allowPagesAndCopies, set: setAllowPagesAndCopies },
              { label: 'Allow Rush Fee', checked: allowRushFee, set: setAllowRushFee },
              { label: 'Pin to Favorites', checked: isFavorite, set: setIsFavorite },
            ].map((flag) => (
              <label
                key={flag.label}
                className="flex items-center gap-2 p-2.5 rounded-lg border border-[var(--pos-border)] cursor-pointer"
              >
                <input
                  type="checkbox"
                  checked={flag.checked}
                  onChange={(e) => flag.set(e.target.checked)}
                  className="h-4 w-4 accent-[var(--pos-primary)]"
                />
                <span className="text-[var(--pos-text)]">{flag.label}</span>
              </label>
            ))}
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" type="button" onClick={() => setEditorOpen(false)}>
              Cancel
            </Button>
            <Button type="submit">Save Service</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
