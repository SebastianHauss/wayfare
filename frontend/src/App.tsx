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
    // Run the session check once on mount. When there's a verify token we skip
    // it entirely and let verification sign the user in; re-running this on the
    // token clearing would fire a redundant /me that can race the just-set auth
    // cookie and wipe the freshly verified user back to logged-out.
    if (verifyToken) {
      setChecking(false);
      return;
    }
    api
      .getCurrentUser()
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setChecking(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
