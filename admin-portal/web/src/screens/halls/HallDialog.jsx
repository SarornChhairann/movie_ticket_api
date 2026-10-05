import { useEffect, useRef, useState } from 'react';
import { api } from '../../api/client.js';
import { Field, errorMessage } from '../../components/ui.jsx';

function toForm(h, cinemas) {
  return {
    cinemaId: h.cinemaId || (cinemas.length === 1 ? cinemas[0].id : ''),
    name: h.name || '',
    city: h.city || '',
    address: h.address || '',
    type: h.type || '',
    totalSeats: h.totalSeats != null ? String(h.totalSeats) : '',
    capacity: h.capacity != null ? String(h.capacity) : '',
    hasDolbyAtmos: !!h.hasDolbyAtmos,
    hasScreenX: !!h.hasScreenX,
    amenities: h.amenities || '',
  };
}

function intOrNull(v) {
  const t = String(v).trim();
  return t === '' ? null : Number.parseInt(t, 10);
}

/** Create/edit form for one hall, in a modal. Body matches AdminHallController.HallRequest. */
export default function HallDialog({ hall, cinemas, knownTypes, onSaved, onClose }) {
  const isNew = hall.id == null;
  const ref = useRef(null);
  const [form, setForm] = useState(() => toForm(hall, cinemas));
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

  // A new hall in an existing cinema usually shares its address.
  function onCinemaChange(e) {
    const id = e.target.value;
    const cinema = cinemas.find((c) => c.id === id);
    setForm((f) => ({ ...f, cinemaId: id, address: f.address || cinema?.address || '' }));
  }

  async function onSubmit(e) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    const body = {
      ...(isNew ? {} : { id: hall.id }),
      cinemaId: form.cinemaId,
      name: form.name.trim(),
      city: form.city.trim() || null,
      address: form.address.trim() || null,
      type: form.type.trim() || null,
      // Seats are generated once, on create. On edit resend the stored count: a
      // missing value makes the server reset it to its default.
      totalSeats: isNew ? intOrNull(form.totalSeats) : hall.totalSeats,
      capacity: intOrNull(form.capacity),
      hasDolbyAtmos: form.hasDolbyAtmos,
      hasScreenX: form.hasScreenX,
      amenities: form.amenities.trim() || null,
    };
    try {
      onSaved(await api('/admin/halls', { method: 'POST', body }));
    } catch (err) {
      setError(errorMessage(err, 'The server could not save this hall.'));
      setSaving(false);
    }
  }

  return (
    <dialog ref={ref} className="dialog dialog-wide" onCancel={(e) => { e.preventDefault(); if (!saving) onClose(); }}>
      <form className="form" onSubmit={onSubmit}>
        <h2>{isNew ? 'New hall' : `Edit ${hall.name}`}</h2>
        <div className="form-grid">
          <Field label="Cinema" required wide>
            <select value={form.cinemaId} onChange={onCinemaChange} required>
              <option value="" disabled>Choose a cinema</option>
              {cinemas.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </Field>
          <Field label="Name" required wide>
            <input value={form.name} onChange={set('name')} required maxLength={80} />
          </Field>
          <Field label="City">
            <input value={form.city} onChange={set('city')} maxLength={100} />
          </Field>
          <Field label="Type">
            <input value={form.type} onChange={set('type')} maxLength={40} list="hall-types" placeholder="STANDARD" />
            <datalist id="hall-types">{knownTypes.map((t) => <option key={t} value={t} />)}</datalist>
          </Field>
          <Field label="Address" wide>
            <input value={form.address} onChange={set('address')} maxLength={300} />
          </Field>
          <Field label="Total seats">
            {isNew
              ? <input type="number" min={1} step={1} value={form.totalSeats} onChange={set('totalSeats')} placeholder="Server default" />
              : <input type="number" value={form.totalSeats} disabled />}
          </Field>
          <Field label="Capacity">
            <input type="number" min={1} step={1} value={form.capacity} onChange={set('capacity')} placeholder="Same as seats" />
          </Field>
          <label className="check">
            <input type="checkbox" checked={form.hasDolbyAtmos} onChange={set('hasDolbyAtmos')} /> Dolby Atmos
          </label>
          <label className="check">
            <input type="checkbox" checked={form.hasScreenX} onChange={set('hasScreenX')} /> ScreenX
          </label>
          <Field label="Amenities" wide>
            <input value={form.amenities} onChange={set('amenities')} placeholder="Comma separated" />
          </Field>
        </div>
        <p className="muted small">
          {isNew
            ? 'The seat layout is generated from the seat count when the hall is created.'
            : 'The seat count is fixed once the layout exists.'}
        </p>
        {error && <p className="error" role="alert">{error}</p>}
        <div className="dialog-actions">
          <button type="button" className="ghost" onClick={onClose} disabled={saving}>Cancel</button>
          <button type="submit" disabled={saving}>{saving ? 'Saving…' : isNew ? 'Create hall' : 'Save changes'}</button>
        </div>
      </form>
    </dialog>
  );
}
