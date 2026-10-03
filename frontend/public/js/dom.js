// DOM construction through textContent only - user/product data can never be interpreted as HTML (XSS-safe by design).
export function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (value === undefined || value === null || value === false) continue;
    if (key.startsWith('on') && typeof value === 'function') node.addEventListener(key.slice(2).toLowerCase(), value);
    else if (key === 'class') node.className = value;
    else if (key === 'text') node.textContent = value;
    else node.setAttribute(key, value === true ? '' : value);
  }
  for (const child of children.flat()) {
    if (child === null || child === undefined || child === false) continue;
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

export const clear = (node) => node.replaceChildren();

export function toast(message, isError = false) {
  const host = document.getElementById('toast');
  const t = el('div', { class: `toast${isError ? ' error' : ''}`, text: message });
  host.append(t);
  setTimeout(() => t.remove(), 4000);
}
