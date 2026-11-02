import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createConversationPlayback} from '../web/conversation-playback.mjs';
const tick=()=>new Promise(r=>setImmediate(r));
function fixture(){
 const spoken=[],sources=[],statuses=[],contexts=[],timers=new Map();let next=0,cancelled=0,loaded=0;
 const voices=[{lang:'ko-KR',localService:false},{lang:'ko-KR',localService:true}];
 const env={speechSynthesis:{getVoices:()=>voices,speak:u=>spoken.push(u),cancel(){cancelled++;}},SpeechSynthesisUtterance:class{constructor(text){this.text=text;}},
 AudioContext:class{constructor(){this.state='running';this.destination={};contexts.push(this);}resume(){return Promise.resolve();}decodeAudioData(){return Promise.resolve({duration:3});}close(){this.closed=true;return Promise.resolve();}createBufferSource(){const s={connect(){},disconnect(){},stop(){this.stopped=true;},start(...args){this.args=args;}};sources.push(s);return s;}},
 setTimeout(fn){const id=++next;timers.set(id,fn);return id;},clearTimeout:id=>timers.delete(id)};
 const player=createConversationPlayback({env,onStatus:s=>statuses.push(s)});
 const recording={duration:3000,cues:[{at:0,questionText:'첫 질문',skipped:false},{at:1000,questionText:'건너뛴 질문',skipped:true},{at:1000,questionText:'마지막 질문',skipped:false}]};
 return {player,recording,env,voices,spoken,sources,statuses,contexts,timers,load:async()=>{loaded++;return new ArrayBuffer(44);},get loaded(){return loaded;},get cancelled(){return cancelled;}};
}
test('question starts within the tap, then original answer slices alternate including skipped questions',async()=>{
 const f=fixture();f.player.start(f.recording,f.load);assert.equal(f.spoken[0].text,'첫 질문');assert.equal(f.spoken[0].voice.localService,true);assert.equal(f.loaded,0);await tick();assert.equal(f.sources.length,0);
 f.spoken[0].onend();assert.deepEqual(f.sources[0].args,[0,0,1]);f.sources[0].onended();assert.equal(f.spoken[1].text,'건너뛴 질문');f.spoken[1].onend();assert.equal(f.sources.length,1);assert.equal(f.spoken[2].text,'마지막 질문');f.spoken[2].onend();assert.deepEqual(f.sources[1].args,[0,1,2]);f.sources[1].onended();assert.equal(f.player.active(),false);assert.equal(f.contexts[0].closed,true);assert.equal(f.timers.size,0);assert.match(f.statuses.at(-1),/모두/);
});
test('stop during loading ignores late audio and speech callbacks, and never resumes after page switch',async()=>{
 const f=fixture();let resolve;f.player.start(f.recording,()=>new Promise(r=>resolve=r));const oldEnd=f.spoken[0].onend;await tick();f.player.stop();resolve(new ArrayBuffer(44));oldEnd();await tick();assert.equal(f.sources.length,0);assert.equal(f.player.active(),false);assert.equal(f.cancelled,1);assert.equal(f.timers.size,0);
});
test('stopping an answer disconnects audio and old callbacks cannot advance a replacement playback',async()=>{
 const f=fixture();f.player.start(f.recording,f.load);await tick();f.spoken[0].onend();const oldEnd=f.sources[0].onended;f.player.start(f.recording,f.load);oldEnd();assert.equal(f.sources[0].stopped,true);assert.equal(f.spoken.length,2);assert.equal(f.player.active(),true);f.player.stop();
});
test('no local Korean voice never invokes remote synthesis or downloads a private recording',async()=>{
 const f=fixture();f.voices.pop();f.player.start(f.recording,f.load);await tick();assert.equal(f.spoken.length,0);assert.equal(f.loaded,0);assert.equal(f.contexts.length,0);assert.equal(f.player.active(),false);assert.match(f.statuses.at(-1),/한국어/);
});
test('speech error, loading failure and missing completion all stop playback rather than skipping questions',async()=>{
 for(const kind of ['speech','network','timeout']){const f=fixture();f.player.start(f.recording,kind==='network'?async()=>{throw new Error('OFFLINE');}:f.load);await tick();if(kind==='speech')f.spoken[0].onerror();if(kind==='timeout')[...f.timers.values()][0]();await tick();assert.equal(f.player.active(),false);assert.equal(f.sources.length,0);assert.equal(f.contexts[0].closed,true);assert.equal(f.timers.size,0);}
});
