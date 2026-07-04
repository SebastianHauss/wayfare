import { useEffect, useState } from 'react';
import * as api from './api';
import type { MeResponse } from './types';

export function VerifyEmail({
  token,
  onVerified,
  onBack,
}: {
  token: string;
  onVerified: (user: MeResponse) => void;
  onBack: () => void;
}) {
  const [status, setStatus] = useState<'verifying' | 'error'>('verifying');
  const [error, setError] = useState('');

  useEffect(() => {
    api
      .verifyEmail(token)
      .then(onVerified)
      .catch((err) => {
        setStatus('error');
        setError(err instanceof Error ? err.message : 'Something went wrong');
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  return (
    <div className="bg-radial-glow flex min-h-screen items-center justify-center bg-cream px-4 py-16">
      <div className="w-full max-w-sm animate-fade-in-up text-center">
        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-ink font-display text-lg font-bold text-cream shadow-lg shadow-ink/15">
          W
        </div>
        <div className="rounded-3xl border border-ink/5 bg-cream-card p-8 shadow-xl shadow-orange/5">
          {status === 'verifying' ? (
            <p className="text-sm text-ink-soft">Verifying your email…</p>
          ) : (
            <>
              <p className="text-sm font-medium text-ink">Verification failed</p>
              <p className="mt-2 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">{error}</p>
              <button
                onClick={onBack}
                className="mt-5 rounded-full bg-orange px-4 py-1.5 text-sm font-medium text-white transition hover:bg-ink"
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
