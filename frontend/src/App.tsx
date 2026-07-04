import { useEffect, useState } from 'react';
import * as api from './api';
import type { MeResponse } from './types';
import { AuthScreen } from './AuthScreen';
import { Dashboard } from './Dashboard';

function readOAuthCallback(): { accessToken: string; refreshToken: string } | null {
  if (window.location.pathname !== '/auth/callback') return null;
  const params = new URLSearchParams(window.location.hash.slice(1));
  const accessToken = params.get('access_token');
  const refreshToken = params.get('refresh_token');
  if (!accessToken || !refreshToken) return null;
  return { accessToken, refreshToken };
}

function readOAuthError(): string | null {
  if (window.location.pathname !== '/auth/callback') return null;
  return new URLSearchParams(window.location.hash.slice(1)).get('error');
}

export default function App() {
  const [user, setUser] = useState<MeResponse | null>(null);
  const [checking, setChecking] = useState(true);
  const [showAuth, setShowAuth] = useState(false);
  const [authError, setAuthError] = useState<string | null>(readOAuthError);

  useEffect(() => {
    const oauthCallback = readOAuthCallback();
    if (oauthCallback) {
      api
        .completeOAuthLogin(oauthCallback.accessToken, oauthCallback.refreshToken)
        .then((u) => {
          setUser(u);
          setShowAuth(false);
          window.history.replaceState({}, '', '/');
        })
        .catch((err) => setAuthError(err instanceof Error ? err.message : 'Sign-in failed'))
        .finally(() => setChecking(false));
      return;
    }
    if (authError) {
      setShowAuth(true);
      window.history.replaceState({}, '', '/');
    }
    if (!api.getAccessToken()) {
      setChecking(false);
      return;
    }
    api
      .getCurrentUser()
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setChecking(false));
  }, [authError]);

  async function handleLogout() {
    await api.logout();
    setUser(null);
  }

  if (checking) return null;

  if (!user && showAuth) {
    return (
      <>
        {authError && (
          <div className="fixed inset-x-4 top-4 z-20 mx-auto max-w-sm rounded-2xl bg-red-50 px-4 py-3 text-sm text-red-600 shadow-lg">
            {authError}
          </div>
        )}
        <AuthScreen
          onAuthenticated={(u) => {
            setUser(u);
            setShowAuth(false);
            setAuthError(null);
          }}
          onBack={() => {
            setShowAuth(false);
            setAuthError(null);
          }}
        />
      </>
    );
  }

  return (
    <Dashboard
      user={user}
      onLogout={handleLogout}
      onAccountDeleted={() => setUser(null)}
      onLoginClick={() => setShowAuth(true)}
    />
  );
}
