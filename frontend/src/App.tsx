import { useEffect, useState } from 'react';
import * as api from './api';
import type { MeResponse } from './types';
import { AuthScreen } from './AuthScreen';
import { Dashboard } from './Dashboard';

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
    if (authError) {
      setShowAuth(true);
      window.history.replaceState({}, '', '/');
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
