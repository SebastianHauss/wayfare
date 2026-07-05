import { useEffect, useState } from 'react';
import * as api from './api';
import type { MeResponse } from './types';
import { AuthScreen } from './AuthScreen';
import { Dashboard } from './Dashboard';
import { VerifyEmail } from './VerifyEmail';
import { ResetPassword } from './ResetPassword';

function readTokenForPath(path: string): string | null {
  if (window.location.pathname !== path) return null;
  return new URLSearchParams(window.location.search).get('token');
}

export default function App() {
  const [user, setUser] = useState<MeResponse | null>(null);
  const [showAuth, setShowAuth] = useState(false);
  const [verifyToken, setVerifyToken] = useState<string | null>(() => readTokenForPath('/verify-email'));
  const [resetToken, setResetToken] = useState<string | null>(() => readTokenForPath('/reset-password'));

  useEffect(() => {
    // Resolve the session in the background. We render the (anonymous) Dashboard
    // immediately rather than blocking on this, so a slow or cold backend can't
    // leave the page blank — if /me comes back authenticated we just fill in the
    // user and the Dashboard reloads that user's links. When there's a verify
    // token we skip the check entirely and let verification sign the user in;
    // re-running it on token clearing would fire a redundant /me that can race
    // the just-set auth cookie and wipe the freshly verified user back out.
    if (verifyToken) return;
    api
      .getCurrentUser()
      .then(setUser)
      .catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function clearVerifyUrl() {
    window.history.replaceState({}, '', '/');
    setVerifyToken(null);
  }

  function clearResetUrl() {
    window.history.replaceState({}, '', '/');
    setResetToken(null);
    setShowAuth(true);
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

  if (resetToken) {
    return <ResetPassword token={resetToken} onBack={clearResetUrl} />;
  }

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
