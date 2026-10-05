import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../../api/client.js';
import { ConfirmDialog, Empty, ErrorNotice, Field, errorMessage } from '../../components/ui.jsx';
import { formatCount, formatDateTime } from '../../format.js';

// POST /admin/notifications/send { title, message } broadcasts to everyone. No per-movie target.
export default function Notifications() {
  const [list, setList] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [title, setTitle] = useState('');
  const [message, setMessage] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState(null);
  const [notice, setNotice] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setList(await api('/admin/notifications'));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const sorted = useMemo(
    () => [...(list || [])].sort((a, b) => String(b.sentAt || '').localeCompare(String(a.sentAt || ''))),
    [list],
  );

  async function send() {
    setSending(true);
    setSendError(null);
    try {
      const sent = await api('/admin/notifications/send', {
        method: 'POST',
        body: { title: title.trim(), message: message.trim() },
      });
      setTitle('');
      setMessage('');
      setConfirming(false);
      setNotice(`Sent “${sent.title}” to all ${formatCount(sent.sentCount)} user(s).`);
      await load();
    } catch (err) {
      setSendError(errorMessage(err, 'The server could not send this notification.'));
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="page">
      <header className="page-header"><h1>Notifications</h1></header>

      <form className="panel form" onSubmit={(e) => { e.preventDefault(); setSendError(null); setNotice(null); setConfirming(true); }}>
        <div className="panel-head"><h2>Broadcast to all users</h2></div>
        <Field label="Title" required>
          <input value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={200} />
        </Field>
        <Field label="Message" required>
          <textarea rows={4} value={message} onChange={(e) => setMessage(e.target.value)} required />
        </Field>
        <div className="form-actions">
          <button type="submit" disabled={!title.trim() || !message.trim()}>Send to everyone</button>
        </div>
      </form>

      {notice && <div className="notice notice-success" role="status">{notice}</div>}

      <section className="panel">
        <div className="panel-head"><h2>Sent</h2></div>
        <ErrorNotice error={error} onRetry={load} />
        {!list && loading && <p className="muted">Loading notifications…</p>}
        {list && (sorted.length === 0 ? <Empty>Nothing has been sent yet.</Empty> : (
          <div className="table-scroll">
            <table className="table">
              <thead><tr><th>Sent</th><th>Title</th><th>Message</th><th>Audience</th><th className="num">Recipients</th></tr></thead>
              <tbody>
                {sorted.map((n) => (
                  <tr key={n.id}>
                    <td>{formatDateTime(n.sentAt)}</td>
                    <td>{n.title}</td>
                    <td className="wrap">{n.message}</td>
                    <td>{n.targetType === 'ALL' || !n.targetType ? 'Everyone' : `${n.targetType} ${n.targetRef || ''}`}</td>
                    <td className="num">{formatCount(n.sentCount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}
      </section>

      <ConfirmDialog open={confirming} title="Send to every user?" confirmLabel="Send" busy={sending}
        onConfirm={send} onCancel={() => setConfirming(false)}>
        <p><strong>{title}</strong></p>
        <p className="muted">{message}</p>
        <p>Every user will see this in their notifications.</p>
        {sendError && <p className="error" role="alert">{sendError}</p>}
      </ConfirmDialog>
    </div>
  );
}
