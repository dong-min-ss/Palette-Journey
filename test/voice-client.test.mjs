import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createVoiceDiary} from '../web/voice.mjs';

test('recorder asks only after consent, pauses microphone, retains failed uploads and releases tracks',async()=>{
  const elements=new Map(),listeners={};
  const element=()=>({disabled:false,hidden:false,checked:false,textContent:'',setAttribute(){},removeAttribute(){},load(){},pause(){},focus(){},replaceChildren(){}});
  const node=id=>{if(!elements.has(id))elements.set(id,element());return elements.get(id);};
  let requests=0,stopped=0;
  const track={enabled:true,stop(){stopped++;}},stream={getTracks:()=>[track],getAudioTracks:()=>[track]};
  class Recorder {
    static isTypeSupported(){return true;}
    constructor(){this.state='inactive';}
    start(){this.state='recording';}pause(){this.state='paused';}resume(){this.state='recording';}
    stop(){this.state='inactive';queueMicrotask(()=>{this.ondataavailable({data:new Blob([new Uint8Array(20)])});this.onstop();});}
  }
  const originals=new Map();
  for(const [key,value] of Object.entries({document:{getElementById:node,createElement:element,addEventListener:(name,fn)=>listeners[name]=fn},window:{addEventListener(){}},navigator:{mediaDevices:{getUserMedia:async()=>{requests++;return stream;}}},MediaRecorder:Recorder,confirm:()=>true})){
    originals.set(key,Object.getOwnPropertyDescriptor(globalThis,key));Object.defineProperty(globalThis,key,{configurable:true,value});
  }
  try{
    const editor=createVoiceDiary({getPage:()=>({id:'page'}),getToken:()=>'',api:async(_path,method)=>{if(method==='PUT')throw new Error('OFFLINE');return {revision:0,recording:null};}});
    await editor.load('page');assert.equal(requests,0);
    await node('voice-start').onclick();assert.equal(requests,0);
    node('voice-consent').checked=true;await node('voice-start').onclick();assert.equal(requests,1);assert.equal(editor.blocked(),true);
    node('voice-pause').onclick();assert.equal(track.enabled,false);
    node('voice-pause').onclick();assert.equal(track.enabled,true);
    node('voice-next').onclick();assert.match(node('voice-question').textContent,/재미/);
    node('voice-stop').onclick();await Promise.resolve();assert.ok(stopped>=1);
    assert.equal(editor.blocked(),true);assert.equal(node('voice-save').disabled,false);
    await node('voice-save').onclick();assert.equal(editor.blocked(),true);assert.equal(node('voice-download').disabled,false);assert.match(node('voice-status').textContent,/저장에 실패/);
    node('voice-discard').onclick();await Promise.resolve();assert.equal(editor.blocked(),false);
    editor.reset();
  }finally{for(const [key,descriptor] of originals){if(descriptor)Object.defineProperty(globalThis,key,descriptor);else delete globalThis[key];}}
});

test('cancelling a pending permission request stops any late microphone stream',async()=>{
  const nodes=new Map(),get=id=>{if(!nodes.has(id))nodes.set(id,{checked:true,pause(){},removeAttribute(){},load(){},replaceChildren(){},focus(){}});return nodes.get(id);};
  let resolvePermission,stops=0;
  const originals=new Map();
  for(const [key,value] of Object.entries({document:{getElementById:get,addEventListener(){}},window:{addEventListener(){}},navigator:{mediaDevices:{getUserMedia:()=>new Promise(r=>resolvePermission=r)}},MediaRecorder:class{}})){
    originals.set(key,Object.getOwnPropertyDescriptor(globalThis,key));Object.defineProperty(globalThis,key,{configurable:true,value});
  }
  try{
    const editor=createVoiceDiary({getPage:()=>({id:'page'}),getToken:()=>'',api:async()=>({revision:0,recording:null})});await editor.load('page');get('voice-consent').checked=true;
    const pending=get('voice-start').onclick();assert.equal(editor.blocked(),true);get('voice-stop').onclick();assert.equal(editor.blocked(),false);
    resolvePermission({getTracks:()=>[{stop(){stops++;}}]});await pending;assert.equal(stops,1);assert.equal(editor.blocked(),false);
  }finally{for(const [key,descriptor] of originals){if(descriptor)Object.defineProperty(globalThis,key,descriptor);else delete globalThis[key];}}
});
