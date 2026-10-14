const assert=require('node:assert/strict');
const test=require('node:test');
const {normalize,plateLike,candidates}=require('../ui/core.js');
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
test('multiple transformations of the same view cannot inflate votes',()=>{
 const box=[.1,.2,.3,.1];const result=candidates([{box,pass:'original',alternatives:[{text:'ABC123DE',confidence:1},{text:'ABC123DE',confidence:.7}]},{box,pass:'original',alternatives:[{text:'ABC123DE',confidence:1}]},{box,pass:'contrast',alternatives:[{text:'ABC128DE',confidence:1}]}]);
 assert.equal(result[0].support,1);assert.equal(result[0].totalViews,2);assert.equal(result[0].conflicting,true);assert.deepEqual(result[0].characters[5].alternatives,['8']);
});
test('failed-format readings remain part of the disagreement evidence',()=>{
 const box=[.1,.2,.3,.1];const result=candidates([{box,pass:'original',alternatives:[{text:'ABC123DE',confidence:1}]},{box,pass:'scaled',alternatives:[{text:'ABC1230E',confidence:1}]}]);
 assert.equal(result[0].support,1);assert.equal(result[0].totalViews,2);assert.equal(result[0].conflicting,true);
});
test('spatially separated vehicles never share votes',()=>{
 const result=candidates([{box:[.1,.1,.2,.1],pass:'original',alternatives:[{text:'ABC123DE',confidence:1}]},{box:[.7,.7,.2,.1],pass:'scaled',alternatives:[{text:'ABC123DE',confidence:1}]}]);assert.equal(result.length,2);assert.equal(result[0].support,1);
});
test('adjacent same-line text fragments join, but unrelated lines do not',()=>{
 const a={box:[.1,.2,.1,.04],pass:'original',alternatives:[{text:'ABC',confidence:.8}]}, b={box:[.205,.2,.16,.04],pass:'original',alternatives:[{text:'123DE',confidence:.9}]};assert.equal(candidates([a,b])[0].text,'ABC123DE');assert.equal(candidates([a,{...b,box:[.205,.7,.16,.04]}]).length,0);
});
test('Unicode lookalikes normalize while original OCR text is preserved',()=>{
 const result=candidates([{box:[0,0,1,1],alternatives:[{text:'АBC123DÉ',confidence:1}]}]);assert.equal(result[0].text,'ABC123DE');assert.equal(result[0].raw,'АBC123DÉ');assert.equal(normalize('ABC1O3DE'),'ABC1O3DE');
});
test('a familiar layout cannot outrank a repeatedly supported valid reading',()=>{
 const result=candidates(['original','scaled','contrast'].map(pass=>({box:[.1,.2,.3,.1],pass,alternatives:[{text:'ABC123DEF',confidence:.9},{text:'ABC123DE',confidence:.8}]})));
 assert.equal(result[0].text,'ABC123DEF');assert.equal(result[0].support,3);assert.equal(result[1].support,0);
});
test('video scan times are valid for short clips, resume positions and frame limits',()=>{
 const {sampleTimes}=require('../ui/core.js');
 assert.deepEqual(sampleTimes(.03),[0]);assert.deepEqual(sampleTimes(3,1),[1,2]);
 assert.deepEqual(sampleTimes(1,1),[0]);assert.equal(sampleTimes(1000).length,120);
 assert.deepEqual(sampleTimes(NaN),[]);assert.deepEqual(sampleTimes(0),[]);
});
