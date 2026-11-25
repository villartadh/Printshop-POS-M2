import React, { useState } from 'react';
import { Download } from 'lucide-react';
import { usePWAInstall } from '../../hooks/usePWAInstall.ts';
import { Button } from './Primitives.tsx';
import { Modal } from './Overlays.tsx';

export const PWAInstallButton: React.FC<{ compact?: boolean }> = ({ compact = false }) => {
  const { isInstallable, isInstalled, isIOS, install } = usePWAInstall();
  const [showIOSGuide, setShowIOSGuide] = useState(false);

  if (isInstalled) return null;

  if (isInstallable) {
    return (
      <Button
        variant="outline"
        size="sm"
        onClick={() => void install()}
        title="Install Printshop POS app"
      >
        <Download className="w-3.5 h-3.5" />
        {!compact && <span>Install App</span>}
      </Button>
    );
  }

  if (isIOS) {
    return (
      <>
        <Button
          variant="outline"
          size="sm"
          onClick={() => setShowIOSGuide(true)}
          title="Install on iPhone or iPad"
        >
          <Download className="w-3.5 h-3.5" />
          {!compact && <span>Install</span>}
        </Button>
        <Modal
          open={showIOSGuide}
          onClose={() => setShowIOSGuide(false)}
          title="Install Printshop POS"
          subtitle="Add to your Home Screen for full-screen offline use"
          maxWidth="sm"
          footer={
            <Button size="sm" onClick={() => setShowIOSGuide(false)}>
              Got it
            </Button>
          }
        >
          <ol className="space-y-2 text-sm text-[var(--pos-text)] list-decimal pl-5">
            <li>Tap the Share button in Safari&apos;s toolbar.</li>
            <li>Scroll down and choose Add to Home Screen.</li>
            <li>Tap Add to install Printshop POS.</li>
          </ol>
        </Modal>
      </>
    );
  }

  return null;
};
