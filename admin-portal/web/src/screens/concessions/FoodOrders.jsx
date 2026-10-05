import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../../api/client.js';
import { Empty, ErrorNotice } from '../../components/ui.jsx';
import { formatCount, formatMoney, formatShortDate, formatShowtime, orDash } from '../../format.js';

function todayIso() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

// GET /admin/food-orders?date= — orders for showtimes on that date (server default: today). Read-only.
export default function FoodOrders() {
  const [params, setParams] = useSearchParams();
  const date = params.get('date') || todayIso();
  const [orders, setOrders] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setOrders(await api(`/admin/food-orders?date=${encodeURIComponent(date)}`));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [date]);

  useEffect(() => { load(); }, [load]);

  const sorted = useMemo(
    () => [...(orders || [])].sort((a, b) =>
      `${a.showTime} ${a.bookingId}`.localeCompare(`${b.showTime} ${b.bookingId}`)),
    [orders],
  );

  return (
    <div className="page">
      <header className="page-header">
        <h1>Food orders</h1>
        <button type="button" className="ghost" onClick={load} disabled={loading}>{loading ? 'Loading…' : 'Refresh'}</button>
      </header>

      <div className="filters">
        <label className="check">
          Show date
          <input type="date" value={date} required
            onChange={(e) => e.target.value && setParams({ date: e.target.value }, { replace: true })} />
        </label>
        {date !== todayIso() && (
          <button type="button" className="ghost" onClick={() => setParams({}, { replace: true })}>Today</button>
        )}
      </div>

      <ErrorNotice error={error} onRetry={load} />
      {!orders && loading && <p className="muted">Loading orders…</p>}

      {orders && (
        sorted.length === 0 ? <Empty>No food orders for showtimes on {formatShortDate(date)}.</Empty> : (
          <div className="panel table-scroll">
            <p className="muted small table-caption">{sorted.length} line(s) for showtimes on {formatShortDate(date)}</p>
            <table className="table">
              <thead>
                <tr>
                  <th>Booking</th><th>Customer</th><th>Movie</th><th>Show</th><th>Item</th>
                  <th className="num">Qty</th><th className="num">Unit</th><th className="num">Line total</th>
                </tr>
              </thead>
              <tbody>
                {sorted.map((o) => (
                  <tr key={o.id}>
                    <td className="muted">#{o.bookingId}</td>
                    <td>{orDash(o.customerName)}</td>
                    <td>{orDash(o.movieTitle)}</td>
                    <td>{formatShowtime(o.showDate, o.showTime)}</td>
                    <td>{orDash(o.foodItemName)}</td>
                    <td className="num">{formatCount(o.quantity)}</td>
                    <td className="num">{formatMoney(o.unitPrice)}</td>
                    <td className="num">{formatMoney(o.lineTotal)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      )}
    </div>
  );
}
