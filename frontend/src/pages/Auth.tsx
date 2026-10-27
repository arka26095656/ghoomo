import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Logo } from '../components/Logo';
import { useAuth } from '../lib/auth';
import { ApiError } from '../lib/api';

export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { login, register } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const pending = params.get('q');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const isRegister = mode === 'register';
  const next = pending ? `/plan?q=${encodeURIComponent(pending)}` : '/plan';
  const switchLink = (isRegister ? '/login' : '/register') + (pending ? `?q=${encodeURIComponent(pending)}` : '');

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFields({});
    try {
      if (isRegister) await register(name, email, password);
      else await login(email, password);
      navigate(next, { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
        setFields(err.fields ?? {});
      } else setError('Something went wrong. Try again.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex min-h-full flex-col items-center justify-center px-5 py-12">
      <Link to="/" aria-label="Ghoomo home">
        <Logo className="text-xl" />
      </Link>
      <div className="panel mt-8 w-full max-w-sm p-6 sm:p-7">
        <h1 className="text-2xl font-semibold">{isRegister ? 'Create your account' : 'Welcome back'}</h1>
        {pending && (
          <p className="mt-2 text-sm text-muted">
            We'll plan <span className="font-medium text-ink">“{pending}”</span> right after.
          </p>
        )}
        <form onSubmit={submit} className="mt-6 space-y-4" noValidate>
          {isRegister && (
            <div>
              <label className="label" htmlFor="name">
                Name
              </label>
              <input id="name" className="field" value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" required />
              {fields.name && <p className="mt-1 text-sm text-danger">{fields.name}</p>}
            </div>
          )}
          <div>
            <label className="label" htmlFor="email">
              Email
            </label>
            <input
              id="email"
              type="email"
              className="field"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="email"
              required
            />
            {fields.email && <p className="mt-1 text-sm text-danger">{fields.email}</p>}
          </div>
          <div>
            <label className="label" htmlFor="password">
              Password
            </label>
            <input
              id="password"
              type="password"
              className="field"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete={isRegister ? 'new-password' : 'current-password'}
              minLength={isRegister ? 8 : undefined}
              required
            />
            {isRegister && !fields.password && <p className="mt-1 text-sm text-muted">At least 8 characters.</p>}
            {fields.password && <p className="mt-1 text-sm text-danger">{fields.password}</p>}
          </div>
          {error && !Object.keys(fields).length && (
            <p className="rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger" role="alert">
              {error}
            </p>
          )}
          <button type="submit" className="btn-primary w-full py-2.5" disabled={busy}>
            {busy ? 'One moment…' : isRegister ? 'Create account' : 'Log in'}
          </button>
        </form>
      </div>
      <p className="mt-5 text-sm text-muted">
        {isRegister ? 'Already have an account?' : 'New to Ghoomo?'}{' '}
        <Link to={switchLink} className="font-semibold text-ink underline-offset-4 hover:underline">
          {isRegister ? 'Log in' : 'Create an account'}
        </Link>
      </p>
    </div>
  );
}
