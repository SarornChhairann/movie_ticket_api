import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../../api/client.js';
import { Empty, ErrorNotice } from '../../components/ui.jsx';
import { formatCount, orDash } from '../../format.js';
import { USER_STATUSES, UserStatusBadge } from './userOptions.jsx';

// GET /admin/users[?q=] — search runs on the server (name, email, phone).
export default function UserList() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') || '';
  const [draft, setDraft] = useState(q);
  const [role, setRole] = useState('');
  const [status, setStatus] = useState('');
  const [users, setUsers] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async (signal) => {
    setLoading(true);
    setError(null);
    try {
      setUsers(await api(q ? `/admin/users?q=${encodeURIComponent(q)}` : '/admin/users', { signal }));
    } catch (err) {
      if (err.name !== 'AbortError') setError(err);
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [q]);

  useEffect(() => {
    const ctrl = new AbortController();
    load(ctrl.signal);
    return () => ctrl.abort();
  }, [load]);

  useEffect(() => { setDraft(q); }, [q]);

  const roles = useMemo(() => [...new Set((users || []).map((u) => u.role).filter(Boolean))].sort(), [users]);
  const visible = useMemo(
    () => (users || []).filter((u) => (!role || u.role === role) && (!status || u.status === status)),
    [users, role, status],
  );

  function onSearch(e) {
    e.preventDefault();
    const next = draft.trim();
    setParams(next ? { q: next } : {}, { replace: true });
  }

  return (
    <div className="page">
      <header className="page-header"><h1>Users</h1></header>

      <form className="filters" onSubmit={onSearch} role="search">
        <input type="search" placeholder="Name, email, or phone" value={draft}
          onChange={(e) => setDraft(e.target.value)} aria-label="Search users" />
        <button type="submit">Search</button>
        {q && <button type="button" className="ghost" onClick={() => setParams({}, { replace: true })}>Clear</button>}
        <select value={role} onChange={(e) => setRole(e.target.value)} aria-label="Filter by role">
          <option value="">All roles</option>
          {roles.map((r) => <option key={r} value={r}>{r}</option>)}
        </select>
        <select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Filter by status">
          <option value="">All statuses</option>
          {USER_STATUSES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
        </select>
      </form>

      <ErrorNotice error={error} onRetry={() => load()} />
      {!users && loading && <p className="muted">Loading users…</p>}

      {users && (
        users.length === 0 ? <Empty>{q ? `No users match “${q}”.` : 'No users yet.'}</Empty>
          : visible.length === 0 ? <Empty>No users match these filters.</Empty>
            : (
              <div className="panel table-scroll">
                <p className="muted small table-caption">{visible.length} user(s)</p>
                <table className="table">
                  <thead>
                    <tr>
                      <th>Name</th><th>Email</th><th>Phone</th><th>Role</th>
                      <th className="num">Points</th><th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visible.map((u) => (
                      <tr key={u.id}>
                        <td><Link to={`/users/${u.id}`}>{orDash(u.name)}</Link></td>
                        <td>{orDash(u.email)}</td>
                        <td>{orDash(u.phone)}</td>
                        <td>{orDash(u.role)}</td>
                        <td className="num">{formatCount(u.points)}</td>
                        <td><UserStatusBadge status={u.status} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
      )}
    </div>
  );
}
