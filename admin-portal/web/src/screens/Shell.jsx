import { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { NAV_ITEMS } from '../nav.js';

export default function Shell() {
  const { session, logout } = useAuth();
  const [busy, setBusy] = useState(false);

  async function onLogout() {
    setBusy(true);
    await logout();
  }

  return (
    <div className="shell">
      <aside className="sidenav">
        <div className="brand">Ticket Admin</div>
        <nav>
          {NAV_ITEMS.map((item) => (
            <NavLink key={item.path} to={item.path || '/'} end={!item.path}>{item.label}</NavLink>
          ))}
        </nav>
        <div className="account">
          <div className="account-name">{session?.name || session?.email}</div>
          <div className="muted small">{session?.email}</div>
          <button type="button" className="secondary" onClick={onLogout} disabled={busy}>
            {busy ? 'Signing out…' : 'Log out'}
          </button>
        </div>
      </aside>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
