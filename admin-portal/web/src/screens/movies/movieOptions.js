// MovieStatus on the server. No other values are accepted.
export const MOVIE_STATUSES = [
  { value: 'NOW_SHOWING', label: 'Now showing' },
  { value: 'COMING_SOON', label: 'Coming soon' },
  { value: 'ARCHIVED', label: 'Archived' },
];

export function statusLabel(value) {
  return MOVIE_STATUSES.find((s) => s.value === value)?.label || value || '—';
}
