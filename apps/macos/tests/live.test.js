const test=require('node:test');
const assert=require('node:assert/strict');
const {createApp,validOCR}=require('./helpers/app-harness.js');
function setup(t){const app=createApp();t.after(()=>app.dispose());app.autoConnect=()=>frame(1,0);return app;}
function frame(sequence,time){return {image:'data:image/jpeg;base64,ZmFrZQ==',sequence,time,captured_at:'2026-10-14T12:00:00Z',changed:true};}
const flush=()=>new Promise(resolve=>setImmediate(resolve));

test('live recognition exports captured frames without camera URLs or credentials',{timeout:3000},async t=>{
 const app=setup(t);await app.connectLive('rtsp://operator:secret@camera.example/stream','Junction camera');
 const request=await app.nextRequest('liveFrame');app.deliver({id:request.id,...frame(2,1)});
 const ocr=await app.nextRequest('recognize');app.get('#live-pause').click();
 app.deliver({id:ocr.id,observations:validOCR,elapsedMs:10});await flush();
 const report=app.report(),record=report.records[0];assert.equal(record.input_type,'live');
 assert.equal(record.source,'Junction camera');assert.equal(record.captured_at,'2026-10-14T12:00:00Z');assert.equal(record.time_seconds,1);
 assert.equal(JSON.stringify(report).includes('secret'),false);assert.equal(JSON.stringify(report).includes('camera.example'),false);
});

test('a repeated camera frame does not generate another OCR result',{timeout:3000},async t=>{
 const app=setup(t);await app.connectLive();const request=await app.nextRequest('liveFrame');
 app.deliver({id:request.id,changed:false});await flush();app.get('#live-pause').click();
 assert.equal(app.requests.recognize.length,0);assert.equal(app.get('#live-pause').textContent,'Resume recognition');
});

test('pausing after a completed reading unlocks its alternative candidates',{timeout:3000},async t=>{
 const app=setup(t);await app.connectLive();const request=await app.nextRequest('liveFrame');
 app.deliver({id:request.id,...frame(2,1)});const ocr=await app.nextRequest('recognize');
 app.deliver({id:ocr.id,observations:validOCR,elapsedMs:10});await flush();
 const alternative=app.get('#alternatives').children[0];assert.equal(alternative.disabled,true);
 app.get('#live-pause').click();assert.equal(alternative.disabled,false);
 await alternative.click();assert.equal(app.get('#reading').textContent,'ABC123DE');
});

test('pause ignores late frames and resume continues with the newest frame',{timeout:3000},async t=>{
 const app=setup(t);await app.connectLive();const first=await app.nextRequest('liveFrame');
 app.get('#live-pause').click();app.deliver({id:first.id,...frame(2,1)});await flush();
 assert.equal(app.requests.recognize.length,0);
 app.get('#live-pause').click();const next=await app.nextRequest('liveFrame',1);app.deliver({id:next.id,...frame(10,8)});
 const ocr=await app.nextRequest('recognize');app.get('#live-pause').click();
 app.deliver({id:ocr.id,observations:validOCR,elapsedMs:10});await flush();
 assert.equal(app.report().records[0].time_seconds,8);
});

test('pause/resume while OCR is pending never replaces its source frame',{timeout:3000},async t=>{
 const app=setup(t);await app.connectLive();const first=await app.nextRequest('liveFrame');app.deliver({id:first.id,...frame(2,1)});
 const ocr=await app.nextRequest('recognize');app.get('#live-pause').click();app.get('#live-pause').click();await flush();
 assert.equal(app.requests.liveFrame.length,1);
 app.get('#live-pause').click();app.deliver({id:ocr.id,observations:validOCR,elapsedMs:10});await flush();
 assert.equal(app.report().records[0].time_seconds,1);
});

test('disconnect cancels a pending connection and ignores late success',{timeout:3000},async t=>{
 const app=setup(t);await app.loadImage('original.png');app.autoConnect=null;
 const connect=app.connectLive();const request=await app.nextRequest('connectLive');
 await app.get('#live-disconnect').click();app.deliver({id:request.id,...frame(1,0)});await connect;
 assert.equal(app.get('#filename').textContent,'original.png');assert.equal(app.get('#live-state').textContent,'Disconnected');
 assert.equal(app.requests.recognize.length,0);assert.equal(app.get('#import').disabled,false);
});

test('a feed error pauses recognition and allows explicit disconnection',{timeout:3000},async t=>{
 const app=setup(t);await app.connectLive();const request=await app.nextRequest('liveFrame');
 app.deliver({id:request.id,error:'Camera feed ended'});await flush();
 assert.match(app.get('#live-state').textContent,/interrupted/);assert.equal(app.get('#live-disconnect').disabled,false);
 await app.get('#live-disconnect').click();assert.equal(app.get('#import').disabled,false);
});

test('the connection form rejects non-camera URLs without sending a request',async t=>{
 const app=setup(t);await app.connectLive('file:///private/video.mp4');assert.equal(app.requests.connectLive.length,0);
 assert.equal(app.get('#dialog').open,true);
});
