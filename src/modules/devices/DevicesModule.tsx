import React, { useEffect, useState } from 'react';
import { Bluetooth, Camera, Printer, RefreshCw, Usb } from 'lucide-react';
import { useApp } from '../../context/AppContext.tsx';
import {
  detectHardwareCapabilities,
  requestBluetoothDevice,
  requestSerialPrinterPort,
  requestUsbPrinterDevice,
} from '../../services/hardwareService.ts';
import { appendDiagnosticLog, getDiagnosticLogs } from '../../services/offlineDb.ts';
import type { DiagnosticLogEntry } from '../../shared/types.ts';
import { Button, Card, StatusIndicator } from '../../components/ui/Primitives.tsx';
import { openPrintPreview } from '../../services/printService.ts';
import type { PaperFormat } from '../../shared/printDocument.ts';

interface PairedDevice {
  type: 'USB' | 'Serial' | 'Bluetooth';
  name: string;
  detail: string;
  connectedAt: string;
}

export const DevicesModule: React.FC = () => {
  const { auditLogs, notify, settings } = useApp();
  const caps = detectHardwareCapabilities();

  const [pairedDevices, setPairedDevices] = useState<PairedDevice[]>([]);
  const [diagLogs, setDiagLogs] = useState<DiagnosticLogEntry[]>([]);
  const [testPaper, setTestPaper] = useState<PaperFormat>(settings.receiptTemplate.defaultPaperSize);
  const [scannerText, setScannerText] = useState('');

  const loadLogs = async () => {
    setDiagLogs(await getDiagnosticLogs());
  };

  useEffect(() => {
    void loadLogs();
  }, []);

  const handleUsb = async () => {
    try {
      const dev = await requestUsbPrinterDevice();
      if (dev) {
        setPairedDevices((prev) => [
          ...prev,
          {
            type: 'USB',
            name: dev.name,
            detail: `VID ${dev.vendorId} · PID ${dev.productId}`,
            connectedAt: new Date().toISOString(),
          },
        ]);
        await appendDiagnosticLog({
          level: 'info',
          category: 'hardware',
          message: `Paired USB device: ${dev.name}`,
        });
        notify(`Authorized USB device: ${dev.name}`, 'success');
        void loadLogs();
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'USB pairing cancelled or unsupported';
      await appendDiagnosticLog({ level: 'warn', category: 'hardware', message: msg });
      notify(msg, 'error');
      void loadLogs();
    }
  };

  const handleSerial = async () => {
    try {
      const port = await requestSerialPrinterPort();
      setPairedDevices((prev) => [
        ...prev,
        {
          type: 'Serial',
          name: 'Serial Thermal Printer',
          detail: port.info,
          connectedAt: new Date().toISOString(),
        },
      ]);
      await appendDiagnosticLog({
        level: 'info',
        category: 'hardware',
        message: `Authorized serial port: ${port.info}`,
      });
      notify('Serial port authorized.', 'success');
      void loadLogs();
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Serial port pairing cancelled';
      await appendDiagnosticLog({ level: 'warn', category: 'hardware', message: msg });
      notify(msg, 'error');
      void loadLogs();
    }
  };

  const handleBluetooth = async () => {
    try {
      const dev = await requestBluetoothDevice();
      setPairedDevices((prev) => [
        ...prev,
        {
          type: 'Bluetooth',
          name: dev.name,
          detail: dev.id,
          connectedAt: new Date().toISOString(),
        },
      ]);
      await appendDiagnosticLog({
        level: 'info',
        category: 'hardware',
        message: `Paired Bluetooth peripheral: ${dev.name}`,
      });
      notify(`Paired Bluetooth device: ${dev.name}`, 'success');
      void loadLogs();
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Bluetooth pairing cancelled';
      await appendDiagnosticLog({ level: 'warn', category: 'hardware', message: msg });
      notify(msg, 'error');
      void loadLogs();
    }
  };



  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[var(--pos-text)]">
            Printers, Devices & Diagnostics
          </h1>
          <p className="text-xs text-[var(--pos-text-muted)]">
            Inspect actual browser hardware capabilities, pair local peripherals, and run print tests
          </p>
        </div>
        <select aria-label="Test paper" value={testPaper} onChange={e => setTestPaper(e.target.value as PaperFormat)}>
          {['58mm','80mm','A4','Letter','Custom'].map(p => <option key={p}>{p}</option>)}
        </select>
        <Button size="sm" disabled={!caps.browserPrintSupported} onClick={() => {
          try { openPrintPreview('PRINTER-TEST', '<div class="text-center font-bold">PRINTER TEST — NOT A SALE</div><div class="border">Check both edges and wrapping. 0123456789 ABCDEFGHIJKLMNOPQRSTUVWXYZ</div><div class="flex justify-between"><span>Left edge</span><span>Right edge</span></div><div>PHP 123.45 · ₱123.45</div>', testPaper, settings.receiptTemplate.customWidthMm); }
          catch (err) { notify(err instanceof Error ? err.message : String(err), 'error'); }
        }}>
          <Printer className="w-4 h-4" />
          Open Test Print Preview
        </Button>
      </div>

      {/* Real Capability Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
        <Card className="space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-sm font-semibold">System / Thermal Print</span>
            <StatusIndicator
              tone={caps.browserPrintSupported ? 'success' : 'error'}
              label={caps.browserPrintSupported ? 'Available' : 'Unavailable'}
            />
          </div>
          <p className="text-xs text-[var(--pos-text-muted)]">
            Prints 58mm, 80mm, A4, and Letter receipts via OS printer spooler.
          </p>
        </Card>

        <Card className="space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-sm font-semibold">USB / Serial Thermal</span>
            <StatusIndicator
              tone={caps.webUsbSupported || caps.webSerialSupported ? 'info' : 'neutral'}
              label={
                caps.webUsbSupported || caps.webSerialSupported ? 'Supported' : 'Not Supported'
              }
            />
          </div>
          <p className="text-xs text-[var(--pos-text-muted)]">
            Device permission discovery only; direct ESC/POS printing is not implemented. Use the system printer.
          </p>
          <div className="flex flex-wrap gap-2 pt-1">
            <Button
              variant="outline"
              size="sm"
              disabled={!caps.webUsbSupported}
              onClick={() => void handleUsb()}
            >
              <Usb className="w-3.5 h-3.5" />
              Pair USB
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={!caps.webSerialSupported}
              onClick={() => void handleSerial()}
            >
              Pair Serial
            </Button>
          </div>
        </Card>

        <Card className="space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-sm font-semibold">Bluetooth & Scanner</span>
            <StatusIndicator
              tone={caps.webBluetoothSupported || caps.cameraSupported ? 'info' : 'neutral'}
              label={caps.cameraSupported ? 'Camera Ready' : 'Check Browser'}
            />
          </div>
          <p className="text-xs text-[var(--pos-text-muted)]">
            BarcodeDetector: {caps.barcodeDetectorSupported ? 'API available' : 'API unavailable'} · Web Bluetooth:{' '}
            {caps.webBluetoothSupported ? 'Available' : 'Unavailable'}
          </p>
          <div className="flex flex-wrap gap-2 pt-1">
            <Button
              variant="outline"
              size="sm"
              disabled={!caps.webBluetoothSupported}
              onClick={() => void handleBluetooth()}
            >
              <Bluetooth className="w-3.5 h-3.5" />
              Pair Bluetooth
            </Button>
            <span className="inline-flex items-center gap-1 text-xs text-[var(--pos-text-muted)] px-2">
              <Camera className="w-3.5 h-3.5" />
              {caps.cameraSupported ? 'Camera API OK' : 'No Camera API'}
            </span>
          </div>
        </Card>
      </div>

      {/* Authorized Peripherals in Session */}
      <Card className="space-y-3">
        <h2 className="text-sm font-semibold">Authorized Peripherals (This Session)</h2>
        {pairedDevices.length === 0 ? (
          <p className="text-xs text-[var(--pos-text-muted)]">
            No direct USB, Serial, or Bluetooth devices paired yet. Standard OS/browser printers are available through Print Document.
          </p>
        ) : (
          <div className="divide-y divide-[var(--pos-border)]">
            {pairedDevices.map((d, idx) => (
              <div key={idx} className="py-2 flex items-center justify-between text-xs">
                <div>
                  <span className="font-semibold">{d.name}</span>
                  <span className="text-[var(--pos-text-muted)] ml-2">
                    · {d.type} · {d.detail}
                  </span>
                </div>
                <StatusIndicator tone="success" label="Authorized" />
              </div>
            ))}
          </div>
        )}
      </Card>

      {/* Diagnostic & Audit Logs */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <Card className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold">Device & Sync Diagnostic Logs</h2>
            <Button variant="ghost" size="sm" onClick={() => void loadLogs()}>
              <RefreshCw className="w-3.5 h-3.5" />
              Refresh
            </Button>
          </div>
          <div className="space-y-2 max-h-60 overflow-y-auto">
            {diagLogs.length === 0 ? (
              <p className="text-xs text-[var(--pos-text-muted)]">No diagnostic events logged.</p>
            ) : (
              diagLogs.map((log) => (
                <div
                  key={log.id}
                  className="text-xs py-1.5 border-b border-[var(--pos-border)] last:border-0"
                >
                  <div className="flex justify-between text-[var(--pos-text-muted)]">
                    <span className="uppercase font-semibold">{log.category}</span>
                    <span>{new Date(log.timestamp).toLocaleTimeString()}</span>
                  </div>
                  <div className="text-[var(--pos-text)] mt-0.5">{log.message}</div>
                </div>
              ))
            )}
          </div>
        </Card>

        <Card className="space-y-3">
          <h2 className="text-sm font-semibold">Security & Transaction Audit Trail</h2>
          <div className="space-y-2 max-h-60 overflow-y-auto">
            {auditLogs.slice(0, 20).map((a) => (
              <div
                key={a.id}
                className="text-xs py-1.5 border-b border-[var(--pos-border)] last:border-0"
              >
                <div className="flex justify-between text-[var(--pos-text-muted)]">
                  <span className="font-semibold">{a.action}</span>
                  <span>
                    {a.actorName} · {new Date(a.createdAt).toLocaleString()}
                  </span>
                </div>
                <div className="text-[var(--pos-text)] mt-0.5">{a.summary}</div>
              </div>
            ))}
          </div>
        </Card>
      </div>

      <Card className="space-y-2">
        <h2>Keyboard-wedge scanner test</h2>
        <input aria-label="Scan test code" className="border p-2 w-full" value={scannerText} onChange={e => setScannerText(e.target.value)} onKeyDown={e => { if(e.key === 'Enter') { e.preventDefault(); notify(scannerText.trim() ? 'Keyboard input received; compare with printed code.' : 'No scanner input received.', scannerText.trim() ? 'success' : 'error'); } }} placeholder="Focus here, then scan a test code" />
        <Button variant="outline" onClick={() => setScannerText('')}>Clear test</Button>
        <p className="text-xs">Cash drawer commands, camera decoding and direct peripheral printing are not implemented. Physical output must be checked manually.</p>
      </Card>
    </div>
  );
};
