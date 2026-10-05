import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../api/client.js';
import { ErrorNotice, Field, errorMessage } from '../../components/ui.jsx';
import { formatCount } from '../../format.js';

const EMPTY = {
  movieId: '', hallId: '', showDate: '', showTime: '', normalPrice: '', couplePrice: '',
  publishNow: true, sendNotification: false,
};

function toForm(s) {
  return {
    movieId: String(s.movieId ?? ''),
    hallId: String(s.hallId ?? ''),
    showDate: s.showDate || '',
    showTime: s.showTime ? String(s.showTime).slice(0, 5) : '',
    normalPrice: s.normalPrice != null ? String(s.normalPrice) : '',
    couplePrice: s.couplePrice != null ? String(s.couplePrice) : '',
    publishNow: !s.blocked,
    sendNotification: false,
  };
}

export default function ShowtimeForm() {
  const { id } = useParams();
  const isNew = id === 'new';
  const navigate = useNavigate();

  const [movies, setMovies] = useState([]);
  const [halls, setHalls] = useState([]);
  const [row, setRow] = useState(null); // the list row: carries editLocked and booking counts
  const [lockHours, setLockHours] = useState(null);
  const [form, setForm] = useState(EMPTY);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    // The single-showtime GET does not compute editLocked, so read the row from the list.
    Promise.all([api('/admin/movies'), api('/admin/halls'), api('/admin/showtimes')])
      .then(([m, h, list]) => {
        if (cancelled) return;
        setMovies(m);
        setHalls(h);
        setLockHours(list.editLockHours);
        if (!isNew) {
          const found = list.showtimes.find((s) => String(s.id) === id);
          if (!found) {
            setLoadError({ status: 404, message: `Showtime ${id} was not found.` });
            return;
          }
          setRow(found);
          setForm(toForm(found));
        }
      })
      .catch((err) => { if (!cancelled) setLoadError(err); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [id, isNew]);

  const sortedMovies = useMemo(
    () => [...movies].sort((a, b) => String(a.title).localeCompare(String(b.title))),
    [movies],
  );
  const sortedHalls = useMemo(
    () => [...halls].sort((a, b) => String(a.cinemaName).localeCompare(String(b.cinemaName)) || String(a.name).localeCompare(String(b.name))),
    [halls],
  );
  const selectedMovie = movies.find((m) => String(m.id) === form.movieId);

  const locked = !!row?.editLocked;
  const hasBookings = Number(row?.bookingCount) > 0;
  const readOnly = locked || hasBookings;

  function set(key) {
    return (e) => {
      const value = e.target.type === 'checkbox' ? e.target.checked : e.target.value;
      setForm((f) => ({ ...f, [key]: value }));
    };
  }

  async function onSubmit(e) {
    e.preventDefault();
    if (readOnly) return;
    setSaving(true);
    setSaveError(null);
    const body = {
      ...(isNew ? {} : { id: Number(id) }),
      movieId: Number(form.movieId),
      hallId: Number(form.hallId),
      showDate: form.showDate,
      showTime: form.showTime,
      normalPrice: form.normalPrice,
      couplePrice: form.couplePrice,
      publishNow: form.publishNow,
      sendNotification: form.publishNow && form.sendNotification,
    };
    try {
      const saved = await api('/admin/showtimes', { method: 'POST', body });
      navigate('/showtimes', {
        state: { highlight: saved.id, notice: `${isNew ? 'Scheduled' : 'Saved'} ${saved.movieTitle} on ${saved.showDate} at ${String(saved.showTime).slice(0, 5)}.` },
      });
    } catch (err) {
      setSaveError(errorMessage(err, 'The server could not save this showtime.'));
      setSaving(false);
    }
  }

  if (loading) return <div className="page"><p className="muted">Loading showtime…</p></div>;
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
        <h1>{isNew ? 'New showtime' : `${row.movieTitle} · ${row.showDate} ${String(row.showTime).slice(0, 5)}`}</h1>
      </header>

      {locked && (
        <div className="notice notice-warning" role="status">
          This showtime starts within {lockHours} hour(s), so it can no longer be edited.
        </div>
      )}
      {!locked && hasBookings && (
        <div className="notice notice-warning" role="status">
          This showtime has {formatCount(row.bookingCount)} booking(s), so it can no longer be edited.
        </div>
      )}

      <form className="panel form" onSubmit={onSubmit}>
        <fieldset className="form-grid" disabled={readOnly || saving}>
          <Field label="Movie" required wide>
            <select value={form.movieId} onChange={set('movieId')} required>
              <option value="" disabled>Choose a movie</option>
              {sortedMovies.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.title}{m.status === 'ARCHIVED' ? ' (archived)' : ''}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Hall" required wide>
            <select value={form.hallId} onChange={set('hallId')} required>
              <option value="" disabled>Choose a hall</option>
              {sortedHalls.map((h) => (
                <option key={h.id} value={h.id}>{h.cinemaName ? `${h.cinemaName} — ` : ''}{h.name}</option>
              ))}
            </select>
          </Field>
          <Field label="Date" required>
            <input type="date" value={form.showDate} onChange={set('showDate')} required />
          </Field>
          <Field label="Start time" required>
            <input type="time" value={form.showTime} onChange={set('showTime')} required />
          </Field>
          <Field label="Normal price (per seat)" required>
            <input type="number" min={0} step="0.01" value={form.normalPrice} onChange={set('normalPrice')} required />
          </Field>
          <Field label="Couple price (per pair, not per seat)" required>
            <input type="number" min={0} step="0.01" value={form.couplePrice} onChange={set('couplePrice')} required />
          </Field>
          <label className="check field-wide">
            <input type="checkbox" checked={form.publishNow} onChange={set('publishNow')} />
            Publish now (unchecked saves it as blocked, hidden from customers)
          </label>
          <label className="check field-wide">
            <input type="checkbox" checked={form.publishNow && form.sendNotification}
              onChange={set('sendNotification')} disabled={!form.publishNow} />
            Notify all users about this showtime
          </label>
        </fieldset>

        {selectedMovie?.durationMinutes && (
          <p className="muted small">
            Runs {selectedMovie.durationMinutes} min. The server refuses times that overlap another showtime in this hall.
          </p>
        )}
        {saveError && <p className="error" role="alert">{saveError}</p>}
        <div className="form-actions">
          <Link className="button ghost" to="/showtimes">{readOnly ? 'Back' : 'Cancel'}</Link>
          {!readOnly && (
            <button type="submit" disabled={saving}>{saving ? 'Saving…' : isNew ? 'Schedule showtime' : 'Save changes'}</button>
          )}
        </div>
      </form>
    </div>
  );
}

function BackLink() {
  return <Link className="back-link" to="/showtimes">← Showtimes</Link>;
}
