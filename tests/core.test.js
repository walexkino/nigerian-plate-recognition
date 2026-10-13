const assert=require('node:assert/strict');
const test=require('node:test');
const {normalize,plateLike,candidates}=require('../app/core.js');
test('normalization preserves characters without substituting likely glyphs',()=>{
 assert.equal(normalize('ABC 12O DE'),'ABC12ODE');
 assert.equal(plateLike('ABC 1O3 DE'),false);
 assert.equal(plateLike('ABC 123 DE'),true);
 assert.equal(plateLike('XY 123 ZZZ'),true);
 assert.equal(plateLike('NIGERIA'),false);
});
test('candidates preserve provenance and numeric scores, deduplicate same box only',()=>{
 const box=[.1,.2,.3,.4];
 const result=candidates([{box,alternatives:[{text:'ABC123DE',confidence:.6},{text:'ABC 123 DE',confidence:.5}]},{box:[.5,.2,.3,.4],alternatives:[{text:'ABC123DE',confidence:.8}]}]);
 assert.equal(result.length,2);assert.equal(result[0].confidence,.8);assert.deepEqual(result[1].box,box);assert.equal(result[1].raw,'ABC123DE');
});
test('no readable plate gives no candidate, rather than a fabricated guess',()=>{
 assert.deepEqual(candidates([]),[]);assert.deepEqual(candidates([{box:[0,0,1,1],alternatives:[{text:'???',confidence:.9}]}]),[]);
});
