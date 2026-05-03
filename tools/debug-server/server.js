#!/usr/bin/env node
// Tiny debug log server for Time Tracker device verification.

'use strict';

const http = require('http');

const args = process.argv.slice(2);
const portArg = args.indexOf('--port');
const port = portArg >= 0 && args[portArg + 1] ? Number(args[portArg + 1]) : 3947;
const maxEntries = 10000;
let entries = [];
let nextId = 1;

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let total = 0;
    req.on('data', (chunk) => {
      total += chunk.length;
      if (total > 1024 * 1024) {
        reject(new Error('request body too large'));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

function sendJson(res, status, value) {
  const body = JSON.stringify(value);
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(body),
  });
  res.end(body);
}

function visibleEntry(entry) {
  const copy = { ...entry };
  delete copy._size;
  return copy;
}

function filteredEntries(url) {
  const category = url.searchParams.get('category');
  const level = url.searchParams.get('level');
  const last = Number(url.searchParams.get('last') || '0');
  let filtered = entries;
  if (category) filtered = filtered.filter((entry) => entry.category === category);
  if (level) filtered = filtered.filter((entry) => entry.level === level);
  if (last > 0) filtered = filtered.slice(-last);
  return filtered.map(visibleEntry);
}

function summary() {
  const byCategory = {};
  const byLevel = {};
  for (const entry of entries) {
    byCategory[entry.category || 'unknown'] = (byCategory[entry.category || 'unknown'] || 0) + 1;
    byLevel[entry.level || 'unknown'] = (byLevel[entry.level || 'unknown'] || 0) + 1;
  }
  return { total: entries.length, byCategory, byLevel };
}

function appendEntry(entry) {
  entry.id = nextId++;
  entry.receivedAt = new Date().toISOString();
  entries.push(entry);
  if (entries.length > maxEntries) entries = entries.slice(-maxEntries);
  return entry.id;
}

function routeKey(req, url) {
  return `${req.method} ${url.pathname}`;
}

function handleHealth(req, res) {
  sendJson(res, 200, { status: 'ok', entries: entries.length });
}

function handleClear(req, res) {
  entries = [];
  sendJson(res, 200, { cleared: true });
}

function handleLogs(req, res, url) {
  sendJson(res, 200, filteredEntries(url));
}

function handleErrors(req, res) {
  sendJson(res, 200, entries.filter((entry) => entry.level === 'error').map(visibleEntry));
}

function handleSummary(req, res) {
  sendJson(res, 200, summary());
}

async function handleLogPost(req, res) {
  const body = await readBody(req);
  const entry = body ? JSON.parse(body) : {};
  const id = appendEntry(entry);
  sendJson(res, 200, { accepted: true, id });
}

const routes = {
  'GET /health': handleHealth,
  'POST /clear': handleClear,
  'GET /logs': handleLogs,
  'GET /logs/errors': handleErrors,
  'GET /logs/summary': handleSummary,
  'POST /log': handleLogPost,
};

async function handle(req, res) {
  const url = new URL(req.url, `http://${req.headers.host || '127.0.0.1'}`);
  const route = routes[routeKey(req, url)];
  if (route) {
    await route(req, res, url);
    return;
  }
  sendJson(res, 404, { error: 'unknown endpoint' });
}

http.createServer((req, res) => {
  handle(req, res).catch((error) => sendJson(res, 500, { error: error.message }));
}).listen(port, '127.0.0.1', () => {
  console.log(`Time Tracker debug log server listening on http://127.0.0.1:${port}`);
});
