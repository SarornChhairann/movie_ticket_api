import BarChart from './BarChart.jsx';
import { Empty } from './ui.jsx';
import { formatCount, formatMoney, formatShortDate, orDash } from '../format.js';

// Pieces shared by the dashboard and the reports page, which read the same ReportService figures.

/**
 * Revenue periods as the server computes them: today from midnight, the last 7 days
 * including today, and the calendar month from the 1st. Confirmed bookings, by booking time.
 */
export function RevenueTiles({ data }) {
  return (
    <section className="tiles" aria-label="Revenue">
      <Tile label="Revenue today" value={formatMoney(data.revenueToday)} />
      <Tile label="Revenue, last 7 days" value={formatMoney(data.revenueWeek)} hint="Including today" />
      <Tile label="Revenue this month" value={formatMoney(data.revenueMonth)} hint="Since the 1st" />
    </section>
  );
}

export function Tile({ label, value, hint }) {
  return (
    <div className="tile">
      <div className="tile-label">{label}</div>
      <div className="tile-value">{value}</div>
      {hint && <div className="muted small">{hint}</div>}
    </div>
  );
}

export function Card({ title, subtitle, children }) {
  return (
    <section className="panel">
      <div className="panel-head">
        <h2>{title}</h2>
        {subtitle && <span className="muted small">{subtitle}</span>}
      </div>
      {children}
    </section>
  );
}

function TableToggle({ children }) {
  return (
    <details className="table-toggle">
      <summary>Show as table</summary>
      {children}
    </details>
  );
}

export function TopMovies({ rows }) {
  if (!rows?.length) return <Empty>No ticket sales yet.</Empty>;
  return (
    <>
      <BarChart
        ariaLabel="Top movies by tickets sold"
        formatValue={(v) => `${formatCount(v)} tickets`}
        rows={rows.map((m) => ({ key: m.title, label: orDash(m.title), value: m.tickets }))}
      />
      <TableToggle>
        <table className="table">
          <thead><tr><th>Movie</th><th className="num">Tickets</th><th className="num">Revenue</th></tr></thead>
          <tbody>
            {rows.map((m) => (
              <tr key={m.title}>
                <td>{orDash(m.title)}</td>
                <td className="num">{formatCount(m.tickets)}</td>
                <td className="num">{formatMoney(m.revenue)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </TableToggle>
    </>
  );
}

export function DailyStats({ rows }) {
  if (!rows?.length) return <Empty>No daily figures.</Empty>;
  const hasActivity = rows.some((d) => Number(d.count) > 0 || Number(d.revenue) > 0);
  const tooltip = (d) => `${formatShortDate(d.date)}: ${formatMoney(d.revenue)} · ${formatCount(d.count)} bookings`;
  return (
    <>
      {!hasActivity && <Empty>No bookings in the last {rows.length} days.</Empty>}
      {hasActivity && (
        <div className="stack">
          <div>
            <div className="chart-title">Revenue</div>
            <BarChart orientation="vertical" ariaLabel="Revenue per day" formatValue={formatMoney}
              rows={rows.map((d) => ({ key: d.date, label: formatShortDate(d.date), value: d.revenue, tooltip: tooltip(d) }))} />
          </div>
          <div>
            <div className="chart-title">Bookings</div>
            <BarChart orientation="vertical" ariaLabel="Bookings per day" formatValue={formatCount}
              rows={rows.map((d) => ({ key: d.date, label: formatShortDate(d.date), value: d.count, tooltip: tooltip(d) }))} />
          </div>
        </div>
      )}
      <TableToggle>
        <table className="table">
          <thead><tr><th>Date</th><th className="num">Bookings</th><th className="num">Revenue</th></tr></thead>
          <tbody>
            {rows.map((d) => (
              <tr key={d.date}>
                <td>{formatShortDate(d.date)}</td>
                <td className="num">{formatCount(d.count)}</td>
                <td className="num">{formatMoney(d.revenue)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </TableToggle>
    </>
  );
}
