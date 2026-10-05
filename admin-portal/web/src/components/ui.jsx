import { useEffect, useRef } from 'react';

export function Empty({ children }) {
  return <div className="empty">{children}</div>;
}

export function ErrorNotice({ error, onRetry }) {
  if (!error) return null;
  return (
    <div className="notice notice-error" role="alert">
      <span>{errorMessage(error)}</span>
      {onRetry && <button type="button" className="ghost" onClick={onRetry}>Try again</button>}
    </div>
  );
}

/**
 * The API's {error} text for 4xx responses. A 5xx body can be a raw stack trace,
 * so it is replaced by `serverFallback`.
 */
export function errorMessage(error, serverFallback = 'The server failed to handle this request.') {
  if (!error) return '';
  if (!error.status) return 'Cannot reach the API. Is it running on port 8010?';
  if (error.status >= 500) return serverFallback;
  return error.message || 'The request was rejected.';
}

/** Modal confirmation built on <dialog>. Open while `open` is true. */
export function ConfirmDialog({ open, title, children, confirmLabel = 'Delete', busy, onConfirm, onCancel }) {
  const ref = useRef(null);

  useEffect(() => {
    const dlg = ref.current;
    if (!dlg) return;
    if (open && !dlg.open) dlg.showModal();
    if (!open && dlg.open) dlg.close();
  }, [open]);

  return (
    <dialog ref={ref} className="dialog" onCancel={(e) => { e.preventDefault(); if (!busy) onCancel(); }}>
      <h2>{title}</h2>
      <div className="dialog-body">{children}</div>
      <div className="dialog-actions">
        <button type="button" className="ghost" onClick={onCancel} disabled={busy}>Cancel</button>
        <button type="button" className="danger" onClick={onConfirm} disabled={busy}>
          {busy ? 'Working…' : confirmLabel}
        </button>
      </div>
    </dialog>
  );
}

export function Field({ label, required, wide, children }) {
  return (
    <label className={wide ? 'field field-wide' : 'field'}>
      <span>{label}{required && <span className="req" aria-hidden="true"> *</span>}</span>
      {children}
    </label>
  );
}
