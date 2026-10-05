import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { Card, DailyStats, RevenueTiles, Tile, TopMovies } from '../components/ReportPanels.jsx';
import StatusBadge from '../components/StatusBadge.jsx';
import { Empty } from '../components/ui.jsx';
import { formatCount, formatMoney, formatShowtime, orDash } from '../format.js';

// GET /admin/dashboard — AdminDashboardController.DashboardResponse. Display only.
export default function Dashboard() {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async (signal) => {
    setLoading(true);
    setError(null);
    try {
      setData(await api('/admin/dashboard', { signal }));
    } catch (err) {
      if (err.name !== 'AbortError') setError(err);
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, []);

  useEffect(() => {
    const ctrl = new AbortController();
    load(ctrl.signal);
    return () => ctrl.abort();
  }, [load]);

  return (
    <div className="page">
      <header className="page-header">
        <h1>Dashboard</h1>
        <button type="button" className="ghost" onClick={() => load()} disabled={loading}>
          {loading ? 'Loading…' : 'Refresh'}
        </button>
      </header>

      {error && (
        <div className="notice notice-error" role="alert">
          {error.status === 403 ? 'This account cannot read the dashboard.' : error.message || 'Could not load the dashboard.'}
          <button type="button" className="ghost" onClick={() => load()}>Try again</button>
        </div>
      )}

      {!data && loading && <p className="muted">Loading dashboard…</p>}

      {data && (
        <>
          <section className="tiles" aria-label="Counts">
            <Tile label="Movies" value={formatCount(data.movieCount)} />
            <Tile label="Halls" value={formatCount(data.hallCount)} />
            <Tile label="Showtimes" value={formatCount(data.showtimeCount)} />
            <Tile label="Bookings" value={formatCount(data.bookingCount)} hint={`${formatCount(data.bookingsToday)} today`} />
            <Tile label="Users" value={formatCount(data.userCount)} />
          </section>

          <RevenueTiles data={data} />

          <div className="grid-2">
            <Card title="Top movies" subtitle="By tickets sold, top 5">
              <TopMovies rows={data.topMovies} />
            </Card>
            <Card title="Last 14 days" subtitle="Revenue and bookings per day">
              <DailyStats rows={data.dailyStats} />
            </Card>
          </div>

          <Card title="Recent bookings" subtitle="Latest 5">
            <RecentBookings rows={data.recentBookings} />
          </Card>
        </>
      )}
    </div>
  );
}

function RecentBookings({ rows }) {
  if (!rows?.length) return <Empty>No bookings yet.</Empty>;
  return (
    <div className="table-scroll">
      <table className="table">
        <thead>
          <tr>
            <th>#</th><th>Movie</th><th>Hall</th><th>Show</th><th>Customer</th>
            <th className="num">Total</th><th>Status</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((b) => (
            <tr key={b.id}>
              <td className="muted">{orDash(b.id)}</td>
              <td>{orDash(b.movieTitle)}</td>
              <td>{orDash(b.hallName)}</td>
              <td>{formatShowtime(b.showDate, b.showTime)}</td>
              <td>{orDash(b.customerName)}</td>
              <td className="num">{formatMoney(b.totalAmount)}</td>
              <td><StatusBadge status={b.status} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
