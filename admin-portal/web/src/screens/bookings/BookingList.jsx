import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../../api/client.js';
import StatusBadge from '../../components/StatusBadge.jsx';
import { ConfirmDialog, Empty, ErrorNotice, errorMessage } from '../../components/ui.jsx';
import { formatDateTime, formatMoney, formatShowtime, orDash } from '../../format.js';

const STATUSES = ['CONFIRMED', 'PENDING', 'CANCELLED'];

// GET /admin/bookings[?q=] → BookingSummary[]. Search runs on the server
// (booking id, customer name or email, movie title).
export default function BookingList() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') || '';
  const [draft, setDraft] = useState(q);
  const [status, setStatus] = useState('');
  const [bookings, setBookings] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(null);
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState(null);
  const [notice, setNotice] = useState(null);

  const load = useCallback(async (signal) => {
    setLoading(true);
    setError(null);
    try {
      const path = q ? `/admin/bookings?q=${encodeURIComponent(q)}` : '/admin/bookings';
      setBookings(await api(path, { signal }));
    } catch (err) {
      if (err.name !== 'AbortError') setError(err);
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [q]);

  useEffect(() => {
    const ctrl = new AbortController();
    load(ctrl.signal);
    return () => ctrl.abort();
  }, [load]);

  useEffect(() => { setDraft(q); }, [q]);

  function onSearch(e) {
    e.preventDefault();
    const next = draft.trim();
    setParams(next ? { q: next } : {}, { replace: true });
  }

  const visible = useMemo(
    () => (bookings || [])
      .filter((b) => !status || b.status === status)
      .sort((a, b) => String(b.bookedAt || '').localeCompare(String(a.bookedAt || ''))),
    [bookings, status],
  );

  async function confirmCancel() {
    setCancelling(true);
    setCancelError(null);
    try {
      await api(`/admin/bookings/${pending.id}/cancel`, { method: 'POST' });
      setBookings((list) => list.map((b) => (b.id === pending.id ? { ...b, status: 'CANCELLED' } : b)));
      setNotice(`Booking #${pending.id} is cancelled. Its seats are on sale again. Loyalty points and promo usage were not returned.`);
      setPending(null);
    } catch (err) {
      setCancelError(errorMessage(err, 'The server could not cancel this booking.'));
    } finally {
      setCancelling(false);
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1>Bookings</h1>
        <button type="button" className="ghost" onClick={() => load()} disabled={loading}>
          {loading ? 'Loading…' : 'Refresh'}
        </button>
      </header>

      <form className="filters" onSubmit={onSearch} role="search">
        <input type="search" placeholder="Booking #, customer name or email, movie" value={draft}
          onChange={(e) => setDraft(e.target.value)} aria-label="Search bookings" />
        <button type="submit">Search</button>
        {q && <button type="button" className="ghost" onClick={() => setParams({}, { replace: true })}>Clear</button>}
        <select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Filter by status">
          <option value="">All statuses</option>
          {STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
      </form>

      <ErrorNotice error={error} onRetry={() => load()} />
      {notice && <div className="notice notice-success" role="status">{notice}</div>}
      {!bookings && loading && <p className="muted">Loading bookings…</p>}

      {bookings && (
        bookings.length === 0 ? <Empty>{q ? `No bookings match “${q}”.` : 'No bookings yet.'}</Empty>
          : visible.length === 0 ? <Empty>No {status.toLowerCase()} bookings in these results.</Empty>
            : (
              <div className="panel table-scroll">
                <p className="muted small table-caption">{visible.length} booking(s){q ? ` matching “${q}”` : ''}</p>
                <table className="table">
                  <thead>
                    <tr>
                      <th>#</th><th>Movie</th><th>Hall</th><th>Show</th><th>Customer</th>
                      <th className="num">Total</th><th>Status</th><th>Booked</th><th />
                    </tr>
                  </thead>
                  <tbody>
                    {visible.map((b) => (
                      <tr key={b.id}>
                        <td className="muted">{b.id}</td>
                        <td>{orDash(b.movieTitle)}</td>
                        <td className="wrap">{orDash(b.hallName)}</td>
                        <td>{formatShowtime(b.showDate, b.showTime)}</td>
                        <td>{orDash(b.customerName)}</td>
                        <td className="num">{formatMoney(b.totalAmount)}</td>
                        <td><StatusBadge status={b.status} /></td>
                        <td>{formatDateTime(b.bookedAt)}</td>
                        <td className="row-actions">
                          {b.status === 'CONFIRMED' && (
                            <button type="button" className="link danger-text"
                              onClick={() => { setCancelError(null); setNotice(null); setPending(b); }}>
                              Cancel
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
      )}

      <ConfirmDialog open={!!pending} title={`Cancel booking #${pending?.id}?`} confirmLabel="Cancel booking"
        busy={cancelling} onConfirm={confirmCancel} onCancel={() => setPending(null)}>
        <p>
          {pending?.movieTitle}, {formatShowtime(pending?.showDate, pending?.showTime)}, for {orDash(pending?.customerName)}
          {' '}({formatMoney(pending?.totalAmount)}).
        </p>
        <ul className="plain-list">
          <li>Allowed even inside the customer cancellation window.</li>
          <li>All tickets are cancelled and the seats go back on sale.</li>
          <li>Loyalty points and promo usage are <strong>not</strong> returned. No refund is issued here.</li>
          <li>The customer is emailed a cancellation notice.</li>
        </ul>
        {cancelError && <p className="error" role="alert">{cancelError}</p>}
      </ConfirmDialog>
    </div>
  );
}
