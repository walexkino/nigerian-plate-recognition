const test=require('node:test');
const assert=require('node:assert/strict');
const {createApp,validOCR}=require('./helpers/app-harness.js');
function setup(t){const app=createApp();t.after(()=>app.dispose());return app;}
const point=(x,y)=>({clientX:x,clientY:y,pointerId:1});

test('an unreadable plate can be transcribed, reviewed, and exported without fabricated OCR',async t=>{
 const app=setup(t);await app.loadImage();await app.analyze([]);
 assert.equal(app.get('#correct').disabled,false);assert.equal(app.get('#review').disabled,true);
 app.get('#correct').click();const area=app.get('#dialog-body').children[0];
 area.children.find(x=>x.tagName==='input').value='ABC 123 DE';
 area.children.find(x=>x.tagName==='button').click();
 assert.equal(app.get('#reading').textContent,'ABC123DE');assert.equal(app.get('#score').textContent,'—');
 assert.equal(app.get('#review').disabled,false);app.get('#review').click();
 const record=app.report().records[0];assert.equal(record.candidate,null);
 assert.equal(record.manual_transcription,'ABC123DE');assert.equal(record.reviewed,true);
 assert.equal(record.per_character_confidence,null);
});

test('changing a crop clears the previous review, and a click cannot reuse an old rectangle',async t=>{
 const app=setup(t);await app.loadImage();await app.analyze(validOCR);app.get('#review').click();
 app.get('#crop').click();const canvas=app.get('#viewer');
 canvas.onpointerdown(point(100,100));canvas.onpointermove(point(300,200));canvas.onpointerup();
 assert.equal(app.get('#reading').textContent,'— — —');assert.equal(app.get('#review').disabled,true);
 await app.analyze(validOCR);assert.ok(app.report().records[1].region);
 canvas.onpointerdown(point(200,200));canvas.onpointerup();await app.analyze(validOCR);
 assert.equal(app.report().records[2].region,null);
 assert.equal(app.report().records[0].reviewed,true);
});

test('switching image or history resets crop interaction and source labels',async t=>{
 const app=setup(t);await app.loadImage('synthetic-demo.png');await app.analyze(validOCR);
 app.get('#crop').click();await app.get('#history').children[0].click();
 assert.equal(app.get('#crop').getAttribute('aria-pressed'),'false');
 assert.equal(app.get('#crop').disabled,true);
 await app.loadImage('new-image.png');assert.equal(app.get('#scene-badge').textContent,'ORIGINAL EVIDENCE');
 assert.equal(app.get('#source-type').textContent,'IMAGE INPUT');
 assert.equal(app.get('#crop').getAttribute('aria-pressed'),'false');
});

test('media decoding locks recognition until the new image is ready',async t=>{
 const app=setup(t);await app.loadImage();const decode=app.deferDecode();const load=app.loadImage('new.png');
 assert.equal(app.get('#analyze').disabled,true);assert.equal(app.get('#import').disabled,true);
 app.get('#analyze').click();assert.equal(app.requests.recognize.length,0);
 decode.resolve();await load;assert.equal(app.get('#analyze').disabled,false);
 await app.analyze(validOCR);assert.equal(app.report().records[0].source,'new.png');
});

test('a failed media decode preserves the previous input and restores controls',async t=>{
 const app=setup(t);await app.loadImage('original.png');const decode=app.deferDecode();const load=app.loadImage('bad.png');
 decode.reject(new Error('Cannot decode image'));await load;
 assert.equal(app.get('#filename').textContent,'original.png');assert.equal(app.get('#analyze').disabled,false);
 await app.analyze(validOCR);assert.equal(app.report().records[0].source,'original.png');
});

test('browser video recognition pauses playback and records the captured timestamp',async t=>{
 const app=setup(t);await app.importBrowserVideo();const video=app.get('#video');video.currentTime=3;
 const task=app.get('#analyze').click();assert.equal(video.paused,true);
 video.currentTime=5;app.deliver({id:app.requests.recognize.at(-1).id,observations:validOCR,elapsedMs:10});await task;
 assert.equal(app.report().records[0].time_seconds,3);
});

test('native frames use the actual decoded timestamp rather than the requested time',async t=>{
 const app=setup(t);await app.loadVideo(3,.04);await app.analyze(validOCR);
 app.autoFrame=()=>({image:'data:image/jpeg;base64,ZmFrZQ==',time:1.1});
 await app.get('#native-time').onchange({target:{value:'1.08'}});await app.analyze(validOCR);
 assert.equal(app.get('#native-timestamp').textContent,'1.1s');
 assert.equal(app.report().records[0].time_seconds,.04);assert.equal(app.report().records[1].time_seconds,1.1);
});

test('scanning a very short video requests a valid frame and completes',async t=>{
 const app=setup(t);await app.loadVideo(.03);
 app.autoFrame=payload=>({image:'data:image/jpeg;base64,ZmFrZQ==',time:payload.time});
 app.autoOCR=()=>({observations:validOCR,elapsedMs:10});await app.get('#scan').click();
 assert.equal(app.requests.videoFrame.length,1);assert.equal(app.requests.videoFrame[0].time,0);
 assert.equal(app.report().records.length,1);assert.equal(app.get('#import').disabled,false);
});

test('stopping during frame extraction does not start another OCR request',async t=>{
 const app=setup(t);await app.loadVideo();const scan=app.get('#scan').click();
 app.get('#stop').click();app.deliver({id:app.requests.videoFrame[0].id,image:'data:image/jpeg;base64,ZmFrZQ==',time:0});
 await scan;assert.equal(app.requests.recognize.length,0);assert.equal(app.get('#import').disabled,false);
});

test('a failed rerun clears the displayed result while keeping the original export record',async t=>{
 const app=setup(t);await app.loadImage();await app.analyze(validOCR);app.get('#review').click();
 await app.analyze([], 'Vision failed');assert.equal(app.get('#reading').textContent,'— — —');
 assert.equal(app.get('#review').disabled,true);assert.equal(app.report().records.length,1);
 assert.equal(app.report().records[0].reviewed,true);
});
