/** Local deployment boundary and bounded credential-attempt policy. */
export function localHost(value: string | undefined): boolean {
  return /^(localhost|127\.0\.0\.1|\[::1\])(?::[0-9]{1,5})?$/i.test(value || '');
}
export function allowedOrigin(host: string | undefined, origin: string | undefined): boolean {
  if (!localHost(host)) return false;
  if (origin === undefined) return true;
  try { const url = new URL(origin); return url.protocol === 'http:' && url.host.toLowerCase() === host!.toLowerCase() && origin === url.origin; } catch { return false; }
}
export function allowedHttpsOrigin(configured: URL, host: string | undefined, origin: string | undefined): boolean {
  return configured.protocol === 'https:' &&
    host?.toLowerCase() === configured.host.toLowerCase() &&
    (origin === undefined || origin === configured.origin);
}
export function attemptGate(limit = 10, windowMs = 60_000, clock = Date.now) {
  let until = 0, count = 0;
  return () => { const now = clock(); if (now >= until) { until = now + windowMs; count = 0; } if (count >= limit) return Math.max(1, Math.ceil((until-now)/1000)); count++; return 0; };
}
export function validPassword(value: unknown): value is string { return typeof value === 'string' && value.length >= 8 && value.length <= 128; }
export function validPin(value: unknown): value is string { return typeof value === 'string' && /^\d{4,6}$/.test(value); }
export function removesLastAdmin(role: unknown, active: unknown, nextRole: unknown, nextActive: unknown, count: number) { return role === 'admin' && active === 1 && (nextRole !== 'admin' || nextActive !== 1) && count <= 1; }
