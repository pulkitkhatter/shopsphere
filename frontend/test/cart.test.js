import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as cart from '../public/js/cart.js';

const phone = { id: 'p1', name: 'Phone', price: { amount: 0.1, currency: 'USD' } };
const case_ = { id: 'p2', name: 'Case', price: { amount: 0.2, currency: 'USD' } };

test('addItem adds a line and merges repeated products', () => {
  let c = cart.addItem(cart.emptyCart(), phone);
  c = cart.addItem(c, phone, 2);
  assert.equal(c.items.length, 1);
  assert.equal(c.items[0].quantity, 3);
});

test('functions never mutate the previous cart', () => {
  const before = cart.addItem(cart.emptyCart(), phone);
  const snapshot = JSON.stringify(before);
  cart.addItem(before, case_);
  cart.setQuantity(before, 'p1', 9);
  cart.removeItem(before, 'p1');
  assert.equal(JSON.stringify(before), snapshot);
});

test('quantity is capped at MAX_QTY', () => {
  let c = cart.addItem(cart.emptyCart(), phone, 99);
  c = cart.addItem(c, phone, 50);
  assert.equal(c.items[0].quantity, cart.MAX_QTY);
  assert.equal(cart.setQuantity(c, 'p1', 5000).items[0].quantity, cart.MAX_QTY);
});

test('setQuantity to zero, negative or fractional removes or rejects the line', () => {
  const c = cart.addItem(cart.emptyCart(), phone, 2);
  assert.equal(cart.setQuantity(c, 'p1', 0).items.length, 0);
  assert.equal(cart.setQuantity(c, 'p1', -3).items.length, 0);
  assert.equal(cart.setQuantity(c, 'p1', 1.5).items.length, 0);
});

test('total is computed in cents: 0.1 x 3 + 0.2 x 1 is exactly 0.50', () => {
  let c = cart.addItem(cart.emptyCart(), phone, 3);
  c = cart.addItem(c, case_);
  assert.equal(cart.totalCents(c), 50);
  assert.equal(cart.totalAmount(c), 0.5);
  assert.equal(cart.itemCount(c), 4);
});

test('order request contains only product ids and quantities - never prices', () => {
  const c = cart.addItem(cart.emptyCart(), phone, 2);
  assert.deepEqual(cart.toOrderRequest(c), { items: [{ productId: 'p1', quantity: 2 }] });
});

test('load/save round-trips and tolerates corrupted or unavailable storage', () => {
  const store = new Map();
  const storage = { getItem: (k) => store.get(k) ?? null, setItem: (k, v) => store.set(k, v) };
  const c = cart.addItem(cart.emptyCart(), phone);
  cart.save(storage, c);
  assert.deepEqual(cart.load(storage), c);

  store.set('shopsphere.cart', '{not json');
  assert.deepEqual(cart.load(storage), cart.emptyCart());
  const broken = { getItem() { throw new Error('blocked'); }, setItem() { throw new Error('blocked'); } };
  assert.deepEqual(cart.load(broken), cart.emptyCart());
  assert.doesNotThrow(() => cart.save(broken, c));
});
