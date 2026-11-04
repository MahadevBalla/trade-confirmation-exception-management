import React, { useCallback, useState } from 'react';
import type { DataMode, SignedInUser } from '../types/tcem';
import { LiveAppProvider } from './LiveAppProvider';
import { MockAppProvider } from './MockAppProvider';
import { LoginScreen } from '../components/layout/LoginScreen';
import { setAuthorization } from '../services/api';

const MODE_KEY = 'tcem-data-mode';
const SESSION_KEY = 'tcem-session';

interface Session {
  user: SignedInUser;
  authorization: string;
}

const initialMode = (): DataMode => {
  try {
    return window.localStorage.getItem(MODE_KEY) === 'demo' ? 'demo' : 'live';
  } catch {
    return 'live';
  }
};

const restoreSession = (): SignedInUser | null => {
  try {
    const stored = window.sessionStorage.getItem(SESSION_KEY);
    if (!stored) return null;
    const session = JSON.parse(stored) as Session;
    setAuthorization(session.authorization);
    return session.user;
  } catch {
    return null;
  }
};

/**
 * Chooses between real backend data, which needs a sign-in, and the in-browser simulation, which doesn't, so it still
 * works when the backend is down. They are separate providers, so their data can never mix, and the mode only changes
 * when the user switches it. A session lasts until the tab closes.
 */
export const AppProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [mode, setMode] = useState<DataMode>(initialMode);
  const [user, setUser] = useState<SignedInUser | null>(restoreSession);

  const setDataMode = useCallback((next: DataMode) => {
    setMode(next);
    try {
      window.localStorage.setItem(MODE_KEY, next);
    } catch {
      // Without storage the choice just lasts until the page reloads.
    }
  }, []);

  const signIn = useCallback((signedIn: SignedInUser, authorization: string) => {
    setAuthorization(authorization);
    setUser(signedIn);
    try {
      window.sessionStorage.setItem(SESSION_KEY, JSON.stringify({ user: signedIn, authorization }));
    } catch {
      // Without storage the session just lasts until the page reloads.
    }
  }, []);

  const signOut = useCallback(() => {
    setAuthorization(null);
    setUser(null);
    try {
      window.sessionStorage.removeItem(SESSION_KEY);
    } catch {
      // Nothing was stored.
    }
  }, []);

  if (mode === 'demo') {
    return <MockAppProvider setDataMode={setDataMode}>{children}</MockAppProvider>;
  }
  if (!user) {
    return <LoginScreen onSignIn={signIn} onUseDemo={() => setDataMode('demo')} />;
  }
  return (
    <LiveAppProvider setDataMode={setDataMode} user={user} signOut={signOut}>
      {children}
    </LiveAppProvider>
  );
};
