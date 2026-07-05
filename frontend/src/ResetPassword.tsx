import { useState } from 'react';
import * as api from './api';
import { EyeIcon } from './Icons';

export function ResetPassword({
  token,
  onBack,
}: {
  token: string;
  onBack: () => void;
}) {
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [status, setStatus] = useState<'idle' | 'submitting' | 'done'>('idle');
  const [error, setError] = useState('');

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setStatus('submitting');
    setError('');
    try {
      await api.resetPassword(token, password);
      setStatus('done');
    } catch (err) {
      setStatus('idle');
      setError(err instanceof Error ? err.message : 'Something went wrong');
    }
  }

  return (
    <div className="bg-radial-glow flex min-h-screen items-center justify-center bg-cream px-4 py-16">
      <div className="w-full max-w-sm animate-fade-in-up text-center">
        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-ink font-display text-lg font-bold text-cream shadow-lg shadow-ink/15">
          W
        </div>
        <div className="rounded-3xl border border-ink/5 bg-cream-card p-8 shadow-xl shadow-orange/5">
          {status === 'done' ? (
            <>
              <p className="text-sm font-medium text-ink">Password reset</p>
              <p className="mt-2 text-sm text-ink-soft">
                Your password has been updated. You can now log in with your new password.
              </p>
              <button
                onClick={onBack}
                className="mt-5 rounded-full bg-orange px-4 py-1.5 text-sm font-medium text-white transition hover:bg-ink"
              >
                Back to log in
              </button>
            </>
          ) : (
            <>
              <p className="text-sm font-medium text-ink">Choose a new password</p>
              <form onSubmit={handleSubmit} className="mt-5 space-y-4 text-left">
                <div>
                  <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-ink-soft">
                    New password
                  </label>
                  <div className="flex items-center gap-2 rounded-xl border border-ink/15 bg-cream px-3 py-2 transition focus-within:border-orange focus-within:ring-2 focus-within:ring-orange/20">
                    <input
                      type={showPassword ? 'text' : 'password'}
                      required
                      minLength={8}
                      autoComplete="new-password"
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
                </div>

                {error && (
                  <div className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">{error}</div>
                )}

                <button
                  type="submit"
                  disabled={status === 'submitting'}
                  className="w-full rounded-full bg-orange py-2.5 text-sm font-medium text-white shadow-md shadow-orange/20 transition hover:bg-ink hover:shadow-lg disabled:opacity-50"
                >
                  {status === 'submitting' ? 'Please wait…' : 'Reset password'}
                </button>
              </form>
              <button
                onClick={onBack}
                className="mt-4 block w-full text-sm text-ink-soft transition hover:text-orange"
              >
                Back to Wayfare
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
