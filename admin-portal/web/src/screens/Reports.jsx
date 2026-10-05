import { useCallback, useEffect, useState } from 'react';
import { api, download } from '../api/client.js';
import { Card, DailyStats, RevenueTiles, TopMovies } from '../components/ReportPanels.jsx';
import { ErrorNotice, errorMessage } from '../components/ui.jsx';

// GET /admin/reports → { revenueToday, revenueWeek, revenueMonth, topMovies (10), dailyStats (30 days) }.
// Same ReportService figures as the dashboard. Display only.
export default function Reports() {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState(null);
  const [exported, setExported] = useState(null);

  const load = useCallback(async (signal) => {
    setLoading(true);
    setError(null);
    try {
      setData(await api('/admin/reports', { signal }));
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

  async function onExport() {
    setExporting(true);
    setExportError(null);
    setExported(null);
    try {
      // Through the API client so the Bearer header is sent; never a bare link to the URL.
      const blob = await download('/admin/reports/export', 'report.csv');
      setExported(`report.csv saved (${Math.max(1, Math.round(blob.size / 1024))} KB).`);
    } catch (err) {
      setExportError(errorMessage(err, 'The server could not build the CSV.'));
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1>Reports</h1>
        <div className="header-actions">
          <button type="button" className="ghost" onClick={() => load()} disabled={loading}>
            {loading ? 'Loading…' : 'Refresh'}
          </button>
          <button type="button" onClick={onExport} disabled={exporting}>
            {exporting ? 'Preparing…' : 'Export CSV'}
          </button>
        </div>
      </header>

      {exportError && <div className="notice notice-error" role="alert">Export failed: {exportError}</div>}
      {exported && <div className="notice notice-success" role="status">{exported}</div>}
      <p className="muted small">
        The CSV lists every booking (all statuses), one row per booking, with show, customer, total, status, and booked-at time.
        The revenue figures below count confirmed bookings only, by the time they were booked.
      </p>

      <ErrorNotice error={error} onRetry={() => load()} />
      {!data && loading && <p className="muted">Loading reports…</p>}

      {data && (
        <>
          <RevenueTiles data={data} />
          <div className="grid-2">
            <Card title="Top movies" subtitle="By tickets sold, top 10">
              <TopMovies rows={data.topMovies} />
            </Card>
            <Card title="Last 30 days" subtitle="Revenue and bookings per day">
              <DailyStats rows={data.dailyStats} />
            </Card>
          </div>
        </>
      )}
    </div>
  );
}
