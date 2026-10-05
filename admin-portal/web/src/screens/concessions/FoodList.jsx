import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../../api/client.js';
import { ConfirmDialog, Empty, ErrorNotice, errorMessage } from '../../components/ui.jsx';
import { formatCount, formatMoney, mediaUrl, orDash } from '../../format.js';
import FoodDialog from './FoodDialog.jsx';

export function stockLabel(qty) {
  return Number(qty) === 0 ? 'Unlimited' : formatCount(qty);
}

// Unavailable items stay listed here; the customer API already hides them.
export default function FoodList() {
  const [items, setItems] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [availability, setAvailability] = useState('');
  const [editing, setEditing] = useState(null);
  const [pending, setPending] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setItems(await api('/admin/food-items'));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (items || [])
      .filter((f) => !availability || (availability === 'on' ? f.available : !f.available))
      .filter((f) => !q || `${f.name} ${f.description || ''}`.toLowerCase().includes(q))
      .sort((a, b) => String(a.name).localeCompare(String(b.name)));
  }, [items, query, availability]);

  function onSaved(saved) {
    setItems((list) => [...(list || []).filter((f) => f.id !== saved.id), saved]);
    setEditing(null);
  }

  async function confirmDelete() {
    setDeleting(true);
    setDeleteError(null);
    try {
      await api(`/admin/food-items/${pending.id}`, { method: 'DELETE' });
      setItems((list) => list.filter((f) => f.id !== pending.id));
      setPending(null);
    } catch (err) {
      setDeleteError(errorMessage(err, 'Could not delete this item. It may appear on past orders; mark it unavailable instead.'));
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1>Food &amp; drinks</h1>
        <button type="button" onClick={() => setEditing({})}>New item</button>
      </header>

      <div className="filters">
        <input type="search" placeholder="Search items" value={query}
          onChange={(e) => setQuery(e.target.value)} aria-label="Search food items" />
        <select value={availability} onChange={(e) => setAvailability(e.target.value)} aria-label="Filter by availability">
          <option value="">Available and unavailable</option>
          <option value="on">Available only</option>
          <option value="off">Unavailable only</option>
        </select>
      </div>

      <ErrorNotice error={error} onRetry={load} />
      {!items && loading && <p className="muted">Loading items…</p>}

      {items && (
        items.length === 0 ? <Empty>No food items yet. Create the first one.</Empty>
          : visible.length === 0 ? <Empty>No items match these filters.</Empty>
            : (
              <div className="panel table-scroll">
                <table className="table">
                  <thead>
                    <tr>
                      <th aria-label="Image" /><th>Item</th><th className="num">Price</th>
                      <th className="num">Stock</th><th>Customers</th><th />
                    </tr>
                  </thead>
                  <tbody>
                    {visible.map((f) => (
                      <tr key={f.id} className={f.available ? undefined : 'row-muted'}>
                        <td className="thumb-cell">
                          {f.imageUrl ? <img className="thumb thumb-square" src={mediaUrl(f.imageUrl)} alt="" loading="lazy" />
                            : <span className="thumb thumb-square thumb-empty" />}
                        </td>
                        <td>
                          <button type="button" className="link" onClick={() => setEditing(f)}>{orDash(f.name)}</button>
                          {f.description && <div className="muted small clamp">{f.description}</div>}
                        </td>
                        <td className="num">{formatMoney(f.price)}</td>
                        <td className="num">{stockLabel(f.stockQuantity)}</td>
                        <td>
                          {f.available
                            ? <span className="badge badge-good">Available</span>
                            : <span className="badge badge-muted">Hidden</span>}
                        </td>
                        <td className="row-actions">
                          <button type="button" className="link" onClick={() => setEditing(f)}>Edit</button>
                          <button type="button" className="link danger-text"
                            onClick={() => { setDeleteError(null); setPending(f); }}>Delete</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
      )}

      {editing && <FoodDialog item={editing} onSaved={onSaved} onClose={() => setEditing(null)} />}

      <ConfirmDialog open={!!pending} title="Delete item?" busy={deleting}
        onConfirm={confirmDelete} onCancel={() => setPending(null)}>
        <p>“{pending?.name}” will be removed. To stop selling it but keep it, mark it unavailable instead.</p>
        {deleteError && <p className="error" role="alert">{deleteError}</p>}
      </ConfirmDialog>
    </div>
  );
}
