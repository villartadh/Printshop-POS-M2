import { appendDiagnosticLog } from './offlineDb.ts';

let memoryAccessToken = '';

export function setMemoryToken(token: string) {
  memoryAccessToken = token;
}

export function getMemoryToken(): string {
  return memoryAccessToken;
}

export function getDeviceDescriptor(): { deviceId: string; deviceName: string } {
  const ua = typeof navigator !== 'undefined' ? navigator.userAgent : 'Unknown';
  let os = 'Web Browser';
  if (/Windows/i.test(ua)) os = 'Windows PC';
  else if (/Android/i.test(ua)) os = /Mobile/i.test(ua) ? 'Android Mobile' : 'Android Tablet';
  else if (/iPad/i.test(ua)) os = 'iPadOS';
  else if (/iPhone/i.test(ua)) os = 'iOS Mobile';
  else if (/Mac/i.test(ua)) os = 'macOS Desktop';

  const isStandalone =
    typeof window !== 'undefined' &&
    window.matchMedia?.('(display-mode: standalone)').matches;

  const deviceName = `${os}${isStandalone ? ' (Installed App)' : ' (Browser)'}`;
  const deviceId = `${os.toLowerCase().replace(/\s+/g, '-')}-${
    typeof screen !== 'undefined' ? `${screen.width}x${screen.height}` : 'std'
  }`;

  return { deviceId, deviceName };
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((options.headers as Record<string, string>) || {}),
  };
  if (memoryAccessToken) {
    headers.Authorization = `Bearer ${memoryAccessToken}`;
  }

  const res = await fetch(path, {
    ...options,
    headers,
  });

  const text = await res.text();
  let data: unknown = {};
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = { message: text };
    }
  }

  if (!res.ok) {
    const errMsg =
      (data as { error?: string; message?: string })?.error ||
      (data as { error?: string; message?: string })?.message ||
      `Request failed (${res.status})`;
    void appendDiagnosticLog({
      level: res.status >= 500 ? 'error' : 'warn',
      category: res.status === 401 ? 'auth' : 'sync',
      message: `${options.method || 'GET'} ${path}: ${errMsg}`,
    });
    throw new Error(errMsg);
  }

  return data as T;
}
