import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../api/client.js';
import StatusBadge from '../../components/StatusBadge.jsx';
import { ConfirmDialog, Empty, ErrorNotice, Field, errorMessage } from '../../components/ui.jsx';
import { useAuth } from '../../auth/AuthContext.jsx';
import { formatCount, formatDateTime, formatMoney, formatShowtime, orDash } from '../../format.js';
import { USER_STATUSES, UserStatusBadge } from './userOptions.jsx';

// GET /admin/users/{id} → { user, bookings, statuses }. No password field: it is never returned by the API.
export default function UserDetail() {
  const { id } = useParams();
  const { session } = useAuth();
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await api(`/admin/users/${id}`));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { load(); }, [load]);

  function onUserChange(user) {
    setData((d) => ({ ...d, user }));
  }

  if (!data && loading) return <div className="page"><p className="muted">Loading user…</p></div>;
  if (!data) {
    return (
      <div className="page">
        <BackLink />
        <ErrorNotice error={error} onRetry={load} />
      </div>
    );
  }

  const { user, bookings } = data;
  const isSelf = session?.email && user.email?.toLowerCase() === session.email.toLowerCase();

  return (
    <div className="page">
      <BackLink />
      <header className="page-header">
        <h1>{orDash(user.name)}</h1>
        <UserStatusBadge status={user.status} />
      </header>

      <section className="panel">
        <dl className="facts">
          <div><dt>Email</dt><dd>{orDash(user.email)}</dd></div>
          <div><dt>Phone</dt><dd>{orDash(user.phone)}</dd></div>
          <div><dt>Role</dt><dd>{orDash(user.role)}</dd></div>
          <div><dt>Points</dt><dd>{formatCount(user.points)}</dd></div>
        </dl>
      </section>

      <div className="grid-2">
        <StatusCard user={user} isSelf={isSelf} onChange={onUserChange} />
        <PointsCard user={user} onChange={onUserChange} />
      </div>

      <section className="panel">
        <div className="panel-head"><h2>Bookings</h2><span className="muted small">{bookings.length} total</span></div>
        {bookings.length === 0 ? <Empty>This user has no bookings.</Empty> : (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr><th>#</th><th>Movie</th><th>Hall</th><th>Show</th><th className="num">Total</th><th>Status</th><th>Booked</th></tr>
              </thead>
              <tbody>
                {bookings.map((b) => (
                  <tr key={b.id}>
                    <td><Link to={`/bookings?q=${b.id}`}>{b.id}</Link></td>
                    <td>{orDash(b.movieTitle)}</td>
                    <td className="wrap">{orDash(b.hallName)}</td>
                    <td>{formatShowtime(b.showDate, b.showTime)}</td>
                    <td className="num">{formatMoney(b.totalAmount)}</td>
                    <td><StatusBadge status={b.status} /></td>
                    <td>{formatDateTime(b.bookedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}

function StatusCard({ user, isSelf, onChange }) {
  const [target, setTarget] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [done, setDone] = useState(null);

  async function confirm() {
    setBusy(true);
    setError(null);
    try {
      onChange(await api(`/admin/users/${user.id}/status`, { method: 'POST', body: { status: target } }));
      setDone(`Status set to ${target}.`);
      setTarget(null);
    } catch (err) {
      setError(errorMessage(err, 'The server could not change the status.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel">
      <div className="panel-head"><h2>Account status</h2></div>
      {isSelf ? (
        <p className="muted small">This is your own account. Changing it here could lock you out, so it is disabled.</p>
      ) : (
        <>
          <div className="segmented" role="group" aria-label="Set status">
            {USER_STATUSES.map((s) => (
              <button key={s.value} type="button"
                className={user.status === s.value ? 'is-current' : 'ghost'}
                disabled={user.status === s.value}
                onClick={() => { setError(null); setDone(null); setTarget(s.value); }}>
                {s.label}
              </button>
            ))}
          </div>
          <p className="muted small">Suspended and banned accounts cannot sign in or use the app.</p>
        </>
      )}
      {done && <p className="success" role="status">{done}</p>}
      <ConfirmDialog open={!!target} title={`Set ${user.name || user.email} to ${target}?`}
        confirmLabel="Change status" busy={busy} onConfirm={confirm} onCancel={() => setTarget(null)}>
        <p>
          {target === 'ACTIVE'
            ? 'The account can sign in and book again.'
            : 'The account is signed out and turned away by the API. Existing bookings are not cancelled.'}
        </p>
        {error && <p className="error" role="alert">{error}</p>}
      </ConfirmDialog>
    </section>
  );
}

function PointsCard({ user, onChange }) {
  const [delta, setDelta] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [done, setDone] = useState(null);

  const parsed = /^-?\d+$/.test(delta.trim()) ? Number.parseInt(delta.trim(), 10) : null;

  async function onSubmit(e) {
    e.preventDefault();
    if (!parsed) return;
    setBusy(true);
    setError(null);
    setDone(null);
    try {
      const updated = await api(`/admin/users/${user.id}/points`, { method: 'POST', body: { delta: parsed } });
      onChange(updated);
      setDone(`${parsed > 0 ? 'Added' : 'Removed'} ${formatCount(Math.abs(parsed))} point(s). Balance is now ${formatCount(updated.points)}.`);
      setDelta('');
    } catch (err) {
      setError(errorMessage(err, 'The server could not adjust points.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel">
      <div className="panel-head"><h2>Loyalty points</h2><span className="muted small">Balance {formatCount(user.points)}</span></div>
      <form className="form" onSubmit={onSubmit}>
        <Field label="Change by (use a minus sign to remove)">
          <input type="number" step={1} value={delta} onChange={(e) => setDelta(e.target.value)} placeholder="e.g. 50 or -20" />
        </Field>
        <p className="muted small">This is a change, not a new total. The server never lets the balance drop below 0.</p>
        {error && <p className="error" role="alert">{error}</p>}
        {done && <p className="success" role="status">{done}</p>}
        <div className="form-actions">
          <button type="submit" disabled={busy || !parsed}>{busy ? 'Saving…' : 'Apply change'}</button>
        </div>
      </form>
    </section>
  );
}

function BackLink() {
  return <Link className="back-link" to="/users">← Users</Link>;
}
