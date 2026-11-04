import React, { useState } from 'react';
import { FlaskConical, Layers, LogIn } from 'lucide-react';
import type { SignedInUser, UserRole } from '../../types/tcem';
import { ApiError, api, basicAuthorization, setAuthorization } from '../../services/api';

export const LoginScreen: React.FC<{
  onSignIn: (user: SignedInUser, authorization: string) => void;
  onUseDemo: () => void;
}> = ({ onSignIn, onUseDemo }) => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [backendDown, setBackendDown] = useState(false);
  const [busy, setBusy] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setBackendDown(false);
    const authorization = basicAuthorization(username.trim(), password);
    setAuthorization(authorization);
    try {
      const me = await api.me();
      onSignIn({ username: me.username, role: me.role as UserRole }, authorization);
    } catch (failure) {
      setAuthorization(null);
      if (failure instanceof ApiError && failure.status === 401) {
        setError('Wrong username or password.');
      } else {
        setBackendDown(true);
        setError('The backend cannot be reached, so you cannot sign in right now.');
      }
    } finally {
      setBusy(false);
    }
  };

  const fieldClass =
    'w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100 focus:border-indigo-500 focus:outline-none';

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#0b0f19] p-4 font-sans text-slate-100">
      <div className="w-full max-w-sm space-y-5 rounded-2xl border border-slate-800 bg-slate-900/60 p-6">
        <div className="flex items-center gap-2.5">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-tr from-indigo-600 to-cyan-500">
            <Layers className="h-5 w-5 text-white" />
          </div>
          <div>
            <h1 className="text-base font-bold text-white">TCEM Platform</h1>
            <p className="text-xs text-slate-400">Sign in to use live data</p>
          </div>
        </div>

        <form onSubmit={submit} className="space-y-3">
          <label className="block space-y-1 text-xs text-slate-300">
            <span>Username</span>
            <input
              className={fieldClass}
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              autoFocus
              required
            />
          </label>
          <label className="block space-y-1 text-xs text-slate-300">
            <span>Password</span>
            <input
              className={fieldClass}
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
          </label>
          {error && (
            <p role="alert" className={`text-xs ${backendDown ? 'text-amber-300' : 'text-red-400'}`}>
              {error}
            </p>
          )}
          <button
            type="submit"
            disabled={busy}
            className="flex w-full items-center justify-center gap-2 rounded-lg bg-indigo-600 px-3 py-2 text-sm font-semibold text-white hover:bg-indigo-500 disabled:opacity-60"
          >
            <LogIn size={15} />
            {busy ? 'Signing in...' : 'Sign in'}
          </button>
        </form>

        <button
          onClick={onUseDemo}
          className="flex w-full items-center justify-center gap-2 rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-xs font-semibold text-amber-200 hover:bg-amber-500/20"
        >
          <FlaskConical size={14} />
          Use demo data instead
        </button>
        <p className="text-center text-[11px] text-slate-500">Demo data is a simulation and needs no sign-in.</p>
      </div>
    </div>
  );
};
