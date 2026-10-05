// Display helpers. They format server values only; nothing here does price math.

import { API_BASE_URL } from './api/client.js';

const DASH = '—';

const money = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
const count = new Intl.NumberFormat('en-US');

function isNumber(v) {
  return v !== null && v !== undefined && v !== '' && Number.isFinite(Number(v));
}

export function formatMoney(v) {
  return isNumber(v) ? money.format(Number(v)) : DASH;
}

export function formatCount(v) {
  return isNumber(v) ? count.format(Number(v)) : DASH;
}

/** "2026-10-05" → "Oct 5". Parsed as a local date, not UTC midnight. */
export function formatShortDate(iso) {
  if (!iso) return DASH;
  const [y, m, d] = String(iso).split('-').map(Number);
  if (!y || !m || !d) return String(iso);
  return new Date(y, m - 1, d).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

/** showDate "2026-10-05" + showTime "19:30:00" → "Oct 5, 19:30". */
export function formatShowtime(date, time) {
  if (!date) return DASH;
  return time ? `${formatShortDate(date)}, ${String(time).slice(0, 5)}` : formatShortDate(date);
}

export function orDash(v) {
  return v === null || v === undefined || v === '' ? DASH : v;
}

/** Server-relative upload paths ("/uploads/...") are served by the API, not this dev server. */
export function mediaUrl(path) {
  if (!path) return null;
  return path.startsWith('/uploads/') ? `${API_BASE_URL}${path}` : path;
}

/** Server LocalDateTime ("2026-10-05T19:25:29.808671", no zone) → "Oct 5, 19:25". Shown as sent. */
export function formatDateTime(value) {
  if (!value) return DASH;
  const [date, time] = String(value).split('T');
  return time ? `${formatShortDate(date)}, ${time.slice(0, 5)}` : formatShortDate(date);
}
