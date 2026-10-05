import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { AccessDeniedError, useAuth } from '../auth/AuthContext.jsx';

export default function Login() {
  const { session, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  if (session?.role === 'ADMIN') return <Navigate to="/" replace />;

  async function onSubmit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email.trim(), password);
      navigate(location.state?.from || '/', { replace: true });
    } catch (err) {
      if (err instanceof AccessDeniedError) {
        navigate('/access-denied', { replace: true });
        return;
      }
      if (err.code === 'ACCOUNT_BLOCKED') setError(err.message || 'This account is blocked.');
      else if (err.status === 401) setError('Wrong email or password.');
      else if (err.status) setError(err.message);
      else setError('Cannot reach the API. Is it running on port 8010?');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="center-page">
      <form className="card" onSubmit={onSubmit}>
        <h1>Ticket Admin</h1>
        <p className="muted">Sign in with an administrator account.</p>
        <label>
          Email
          <input type="email" autoComplete="username" value={email}
            onChange={(e) => setEmail(e.target.value)} required autoFocus />
        </label>
        <label>
          Password
          <input type="password" autoComplete="current-password" value={password}
            onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {error && <p className="error" role="alert">{error}</p>}
        <button type="submit" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
      </form>
    </div>
  );
}
