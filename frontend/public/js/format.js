export function money(amount, currency = 'USD', locale = 'en-US') {
  return new Intl.NumberFormat(locale, { style: 'currency', currency }).format(Number(amount));
}

export function formatDate(iso, locale = 'en-US') {
  if (!iso) return '';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleString(locale, { dateStyle: 'medium', timeStyle: 'short' });
}

export function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

/** A UUID usable as Idempotency-Key. */
export function newKey(cryptoImpl = globalThis.crypto) {
  return cryptoImpl.randomUUID();
}
