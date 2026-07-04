import * as api from './api';
import type { MeResponse } from './types';

type OAuthProvider = {
  id: 'google' | 'github';
  label: string;
  mark: string;
  markClassName: string;
};

const providers: OAuthProvider[] = [
  { id: 'google', label: 'Continue with Google', mark: 'G', markClassName: 'text-[#4285f4]' },
  { id: 'github', label: 'Continue with GitHub', mark: 'GH', markClassName: 'bg-ink text-[10px] text-white' },
];

export function AuthScreen({
  onBack,
}: {
  onAuthenticated: (user: MeResponse) => void;
  onBack?: () => void;
}) {
  function signIn(provider: OAuthProvider['id']) {
    window.location.assign(api.getOAuthUrl(provider));
  }

  return (
    <div className="bg-radial-glow flex min-h-screen items-center justify-center bg-cream px-4 py-16">
      <div className="w-full max-w-sm animate-fade-in-up">
        {onBack && (
          <button
            onClick={onBack}
            className="mb-4 text-sm text-ink-soft transition hover:text-orange"
          >
            Back
          </button>
        )}
        <div className="mb-8 flex flex-col items-center text-center">
          <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-ink font-display text-lg font-bold text-cream shadow-lg shadow-ink/15">
            W
          </div>
          <h1 className="font-display text-5xl font-bold tracking-tight text-ink">Wayfare</h1>
          <p className="mt-2 text-sm text-ink-soft">Enterprise-grade links, for one</p>
        </div>

        <div className="rounded-3xl border border-ink/5 bg-cream-card p-8 text-center shadow-xl shadow-orange/5">
          <p className="text-sm font-medium text-ink">Sign in to keep your links synced</p>
          <p className="mt-2 text-sm text-ink-soft">
            Use Google or GitHub to create or access your Wayfare workspace.
          </p>
          <div className="mt-6 space-y-3">
            {providers.map((provider) => (
              <button
                key={provider.id}
                type="button"
                onClick={() => signIn(provider.id)}
                className="flex w-full items-center justify-center gap-3 rounded-full border border-ink/15 bg-white px-4 py-2.5 text-sm font-medium text-ink shadow-sm transition hover:border-orange hover:text-orange"
              >
                <span
                  className={`flex h-5 w-5 items-center justify-center rounded-full bg-white font-display font-bold ${provider.markClassName}`}
                >
                  {provider.mark}
                </span>
                {provider.label}
              </button>
            ))}
          </div>
        </div>
        <p className="mt-6 text-center text-xs text-ink-soft/70">Your links, your data. Nothing shared, nothing sold.</p>
      </div>
    </div>
  );
}
