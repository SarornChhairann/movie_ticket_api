// UserStatus on the server.
export const USER_STATUSES = [
  { value: 'ACTIVE', label: 'Active', tone: 'good' },
  { value: 'SUSPENDED', label: 'Suspended', tone: 'warning' },
  { value: 'BANNED', label: 'Banned', tone: 'critical' },
];

export function UserStatusBadge({ status }) {
  const s = USER_STATUSES.find((x) => x.value === status);
  return <span className={`badge badge-${s?.tone || 'muted'}`}>{s?.label || status || '—'}</span>;
}
