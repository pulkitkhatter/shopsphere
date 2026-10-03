#!/usr/bin/env node
// Tiny dependency-free HTTP load generator (closed model: N concurrent workers, each sends the next request when the last returns).
// usage: node perf/load-test.mjs <url> [--c 32] [--d 10] [--header "Authorization: Bearer x"] [--warmup 2]
import { performance } from 'node:perf_hooks';
import http from 'node:http';

const args = process.argv.slice(2);
const url = new URL(args[0]);
const opt = (name, def) => { const i = args.indexOf(`--${name}`); return i >= 0 ? args[i + 1] : def; };
const concurrency = Number(opt('c', 32));
const duration = Number(opt('d', 10));
const warmup = Number(opt('warmup', 2));
const headers = {};
for (let i = 0; i < args.length; i++) if (args[i] === '--header') { const [k, ...v] = args[i + 1].split(':'); headers[k.trim()] = v.join(':').trim(); }

const agent = new http.Agent({ keepAlive: true, maxSockets: concurrency });
const latencies = [];
const statuses = new Map();
let measuring = false;

function once() {
  return new Promise((resolve) => {
    const t0 = performance.now();
    const req = http.request({ host: url.hostname, port: url.port, path: url.pathname + url.search, headers, agent }, (res) => {
      res.resume();
      res.on('end', () => { if (measuring) { latencies.push(performance.now() - t0); statuses.set(res.statusCode, (statuses.get(res.statusCode) || 0) + 1); } resolve(); });
    });
    req.on('error', () => { if (measuring) statuses.set('error', (statuses.get('error') || 0) + 1); resolve(); });
    req.end();
  });
}

let stop = false;
const workers = Array.from({ length: concurrency }, async () => { while (!stop) await once(); });
await new Promise((r) => setTimeout(r, warmup * 1000));       // JIT / connection warm-up is not measured
measuring = true;
const start = performance.now();
await new Promise((r) => setTimeout(r, duration * 1000));
measuring = false; stop = true;
const seconds = (performance.now() - start) / 1000;
await Promise.all(workers);
agent.destroy();

latencies.sort((a, b) => a - b);
const pct = (p) => latencies.length ? latencies[Math.min(latencies.length - 1, Math.floor(p / 100 * latencies.length))] : NaN;
const ok = statuses.get(200) || 0;
console.log(JSON.stringify({
  url: args[0], concurrency, seconds: Number(seconds.toFixed(1)),
  requests: latencies.length, rps: Math.round(latencies.length / seconds),
  ok, nonOk: latencies.length - ok, statuses: Object.fromEntries(statuses),
  ms: { p50: +pct(50).toFixed(2), p95: +pct(95).toFixed(2), p99: +pct(99).toFixed(2), max: +(latencies.at(-1) ?? NaN).toFixed(2) },
}));
