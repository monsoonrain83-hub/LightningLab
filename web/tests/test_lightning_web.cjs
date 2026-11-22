const assert=require('node:assert/strict');
const c=require('../lightning-web-core.js');
assert.equal(c.scientificValidation,false);
assert.ok(Math.abs(c.meanLuma(new Uint8ClampedArray([255,255,255,255]))-255)<1e-8);
assert.equal(c.meanLuma(new Uint8ClampedArray([0,0,0,255])),0);
assert.deepEqual(c.detectJumps([{time:0,y:10},{time:1,y:15},{time:2,y:60}],{threshold:25}).map(x=>x.time),[2]);
assert.equal(c.detectJumps([{time:0,y:10},{time:1,y:20}],{threshold:25}).length,0);
assert.throws(()=>c.detectJumps([], {threshold:-1}),TypeError);
console.log('LightningLab web luminance kernel tests: PASS');