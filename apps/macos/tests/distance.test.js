const test=require('node:test'),assert=require('node:assert/strict');
const {createApp,validOCR}=require('./helpers/app-harness.js');
test('distant mode waits for a region and uses original-source recognition with mapped evidence',async t=>{
 const app=createApp();t.after(()=>app.dispose());await app.loadImage();app.get('#distance').click();await app.get('#analyze').click();assert.equal(app.requests.recognize.length,0);assert.equal(app.requests.recognizeRegion.length,0);
 const canvas=app.get('#viewer');canvas.onpointerdown({clientX:100,clientY:100,pointerId:1});canvas.onpointermove({clientX:300,clientY:200});canvas.onpointerup();
 const task=app.get('#analyze').click(),request=await app.nextRequest('recognizeRegion');assert.equal(request.live,false);assert.deepEqual(Array.from(request.region,x=>Math.round(x*1000)),[78,139,156,139]);
 app.deliver({id:request.id,observations:validOCR,elapsedMs:12,sourcePlateWidth:640,sourcePlateHeight:320});await task;
 const record=app.report().records[0];assert.equal(record.capture_mode,'distant-region');assert.deepEqual(record.source_plate_pixels,[640,320]);assert.equal(record.observations[0].box[0],(100+.1*200)/1280);assert.equal(record.calibrated,false);
});
test('detailed live OCR uses the exact paused frame and never a newer decoder frame',{timeout:3000},async t=>{
 const app=createApp();t.after(()=>app.dispose());app.get('#distance').click();app.autoConnect=()=>({image:'data:image/jpeg;base64,Zmlyc3Q=',sequence:1,time:0,captured_at:'2026-10-15T12:00:00Z'});await app.connectLive();
 const poll=await app.nextRequest('liveFrame');app.deliver({id:poll.id,image:'data:image/jpeg;base64,c2Vjb25k',sequence:2,time:1,captured_at:'2026-10-15T12:00:01Z',changed:true});await new Promise(r=>setImmediate(r));assert.equal(app.get('#live-pause').textContent,'Resume recognition');
 app.get('#crop').click();const c=app.get('#viewer');c.onpointerdown({clientX:100,clientY:100,pointerId:1});c.onpointermove({clientX:400,clientY:200});c.onpointerup();const task=app.get('#analyze').click(),request=await app.nextRequest('recognizeRegion');assert.equal(request.liveImage,'c2Vjb25k');assert.equal(request.time,1);
 app.deliver({id:request.id,observations:validOCR,elapsedMs:10,sourcePlateWidth:600,sourcePlateHeight:200});await task;const record=app.report().records[0];assert.equal(record.captured_at,'2026-10-15T12:00:01Z');assert.equal(JSON.stringify(record).includes('c2Vjb25k'),false);
});
