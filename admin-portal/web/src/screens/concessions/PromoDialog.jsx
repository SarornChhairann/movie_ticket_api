import { useEffect, useRef, useState } from 'react';
import { api } from '../../api/client.js';
import { Field, errorMessage } from '../../components/ui.jsx';
import { formatCount } from '../../format.js';

// DiscountType on the server. Checkout applies discountPercent for both today.
const DISCOUNT_TYPES = ['PERCENT', 'FIXED'];

/**
 * Create/edit a promo code: POST /admin/promo-codes with JSON, id only on update.
 * usedCount is shown but never sent; the server keeps its own count.
 */
export default function PromoDialog({ promo, onSaved, onClose }) {
  const isNew = promo.id == null;
  const ref = useRef(null);
  const [form, setForm] = useState({
    code: promo.code || '',
    discountType: promo.discountType || 'PERCENT',
    discountPercent: promo.discountPercent != null ? String(promo.discountPercent) : '',
    minPurchase: promo.minPurchase != null ? String(promo.minPurchase) : '',
    validUntil: promo.validUntil || '',
    maxUses: promo.maxUses != null ? String(promo.maxUses) : '0',
    active: promo.active ?? true,
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    const dlg = ref.current;
    if (dlg && !dlg.open) dlg.showModal();
  }, []);

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
    const body = {
      ...(isNew ? {} : { id: promo.id }),
      code: form.code.trim().toUpperCase(),
      discountType: form.discountType,
      discountPercent: Number.parseInt(form.discountPercent, 10),
      minPurchase: form.minPurchase.trim() === '' ? null : form.minPurchase.trim(),
      validUntil: form.validUntil || null,
      maxUses: form.maxUses === '' ? 0 : Number.parseInt(form.maxUses, 10),
      active: form.active,
    };
    try {
      onSaved(await api('/admin/promo-codes', { method: 'POST', body }));
    } catch (err) {
      setError(errorMessage(err, 'The server could not save this code.'));
      setSaving(false);
    }
  }

  return (
    <dialog ref={ref} className="dialog dialog-wide" onCancel={(e) => { e.preventDefault(); if (!saving) onClose(); }}>
      <form className="form" onSubmit={onSubmit}>
        <h2>{isNew ? 'New promo code' : `Edit ${promo.code}`}</h2>
        <div className="form-grid">
          <Field label="Code" required>
            <input className="mono" value={form.code} onChange={set('code')} required maxLength={50}
              placeholder="SAVE10" autoCapitalize="characters" />
          </Field>
          <Field label="Percent off (1–100)" required>
            <input type="number" min={1} max={100} step={1} value={form.discountPercent}
              onChange={set('discountPercent')} required />
          </Field>
          <Field label="Discount type">
            <select value={form.discountType} onChange={set('discountType')}>
              {DISCOUNT_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
          </Field>
          <Field label="Minimum purchase">
            <input type="number" min={0} step="0.01" value={form.minPurchase} onChange={set('minPurchase')} placeholder="None" />
          </Field>
          <Field label="Valid until">
            <input type="date" value={form.validUntil} onChange={set('validUntil')} />
          </Field>
          <Field label="Max uses (0 = unlimited)">
            <input type="number" min={0} step={1} value={form.maxUses} onChange={set('maxUses')} />
          </Field>
          <div className="field">
            <span>Times used</span>
            <span className="readonly-value">{isNew ? '0' : formatCount(promo.usedCount)}</span>
          </div>
          <label className="check">
            <input type="checkbox" checked={form.active} onChange={set('active')} /> Active
          </label>
        </div>

        <ul className="plain-list muted small">
          <li>The percent reduces the <strong>ticket subtotal only</strong> — never tax, service fee, or food.</li>
          <li>Discount type is stored, but checkout always applies the percent above. There is no fixed-amount discount yet.</li>
          <li>The minimum purchase is checked against the ticket subtotal. A blank end date never expires.</li>
          <li>Times used is counted by checkout and cannot be edited.</li>
        </ul>

        {error && <p className="error" role="alert">{error}</p>}
        <div className="dialog-actions">
          <button type="button" className="ghost" onClick={onClose} disabled={saving}>Cancel</button>
          <button type="submit" disabled={saving}>{saving ? 'Saving…' : isNew ? 'Create code' : 'Save changes'}</button>
        </div>
      </form>
    </dialog>
  );
}
