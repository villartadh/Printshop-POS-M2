import React, { useState } from 'react';
import { Lock } from 'lucide-react';
import { useApp } from '../../context/AppContext.tsx';
import { Button, Input, Select } from '../../components/ui/Primitives.tsx';
import { Modal } from '../../components/ui/Overlays.tsx';

export const PinLockModal: React.FC = () => {
  const {
    isLocked,
    setIsLocked,
    session,
    quickUsers,
    loginWithPin,
    loginWithPassword,
    logout,
  } = useApp();

  const pinUsers = quickUsers.filter((u) => u.hasPin);
  const [selectedUserId, setSelectedUserId] = useState(
    session?.user.id || pinUsers[0]?.id || '',
  );
  const [pin, setPin] = useState('');
  const [password, setPassword] = useState('');
  const [usePassword, setUsePassword] = useState(pinUsers.length === 0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isLocked || !session) return null;

  const handleUnlock = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      if (usePassword) {
        await loginWithPassword(session.user.username, password);
      } else {
        await loginWithPin(selectedUserId || session.user.id, pin);
      }
      setPin('');
      setPassword('');
      setIsLocked(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Invalid credentials');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      open={isLocked}
      onClose={() => {}}
      title="Terminal Locked"
      subtitle={`Current session: ${session.user.fullName}`}
      maxWidth="sm"
      footer={
        <div className="w-full flex items-center justify-between gap-2">
          <Button variant="ghost" size="sm" onClick={() => void logout()}>
            Sign Out Completely
          </Button>
          {pinUsers.length > 0 && (
            <button
              type="button"
              onClick={() => setUsePassword((v) => !v)}
              className="text-xs font-medium text-[var(--pos-primary)] hover:underline cursor-pointer"
            >
              {usePassword ? 'Use PIN instead' : 'Use password'}
            </button>
          )}
        </div>
      }
    >
      <form onSubmit={handleUnlock} className="space-y-4">
        <div className="flex items-center gap-3 p-3 rounded-lg bg-slate-50 dark:bg-slate-800/60 border border-[var(--pos-border)]">
          <div className="h-9 w-9 rounded-lg bg-[var(--pos-primary)]/15 text-[var(--pos-primary)] flex items-center justify-center shrink-0">
            <Lock className="w-4 h-4" />
          </div>
          <p className="text-xs text-[var(--pos-text-muted)]">
            Enter a cashier PIN to unlock or switch the active operator on this terminal.
          </p>
        </div>

        {error && (
          <div className="rounded-lg border border-[#DC2626]/30 bg-[#DC2626]/10 px-3 py-2 text-xs text-[#DC2626]">
            {error}
          </div>
        )}

        {!usePassword && pinUsers.length > 0 ? (
          <>
            <Select
              label="Cashier / Operator"
              value={selectedUserId || session.user.id}
              onChange={(e) => setSelectedUserId(e.target.value)}
              options={pinUsers.map((u) => ({
                value: u.id,
                label: `${u.fullName} (${u.role})`,
              }))}
            />
            <Input
              label="PIN"
              type="password"
              inputMode="numeric"
              maxLength={6}
              value={pin}
              onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))}
              placeholder="Enter 4–6 digit PIN"
              autoFocus
            />
          </>
        ) : (
          <Input
            label={`Password for ${session.user.username}`}
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Enter password"
            autoFocus
          />
        )}

        <Button type="submit" className="w-full" loading={loading}>
          Unlock Terminal
        </Button>
      </form>
    </Modal>
  );
};
