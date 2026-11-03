import {test} from 'node:test';
import assert from 'node:assert/strict';
import {hitPaintStroke} from '../web/stickers.mjs';
const line=(points,width=4,tool='paint')=>({tool,width,color:'#000000',points});
test('stroke eraser detects tap, line thickness and misses without changing strokes',()=>{
 const strokes=[line([[10,10],[100,10]]),line([[200,200]])];
 const before=structuredClone(strokes);
 assert.equal(hitPaintStroke(strokes,[50,14],[50,14],4),0);
 assert.equal(hitPaintStroke(strokes,[50,15],[50,15],4),-1);
 assert.equal(hitPaintStroke(strokes,[200,200],[200,200],4),1);
 assert.deepEqual(strokes,before);
});
test('fast eraser movement intersects long segments between pointer events',()=>{
 const strokes=[line([[50,0],[50,100]])];
 assert.equal(hitPaintStroke(strokes,[0,50],[100,50],2),0);
 assert.equal(hitPaintStroke(strokes,[0,110],[100,110],2),-1);
});
test('erase marks are retained and only a whole painted stroke is removed',()=>{
 const first=line([[10,10],[100,10]]),second=line([[10,50],[100,50]]),eraser=line([[50,0],[50,100]],8,'erase');
 const strokes=[first,second,eraser];
 const index=hitPaintStroke(strokes,[20,50],[20,50],4);
 assert.equal(index,1);strokes.splice(index,1);
 assert.deepEqual(strokes,[first,eraser]);
 assert.equal(hitPaintStroke(strokes,[20,50],[20,50],4),-1);
});
