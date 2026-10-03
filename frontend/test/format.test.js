import { test } from 'node:test';
import assert from 'node:assert/strict';
import { money, formatDate, escapeHtml, newKey } from '../public/js/format.js';

test('money formats currency', () => {
  assert.equal(money(1099), '$1,099.00');
  assert.equal(money('12.5'), '$12.50');
});

test('formatDate tolerates missing or invalid input', () => {
  assert.equal(formatDate(null), '');
  assert.equal(formatDate('nonsense'), '');
  assert.match(formatDate('2026-03-01T12:00:00Z'), /2026/);
});

test('escapeHtml neutralises markup', () => {
  assert.equal(escapeHtml('<img src=x onerror="alert(1)">'), '&lt;img src=x onerror=&quot;alert(1)&quot;&gt;');
  assert.equal(escapeHtml("O'Neil & Sons"), 'O&#39;Neil &amp; Sons');
});

test('newKey returns distinct UUIDs', () => {
  const a = newKey(); const b = newKey();
  assert.match(a, /^[0-9a-f-]{36}$/);
  assert.notEqual(a, b);
});
