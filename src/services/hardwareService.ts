export interface HardwareCapabilityReport {
  browserPrintSupported: boolean;
  webSerialSupported: boolean;
  webUsbSupported: boolean;
  webBluetoothSupported: boolean;
  cameraSupported: boolean;
  barcodeDetectorSupported: boolean;
}

export function detectHardwareCapabilities(): HardwareCapabilityReport {
  const nav = typeof navigator !== 'undefined' ? (navigator as unknown as Record<string, unknown>) : {};
  const win = typeof window !== 'undefined' ? (window as unknown as Record<string, unknown>) : {};

  return {
    browserPrintSupported: typeof window !== 'undefined' && typeof window.print === 'function',
    webSerialSupported: 'serial' in nav,
    webUsbSupported: 'usb' in nav,
    webBluetoothSupported: 'bluetooth' in nav,
    cameraSupported:
      typeof navigator !== 'undefined' &&
      Boolean(navigator.mediaDevices && navigator.mediaDevices.getUserMedia),
    barcodeDetectorSupported: 'BarcodeDetector' in win,
  };
}

export async function requestUsbPrinterDevice(): Promise<{ name: string; vendorId: number; productId: number } | null> {
  const nav = navigator as unknown as {
    usb?: { requestDevice: (opts: { filters: unknown[] }) => Promise<{ productName?: string; vendorId: number; productId: number }> };
  };
  if (!nav.usb) {
    throw new Error('WebUSB is not supported in this browser or context.');
  }
  const device = await nav.usb.requestDevice({ filters: [] });
  return {
    name: device.productName || 'USB Device',
    vendorId: device.vendorId,
    productId: device.productId,
  };
}

export async function requestSerialPrinterPort(): Promise<{ info: string }> {
  const nav = navigator as unknown as {
    serial?: { requestPort: () => Promise<{ getInfo: () => { usbVendorId?: number; usbProductId?: number } }> };
  };
  if (!nav.serial) {
    throw new Error('Web Serial API is not supported in this browser.');
  }
  const port = await nav.serial.requestPort();
  const info = port.getInfo();
  return {
    info: info.usbVendorId
      ? `USB Serial (${info.usbVendorId}:${info.usbProductId || '0'})`
      : 'Serial Port Selected',
  };
}

export async function requestBluetoothDevice(): Promise<{ name: string; id: string }> {
  const nav = navigator as unknown as {
    bluetooth?: {
      requestDevice: (opts: { acceptAllDevices: boolean }) => Promise<{ name?: string; id: string }>;
    };
  };
  if (!nav.bluetooth) {
    throw new Error('Web Bluetooth is not supported in this browser.');
  }
  const device = await nav.bluetooth.requestDevice({ acceptAllDevices: true });
  return {
    name: device.name || 'Bluetooth Peripheral',
    id: device.id,
  };
}

export function downloadCsv(filename: string, headers: string[], rows: Array<Array<string | number>>) {
  const escape = (val: string | number) => {
    const str = String(val ?? '');
    if (/[",\n]/.test(str)) {
      return `"${str.replace(/"/g, '""')}"`;
    }
    return str;
  };
  const csv = [headers.map(escape).join(','), ...rows.map((r) => r.map(escape).join(','))].join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
