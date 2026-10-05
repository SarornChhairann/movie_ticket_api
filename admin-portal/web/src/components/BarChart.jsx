import { useState } from 'react';

/**
 * One-series bar chart in plain HTML. Horizontal bars for ranked lists,
 * columns for a time series. Each bar has a hover/focus tooltip and an
 * aria-label, and the caller supplies a table view alongside it.
 *
 * rows: [{ key, label, value, tooltip }]
 */
export default function BarChart({ rows, orientation = 'horizontal', formatValue, ariaLabel }) {
  const [active, setActive] = useState(null);
  const max = Math.max(0, ...rows.map((r) => Number(r.value) || 0));
  const pct = (v) => (max > 0 ? Math.max(0, (Number(v) || 0) / max) * 100 : 0);

  if (orientation === 'horizontal') {
    return (
      <div className="hbars" role="list" aria-label={ariaLabel}>
        {rows.map((r) => (
          <div key={r.key} className="hbar-row" role="listitem"
            aria-label={`${r.label}: ${formatValue(r.value)}`}>
            <span className="hbar-label" title={r.label}>{r.label}</span>
            <span className="hbar-track">
              <span className="hbar" style={{ width: `${pct(r.value)}%` }} />
            </span>
            <span className="hbar-value">{formatValue(r.value)}</span>
          </div>
        ))}
      </div>
    );
  }

  const activeRow = rows.find((r) => r.key === active);
  return (
    <div className="columns-wrap">
      <div className="columns" role="list" aria-label={ariaLabel} onMouseLeave={() => setActive(null)}>
        {rows.map((r) => (
          <div key={r.key} className={`column-slot${active === r.key ? ' is-active' : ''}`} role="listitem"
            tabIndex={0} aria-label={`${r.label}: ${formatValue(r.value)}`}
            onMouseEnter={() => setActive(r.key)} onFocus={() => setActive(r.key)}
            onBlur={() => setActive(null)}>
            <span className="column" style={{ height: `${pct(r.value)}%` }} />
          </div>
        ))}
      </div>
      <div className="column-axis" aria-hidden="true">
        <span>{rows[0]?.label}</span>
        <span>{rows[rows.length - 1]?.label}</span>
      </div>
      <div className="chart-tooltip" aria-live="polite">
        {activeRow ? (activeRow.tooltip || `${activeRow.label}: ${formatValue(activeRow.value)}`) : ' '}
      </div>
    </div>
  );
}
