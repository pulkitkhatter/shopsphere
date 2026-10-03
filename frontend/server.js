// Minimal static file server (no dependencies) with security headers.
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(fileURLToPath(new URL('./public', import.meta.url)));
const PORT = Number(process.env.PORT || 3000);
const API = process.env.API_ORIGIN || 'http://localhost:8080';

const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml', '.json': 'application/json', '.ico': 'image/x-icon',
};

// OWASP: strict CSP (no inline script, no third-party origins), clickjacking + MIME sniffing protection.
export const SECURITY_HEADERS = {
  'Content-Security-Policy': `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self' ${API}; frame-ancestors 'none'; base-uri 'none'; form-action 'self'`,
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'no-referrer',
  'Permissions-Policy': 'geolocation=(), camera=(), microphone=()',
};

export function resolveSafe(urlPath) {
  const decoded = decodeURIComponent(urlPath.split('?')[0]);
  const target = normalize(join(ROOT, decoded === '/' ? '/index.html' : decoded));
  return target.startsWith(ROOT + '/') || target === ROOT ? target : null;   // blocks ../ traversal
}

export const server = createServer(async (req, res) => {
  const headers = { ...SECURITY_HEADERS };
  try {
    if (req.method !== 'GET' && req.method !== 'HEAD') { res.writeHead(405, headers); return res.end(); }
    const file = resolveSafe(req.url || '/');
    if (!file) { res.writeHead(400, headers); return res.end('Bad request'); }
    const body = await readFile(file);
    res.writeHead(200, { ...headers, 'Content-Type': MIME[extname(file)] || 'application/octet-stream',
      'Cache-Control': extname(file) === '.html' ? 'no-cache' : 'public, max-age=300' });
    res.end(req.method === 'HEAD' ? undefined : body);
  } catch {
    res.writeHead(404, headers); res.end('Not found');
  }
});

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  server.listen(PORT, () => console.log(`ShopSphere web on http://localhost:${PORT} (API ${API})`));
}
