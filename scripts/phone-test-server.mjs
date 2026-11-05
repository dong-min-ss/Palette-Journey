// Isolated, in-memory fixture. Never reads the user's travel book or recordings.
import {mkdirSync,writeFileSync} from 'node:fs';
import {randomBytes} from 'node:crypto';
import {AccountStore} from '../backend/store.mjs';
import {createApp} from '../backend/server.mjs';
const store=new AccountStore(':memory:');
const owner=randomBytes(32).toString('hex');store.register(owner);
const session=store.createSession(owner);
mkdirSync('evidence',{recursive:true});
writeFileSync('evidence/phone-fixture.json',JSON.stringify({code:session.code}));
const server=createApp(store);
server.listen(3001,'127.0.0.1',()=>console.log('Isolated phone fixture: 3001'));
process.on('SIGTERM',()=>server.close(()=>store.close()));
