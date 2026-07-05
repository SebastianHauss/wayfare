import { useState } from 'react';
import * as api from './api';
import type { MeResponse } from './types';
import { EyeIcon } from './Icons';

export function AuthScreen({
  onAuthenticated,
  onBack,
}: {
  onAuthenticated: (user: MeResponse) => void;
  onBack?: () => void;
}) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [canReactivate, setCanReactivate] = useState(false);
  const [canResendVerification, setCanResendVerification] = useState(false);
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null);
  const [resendStatus, setResendStatus] = useState<'idle' | 'sending' | 'sent'>('idle');
  const [showForgot, setShowForgot] = useState(false);
  const [forgotStatus, setForgotStatus] = useState<'idle' | 'sending' | 'sent'>('idle');

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError('');
    setCanReactivate(false);
    setCanResendVerification(false);
    setResendStatus('idle');
    try {
      if (mode === 'register') {
        await api.register(email, password);
        setRegisteredEmail(email);
      } else {
        const user = await api.login(email, password);
        onAuthenticated(user);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
      if (mode === 'login' && err instanceof api.ApiError) {
        if (err.code === 'ACCOUNT_DELETED') setCanReactivate(true);
        if (err.code === 'EMAIL_NOT_VERIFIED') setCanResendVerification(true);
      }
    } finally {
      setLoading(false);
    }
  }

  async function handleReactivate() {
    setLoading(true);
    setError('');
    try {
      const user = await api.reactivate(email, password);
      onAuthenticated(user);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
      setCanReactivate(false);
    } finally {
      setLoading(false);
    }
  }

  async function handleResend() {
    if (!registeredEmail) return;
    setResendStatus('sending');
    try {
      await api.resendVerification(registeredEmail);
      setResendStatus('sent');
    } catch {
      setResendStatus('idle');
    }
  }

  async function handleResendFromLogin() {
    setResendStatus('sending');
    try {
      await api.resendVerification(email);
      setResendStatus('sent');
    } catch {
      setResendStatus('idle');
    }
  }

  async function handleForgot(e: React.FormEvent) {
    e.preventDefault();
    setForgotStatus('sending');
    setError('');
    try {
      await api.forgotPassword(email);
      setForgotStatus('sent');
    } catch (err) {
      setForgotStatus('idle');
      setError(err instanceof Error ? err.message : 'Something went wrong');
    }
  }

  function openForgot() {
    setShowForgot(true);
    setError('');
    setForgotStatus('idle');
    setCanReactivate(false);
    setCanResendVerification(false);
  }

  function closeForgot() {
    setShowForgot(false);
    setForgotStatus('idle');
    setError('');
  }

  function switchMode(next: 'login' | 'register') {
    setMode(next);
    setError('');
    setCanReactivate(false);
    setCanResendVerification(false);
    setResendStatus('idle');
  }

  function backToLogin() {
    setRegisteredEmail(null);
    setResendStatus('idle');
    setPassword('');
    setMode('login');
  }

  return (
    <div className="bg-radial-glow flex min-h-screen items-center justify-center bg-cream px-4 py-16">
      <div className="w-full max-w-sm animate-fade-in-up">
        {onBack && !registeredEmail && !showForgot && (
          <button
            onClick={onBack}
            className="mb-4 text-sm text-ink-soft transition hover:text-orange"
          >
            ← Back
          </button>
        )}
        <div className="mb-8 flex flex-col items-center text-center">
          <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-ink font-display text-lg font-bold text-cream shadow-lg shadow-ink/15">
            W
          </div>
          <h1 className="font-display text-5xl font-bold tracking-tight text-ink">Wayfare</h1>
          <p className="mt-2 text-sm text-ink-soft">Enterprise-grade links, for one</p>
        </div>

        {showForgot ? (
          <div className="rounded-3xl border border-ink/5 bg-cream-card p-8 shadow-xl shadow-orange/5">
            {forgotStatus === 'sent' ? (
              <div className="text-center">
                <p className="text-sm font-medium text-ink">Check your email</p>
                <p className="mt-2 text-sm text-ink-soft">
                  If an account exists for <span className="font-medium text-ink">{email}</span>, we've sent a link to
                  reset your password.
                </p>
              </div>
            ) : (
              <>
                <p className="text-center text-sm font-medium text-ink">Reset your password</p>
                <p className="mt-2 text-center text-sm text-ink-soft">
                  Enter your email and we'll send you a link to choose a new password.
                </p>
                <form onSubmit={handleForgot} className="mt-5 space-y-4">
                  <div>
                    <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-ink-soft">
                      Email
                    </label>
                    <input
                      type="email"
                      required
                      autoComplete="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      className="w-full rounded-xl border border-ink/15 bg-cream px-3 py-2 text-sm text-ink outline-none transition focus:border-orange focus:ring-2 focus:ring-orange/20"
                    />
                  </div>
                  {error && <div className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">{error}</div>}
                  <button
                    type="submit"
                    disabled={forgotStatus === 'sending'}
                    className="w-full rounded-full bg-orange py-2.5 text-sm font-medium text-white shadow-md shadow-orange/20 transition hover:bg-ink hover:shadow-lg disabled:opacity-50"
                  >
                    {forgotStatus === 'sending' ? 'Sending…' : 'Send reset link'}
                  </button>
                </form>
              </>
            )}
            <button
              onClick={closeForgot}
              className="mt-4 block w-full text-sm text-ink-soft transition hover:text-orange"
            >
              Back to log in
            </button>
          </div>
        ) : registeredEmail ? (
          <div className="rounded-3xl border border-ink/5 bg-cream-card p-8 text-center shadow-xl shadow-orange/5">
            <p className="text-sm font-medium text-ink">Check your email</p>
            <p className="mt-2 text-sm text-ink-soft">
              We sent a verification link to <span className="font-medium text-ink">{registeredEmail}</span>. Click it
              to activate your account before logging in.
            </p>
            <button
              onClick={handleResend}
              disabled={resendStatus === 'sending'}
              className="mt-5 rounded-full border border-ink/15 px-4 py-1.5 text-sm text-ink-soft transition hover:border-orange hover:text-orange disabled:opacity-50"
            >
              {resendStatus === 'sending' ? 'Sending…' : resendStatus === 'sent' ? 'Sent — check your inbox' : 'Resend email'}
            </button>
            <button
              onClick={backToLogin}
              className="mt-4 block w-full text-sm text-ink-soft transition hover:text-orange"
            >
              Back to log in
            </button>
          </div>
        ) : (
          <div className="rounded-3xl border border-ink/5 bg-cream-card p-8 shadow-xl shadow-orange/5">
            <div className="relative mb-6 flex rounded-full bg-orange-light/40 p-1 text-sm font-medium">
              <span
                className="absolute inset-y-1 left-1 w-[calc(50%-4px)] rounded-full bg-orange shadow-sm transition-transform duration-300 ease-out"
                style={{ transform: mode === 'register' ? 'translateX(100%)' : 'translateX(0)' }}
              />
              <button
                type="button"
                className={`relative z-10 flex-1 rounded-full py-1.5 transition-colors ${
                  mode === 'login' ? 'text-white' : 'text-ink-soft hover:text-ink'
                }`}
                onClick={() => switchMode('login')}
              >
                Log in
              </button>
              <button
                type="button"
                className={`relative z-10 flex-1 rounded-full py-1.5 transition-colors ${
                  mode === 'register' ? 'text-white' : 'text-ink-soft hover:text-ink'
                }`}
                onClick={() => switchMode('register')}
              >
                Register
              </button>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-ink-soft">
                  Email
                </label>
                <input
                  type="email"
                  required
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full rounded-xl border border-ink/15 bg-cream px-3 py-2 text-sm text-ink outline-none transition focus:border-orange focus:ring-2 focus:ring-orange/20"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-ink-soft">
                  Password
                </label>
                <div className="flex items-center gap-2 rounded-xl border border-ink/15 bg-cream px-3 py-2 transition focus-within:border-orange focus-within:ring-2 focus-within:ring-orange/20">
                  <input
                    type={showPassword ? 'text' : 'password'}
                    required
                    minLength={8}
                    autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="w-full min-w-0 bg-transparent text-sm text-ink outline-none"
                  />
                  <button
                    type="button"
                    tabIndex={-1}
                    onClick={() => setShowPassword((s) => !s)}
                    className="shrink-0 text-ink-soft/50 transition hover:text-orange"
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                  >
                    <EyeIcon off={showPassword} className="h-4 w-4" />
                  </button>
                </div>
                {mode === 'login' && (
                  <button
                    type="button"
                    onClick={openForgot}
                    className="mt-2 block text-xs text-ink-soft transition hover:text-orange"
                  >
                    Forgot password?
                  </button>
                )}
              </div>

              {error && (
                <div className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">
                  <p>{error}</p>
                  {canReactivate && (
                    <button
                      type="button"
                      onClick={handleReactivate}
                      disabled={loading}
                      className="mt-2 rounded-full bg-red-600 px-3 py-1 text-xs font-medium text-white transition hover:bg-red-700 disabled:opacity-50"
                    >
                      {loading ? 'Reactivating…' : 'Reactivate account'}
                    </button>
                  )}
                  {canResendVerification && (
                    <button
                      type="button"
                      onClick={handleResendFromLogin}
                      disabled={resendStatus === 'sending'}
                      className="mt-2 rounded-full bg-red-600 px-3 py-1 text-xs font-medium text-white transition hover:bg-red-700 disabled:opacity-50"
                    >
                      {resendStatus === 'sending'
                        ? 'Sending…'
                        : resendStatus === 'sent'
                          ? 'Sent — check your inbox'
                          : 'Resend verification email'}
                    </button>
                  )}
                </div>
              )}

              <button
                type="submit"
                disabled={loading}
                className="w-full rounded-full bg-orange py-2.5 text-sm font-medium text-white shadow-md shadow-orange/20 transition hover:bg-ink hover:shadow-lg disabled:opacity-50"
              >
                {loading ? 'Please wait…' : mode === 'login' ? 'Log in' : 'Create account'}
              </button>
            </form>
          </div>
        )}
        <p className="mt-6 text-center text-xs text-ink-soft/70">Your links, your data — nothing shared, nothing sold.</p>
      </div>
    </div>
  );
}
