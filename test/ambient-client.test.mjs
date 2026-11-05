import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createAmbientSync} from '../web/ambient.mjs';

test('rapid color changes and switching off cannot be overwritten by a late enable response',async()=>{
 const nodes=new Map(),events={};const node=id=>{if(!nodes.has(id))nodes.set(id,{setAttribute(){},textContent:''});return nodes.get(id);};
 const old=new Map();let heartbeat;
 for(const [key,value] of Object.entries({document:{getElementById:node,hidden:false,addEventListener:(e,fn)=>events[e]=fn},window:{addEventListener(){}},setInterval:fn=>{heartbeat=fn;}})){
  old.set(key,Object.getOwnPropertyDescriptor(globalThis,key));Object.defineProperty(globalThis,key,{configurable:true,value});
 }
 try{
  const calls=[],resolve=[];
  const sync=createAmbientSync({getToken:()=> 'family',api:(_path,_method,body)=>{calls.push(body);return new Promise(r=>resolve.push(r));}});
  const flush=async()=>{for(let i=0;i<5;i++)await Promise.resolve();};
  sync.select('blue');node('ambient-toggle').onclick();assert.equal(calls[0].enabled,true);
  sync.select('red');node('ambient-toggle').onclick();resolve.shift()({status:'applied'});await flush();
  assert.deepEqual(calls[1],{enabled:false,colorId:'red'});assert.doesNotMatch(node('ambient-status').textContent,/재조회를 확인/);
  resolve.shift()({status:'off'});await flush();heartbeat();assert.equal(calls.length,2);
  node('ambient-toggle').onclick();resolve.shift()({status:'applied'});await flush();
  events['palette-app-background']();assert.equal(calls.at(-1).enabled,false);resolve.shift()({status:'off'});await flush();
 }finally{for(const [key,d] of old){if(d)Object.defineProperty(globalThis,key,d);else delete globalThis[key];}}
});
