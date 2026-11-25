import React, { useState } from 'react';
import { KeyRound, Lock, Printer, ShieldCheck, UserCheck } from 'lucide-react';
import { useApp } from '../../context/AppContext.tsx';
import { Button, Card, Input, Select } from '../../components/ui/Primitives.tsx';

export const AuthScreen: React.FC = () => {
  const {
    needsSetup,
    quickUsers,
    settings,
    setupAdmin,
    loginWithPassword,
    loginWithPin,
  } = useApp();

  const [mode, setMode] = useState<'password' | 'pin' | 'recovery'>(
    quickUsers.some((u) => u.hasPin) ? 'pin' : 'password',
  );
  const [username, setUsername] = useState('');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [pin, setPin] = useState('');
  const [selectedUserId, setSelectedUserId] = useState(quickUsers[0]?.id || '');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const pinUsers = quickUsers.filter((u) => u.hasPin);
  const effectivePinUserId =
    selectedUserId && pinUsers.some((u) => u.id === selectedUserId)
      ? selectedUserId
      : pinUsers[0]?.id || '';

  const handleSetupSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await setupAdmin({ username, fullName, email, password, pin });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to complete setup.');
    } finally {
      setSubmitting(false);
    }
  };

  const handlePasswordLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await loginWithPassword(username, password);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Invalid credentials.');
    } finally {
      setSubmitting(false);
    }
  };

  const handlePinLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!effectivePinUserId || !pin) return;
    setError(null);
    setSubmitting(true);
    try {
      await loginWithPin(effectivePinUserId, pin);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Invalid PIN.');
      setPin('');
    } finally {
      setSubmitting(false);
    }
  };

  const appendPinDigit = (digit: string) => {
    if (pin.length >= 6) return;
    setPin((prev) => prev + digit);
  };

  return (
    <div className="min-h-dvh flex flex-col lg:flex-row bg-[var(--pos-bg)]">
      {/* Left Brand Panel (Desktop) / Top Header (Mobile) */}
      <div
        style={{ backgroundColor: settings.tokens.navBackground }}
        className="lg:w-[44%] px-6 py-10 lg:p-12 text-white flex flex-col justify-between"
      >
        <div className="flex items-center gap-3">
          <div
            style={{ backgroundColor: settings.tokens.primary }}
            className="h-11 w-11 rounded-xl flex items-center justify-center shrink-0 shadow-sm"
          >
            {settings.branding.logoDataUrl ? (
              <img
                src={settings.branding.logoDataUrl}
                alt={settings.branding.appName}
                className="h-8 w-8 object-contain rounded"
                referrerPolicy="no-referrer"
              />
            ) : (
              <Printer className="w-6 h-6 text-white" />
            )}
          </div>
          <div>
            <h1 className="text-xl font-bold tracking-tight">{settings.branding.appName}</h1>
            <p className="text-xs text-teal-200/90">{settings.branding.tagline}</p>
          </div>
        </div>

        <div className="my-8 lg:my-0 max-w-md">
          <h2 className="text-2xl lg:text-3xl font-bold tracking-tight leading-tight text-balance">
            {needsSetup
              ? 'Set up your print shop administrator account'
              : `Welcome back to ${settings.branding.businessName}`}
          </h2>
          <p className="mt-3 text-sm text-slate-300 leading-relaxed">
            {needsSetup
              ? 'Create your primary owner account and optional quick cashier PIN to unlock counter checkout, dynamic print pricing, and production job tracking.'
              : 'Sign in with your account credentials or switch cashiers using your 4 to 6 digit terminal PIN.'}
          </p>
        </div>

        <div className="hidden lg:flex items-center gap-6 text-xs text-slate-300">
          <span className="inline-flex items-center gap-1.5">
            <ShieldCheck className="w-4 h-4 text-teal-300" />
            Role-based access control
          </span>
          <span aria-hidden="true">·</span>
          <span>Offline-ready terminal</span>
        </div>
      </div>

      {/* Right Auth Form Panel */}
      <div className="flex-1 flex items-center justify-center p-6 sm:p-10">
        <div className="w-full max-w-md">
          {needsSetup ? (
            <Card className="p-6 sm:p-7">
              <div className="mb-5">
                <h3 className="text-lg font-semibold text-[var(--pos-text)]">
                  First-Run Store Setup
                </h3>
                <p className="text-xs text-[var(--pos-text-muted)] mt-1">
                  This one-time step creates your primary administrator account.
                </p>
              </div>

              {error && (
                <div className="mb-4 rounded-lg border border-[#DC2626]/30 bg-[#DC2626]/10 px-3 py-2 text-xs text-[#DC2626]">
                  {error}
                </div>
              )}

              <form onSubmit={handleSetupSubmit} className="space-y-3.5">
                <Input
                  label="Full Name"
                  placeholder="e.g. Maria Santos"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  required
                />
                <Input
                  label="Username"
                  placeholder="e.g. admin"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  required
                />
                <Input
                  label="Email (Optional)"
                  type="email"
                  placeholder="owner@printshop.local"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
                <Input
                  label="Password (8–128 characters)"
                  type="password"
                  placeholder="Enter a strong password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
                <Input
                  label="Quick Cashier PIN (Optional, 4–6 digits)"
                  type="password"
                  inputMode="numeric"
                  maxLength={6}
                  placeholder="e.g. 1234"
                  value={pin}
                  onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))}
                />
                <Button type="submit" className="w-full" size="lg" loading={submitting}>
                  Create Admin & Open POS
                </Button>
              </form>
            </Card>
          ) : (
            <Card className="p-6 sm:p-7">
              <div className="flex items-center gap-1 p-1 bg-slate-100 dark:bg-slate-800 rounded-lg mb-5">
                {pinUsers.length > 0 && (
                  <button
                    type="button"
                    onClick={() => {
                      setMode('pin');
                      setError(null);
                    }}
                    className={`flex-1 py-2 text-xs font-medium rounded-md transition-colors cursor-pointer ${
                      mode === 'pin'
                        ? 'bg-[var(--pos-surface)] text-[var(--pos-text)] shadow-xs'
                        : 'text-[var(--pos-text-muted)] hover:text-[var(--pos-text)]'
                    }`}
                  >
                    Cashier PIN
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => {
                    setMode('password');
                    setError(null);
                  }}
                  className={`flex-1 py-2 text-xs font-medium rounded-md transition-colors cursor-pointer ${
                    mode === 'password'
                      ? 'bg-[var(--pos-surface)] text-[var(--pos-text)] shadow-xs'
                      : 'text-[var(--pos-text-muted)] hover:text-[var(--pos-text)]'
                  }`}
                >
                  Password Sign In
                </button>
              </div>

              {error && (
                <div className="mb-4 rounded-lg border border-[#DC2626]/30 bg-[#DC2626]/10 px-3 py-2 text-xs text-[#DC2626]">
                  {error}
                </div>
              )}

              {mode === 'pin' && pinUsers.length > 0 && (
                <form onSubmit={handlePinLogin} className="space-y-4">
                  <Select
                    label="Select Operator"
                    value={effectivePinUserId}
                    onChange={(e) => setSelectedUserId(e.target.value)}
                    options={pinUsers.map((u) => ({
                      value: u.id,
                      label: `${u.fullName} (${u.role})`,
                    }))}
                  />

                  <div>
                    <label className="block text-xs font-medium text-[var(--pos-text-muted)] mb-1.5">
                      Terminal PIN
                    </label>
                    <input
                      type="password"
                      inputMode="numeric"
                      maxLength={6}
                      value={pin}
                      onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))}
                      placeholder="Enter 4–6 digit PIN"
                      style={{ borderRadius: 'var(--pos-radius)' }}
                      className="w-full text-center tracking-[0.4em] text-lg font-mono-num min-h-[46px] bg-[var(--pos-surface)] border border-[var(--pos-border)] px-3 py-2 text-[var(--pos-text)] focus:outline-none focus:border-[var(--pos-primary)]"
                    />
                  </div>

                  <div className="grid grid-cols-3 gap-2">
                    {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((d) => (
                      <button
                        key={d}
                        type="button"
                        onClick={() => appendPinDigit(d)}
                        style={{ borderRadius: 'var(--pos-radius)' }}
                        className="h-12 border border-[var(--pos-border)] bg-slate-50 dark:bg-slate-800/70 hover:bg-slate-100 dark:hover:bg-slate-700 text-base font-semibold text-[var(--pos-text)] cursor-pointer"
                      >
                        {d}
                      </button>
                    ))}
                    <button
                      type="button"
                      onClick={() => setPin('')}
                      style={{ borderRadius: 'var(--pos-radius)' }}
                      className="h-12 border border-[var(--pos-border)] text-xs font-medium text-[var(--pos-text-muted)] hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
                    >
                      Clear
                    </button>
                    <button
                      type="button"
                      onClick={() => appendPinDigit('0')}
                      style={{ borderRadius: 'var(--pos-radius)' }}
                      className="h-12 border border-[var(--pos-border)] bg-slate-50 dark:bg-slate-800/70 hover:bg-slate-100 dark:hover:bg-slate-700 text-base font-semibold text-[var(--pos-text)] cursor-pointer"
                    >
                      0
                    </button>
                    <button
                      type="button"
                      onClick={() => setPin((p) => p.slice(0, -1))}
                      style={{ borderRadius: 'var(--pos-radius)' }}
                      className="h-12 border border-[var(--pos-border)] text-xs font-medium text-[var(--pos-text-muted)] hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
                    >
                      Back
                    </button>
                  </div>

                  <Button type="submit" className="w-full" size="lg" loading={submitting}>
                    <KeyRound className="w-4 h-4" />
                    Unlock POS Terminal
                  </Button>
                </form>
              )}

              {mode === 'password' && (
                <form onSubmit={handlePasswordLogin} className="space-y-4">
                  <Input
                    label="Username or Email"
                    leftIcon={<UserCheck className="w-4 h-4" />}
                    placeholder="Enter username"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    required
                  />
                  <Input
                    label="Password"
                    type="password"
                    leftIcon={<Lock className="w-4 h-4" />}
                    placeholder="Enter password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                  />
                  <div className="flex justify-end">
                    <button
                      type="button"
                      onClick={() => setMode('recovery')}
                      className="text-xs font-medium text-[var(--pos-primary)] hover:underline cursor-pointer"
                    >
                      Forgot password?
                    </button>
                  </div>
                  <Button type="submit" className="w-full" size="lg" loading={submitting}>
                    Sign In
                  </Button>
                </form>
              )}

              {mode === 'recovery' && (
                <div className="space-y-4">
                  <h3 className="text-sm font-semibold text-[var(--pos-text)]">
                    Password & PIN Recovery
                  </h3>
                  <p className="text-xs text-[var(--pos-text-muted)] leading-relaxed">
                    For point-of-sale security, staff passwords and cashier PINs are managed by your store administrator under Settings → Users & Roles. Ask an administrator to assign a temporary password or reset your terminal PIN.
                  </p>
                  <Button
                    variant="outline"
                    className="w-full"
                    onClick={() => setMode('password')}
                  >
                    Back to Sign In
                  </Button>
                </div>
              )}
            </Card>
          )}
        </div>
      </div>
    </div>
  );
};
