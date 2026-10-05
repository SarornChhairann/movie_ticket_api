import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../api/client.js';
import { ErrorNotice, Field, errorMessage } from '../../components/ui.jsx';

const EMPTY = { name: '', address: '', openingHours: '', latitude: '', longitude: '' };

function toForm(cinema) {
  const form = { ...EMPTY };
  for (const key of Object.keys(EMPTY)) {
    if (cinema[key] !== null && cinema[key] !== undefined) form[key] = String(cinema[key]);
  }
  return form;
}

function toNumberOrNull(v) {
  const t = String(v).trim();
  return t === '' ? null : Number(t);
}

export default function CinemaForm() {
  const { id } = useParams(); // a UUID string, or "new"
  const isNew = id === 'new';
  const navigate = useNavigate();
  const location = useLocation();

  const [cinema, setCinema] = useState(null); // the stored row; its other fields go back unchanged
  const [form, setForm] = useState(EMPTY);
  const [loading, setLoading] = useState(!isNew);
  const [loadError, setLoadError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const [saved, setSaved] = useState(!!location.state?.saved);

  useEffect(() => {
    if (isNew) return undefined;
    let cancelled = false;
    setLoading(true);
    api(`/admin/cinemas/${encodeURIComponent(id)}`)
      .then((c) => { if (!cancelled) { setCinema(c); setForm(toForm(c)); } })
      .catch((err) => { if (!cancelled) setLoadError(err); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [id, isNew]);

  function set(key) {
    return (e) => setForm((f) => ({ ...f, [key]: e.target.value }));
  }

  async function onSubmit(e) {
    e.preventDefault();
    setSaving(true);
    setSaveError(null);
    setSaved(false);

    // The server saves the posted object as the whole row, so start from the stored
    // one (keeps totalHalls) and send id only when editing.
    const body = {
      ...(isNew ? {} : cinema),
      name: form.name.trim(),
      address: form.address.trim() || null,
      openingHours: form.openingHours.trim() || null,
      latitude: toNumberOrNull(form.latitude),
      longitude: toNumberOrNull(form.longitude),
    };

    try {
      const result = await api('/admin/cinemas', { method: 'POST', body });
      if (isNew) {
        navigate(`/cinemas/${result.id}`, { replace: true, state: { saved: true } });
        return;
      }
      setCinema(result);
      setForm(toForm(result));
      setSaved(true);
    } catch (err) {
      setSaveError(errorMessage(err, 'The server could not save this cinema.'));
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <div className="page"><p className="muted">Loading cinema…</p></div>;
  if (loadError) {
    return (
      <div className="page">
        <BackLink />
        <ErrorNotice error={loadError} />
      </div>
    );
  }

  return (
    <div className="page">
      <BackLink />
      <header className="page-header">
        <h1>{isNew ? 'New cinema' : form.name || 'Edit cinema'}</h1>
      </header>

      <form className="panel form" onSubmit={onSubmit}>
        <div className="form-grid">
          <Field label="Name" required wide>
            <input value={form.name} onChange={set('name')} required maxLength={140} />
          </Field>
          <Field label="Address" wide>
            <input value={form.address} onChange={set('address')} maxLength={300} />
          </Field>
          <Field label="Opening hours">
            <input value={form.openingHours} onChange={set('openingHours')} maxLength={50} placeholder="08:00 AM - 11:00 PM" />
          </Field>
          <div className="field">
            <span>Halls</span>
            <span className="muted">{cinema?.totalHalls ?? '—'} <span className="small">(managed in Halls)</span></span>
          </div>
          <Field label="Latitude">
            <input type="number" step="any" min={-90} max={90} value={form.latitude} onChange={set('latitude')} />
          </Field>
          <Field label="Longitude">
            <input type="number" step="any" min={-180} max={180} value={form.longitude} onChange={set('longitude')} />
          </Field>
        </div>

        {!isNew && <p className="muted small">ID {cinema?.id}</p>}
        {saveError && <p className="error" role="alert">{saveError}</p>}
        {saved && <p className="success" role="status">Saved.</p>}
        <div className="form-actions">
          <Link className="button ghost" to="/cinemas">Cancel</Link>
          <button type="submit" disabled={saving}>{saving ? 'Saving…' : isNew ? 'Create cinema' : 'Save changes'}</button>
        </div>
      </form>
    </div>
  );
}

function BackLink() {
  return <Link className="back-link" to="/cinemas">← Cinemas</Link>;
}
