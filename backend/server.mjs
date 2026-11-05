import { createServer } from 'node:http';
import { mkdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { AccountStore, accountKey } from './store.mjs';
import { ConversationStore } from './conversation.mjs';
import { VoiceStore } from './voice.mjs';
import { ConnectionStore } from './connection.mjs';
import { DiaryStore } from './diaries.mjs';
import { contentCatalog, STICKERS } from '../core/catalog.mjs';
import { VisitStore } from './visits.mjs';
import { DriveStore } from './drive.mjs';
import { RevisitStore } from './revisits.mjs';
import { ownedStickers } from './stickers.mjs';

async function readJson(req) {
  const chunks=[];let length=0;
  for await (const chunk of req) {
    length+=chunk.length;
    if (length > (req.url?.endsWith('/voice/vehicle')?5300000:req.url?.endsWith('/voice')?2900000:(req.url==='/api/session/artwork'||req.url?.startsWith('/api/session/pages/'))?524288:4096)) throw new Error('BODY_TOO_LARGE');
    chunks.push(chunk);
  }
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { throw new Error('INVALID_JSON'); }
}

export function createApp(store,options={}) {
  const diaries=new DiaryStore(store),voice=new VoiceStore(diaries),connection=new ConnectionStore(store,diaries),conversation=new ConversationStore(voice,options.synthesizeQuestions);
  const revisits=new RevisitStore(store),visits=new VisitStore(store,revisits);
  const drive=new DriveStore(store,visits);
  return createServer(async (req, res) => {
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; media-src 'self' blob:; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
    res.setHeader('Referrer-Policy', 'no-referrer');
    try {
      const assets = { '/': ['index.html', 'text/html; charset=utf-8'], '/app.mjs': ['app.mjs', 'text/javascript; charset=utf-8'], '/style.css': ['style.css', 'text/css; charset=utf-8'] };
      for(const item of STICKERS)for(const suffix of ['', '-outline']){const path=item.asset.replace('.svg',suffix+'.svg');assets[path]=[path.slice(1),'image/svg+xml'];}
      assets['/catalog-view.mjs']=['assets/catalog-view.mjs','text/javascript; charset=utf-8'];
      assets['/catalog-view.css']=['assets/catalog-view.css','text/css; charset=utf-8'];
      assets['/voice.mjs']=['voice.mjs','text/javascript; charset=utf-8'];
      assets['/stickers.mjs']=['stickers.mjs','text/javascript; charset=utf-8'];
      if (req.method === 'GET' && Object.hasOwn(assets, req.url)) {
        const [file, type] = assets[req.url];
        res.setHeader('Content-Type', type);
        res.end(readFileSync(new URL(`../web/${file}`, import.meta.url))); return;
      }
      if (req.method === 'GET' && req.url === '/health') {
        res.end(JSON.stringify({ status: 'ok' })); return;
      }
      const token = /^Bearer ([a-f0-9]{64})$/.exec(req.headers.authorization ?? '')?.[1];
      accountKey(token);
      let result;
      if(req.method==='GET'&&/^\/api\/session\/pages\/[^/]+\/voice\/conversation$/.test(req.url)){const data=await conversation.audio(token,req.url.split('/')[4]);res.setHeader('Content-Type',data.mime);res.setHeader('X-Voice-Revision',String(data.revision));res.end(data.audio);return;}
      else if(req.method==='PUT'&&/^\/api\/session\/pages\/[^/]+\/voice\/vehicle$/.test(req.url)){result=voice.write(token,req.url.split('/')[4],await readJson(req),false,true);}
      else if(/^\/api\/session\/pages\/[^/]+\/voice(?:\/audio)?$/.test(req.url)){
        const id=req.url.split('/')[4];
        if(req.method==='GET'&&req.url.endsWith('/audio')){const data=voice.audio(token,id);res.setHeader('Content-Type',data.mime);res.setHeader('X-Voice-Revision',String(data.revision));res.end(data.audio);return;}
        if(req.method==='GET')result=voice.get(token,id);
        else if((req.method==='PUT'||req.method==='DELETE')&&!req.url.endsWith('/audio'))result=voice.write(token,id,await readJson(req),req.method==='DELETE');
        else{res.statusCode=405;res.end(JSON.stringify({error:'METHOD_NOT_ALLOWED'}));return;}
      }
      else if(req.method==='GET'&&req.url==='/api/session/connection')result=connection.get(token);
      else if(req.method==='PUT'&&req.url==='/api/session/connection')result=connection.set(token,await readJson(req));
      else if(req.method==='POST'&&req.url==='/api/session/invitations')result=store.invite(token);
      else if(req.method==='GET'&&req.url==='/api/session/visits')result=visits.state(token);
      else if(req.method==='PUT'&&req.url==='/api/session/revisit')result=revisits.update(token,await readJson(req));
      else if(req.method==='GET'&&(req.url==='/api/session/revisit'||req.url.startsWith('/api/session/revisit?')))
        result=revisits.state(token,new URL(req.url,'http://localhost').searchParams.get('childId'));
      else if(req.method==='GET'&&req.url==='/api/session/visits/drive')result=drive.state(token);
      else if(req.method==='POST'&&req.url==='/api/session/visits/drive'){const body=await readJson(req);if(body?.action==='select')result=drive.select(token,body.regionId);else if(body?.action==='tick')result=drive.tick(token,body);else throw new Error('INVALID_DRIVE');}
      else if(req.method==='POST'&&req.url==='/api/session/visits/reset')result=drive.reset(token);
      else if(req.method==='POST'&&req.url==='/api/session/visits/vehicle')result=visits.vehicle(token,await readJson(req));
      else if(req.method==='POST'&&req.url==='/api/session/visits/mock')result=visits.sample(token,await readJson(req));
      else if(req.method==='GET'&&req.url==='/api/session/catalog')result=contentCatalog(store.sessionPalette(token),ownedStickers(store,token),store.developmentStickers(token));
      else if(req.method==='GET'&&req.url==='/api/session/library')result=diaries.library(token);
      else if(req.method==='POST'&&req.url==='/api/session/children')result=diaries.createChild(token,await readJson(req));
      else if(req.method==='POST'&&req.url==='/api/session/trips')result=diaries.createTrip(token,await readJson(req));
      else if(req.method==='POST'&&req.url==='/api/session/diaries')result=diaries.createDiary(token,await readJson(req));
      else if(req.method==='POST'&&/^\/api\/session\/diaries\/[^/]+\/pages$/.test(req.url))result=diaries.createPage(token,req.url.split('/')[4],await readJson(req));
      else if(req.method==='GET'&&/^\/api\/session\/pages\/[^/]+$/.test(req.url))result=diaries.page(token,req.url.split('/')[4]);
      else if(req.method==='PUT'&&/^\/api\/session\/pages\/[^/]+$/.test(req.url))result=diaries.savePage(token,req.url.split('/')[4],await readJson(req));
      else if(req.method==='PUT'&&req.url==='/api/account/demo')result=store.registerDemo(token);
      else if (req.method === 'PUT' && req.url === '/api/account') result = store.register(token);
      else if (req.method === 'POST' && req.url === '/api/sessions') result = store.createSession(token);
      else if (req.method === 'POST' && req.url === '/api/sessions/join') {
        const body = await readJson(req);
        result = store.joinSession(token, body?.code);
      }
      else if (req.method === 'DELETE' && /^\/api\/sessions\/[^/]+$/.test(req.url)) {
        result = store.revokeSession(token, req.url.split('/').at(-1));
      }
      else if (req.method === 'GET' && req.url === '/api/session/palette') result = store.sessionPalette(token);
      else if (req.method === 'GET' && req.url === '/api/session/artwork') result = store.artwork(token);
      else if (req.method === 'PUT' && req.url === '/api/session/artwork') result = store.saveArtwork(token, await readJson(req));
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
      const codes = { INVALID_DRIVE:400, DRIVE_NOT_STARTED:409, DEMO_ONLY:403, INVALID_DEMO_ACCOUNT:409, SIMULATION_DISABLED:403,INVALID_LOCATION:400, CONVERSATION_UNAVAILABLE:503,CONNECTION_CONFLICT:409,INVALID_VOICE:400,VOICE_CONFLICT:409,VOICE_NOT_FOUND:404, UNAUTHORIZED: 401, NO_TICKET: 409, INVALID_COLOR: 400, INVALID_JSON: 400,
        BODY_TOO_LARGE: 413, INVALID_SESSION: 400, PARTICIPANT_IN_USE: 409, SESSION_NOT_FOUND: 404,
        INVALID_ARTWORK: 400, ARTWORK_CONFLICT: 409, INVALID_DIARY:400,DIARY_NOT_FOUND:404,INVALID_STICKER_PLACEMENT:400,INVALID_REVISIT:400 };
      res.statusCode = codes[error.message] ?? 500;
      res.end(JSON.stringify({ error: res.statusCode === 500 ? 'INTERNAL_ERROR' : error.message }));
    }
  });
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const directory = fileURLToPath(new URL('./data/', import.meta.url));
  mkdirSync(directory, { recursive: true });
  const store = new AccountStore(resolve(directory, 'palette.sqlite'));
  store.developmentStickerAccess = process.env.PALETTE_DEV_UNLOCK_STICKERS === '1';
  store.mockLocationAccess = process.env.PALETTE_MOCK_LOCATION === '1';
  const server = createApp(store);
  server.listen(3000, process.env.PALETTE_LISTEN_HOST || '127.0.0.1', () => console.log('Palette Journey: '+(process.env.PALETTE_WEB_ORIGIN||'http://127.0.0.1:3000')));
  const stop = () => server.close(() => { store.close(); });
  process.on('SIGINT', stop);
  process.on('SIGTERM', stop);
}
