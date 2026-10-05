import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { api } from '../../api/client.js';
import { ConfirmDialog, Empty, ErrorNotice, errorMessage } from '../../components/ui.jsx';
import { formatCount, formatMoney, formatShortDate, orDash } from '../../format.js';

function todayIso() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

// GET /admin/showtimes → { showtimes: [...], editLockHours }
export default function ShowtimeList() {
  const location = useLocation();
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [upcomingOnly, setUpcomingOnly] = useState(true);
  const [state, setState] = useState(''); // '', 'published', 'blocked'
  const [busyId, setBusyId] = useState(null);
  const [actionError, setActionError] = useState(null);
  const [notice, setNotice] = useState(location.state?.notice || null);
  const [highlight, setHighlight] = useState(location.state?.highlight || null);
  const [pending, setPending] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await api('/admin/showtimes'));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    const today = todayIso();
    return (data?.showtimes || [])
      .filter((s) => !upcomingOnly || String(s.showDate) >= today)
      .filter((s) => !state || (state === 'blocked' ? s.blocked : !s.blocked))
      .filter((s) => !q || `${s.movieTitle} ${s.hallName}`.toLowerCase().includes(q))
      .sort((a, b) => `${a.showDate} ${a.showTime}`.localeCompare(`${b.showDate} ${b.showTime}`));
  }, [data, query, upcomingOnly, state]);

  async function runAction(s, path, describe) {
    setBusyId(s.id);
    setActionError(null);
    setNotice(null);
    try {
      const result = await api(`/admin/showtimes/${s.id}/${path}`, { method: 'POST' });
      await load();
      setNotice(describe(result));
      setHighlight(result?.id ?? s.id);
    } catch (err) {
      setActionError(err);
    } finally {
      setBusyId(null);
    }
  }

  function toggleBlock(s) {
    runAction(s, 'block', (r) => `${r.movieTitle} on ${formatShortDate(r.showDate)} is now ${r.blocked ? 'blocked' : 'published'}.`);
  }

  function copyNextWeek(s) {
    runAction(s, 'copy-next-week', (r) => `Copied to ${formatShortDate(r.showDate)} ${String(r.showTime).slice(0, 5)}.`);
  }

  async function confirmDelete() {
    setDeleting(true);
    setDeleteError(null);
    try {
      await api(`/admin/showtimes/${pending.id}`, { method: 'DELETE' });
      setData((d) => ({ ...d, showtimes: d.showtimes.filter((s) => s.id !== pending.id) }));
      setPending(null);
    } catch (err) {
      setDeleteError(errorMessage(err, 'The server could not delete this showtime.'));
    } finally {
      setDeleting(false);
    }
  }

  const lockHours = data?.editLockHours;

  return (
    <div className="page">
      <header className="page-header">
        <h1>Showtimes</h1>
        <Link className="button" to="/showtimes/new">New showtime</Link>
      </header>

      <div className="filters">
        <input type="search" placeholder="Search movie or hall" value={query}
          onChange={(e) => setQuery(e.target.value)} aria-label="Search showtimes" />
        <select value={state} onChange={(e) => setState(e.target.value)} aria-label="Filter by state">
          <option value="">Published and blocked</option>
          <option value="published">Published only</option>
          <option value="blocked">Blocked only</option>
        </select>
        <label className="check">
          <input type="checkbox" checked={upcomingOnly} onChange={(e) => setUpcomingOnly(e.target.checked)} /> From today on
        </label>
      </div>

      {lockHours != null && (
        <p className="muted small">
          Showtimes starting within {lockHours} hour(s) are locked: they cannot be edited, blocked, or published.
        </p>
      )}

      <ErrorNotice error={error} onRetry={load} />
      <ErrorNotice error={actionError} />
      {notice && <div className="notice notice-success" role="status">{notice}</div>}
      {!data && loading && <p className="muted">Loading showtimes…</p>}

      {data && (
        data.showtimes.length === 0 ? <Empty>No showtimes yet. Schedule the first one.</Empty>
          : visible.length === 0 ? <Empty>No showtimes match these filters.</Empty>
            : (
              <div className="panel table-scroll">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Movie</th><th>Hall</th><th>Date</th><th>Time</th>
                      <th className="num">Normal / seat</th><th className="num">Couple / pair</th>
                      <th>State</th><th className="num">Bookings</th><th />
                    </tr>
                  </thead>
                  <tbody>
                    {visible.map((s) => {
                      const busy = busyId === s.id;
                      const hasActive = Number(s.activeBookingCount) > 0;
                      return (
                        <tr key={s.id} className={highlight === s.id ? 'row-highlight' : undefined}>
                          <td><Link to={`/showtimes/${s.id}`}>{orDash(s.movieTitle)}</Link></td>
                          <td className="wrap">{orDash(s.hallName)}</td>
                          <td>{formatShortDate(s.showDate)}</td>
                          <td>{s.showTime ? String(s.showTime).slice(0, 5) : '—'}</td>
                          <td className="num">{formatMoney(s.normalPrice)}</td>
                          <td className="num">{formatMoney(s.couplePrice)}</td>
                          <td>
                            {s.blocked
                              ? <span className="badge badge-muted">Blocked</span>
                              : <span className="badge badge-good">Published</span>}
                            {s.editLocked && <span className="badge badge-warning" title={`Starts within ${lockHours} hour(s)`}> Locked</span>}
                          </td>
                          <td className="num" title={`${formatCount(s.activeBookingCount)} active of ${formatCount(s.bookingCount)} total`}>
                            {formatCount(s.activeBookingCount)}
                            {Number(s.bookingCount) > Number(s.activeBookingCount) && <span className="muted"> / {formatCount(s.bookingCount)}</span>}
                          </td>
                          <td className="row-actions">
                            <Link to={`/showtimes/${s.id}`}>{s.editLocked ? 'View' : 'Edit'}</Link>
                            <button type="button" className="link" onClick={() => toggleBlock(s)}
                              disabled={busy || s.editLocked}
                              title={s.editLocked ? `Locked: starts within ${lockHours} hour(s)` : undefined}>
                              {s.blocked ? 'Publish' : 'Block'}
                            </button>
                            <button type="button" className="link" onClick={() => copyNextWeek(s)} disabled={busy}>
                              Copy next week
                            </button>
                            <button type="button" className="link danger-text"
                              disabled={busy || hasActive}
                              title={hasActive ? `Has ${s.activeBookingCount} active booking(s)` : undefined}
                              onClick={() => { setDeleteError(null); setPending(s); }}>
                              Delete
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )
      )}

      <ConfirmDialog open={!!pending} title="Delete showtime?" busy={deleting}
        onConfirm={confirmDelete} onCancel={() => setPending(null)}>
        <p>
          {pending?.movieTitle} on {formatShortDate(pending?.showDate)} at {String(pending?.showTime || '').slice(0, 5)} will
          be removed. Only blocked showtimes without bookings can be deleted.
        </p>
        {deleteError && <p className="error" role="alert">{deleteError}</p>}
      </ConfirmDialog>
    </div>
  );
}
