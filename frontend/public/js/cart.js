// Pure cart logic: every function returns a NEW cart object (easy to test, no hidden state).
// Money is summed in integer cents so 0.1 + 0.2 style floating point errors cannot reach the screen.
export const MAX_QTY = 100;

export const emptyCart = () => ({ items: [] });

const toCents = (price) => Math.round(Number(price) * 100);

export function addItem(cart, product, qty = 1) {
  const existing = cart.items.find((i) => i.productId === product.id);
  const quantity = Math.min(MAX_QTY, (existing?.quantity ?? 0) + qty);
  const line = { productId: product.id, name: product.name, price: product.price.amount, quantity };
  return { items: existing ? cart.items.map((i) => (i.productId === product.id ? line : i)) : [...cart.items, line] };
}

export function setQuantity(cart, productId, qty) {
  if (!Number.isInteger(qty) || qty <= 0) return removeItem(cart, productId);
  return { items: cart.items.map((i) => (i.productId === productId ? { ...i, quantity: Math.min(MAX_QTY, qty) } : i)) };
}

export function removeItem(cart, productId) {
  return { items: cart.items.filter((i) => i.productId !== productId) };
}

export function totalCents(cart) {
  return cart.items.reduce((sum, i) => sum + toCents(i.price) * i.quantity, 0);
}

export const totalAmount = (cart) => totalCents(cart) / 100;
export const itemCount = (cart) => cart.items.reduce((n, i) => n + i.quantity, 0);

/** The order request body. Prices are NOT sent: the server prices the order. */
export function toOrderRequest(cart) {
  return { items: cart.items.map((i) => ({ productId: i.productId, quantity: i.quantity })) };
}

export function load(storage, key = 'shopsphere.cart') {
  try {
    const parsed = JSON.parse(storage.getItem(key));
    if (parsed && Array.isArray(parsed.items)) return parsed;
  } catch { /* corrupted or unavailable storage: start empty */ }
  return emptyCart();
}

export function save(storage, cart, key = 'shopsphere.cart') {
  try { storage.setItem(key, JSON.stringify(cart)); } catch { /* storage full/blocked: cart stays in memory */ }
}
