import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../api/client.js';
import { ErrorNotice, Field, errorMessage } from '../../components/ui.jsx';
import { mediaUrl } from '../../format.js';
import { MOVIE_STATUSES } from './movieOptions.js';

// Every text field of Movie the form edits. The server's save copies all of them
// onto the stored row, so the form always sends the full set (blank → omitted → null).
const TEXT_FIELDS = ['title', 'genre', 'description', 'director', 'language', 'format', 'cast', 'trailerUrl', 'rating', 'releaseDate'];

const EMPTY = {
  title: '', genre: '', description: '', director: '', language: '', format: '', cast: '',
  trailerUrl: '', rating: '', releaseDate: '', durationMinutes: '', status: 'COMING_SOON',
};

function toForm(movie) {
  const form = { ...EMPTY };
  for (const key of Object.keys(EMPTY)) {
    if (movie[key] !== null && movie[key] !== undefined) form[key] = String(movie[key]);
  }
  return form;
}

export default function MovieForm() {
  const { id } = useParams();
  const isNew = id === 'new';
  const navigate = useNavigate();

  const [movie, setMovie] = useState(null); // the stored row, for current image paths
  const [form, setForm] = useState(EMPTY);
  const [poster, setPoster] = useState(null);
  const [banner, setBanner] = useState(null);
  const [loading, setLoading] = useState(!isNew);
  const [loadError, setLoadError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const location = useLocation();
  const [saved, setSaved] = useState(!!location.state?.saved);
  const [fileKey, setFileKey] = useState(0); // bumped after a save to clear the file inputs

  useEffect(() => {
    if (isNew) return;
    let cancelled = false;
    setLoading(true);
    api(`/admin/movies/${id}`)
      .then((m) => { if (!cancelled) { setMovie(m); setForm(toForm(m)); } })
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

    // The controller binds @ModelAttribute Movie, so this must be multipart, never JSON.
    const body = new FormData();
    if (!isNew) body.append('id', id);
    for (const key of TEXT_FIELDS) {
      const value = form[key].trim();
      if (value) body.append(key, value);
    }
    body.append('durationMinutes', form.durationMinutes);
    body.append('status', form.status);
    // No imagePath/bannerPath: the server keeps the stored ones unless a new file is sent.
    if (poster) body.append('imageFile', poster);
    if (banner) body.append('bannerFile', banner);

    try {
      const saved = await api('/admin/movies', { method: 'POST', body });
      if (isNew) {
        navigate(`/movies/${saved.id}`, { replace: true, state: { saved: true } });
        return;
      }
      setMovie(saved);
      setForm(toForm(saved));
      setPoster(null);
      setBanner(null);
      setFileKey((n) => n + 1);
      setSaved(true);
    } catch (err) {
      setSaveError(errorMessage(err, 'The server could not save this movie. Check the image type and size (max 5 MB).'));
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <div className="page"><p className="muted">Loading movie…</p></div>;
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
        <h1>{isNew ? 'New movie' : form.title || 'Edit movie'}</h1>
      </header>

      <form className="panel form" onSubmit={onSubmit}>
        <div className="form-grid">
          <Field label="Title" required>
            <input value={form.title} onChange={set('title')} required maxLength={200} />
          </Field>
          <Field label="Genre" required>
            <input value={form.genre} onChange={set('genre')} required maxLength={100} />
          </Field>
          <Field label="Status" required>
            <select value={form.status} onChange={set('status')} required>
              {MOVIE_STATUSES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
            </select>
          </Field>
          <Field label="Duration (minutes)" required>
            <input type="number" min={1} step={1} value={form.durationMinutes} onChange={set('durationMinutes')} required />
          </Field>
          <Field label="Release date">
            <input type="date" value={form.releaseDate} onChange={set('releaseDate')} />
          </Field>
          <Field label="Rating">
            <input value={form.rating} onChange={set('rating')} maxLength={10} placeholder="PG-13" />
          </Field>
          <Field label="Director">
            <input value={form.director} onChange={set('director')} maxLength={120} />
          </Field>
          <Field label="Language">
            <input value={form.language} onChange={set('language')} maxLength={60} />
          </Field>
          <Field label="Format">
            <input value={form.format} onChange={set('format')} maxLength={20} placeholder="2D, 3D" />
          </Field>
          <Field label="Trailer URL">
            <input type="url" value={form.trailerUrl} onChange={set('trailerUrl')} maxLength={500} />
          </Field>
          <Field label="Cast" wide>
            <input value={form.cast} onChange={set('cast')} placeholder="Comma separated" />
          </Field>
          <Field label="Description" wide>
            <textarea rows={4} value={form.description} onChange={set('description')} />
          </Field>
        </div>

        <div className="image-grid">
          <ImageField key={`poster-${fileKey}`} label="Poster" current={movie?.imagePath} file={poster} onChange={setPoster} />
          <ImageField key={`banner-${fileKey}`} label="Banner" current={movie?.bannerPath} file={banner} onChange={setBanner} wide />
        </div>

        {saveError && <p className="error" role="alert">{saveError}</p>}
        {saved && <p className="success" role="status">Saved.</p>}
        <div className="form-actions">
          <Link className="button ghost" to="/movies">Cancel</Link>
          <button type="submit" disabled={saving}>{saving ? 'Saving…' : isNew ? 'Create movie' : 'Save changes'}</button>
        </div>
      </form>
    </div>
  );
}

function BackLink() {
  return <Link className="back-link" to="/movies">← Movies</Link>;
}

function ImageField({ label, current, file, onChange, wide }) {
  const [preview, setPreview] = useState(null);

  useEffect(() => {
    if (!file) { setPreview(null); return undefined; }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const shown = preview || mediaUrl(current);
  return (
    <div className="image-field">
      <span className="field-label">{label}</span>
      <div className={`image-preview${wide ? ' image-preview-wide' : ''}`}>
        {shown ? <img src={shown} alt={`${label} preview`} /> : <span className="muted small">No image</span>}
      </div>
      <input type="file" accept="image/*" onChange={(e) => onChange(e.target.files?.[0] || null)} />
      <span className="muted small">
        {file ? `New file: ${file.name}` : current ? 'Leave empty to keep the current image.' : 'Optional.'}
      </span>
    </div>
  );
}
