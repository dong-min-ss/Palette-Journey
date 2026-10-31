import { createServer } from 'node:http';
import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { AccountStore, accountKey } from './store.mjs';

async function readJson(req) {
  let body = '';
  for await (const chunk of req) {
    body += chunk;
    if (Buffer.byteLength(body) > 4096) throw new Error('BODY_TOO_LARGE');
  }
  try { return JSON.parse(body); } catch { throw new Error('INVALID_JSON'); }
}

export function createApp(store) {
  return createServer(async (req, res) => {
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    try {
      if (req.method === 'GET' && req.url === '/health') {
        res.end(JSON.stringify({ status: 'ok' })); return;
      }
      const token = /^Bearer ([a-f0-9]{64})$/.exec(req.headers.authorization ?? '')?.[1];
      accountKey(token);
      let result;
      if (req.method === 'PUT' && req.url === '/api/account') result = store.register(token);
      else if (req.method === 'POST' && req.url === '/api/sessions') result = store.createSession(token);
      else if (req.method === 'POST' && req.url === '/api/sessions/join') {
        const body = await readJson(req);
        result = store.joinSession(token, body?.code);
      }
      else if (req.method === 'DELETE' && /^\/api\/sessions\/[^/]+$/.test(req.url)) {
        result = store.revokeSession(token, req.url.split('/').at(-1));
      }
      else if (req.method === 'GET' && req.url === '/api/session/palette') result = store.sessionPalette(token);
      else if (req.method === 'POST' && req.url === '/api/session/palette/personal') {
        const body = await readJson(req);
        if (!body || typeof body.hex !== 'string') throw new Error('INVALID_COLOR');
        result = store.unlock(token, body.hex, true);
      }
      else if (req.method === 'GET' && req.url === '/api/palette') result = store.get(token);
      else if (req.method === 'POST' && req.url === '/api/palette/personal') {
        const body = await readJson(req);
        if (!body || typeof body.hex !== 'string') throw new Error('INVALID_COLOR');
        result = store.unlock(token, body.hex);
      } else { res.statusCode = 404; res.end(JSON.stringify({ error: 'NOT_FOUND' })); return; }
      res.end(JSON.stringify(result));
    } catch (error) {
      const codes = { UNAUTHORIZED: 401, NO_TICKET: 409, INVALID_COLOR: 400, INVALID_JSON: 400,
        BODY_TOO_LARGE: 413, INVALID_SESSION: 400, PARTICIPANT_IN_USE: 409, SESSION_NOT_FOUND: 404 };
      res.statusCode = codes[error.message] ?? 500;
      res.end(JSON.stringify({ error: res.statusCode === 500 ? 'INTERNAL_ERROR' : error.message }));
    }
  });
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const directory = fileURLToPath(new URL('./data/', import.meta.url));
  mkdirSync(directory, { recursive: true });
  const store = new AccountStore(resolve(directory, 'palette.sqlite'));
  const server = createApp(store);
  server.listen(3000, '127.0.0.1', () => console.log('Palette Journey: http://127.0.0.1:3000'));
  const stop = () => server.close(() => { store.close(); });
  process.on('SIGINT', stop);
  process.on('SIGTERM', stop);
}
