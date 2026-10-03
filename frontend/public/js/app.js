import { API_BASE } from './config.js';
import { createApi, ApiError } from './api.js';
import { createTokenStore, rolesOf, usernameOf } from './auth.js';
import * as cartLib from './cart.js';
import { money, formatDate, newKey } from './format.js';
import { el, clear, toast } from './dom.js';

const tokens = createTokenStore();
const api = createApi({ baseUrl: API_BASE, tokens, onSignedOut: () => { renderChrome(); toast('Session expired, please sign in again', true); location.hash = '#/shop'; } });
let cart = cartLib.load(sessionStorage);
let checkoutKey = null;              // Idempotency-Key of the checkout in progress (kept across retries, reset on success)
const shopState = { q: '', category: '', sort: 'name,asc', inStock: false, page: 0 };

const $view = document.getElementById('view');
const $nav = document.getElementById('nav');
const $user = document.getElementById('user');
const $dialog = document.getElementById('auth-dialog');

const signedIn = () => Boolean(tokens.access || tokens.refresh);
const isAdmin = () => rolesOf(tokens.access).includes('ADMIN');
const setCart = (c) => { cart = c; cartLib.save(sessionStorage, cart); renderChrome(); };
const fail = (e) => toast(e instanceof ApiError ? e.message : 'Something went wrong', true);

// ---------- chrome ----------
const ROUTES = [
  { path: '/shop', label: 'Shop', render: renderShop },
  { path: '/cart', label: () => `Cart (${cartLib.itemCount(cart)})`, render: renderCart },
  { path: '/orders', label: 'My orders', render: renderOrders, auth: true },
  { path: '/notifications', label: 'Notifications', render: renderNotifications, auth: true },
  { path: '/admin', label: 'Admin', render: renderAdmin, auth: true, admin: true },
];

function renderChrome() {
  const current = location.hash.slice(1) || '/shop';
  clear($nav);
  for (const r of ROUTES) {
    if ((r.auth && !signedIn()) || (r.admin && !isAdmin())) continue;
    $nav.append(el('a', { href: `#${r.path}`, 'aria-current': current === r.path ? 'page' : undefined }, typeof r.label === 'function' ? r.label() : r.label));
  }
  clear($user);
  if (signedIn()) {
    const name = usernameOf(tokens.access) || 'account';
    $user.append(el('span', {}, `Signed in as ${name}`), el('button', { onclick: () => { api.logout(); setCart(cartLib.emptyCart()); renderChrome(); location.hash = '#/shop'; render(); } }, 'Sign out'));
  } else {
    $user.append(el('button', { class: 'primary', onclick: () => openAuth('login') }, 'Sign in'));
  }
}

function openAuth(mode) {
  clear($dialog);
  const error = el('div', { class: 'error-text', role: 'alert' });
  const username = el('input', { name: 'username', autocomplete: 'username', required: true, minlength: 3, maxlength: 32 });
  const email = el('input', { name: 'email', type: 'email', autocomplete: 'email', required: true });
  const password = el('input', { name: 'password', type: 'password', autocomplete: mode === 'login' ? 'current-password' : 'new-password', required: true, minlength: mode === 'login' ? 1 : 10 });
  const submit = el('button', { class: 'primary', type: 'submit' }, mode === 'login' ? 'Sign in' : 'Create account');
  const form = el('form', { class: 'stack', method: 'dialog' },
    el('h2', {}, mode === 'login' ? 'Sign in' : 'Create account'),
    el('label', {}, 'Username', username),
    mode === 'register' ? el('label', {}, 'Email', email) : null,
    el('label', {}, mode === 'register' ? 'Password (min. 10 characters)' : 'Password', password),
    error,
    el('div', { class: 'row' }, submit, el('button', { type: 'button', onclick: () => $dialog.close() }, 'Cancel'),
      el('button', { type: 'button', onclick: () => openAuth(mode === 'login' ? 'register' : 'login') }, mode === 'login' ? 'New here? Register' : 'Have an account?')));
  form.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    submit.disabled = true; error.textContent = '';
    try {
      if (mode === 'register') await api.register(username.value.trim(), email.value.trim(), password.value);
      await api.login(username.value.trim(), password.value);
      $dialog.close(); renderChrome(); render(); toast('Welcome!');
    } catch (e) {
      error.textContent = e instanceof ApiError ? (Object.values(e.fieldErrors)[0] || e.message) : 'Network error';
    } finally { submit.disabled = false; }
  });
  $dialog.append(form);
  $dialog.showModal();
}

// ---------- shop ----------
async function renderShop() {
  clear($view);
  const q = el('input', { type: 'search', placeholder: 'Search products (whole words)', value: shopState.q, 'aria-label': 'Search' });
  const category = el('select', { 'aria-label': 'Category' }, ...[['', 'All categories'], ['electronics', 'Electronics'], ['books', 'Books'], ['home', 'Home'], ['sports', 'Sports']]
    .map(([v, l]) => el('option', { value: v, selected: v === shopState.category }, l)));
  const sort = el('select', { 'aria-label': 'Sort' }, ...[['name,asc', 'Name A-Z'], ['price,asc', 'Price low to high'], ['price,desc', 'Price high to low'], ['createdAt,desc', 'Newest']]
    .map(([v, l]) => el('option', { value: v, selected: v === shopState.sort }, l)));
  const inStock = el('input', { type: 'checkbox', checked: shopState.inStock });
  const apply = () => { Object.assign(shopState, { q: q.value.trim(), category: category.value, sort: sort.value, inStock: inStock.checked, page: 0 }); renderShop(); };
  q.addEventListener('keydown', (e) => { if (e.key === 'Enter') apply(); });
  for (const c of [category, sort, inStock]) c.addEventListener('change', apply);
  $view.append(el('h1', {}, 'Shop'), el('div', { class: 'filters' }, q, category, sort, el('label', { class: 'row' }, inStock, 'In stock only'), el('button', { onclick: apply }, 'Search')));
  const results = el('div', {}, el('p', { class: 'muted' }, 'Loading...'));
  $view.append(results);

  const params = new URLSearchParams({ page: shopState.page, size: 8, sort: shopState.sort });
  if (shopState.q) params.set('q', shopState.q);
  if (shopState.category) params.set('category', shopState.category);
  if (shopState.inStock) params.set('inStock', 'true');
  try {
    const { data } = await api.get(`/api/v2/products?${params}`, { auth: false });
    clear(results);
    if (!data.content.length) { results.append(el('p', { class: 'empty' }, 'No products match your search.')); return; }
    results.append(el('div', { class: 'grid' }, data.content.map(productCard)),
      el('div', { class: 'pager' },
        el('button', { disabled: data.page === 0, onclick: () => { shopState.page--; renderShop(); } }, 'Previous'),
        el('span', { class: 'muted' }, `Page ${data.page + 1} of ${Math.max(1, data.totalPages)} (${data.totalElements} products)`),
        el('button', { disabled: data.page + 1 >= data.totalPages, onclick: () => { shopState.page++; renderShop(); } }, 'Next')));
  } catch (e) { clear(results); results.append(el('p', { class: 'empty' }, 'Could not load products.')); fail(e); }
}

function productCard(p) {
  return el('article', { class: 'card' },
    el('h3', {}, p.name), el('div', { class: 'muted' }, `${p.category} - ${p.sku}`),
    el('p', {}, p.description || ''),
    el('div', { class: 'row' }, el('span', { class: 'price' }, money(p.price.amount, p.price.currency)),
      el('span', { class: `badge ${p.inStock ? 'ok' : 'bad'}` }, p.inStock ? `${p.stock} in stock` : 'Out of stock')),
    el('button', { class: 'primary', disabled: !p.inStock, onclick: () => { setCart(cartLib.addItem(cart, p)); toast(`${p.name} added to cart`); } }, 'Add to cart'));
}

// ---------- cart ----------
function renderCart() {
  clear($view);
  $view.append(el('h1', {}, 'Your cart'));
  if (!cart.items.length) { $view.append(el('p', { class: 'empty' }, 'Your cart is empty.')); return; }
  const rows = cart.items.map((i) => el('tr', {},
    el('td', {}, i.name), el('td', {}, money(i.price)),
    el('td', {}, el('input', { type: 'number', min: 1, max: cartLib.MAX_QTY, value: i.quantity, 'aria-label': `Quantity of ${i.name}`,
      onchange: (e) => { setCart(cartLib.setQuantity(cart, i.productId, Number(e.target.value))); renderCart(); } })),
    el('td', {}, money(i.price * i.quantity)),
    el('td', {}, el('button', { class: 'danger', onclick: () => { setCart(cartLib.removeItem(cart, i.productId)); renderCart(); } }, 'Remove'))));
  const place = el('button', { class: 'primary', onclick: checkout }, signedIn() ? 'Place order' : 'Sign in to order');
  $view.append(el('table', {}, el('thead', {}, el('tr', {}, ['Item', 'Price', 'Qty', 'Line total', ''].map((h) => el('th', {}, h)))), el('tbody', {}, rows)),
    el('p', { class: 'row' }, el('strong', {}, `Total (estimate): ${money(cartLib.totalAmount(cart))}`)),
    el('p', { class: 'muted' }, 'The final price is calculated by the server from the current catalogue.'), place);

  async function checkout() {
    if (!signedIn()) return openAuth('login');
    place.disabled = true;
    checkoutKey ??= newKey();                       // same key on retry => never two orders for one click
    try {
      const { status, data } = await api.post('/api/v1/orders', cartLib.toOrderRequest(cart), { headers: { 'Idempotency-Key': checkoutKey } });
      checkoutKey = null;
      setCart(cartLib.emptyCart());
      toast(status === 201 ? `Order placed - total ${money(data.total)}` : 'Order was already placed');
      location.hash = '#/orders';
    } catch (e) { fail(e); place.disabled = false; }
  }
}

// ---------- orders ----------
async function renderOrders() {
  clear($view); $view.append(el('h1', {}, 'My orders'));
  const list = el('div', {}, el('p', { class: 'muted' }, 'Loading...')); $view.append(list);
  try {
    const { data } = await api.get('/api/v1/orders?size=20');
    clear(list);
    if (!data.content.length) { list.append(el('p', { class: 'empty' }, 'You have not placed any orders yet.')); return; }
    list.append(el('table', {}, el('thead', {}, el('tr', {}, ['Placed', 'Items', 'Total', 'Status', ''].map((h) => el('th', {}, h)))),
      el('tbody', {}, data.content.map((o) => el('tr', {},
        el('td', {}, formatDate(o.createdAt)),
        el('td', {}, o.items.map((i) => `${i.quantity} x ${i.name}`).join(', ')),
        el('td', {}, money(o.total)),
        el('td', {}, el('span', { class: `badge ${o.status === 'PLACED' ? 'ok' : 'bad'}` }, o.status)),
        el('td', {}, o.status === 'PLACED' ? el('button', { class: 'danger', onclick: async () => {
          try { await api.post(`/api/v1/orders/${encodeURIComponent(o.id)}/cancel`); toast('Order cancelled'); renderOrders(); } catch (e) { fail(e); }
        } }, 'Cancel') : null))))));
  } catch (e) { clear(list); fail(e); }
}

// ---------- notifications ----------
async function renderNotifications() {
  clear($view); $view.append(el('h1', {}, 'Notifications'));
  const list = el('div', {}, el('p', { class: 'muted' }, 'Loading...')); $view.append(list);
  try {
    const { data } = await api.get('/api/v1/notifications?size=30');
    clear(list);
    if (!data.content.length) { list.append(el('p', { class: 'empty' }, 'Nothing yet. Order events arrive here asynchronously through Kafka.')); return; }
    list.append(el('div', { class: 'grid' }, data.content.map((n) => el('article', { class: 'card' },
      el('span', { class: 'badge' }, n.type), el('p', {}, n.message), el('div', { class: 'muted' }, formatDate(n.createdAt))))));
  } catch (e) { clear(list); fail(e); }
}

// ---------- admin ----------
function renderAdmin() {
  clear($view); $view.append(el('h1', {}, 'Admin: add product'));
  const f = (name, attrs = {}) => el('input', { name, required: true, ...attrs });
  const fields = { sku: f('sku', { pattern: '[A-Za-z0-9-]+', maxlength: 40 }), name: f('name', { maxlength: 120 }),
    category: f('category', { maxlength: 60 }), price: f('price', { type: 'number', step: '0.01', min: '0.01' }),
    stock: f('stock', { type: 'number', min: 0, value: 10 }), tags: el('input', { name: 'tags', placeholder: 'comma separated' }),
    description: el('textarea', { name: 'description', rows: 3, maxlength: 2000 }) };
  const error = el('div', { class: 'error-text', role: 'alert' });
  const form = el('form', { class: 'stack' },
    ...Object.entries(fields).map(([k, input]) => el('label', {}, k[0].toUpperCase() + k.slice(1), input)), error,
    el('button', { class: 'primary', type: 'submit' }, 'Create product'));
  form.addEventListener('submit', async (ev) => {
    ev.preventDefault(); error.textContent = '';
    try {
      await api.post('/api/v2/products', {
        sku: fields.sku.value.trim(), name: fields.name.value.trim(), description: fields.description.value.trim(),
        category: fields.category.value.trim().toLowerCase(), price: Number(fields.price.value), stock: Number(fields.stock.value),
        tags: fields.tags.value.split(',').map((t) => t.trim()).filter(Boolean),
      });
      toast('Product created'); form.reset();
    } catch (e) { error.textContent = e instanceof ApiError ? (Object.values(e.fieldErrors)[0] || e.message) : 'Network error'; }
  });
  $view.append(form);
}

// ---------- router ----------
function render() {
  const path = location.hash.slice(1) || '/shop';
  const route = ROUTES.find((r) => r.path === path) || ROUTES[0];
  renderChrome();
  if ((route.auth && !signedIn()) || (route.admin && !isAdmin())) {
    clear($view); $view.append(el('p', { class: 'empty' }, 'Please sign in with the required role to view this page.'));
    if (!signedIn()) openAuth('login');
    return;
  }
  route.render();
  $view.focus({ preventScroll: true });
}

window.addEventListener('hashchange', render);
// on reload: restore the session by exchanging the refresh token for a fresh access token
(async () => {
  if (tokens.refresh && !tokens.access) {
    try { await api.request('GET', '/userinfo'); } catch { /* handled by refresh flow */ }
  }
  render();
})();
