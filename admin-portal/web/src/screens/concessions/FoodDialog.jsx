import { useEffect, useRef, useState } from 'react';
import { api } from '../../api/client.js';
import { Field, errorMessage } from '../../components/ui.jsx';
import { mediaUrl } from '../../format.js';

/** Create/edit one food item. POST /admin/food-items is multipart (@ModelAttribute FoodItem + imageFile). */
export default function FoodDialog({ item, onSaved, onClose }) {
  const isNew = item.id == null;
  const ref = useRef(null);
  const [form, setForm] = useState({
    name: item.name || '',
    description: item.description || '',
    price: item.price != null ? String(item.price) : '',
    stockQuantity: item.stockQuantity != null ? String(item.stockQuantity) : '0',
    available: item.available ?? true,
  });
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    const dlg = ref.current;
    if (dlg && !dlg.open) dlg.showModal();
  }, []);

  useEffect(() => {
    if (!file) { setPreview(null); return undefined; }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  function set(key) {
    return (e) => {
      const value = e.target.type === 'checkbox' ? e.target.checked : e.target.value;
      setForm((f) => ({ ...f, [key]: value }));
    };
  }

  async function onSubmit(e) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    const body = new FormData();
    if (!isNew) body.append('id', item.id);
    body.append('name', form.name.trim());
    if (form.description.trim()) body.append('description', form.description.trim());
    body.append('price', form.price);
    // Always sent: a missing field binds to the entity default (available=true, stock=0).
    body.append('stockQuantity', form.stockQuantity === '' ? '0' : form.stockQuantity);
    body.append('available', String(form.available));
    // No imageUrl: the server keeps the stored image unless a new file is sent.
    if (file) body.append('imageFile', file);
    try {
      onSaved(await api('/admin/food-items', { method: 'POST', body }));
    } catch (err) {
      setError(errorMessage(err, 'The server could not save this item. Check the image type and size.'));
      setSaving(false);
    }
  }

  const shown = preview || mediaUrl(item.imageUrl);
  const unlimited = Number(form.stockQuantity || 0) === 0;

  return (
    <dialog ref={ref} className="dialog dialog-wide" onCancel={(e) => { e.preventDefault(); if (!saving) onClose(); }}>
      <form className="form" onSubmit={onSubmit}>
        <h2>{isNew ? 'New item' : `Edit ${item.name}`}</h2>
        <div className="form-grid">
          <Field label="Name" required wide>
            <input value={form.name} onChange={set('name')} required maxLength={100} />
          </Field>
          <Field label="Description" wide>
            <input value={form.description} onChange={set('description')} maxLength={300} />
          </Field>
          <Field label="Price" required>
            <input type="number" min={0} step="0.01" value={form.price} onChange={set('price')} required />
          </Field>
          <Field label={`Stock quantity${unlimited ? ' (0 = unlimited)' : ''}`}>
            <input type="number" min={0} step={1} value={form.stockQuantity} onChange={set('stockQuantity')} />
          </Field>
          <label className="check field-wide">
            <input type="checkbox" checked={form.available} onChange={set('available')} />
            Available to customers (unchecked hides it from the app but keeps it here)
          </label>
        </div>
        <div className="image-field">
          <span className="field-label">Image</span>
          <div className="image-preview image-preview-square">
            {shown ? <img src={shown} alt="Item preview" /> : <span className="muted small">No image</span>}
          </div>
          <input type="file" accept="image/*" onChange={(e) => setFile(e.target.files?.[0] || null)} />
          <span className="muted small">
            {file ? `New file: ${file.name}` : item.imageUrl ? 'Leave empty to keep the current image.' : 'Optional.'}
          </span>
        </div>
        {error && <p className="error" role="alert">{error}</p>}
        <div className="dialog-actions">
          <button type="button" className="ghost" onClick={onClose} disabled={saving}>Cancel</button>
          <button type="submit" disabled={saving}>{saving ? 'Saving…' : isNew ? 'Create item' : 'Save changes'}</button>
        </div>
      </form>
    </dialog>
  );
}
