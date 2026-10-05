import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client.js';
import { ErrorNotice, errorMessage } from '../../components/ui.jsx';

// The six keys the server reads. Rates are decimals (0.10 = 10%).
const FIELDS = [
  { key: 'hold_minutes', label: 'Seat hold', unit: 'minutes', kind: 'int', min: 1,
    help: 'How long seats stay reserved while a customer checks out. Whole number.' },
  { key: 'cancellation_hours', label: 'Cancellation cut-off', unit: 'hours', kind: 'int', min: 0, max: 72,
    help: 'Customers cannot cancel within this many hours of the show. Showtime edits lock in the same window (max 72). Whole number.' },
  { key: 'service_fee', label: 'Service fee', unit: 'USD per booking', kind: 'money', min: 0,
    help: 'Flat amount added once per booking, e.g. 1.50.' },
  { key: 'tax_rate', label: 'Tax rate', unit: 'decimal', kind: 'rate',
    help: 'Decimal, not percent: 0.10 means 10%.' },
  { key: 'weekend_surcharge_rate', label: 'Weekend surcharge', unit: 'decimal', kind: 'rate',
    help: 'Decimal added on weekend shows: 0.10 means 10%.' },
  { key: 'morning_discount_rate', label: 'Morning discount', unit: 'decimal', kind: 'rate',
    help: 'Decimal off shows before noon: 0.10 means 10%.' },
];

function validate(field, raw) {
  const v = String(raw ?? '').trim();
  if (v === '') return 'Required.';
  if (field.kind === 'int') {
    if (!/^\d+$/.test(v)) return 'Whole number only.';
    const n = Number(v);
    if (field.min != null && n < field.min) return `At least ${field.min}.`;
    if (field.max != null && n > field.max) return `At most ${field.max}.`;
    return null;
  }
  if (!/^\d+(\.\d+)?$/.test(v)) return 'Number only, e.g. 0.10.';
  if (field.kind === 'rate' && Number(v) > 1) return 'Use a decimal between 0 and 1 (0.10 = 10%).';
  return null;
}

function asPercent(v) {
  const n = Number(v);
  if (!Number.isFinite(n) || String(v).trim() === '') return null;
  return `${Math.round(n * 10000) / 100}%`;
}

export default function Settings() {
  const [stored, setStored] = useState(null); // key → value from the server
  const [form, setForm] = useState({});
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const [saved, setSaved] = useState(false);

  const apply = useCallback((rows) => {
    const map = Object.fromEntries(rows.map((r) => [r.key, r.value]));
    setStored(map);
    setForm(Object.fromEntries(FIELDS.map((f) => [f.key, map[f.key] ?? ''])));
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      apply(await api('/admin/settings'));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [apply]);

  useEffect(() => { load(); }, [load]);

  const errors = Object.fromEntries(FIELDS.map((f) => [f.key, validate(f, form[f.key])]));
  const hasErrors = Object.values(errors).some(Boolean);
  const dirty = stored && FIELDS.some((f) => String(form[f.key]).trim() !== String(stored[f.key] ?? ''));

  async function onSubmit(e) {
    e.preventDefault();
    if (hasErrors) return;
    setSaving(true);
    setSaveError(null);
    setSaved(false);
    // Always the six keys, as strings.
    const body = Object.fromEntries(FIELDS.map((f) => [f.key, String(form[f.key]).trim()]));
    try {
      apply(await api('/admin/settings', { method: 'POST', body }));
      setSaved(true);
    } catch (err) {
      setSaveError(errorMessage(err, 'The server could not save the settings.'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="page">
      <header className="page-header"><h1>Pricing &amp; timing</h1></header>
      <ErrorNotice error={error} onRetry={load} />
      {!stored && loading && <p className="muted">Loading settings…</p>}

      {stored && (
        <form className="panel form" onSubmit={onSubmit} noValidate>
          <div className="settings-grid">
            {FIELDS.map((f) => {
              const err = errors[f.key];
              const changed = String(form[f.key]).trim() !== String(stored[f.key] ?? '');
              return (
                <label key={f.key} className="field">
                  <span>{f.label} <span className="muted small">({f.unit})</span>{changed && <span className="changed"> · changed</span>}</span>
                  <input inputMode={f.kind === 'int' ? 'numeric' : 'decimal'} value={form[f.key]}
                    onChange={(e) => setForm((s) => ({ ...s, [f.key]: e.target.value }))}
                    aria-invalid={!!err} className={err ? 'invalid' : undefined} />
                  {f.kind === 'rate' && !err && <span className="muted small">= {asPercent(form[f.key])}</span>}
                  {err ? <span className="error small">{err}</span> : <span className="muted small">{f.help}</span>}
                  <span className="muted small mono">{f.key}</span>
                </label>
              );
            })}
          </div>
          <p className="muted small">
            New values apply to the next seat hold and the next checkout quote. Bookings already made keep the prices they were sold at.
          </p>
          {saveError && <p className="error" role="alert">{saveError}</p>}
          {saved && !dirty && <p className="success" role="status">Saved.</p>}
          <div className="form-actions">
            <button type="button" className="ghost" disabled={!dirty || saving} onClick={() => apply(Object.entries(stored).map(([key, value]) => ({ key, value })))}>
              Reset
            </button>
            <button type="submit" disabled={!dirty || hasErrors || saving}>{saving ? 'Saving…' : 'Save settings'}</button>
          </div>
        </form>
      )}
    </div>
  );
}
