import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api/client.js';
import { ConfirmDialog, Empty, ErrorNotice, errorMessage } from '../../components/ui.jsx';
import { formatCount, orDash } from '../../format.js';

export default function CinemaList() {
  const [cinemas, setCinemas] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [pending, setPending] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setCinemas(await api('/admin/cinemas'));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (cinemas || [])
      .filter((c) => !q || `${c.name} ${c.address || ''}`.toLowerCase().includes(q))
      .sort((a, b) => String(a.name).localeCompare(String(b.name)));
  }, [cinemas, query]);

  async function confirmDelete() {
    setDeleting(true);
    setDeleteError(null);
    try {
      // 2xx either way: the API answers 200 with a message body rather than 204.
      await api(`/admin/cinemas/${pending.id}`, { method: 'DELETE' });
      setCinemas((list) => list.filter((c) => c.id !== pending.id));
      setPending(null);
    } catch (err) {
      setDeleteError(errorMessage(err, 'Could not delete this cinema.'));
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1>Cinemas</h1>
        <Link className="button" to="/cinemas/new">New cinema</Link>
      </header>

      <div className="filters">
        <input type="search" placeholder="Search name or address" value={query}
          onChange={(e) => setQuery(e.target.value)} aria-label="Search cinemas" />
      </div>

      <ErrorNotice error={error} onRetry={load} />
      {!cinemas && loading && <p className="muted">Loading cinemas…</p>}

      {cinemas && (
        cinemas.length === 0 ? <Empty>No cinemas yet. Create the first one.</Empty>
          : visible.length === 0 ? <Empty>No cinemas match this search.</Empty>
            : (
              <div className="panel table-scroll">
                <table className="table">
                  <thead>
                    <tr><th>Name</th><th>Address</th><th>Opening hours</th><th className="num">Halls</th><th /></tr>
                  </thead>
                  <tbody>
                    {visible.map((c) => (
                      <tr key={c.id}>
                        <td><Link to={`/cinemas/${c.id}`}>{orDash(c.name)}</Link></td>
                        <td className="wrap">{orDash(c.address)}</td>
                        <td>{orDash(c.openingHours)}</td>
                        <td className="num">{formatCount(c.totalHalls)}</td>
                        <td className="row-actions">
                          <Link to={`/cinemas/${c.id}`}>Edit</Link>
                          <button type="button" className="link danger-text"
                            onClick={() => { setDeleteError(null); setPending(c); }}>Delete</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
      )}

      <ConfirmDialog open={!!pending} title="Delete cinema?" busy={deleting}
        onConfirm={confirmDelete} onCancel={() => setPending(null)}>
        <p>“{pending?.name}” will be removed. A cinema that still has halls cannot be deleted.</p>
        {deleteError && <p className="error" role="alert">{deleteError}</p>}
      </ConfirmDialog>
    </div>
  );
}
