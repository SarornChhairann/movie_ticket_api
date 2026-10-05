import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api/client.js';
import { ConfirmDialog, Empty, ErrorNotice, errorMessage } from '../../components/ui.jsx';
import { mediaUrl, orDash } from '../../format.js';
import { MOVIE_STATUSES, statusLabel } from './movieOptions.js';

export default function MovieList() {
  const [movies, setMovies] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [pending, setPending] = useState(null); // movie awaiting delete confirmation
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setMovies(await api('/admin/movies'));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (movies || [])
      .filter((m) => !status || m.status === status)
      .filter((m) => !q || `${m.title} ${m.genre}`.toLowerCase().includes(q))
      .sort((a, b) => String(a.title).localeCompare(String(b.title)));
  }, [movies, query, status]);

  async function confirmDelete() {
    setDeleting(true);
    setDeleteError(null);
    try {
      await api(`/admin/movies/${pending.id}`, { method: 'DELETE' });
      setMovies((list) => list.filter((m) => m.id !== pending.id));
      setPending(null);
    } catch (err) {
      // A movie with showtimes fails on a foreign key; the API reports that as a 500.
      setDeleteError(errorMessage(err, 'Could not delete this movie. It may still have showtimes or bookings.'));
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1>Movies</h1>
        <Link className="button" to="/movies/new">New movie</Link>
      </header>

      <div className="filters">
        <input type="search" placeholder="Search title or genre" value={query}
          onChange={(e) => setQuery(e.target.value)} aria-label="Search movies" />
        <select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Filter by status">
          <option value="">All statuses</option>
          {MOVIE_STATUSES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
        </select>
      </div>

      <ErrorNotice error={error} onRetry={load} />
      {!movies && loading && <p className="muted">Loading movies…</p>}

      {movies && (
        movies.length === 0 ? <Empty>No movies yet. Create the first one.</Empty>
          : visible.length === 0 ? <Empty>No movies match these filters.</Empty>
            : (
              <div className="panel table-scroll">
                <table className="table">
                  <thead>
                    <tr>
                      <th aria-label="Poster" />
                      <th>Title</th><th>Genre</th><th>Status</th><th className="num">Duration</th><th />
                    </tr>
                  </thead>
                  <tbody>
                    {visible.map((m) => (
                      <tr key={m.id}>
                        <td className="thumb-cell">
                          {m.imagePath ? <img className="thumb" src={mediaUrl(m.imagePath)} alt="" loading="lazy" />
                            : <span className="thumb thumb-empty" />}
                        </td>
                        <td><Link to={`/movies/${m.id}`}>{orDash(m.title)}</Link></td>
                        <td>{orDash(m.genre)}</td>
                        <td><span className={`badge badge-movie-${m.status}`}>{statusLabel(m.status)}</span></td>
                        <td className="num">{m.durationMinutes ? `${m.durationMinutes} min` : '—'}</td>
                        <td className="row-actions">
                          <Link to={`/movies/${m.id}`}>Edit</Link>
                          <button type="button" className="link danger-text"
                            onClick={() => { setDeleteError(null); setPending(m); }}>Delete</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
      )}

      <ConfirmDialog open={!!pending} title="Delete movie?" busy={deleting}
        onConfirm={confirmDelete} onCancel={() => setPending(null)}>
        <p>“{pending?.title}” will be removed from the catalog. This cannot be undone.</p>
        {deleteError && <p className="error" role="alert">{deleteError}</p>}
      </ConfirmDialog>
    </div>
  );
}
