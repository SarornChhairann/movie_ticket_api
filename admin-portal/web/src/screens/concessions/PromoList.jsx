import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../../api/client.js';
import { ConfirmDialog, Empty, ErrorNotice, errorMessage } from '../../components/ui.jsx';
import { formatCount, formatMoney, formatShortDate } from '../../format.js';
import PromoDialog from './PromoDialog.jsx';

function todayIso() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Mirrors the server's validation order: inactive, expired, used up. Display only. */
function promoState(p) {
  if (!p.active) return { label: 'Inactive', tone: 'muted' };
  if (p.validUntil && String(p.validUntil) < todayIso()) return { label: 'Expired', tone: 'critical' };
  if (p.maxUses > 0 && p.usedCount >= p.maxUses) return { label: 'Used up', tone: 'warning' };
  return { label: 'Live', tone: 'good' };
}

export default function PromoList() {
  const [promos, setPromos] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [editing, setEditing] = useState(null);
  const [pending, setPending] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setPromos(await api('/admin/promo-codes'));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (promos || [])
      .filter((p) => !q || String(p.code).toLowerCase().includes(q))
      .sort((a, b) => String(a.code).localeCompare(String(b.code)));
  }, [promos, query]);

  function onSaved(saved) {
    setPromos((list) => [...(list || []).filter((p) => p.id !== saved.id), saved]);
    setEditing(null);
  }

  async function confirmDelete() {
    setDeleting(true);
    setDeleteError(null);
    try {
      await api(`/admin/promo-codes/${pending.id}`, { method: 'DELETE' });
      setPromos((list) => list.filter((p) => p.id !== pending.id));
      setPending(null);
    } catch (err) {
      setDeleteError(errorMessage(err, 'Could not delete this code. It may be on past bookings; deactivate it instead.'));
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1>Promo codes</h1>
        <button type="button" onClick={() => setEditing({})}>New code</button>
      </header>

      <div className="filters">
        <input type="search" placeholder="Search codes" value={query}
          onChange={(e) => setQuery(e.target.value)} aria-label="Search promo codes" />
      </div>

      <ErrorNotice error={error} onRetry={load} />
      {!promos && loading && <p className="muted">Loading promo codes…</p>}

      {promos && (
        promos.length === 0 ? <Empty>No promo codes yet. Create the first one.</Empty>
          : visible.length === 0 ? <Empty>No codes match this search.</Empty>
            : (
              <div className="panel table-scroll">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Code</th><th className="num">Percent off</th><th className="num">Min. purchase</th>
                      <th>Valid until</th><th className="num">Used / max</th><th>State</th><th />
                    </tr>
                  </thead>
                  <tbody>
                    {visible.map((p) => {
                      const st = promoState(p);
                      return (
                        <tr key={p.id}>
                          <td><button type="button" className="link mono" onClick={() => setEditing(p)}>{p.code}</button></td>
                          <td className="num">{p.discountPercent != null ? `${p.discountPercent}%` : '—'}</td>
                          <td className="num">{p.minPurchase != null ? formatMoney(p.minPurchase) : 'None'}</td>
                          <td>{p.validUntil ? formatShortDate(p.validUntil) : 'No end date'}</td>
                          <td className="num">
                            {formatCount(p.usedCount)} / {p.maxUses > 0 ? formatCount(p.maxUses) : 'Unlimited'}
                          </td>
                          <td><span className={`badge badge-${st.tone}`}>{st.label}</span></td>
                          <td className="row-actions">
                            <button type="button" className="link" onClick={() => setEditing(p)}>Edit</button>
                            <button type="button" className="link danger-text"
                              onClick={() => { setDeleteError(null); setPending(p); }}>Delete</button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )
      )}

      {editing && <PromoDialog promo={editing} onSaved={onSaved} onClose={() => setEditing(null)} />}

      <ConfirmDialog open={!!pending} title="Delete promo code?" busy={deleting}
        onConfirm={confirmDelete} onCancel={() => setPending(null)}>
        <p>“{pending?.code}” will stop working at checkout. To pause it instead, untick Active.</p>
        {deleteError && <p className="error" role="alert">{deleteError}</p>}
      </ConfirmDialog>
    </div>
  );
}
