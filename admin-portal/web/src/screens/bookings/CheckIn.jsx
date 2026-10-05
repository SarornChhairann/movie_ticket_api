import { useEffect, useRef, useState } from 'react';
import { api } from '../../api/client.js';
import { errorMessage } from '../../components/ui.jsx';
import { formatDateTime } from '../../format.js';

let nextKey = 1;

// One field + Enter, so a barcode scanner that types the code and a return works unattended.
// POST /admin/checkin { ticketCode } → { ticketCode, status, scannedAt, message }.
export default function CheckIn() {
  const inputRef = useRef(null);
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [scans, setScans] = useState([]); // newest first, this session only

  useEffect(() => { inputRef.current?.focus(); }, []);

  async function onSubmit(e) {
    e.preventDefault();
    const ticketCode = code.trim();
    if (!ticketCode || busy) return;
    setBusy(true);
    let entry;
    try {
      const res = await api('/admin/checkin', { method: 'POST', body: { ticketCode } });
      entry = { ok: true, ticketCode: res.ticketCode, status: res.status, scannedAt: res.scannedAt, message: res.message };
    } catch (err) {
      entry = { ok: false, ticketCode, message: errorMessage(err, 'The server could not check in this ticket.') };
    }
    setScans((list) => [{ key: nextKey++, ...entry }, ...list].slice(0, 50));
    setCode('');
    setBusy(false);
    inputRef.current?.focus();
  }

  const latest = scans[0];

  return (
    <div className="page checkin">
      <header className="page-header"><h1>Check-in</h1></header>

      <form className="panel checkin-form" onSubmit={onSubmit}>
        <label htmlFor="ticket-code">Ticket code</label>
        <div className="checkin-row">
          <input id="ticket-code" ref={inputRef} value={code} onChange={(e) => setCode(e.target.value)}
            autoComplete="off" autoCapitalize="characters" spellCheck={false} placeholder="Scan or type the code" />
          <button type="submit" disabled={busy || !code.trim()}>{busy ? 'Checking…' : 'Check in'}</button>
        </div>
      </form>

      {latest && (
        <div className={`checkin-result ${latest.ok ? 'is-ok' : 'is-error'}`} role="status" aria-live="assertive">
          <div className="checkin-result-title">{latest.ok ? 'Admitted' : 'Not admitted'}</div>
          <div className="checkin-result-code">{latest.ticketCode}</div>
          {latest.ok
            ? <div>Status {latest.status} · scanned {formatDateTime(latest.scannedAt)}</div>
            : <div>{latest.message}</div>}
        </div>
      )}

      {scans.length > 0 && (
        <section className="panel">
          <div className="panel-head"><h2>This session</h2><span className="muted small">Not saved; clears on reload</span></div>
          <div className="table-scroll">
            <table className="table">
              <thead><tr><th>Code</th><th>Result</th><th>Status</th><th>Scanned at</th></tr></thead>
              <tbody>
                {scans.map((s) => (
                  <tr key={s.key}>
                    <td className="mono">{s.ticketCode}</td>
                    <td className={s.ok ? 'success' : 'error'}>{s.ok ? 'Admitted' : s.message}</td>
                    <td>{s.ok ? s.status : '—'}</td>
                    <td>{s.ok ? formatDateTime(s.scannedAt) : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}
