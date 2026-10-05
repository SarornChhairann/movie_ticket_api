// BookingStatus from the server (PENDING, CONFIRMED, CANCELLED), shown as text with a tone.
const TONES = {
  CONFIRMED: 'good',
  PENDING: 'warning',
  CANCELLED: 'critical',
};

export default function StatusBadge({ status }) {
  if (!status) return <span className="muted">—</span>;
  return <span className={`badge badge-${TONES[status] || 'muted'}`}>{status}</span>;
}
