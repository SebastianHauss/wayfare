import { useEffect, useState } from 'react';
import * as api from './api';
import type { MeResponse } from './types';
import { AuthScreen } from './AuthScreen';
import { Dashboard } from './Dashboard';
import { VerifyEmail } from './VerifyEmail';

function readVerifyToken(): string | null {
  if (window.location.pathname !== '/verify-email') return null;
  return new URLSearchParams(window.location.search).get('token');
}

export default function App() {
  const [user, setUser] = useState<MeResponse | null>(null);
  const [checking, setChecking] = useState(true);
  const [showAuth, setShowAuth] = useState(false);
  const [verifyToken, setVerifyToken] = useState<string | null>(readVerifyToken);

  useEffect(() => {
    if (verifyToken) {
      setChecking(false);
      return;
    }
    api
      .getCurrentUser()
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setChecking(false));
  }, [verifyToken]);

  function clearVerifyUrl() {
    window.history.replaceState({}, '', '/');
    setVerifyToken(null);
  }

  async function handleLogout() {
    await api.logout();
    setUser(null);
  }

  if (verifyToken) {
    return (
      <VerifyEmail
        token={verifyToken}
        onVerified={(u) => {
          setUser(u);
          clearVerifyUrl();
        }}
        onBack={clearVerifyUrl}
      />
    );
  }

  if (checking) return null;

  if (!user && showAuth) {
    return (
      <AuthScreen
        onAuthenticated={(u) => {
          setUser(u);
          setShowAuth(false);
        }}
        onBack={() => setShowAuth(false)}
      />
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
