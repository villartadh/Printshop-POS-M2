import { DEFAULT_SETTINGS, DEFAULT_MODULE_ORDER } from './defaults.ts';

const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const finite = (v: unknown, min: number, max: number) => typeof v === 'number' && Number.isFinite(v) && v >= min && v <= max;
const oneOf = (v: unknown, values: readonly string[]) => typeof v === 'string' && values.includes(v);
const short = (v: unknown, max = 2000) => typeof v === 'string' && v.length <= max;

export function validateSettings(value: unknown): string | null {
  if (!object(value)) return 'Settings must be an object.';
  const s = value;
  for (const key of ['branding','tokens','posLayout','receiptTemplate','taxAndCurrency'] as const) {
    const section = s[key];
    if (!object(section)) return 'Missing or invalid settings section: '+key;
    for (const [field, fallback] of Object.entries(DEFAULT_SETTINGS[key])) {
      const v = section[field];
      if (typeof v !== typeof fallback || (typeof v === 'number' && !Number.isFinite(v)) ||
          (typeof v === 'string' && !short(v, field === 'logoDataUrl' ? 700000 : 2000))) {
        return 'Invalid settings field: '+key+'.'+field;
      }
    }
  }
  if (!oneOf(s.themeMode,['light','dark','system']) || !oneOf(s.density,['comfortable','compact','touch']) ||
      !oneOf(s.defaultLandingModule,DEFAULT_MODULE_ORDER) || typeof s.demoModeActive !== 'boolean') return 'Invalid general preferences.';
  for (const key of ['moduleOrder','hiddenModules'] as const) {
    const list = s[key];
    if (!Array.isArray(list) || !list.every(v=>oneOf(v,DEFAULT_MODULE_ORDER)) || new Set(list).size!==list.length) return 'Invalid module configuration.';
  }
  if (!(s.moduleOrder as unknown[]).length || (s.hiddenModules as unknown[]).some(v=>v==='pos'||v==='settings')) return 'Required modules cannot be hidden.';
  if (s.shellPreferences !== undefined && (!object(s.shellPreferences) || typeof s.shellPreferences.sidebarCollapsed !== 'boolean')) return 'Invalid sidebar preference.';
  const tax = s.taxAndCurrency as Record<string,unknown>;
  if (typeof tax.currencyCode !== 'string' || !/^[A-Z]{3}$/.test(tax.currencyCode) || !String(tax.currencySymbol).trim() ||
      !finite(tax.taxRatePercent,0,100) || !finite(tax.decimalPlaces,0,2) || !Number.isInteger(tax.decimalPlaces) ||
      !oneOf(tax.roundingMode,['none','nearest_0_25','nearest_1'])) return 'Invalid tax or currency configuration.';
  const receipt = s.receiptTemplate as Record<string,unknown>;
  if (!oneOf(receipt.defaultPaperSize,['58mm','80mm','A4','Letter','Custom']) ||
      !finite(receipt.customWidthMm,30,500) ||
      !oneOf(receipt.defaultDocumentType,['sales_receipt','invoice','quotation','job_order_slip','payment_acknowledgment'])) return 'Invalid receipt configuration.';
  const pos = s.posLayout as Record<string,unknown>;
  if (!oneOf(pos.defaultView,['grid','list']) || !oneOf(pos.sortBy,['name','price_asc','price_desc','popular']) ||
      !finite(pos.gridColumnsDesktop,1,12) || !Number.isInteger(pos.gridColumnsDesktop) ||
      !finite(pos.cartPanelWidthPx,200,1000)) return 'Invalid POS layout.';
  const tokens = s.tokens as Record<string,unknown>;
  if (!finite(tokens.borderRadiusPx,0,40) || !finite(tokens.fontSizeScale,0.5,2) || !finite(tokens.sidebarWidthPx,100,600)) return 'Invalid display dimensions.';
  const methods = s.paymentMethods;
  if (!Array.isArray(methods) || !methods.length || methods.length>50) return 'Invalid payment methods.';
  const ids = new Set<string>();
  for (const m of methods) {
    if (!object(m) || typeof m.id !== 'string' || !m.id.trim() || !short(m.id,100) || ids.has(m.id) ||
        !short(m.name,100) || !String(m.name).trim() || !oneOf(m.type,['cash','ewallet','bank_transfer','card','credit_balance','custom']) ||
        !['enabled','requiresReference','requiresVerification'].every(k=>typeof m[k]==='boolean')) return 'Invalid or duplicate payment method.';
    ids.add(m.id);
  }
  if (!methods.some(m=>m.enabled===true)) return 'Enable at least one payment method.';
  if (!Array.isArray(s.dashboardWidgets) || s.dashboardWidgets.length>50 || !s.dashboardWidgets.every(w=>object(w)&&short(w.id,100)&&short(w.label,200)&&typeof w.visible==='boolean'&&finite(w.order,0,100))) return 'Invalid dashboard widgets.';
  return null;
}
