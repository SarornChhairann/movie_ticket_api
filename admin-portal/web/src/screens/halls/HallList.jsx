import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api/client.js';
import { ConfirmDialog, Empty, ErrorNotice, errorMessage } from '../../components/ui.jsx';
import { formatCount, orDash } from '../../format.js';
import HallDialog from './HallDialog.jsx';

export default function HallList() {
  const [halls, setHalls] = useState(null);
  const [cinemas, setCinemas] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [cinemaFilter, setCinemaFilter] = useState('');
  const [editing, setEditing] = useState(null); // {} for a new hall, or the hall row
  const [pending, setPending] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [h, c] = await Promise.all([api('/admin/halls'), api('/admin/cinemas')]);
      setHalls(h);
      setCinemas(c);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (halls || [])
      .filter((h) => !cinemaFilter || h.cinemaId === cinemaFilter)
      .filter((h) => !q || `${h.name} ${h.city || ''} ${h.cinemaName || ''}`.toLowerCase().includes(q))
      .sort((a, b) => String(a.cinemaName).localeCompare(String(b.cinemaName)) || String(a.name).localeCompare(String(b.name)));
  }, [halls, query, cinemaFilter]);

  const knownTypes = useMemo(
    () => [...new Set((halls || []).map((h) => h.type).filter(Boolean))].sort(),
    [halls],
  );

  function onSaved(saved) {
    setHalls((list) => {
      const rest = (list || []).filter((h) => h.id !== saved.id);
      return [...rest, saved];
    });
    setEditing(null);
  }

  async function confirmDelete() {
    setDeleting(true);
    setDeleteError(null);
    try {
      await api(`/admin/halls/${pending.id}`, { method: 'DELETE' });
      setHalls((list) => list.filter((h) => h.id !== pending.id));
      setPending(null);
    } catch (err) {
      // 409 when the hall still has showtimes; the server names the count.
      setDeleteError(errorMessage(err, 'The server could not delete this hall.'));
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1>Halls</h1>
        <button type="button" onClick={() => setEditing({})} disabled={!cinemas.length}>New hall</button>
      </header>

      <div className="filters">
        <input type="search" placeholder="Search hall, city, or cinema" value={query}
          onChange={(e) => setQuery(e.target.value)} aria-label="Search halls" />
        <select value={cinemaFilter} onChange={(e) => setCinemaFilter(e.target.value)} aria-label="Filter by cinema">
          <option value="">All cinemas</option>
          {cinemas.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
      </div>

      <ErrorNotice error={error} onRetry={load} />
      {!halls && loading && <p className="muted">Loading halls…</p>}
      {halls && !cinemas.length && (
        <Empty>A hall belongs to a cinema. <Link to="/cinemas/new">Create a cinema</Link> first.</Empty>
      )}

      {halls && cinemas.length > 0 && (
        halls.length === 0 ? <Empty>No halls yet. Create the first one.</Empty>
          : visible.length === 0 ? <Empty>No halls match these filters.</Empty>
            : (
              <div className="panel table-scroll">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Hall</th><th>Cinema</th><th>City</th><th>Type</th>
                      <th className="num">Seats</th><th className="num">Capacity</th><th>Features</th><th />
                    </tr>
                  </thead>
                  <tbody>
                    {visible.map((h) => (
                      <tr key={h.id}>
                        <td><button type="button" className="link" onClick={() => setEditing(h)}>{orDash(h.name)}</button></td>
                        <td>{orDash(h.cinemaName)}</td>
                        <td>{orDash(h.city)}</td>
                        <td>{orDash(h.type)}</td>
                        <td className="num">{formatCount(h.totalSeats)}</td>
                        <td className="num">{formatCount(h.capacity)}</td>
                        <td>
                          {h.hasDolbyAtmos && <span className="badge badge-muted">Dolby Atmos</span>}{' '}
                          {h.hasScreenX && <span className="badge badge-muted">ScreenX</span>}
                          {!h.hasDolbyAtmos && !h.hasScreenX && <span className="muted">—</span>}
                        </td>
                        <td className="row-actions">
                          <button type="button" className="link" onClick={() => setEditing(h)}>Edit</button>
                          <button type="button" className="link danger-text"
                            onClick={() => { setDeleteError(null); setPending(h); }}>Delete</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
      )}

      {editing && (
        <HallDialog hall={editing} cinemas={cinemas} knownTypes={knownTypes}
          onSaved={onSaved} onClose={() => setEditing(null)} />
      )}

      <ConfirmDialog open={!!pending} title="Delete hall?" busy={deleting}
        onConfirm={confirmDelete} onCancel={() => setPending(null)}>
        <p>“{pending?.name}” and its seat layout will be removed. A hall that still has showtimes cannot be deleted.</p>
        {deleteError && <p className="error" role="alert">{deleteError}</p>}
      </ConfirmDialog>

    </div>
  );
}
