#!/usr/bin/env node
/**
 * Zero-dependency production server for the built Nexus UI.
 *
 * - Serves static files from ./dist/ with SPA fallback to index.html
 * - Proxies /api/* → VITE_BACKEND_URL  (strips /api prefix)
 * - Proxies /rules-api/* → VITE_RULES_ENGINE_URL (strips /rules-api prefix)
 * - Proxies /simulate/* → VITE_SIMULATOR_URL
 * - Proxies /ws WebSocket upgrades → VITE_BACKEND_URL
 *
 * Usage:
 *   VITE_BACKEND_URL=http://10.144.177.20:8083 \
 *   VITE_SIMULATOR_URL=http://10.144.177.30:8081 \
 *   node server.js
 */
'use strict';
const http = require('http');
const fs   = require('fs');
const path = require('path');
const net  = require('net');
const url  = require('url');

const DIST          = path.join(__dirname, 'dist');
const PORT          = parseInt(process.env.PORT || '5173', 10);
const BACKEND_URL      = process.env.VITE_BACKEND_URL      || 'http://localhost:8083';
const SIMULATOR_URL    = process.env.VITE_SIMULATOR_URL    || 'http://localhost:8081';
const RULES_ENGINE_URL = process.env.VITE_RULES_ENGINE_URL || 'http://localhost:8080';

const backend      = new url.URL(BACKEND_URL);
const simulator    = new url.URL(SIMULATOR_URL);
const rulesEngine  = new url.URL(RULES_ENGINE_URL);

const MIME = {
  '.html':  'text/html; charset=utf-8',
  '.js':    'application/javascript',
  '.css':   'text/css',
  '.png':   'image/png',
  '.jpg':   'image/jpeg',
  '.svg':   'image/svg+xml',
  '.ico':   'image/x-icon',
  '.json':  'application/json',
  '.woff':  'font/woff',
  '.woff2': 'font/woff2',
  '.ttf':   'font/ttf',
};

function proxyHttp(req, res, target, rewrite) {
  const reqUrl = rewrite ? req.url.replace(rewrite, '') || '/' : req.url;
  const opts = {
    hostname: target.hostname,
    port:     parseInt(target.port) || 80,
    path:     reqUrl,
    method:   req.method,
    headers:  Object.assign({}, req.headers, { host: target.host }),
  };
  const proxy = http.request(opts, (pr) => {
    res.writeHead(pr.statusCode, pr.headers);
    pr.pipe(res, { end: true });
  });
  proxy.on('error', () => { try { res.writeHead(502); res.end('Bad Gateway'); } catch (_) {} });
  req.pipe(proxy, { end: true });
}

const server = http.createServer((req, res) => {
  if (req.url.startsWith('/rules-api')) {
    return proxyHttp(req, res, rulesEngine, /^\/rules-api/);
  }
  if (req.url.startsWith('/api')) {
    return proxyHttp(req, res, backend, /^\/api/);
  }
  if (req.url.startsWith('/simulate')) {
    return proxyHttp(req, res, simulator, null);
  }

  // Static file serving with SPA fallback
  let filePath = path.join(DIST, decodeURIComponent(req.url.split('?')[0]));
  // Security: prevent path traversal
  if (!filePath.startsWith(DIST)) { res.writeHead(403); return res.end(); }
  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    filePath = path.join(DIST, 'index.html');
  }
  const mime = MIME[path.extname(filePath)] || 'application/octet-stream';
  res.writeHead(200, { 'Content-Type': mime });
  fs.createReadStream(filePath).pipe(res);
});

// WebSocket proxy: forward all /ws upgrades to the backend
server.on('upgrade', (req, socket, head) => {
  const target = net.createConnection(
    parseInt(backend.port) || 80,
    backend.hostname,
    () => {
      target.write(`GET ${req.url} HTTP/1.1\r\n`);
      target.write(`Host: ${backend.host}\r\n`);
      for (const [k, v] of Object.entries(req.headers)) {
        if (k.toLowerCase() !== 'host') target.write(`${k}: ${v}\r\n`);
      }
      target.write('\r\n');
      if (head && head.length) target.write(head);
    }
  );
  target.on('data',  (d) => socket.write(d));
  target.on('end',   ()  => socket.end());
  target.on('error', ()  => socket.end());
  socket.on('data',  (d) => target.write(d));
  socket.on('end',   ()  => target.end());
  socket.on('error', ()  => target.end());
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Nexus UI running on http://0.0.0.0:${PORT}`);
  console.log(`  API  proxy → ${BACKEND_URL}`);
  console.log(`  Rules proxy → ${RULES_ENGINE_URL}`);
  console.log(`  Sim  proxy → ${SIMULATOR_URL}`);
});
