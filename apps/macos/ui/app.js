'use strict';
const $=s=>document.querySelector(s), canvas=$('#viewer'), ctx=canvas.getContext('2d');
$('#version').textContent='v'+PlateRelease.version;
const state={source:null, sourceName:'', video:false, roi:null, selecting:false, drag:null, observations:[], candidate:null, history:[], records:[], busy:false, loading:false, scanning:false, stop:false, objectURL:null, selectedChar:null, reviewed:false, corrected:null, activeRecord:null, nativeVideo:null, inspectingHistory:false, mediaName:'', serial:0};
const pending=new Map();
state.live={connected:false,connecting:false,running:false,generation:0,timer:null,sequence:0,time:0,capturedAt:null,interval:1000};
state.inputType='image';
function locked(){return state.busy||state.scanning||state.loading||state.live.running;}
function clearReading(){state.observations=[];state.candidate=null;state.corrected=null;state.reviewed=false;state.activeRecord=null;}
function resetSelection(){state.roi=null;state.drag=null;state.selecting=false;$('#crop').setAttribute('aria-pressed','false');canvas.style.cursor='default';$('#reset-crop').hidden=true;}
function notice(message){$('#notice').textContent=message;}
function dialog(title,content){$('#dialog-title').textContent=title;$('#dialog-body').replaceChildren();if(typeof content==='string'){const p=document.createElement('p');p.textContent=content;$('#dialog-body').append(p);}else $('#dialog-body').append(content);$('#dialog').showModal();}
$('#close-dialog').onclick=()=>$('#dialog').close();
window.nativeResult=result=>{const entry=pending.get(result.id);if(entry){clearTimeout(entry.timer);pending.delete(result.id);result.error?entry.reject(new Error(result.error)):entry.resolve(result);}};
function recognize(image){
  if(window.webkit?.messageHandlers?.recognize){return nativeRequest('recognize',{image:image.split(',')[1]},60000,'Recognition timed out. Reopen the app if this persists.');}
  return fetch('/api/recognize',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({image:image.split(',')[1]})}).then(async r=>{const data=await r.json();if(!r.ok)throw new Error(data.error||'Recognition unavailable');return data;});
}
function controls(){
  $('#analyze').disabled=!state.source||locked()||state.inspectingHistory;
  $('#crop').disabled=!state.source||locked()||state.inspectingHistory;
  $('#import').disabled=locked()||state.live.connected;$('#empty-import').disabled=$('#import').disabled;$('#file').disabled=$('#import').disabled;
  $('#samples').disabled=$('#import').disabled;$('#empty-sample').disabled=$('#import').disabled;$('#scan').disabled=locked();
  $('#stop').disabled=!state.scanning;$('#native-time').disabled=locked();$('#reset-crop').disabled=locked()||state.inspectingHistory;
  $('#review').disabled=!state.activeRecord||!(state.candidate||state.corrected)||locked();$('#correct').disabled=!state.activeRecord||locked();
  $('#live').disabled=locked()||state.live.connected;$('#live-pause').disabled=!state.live.connected;$('#live-disconnect').disabled=!(state.live.connected||state.live.connecting);
  $('#alternatives').querySelectorAll('button').forEach(button=>{button.disabled=locked();});
}
function workingFrame(source){const c=document.createElement('canvas');const w=source.videoWidth||source.naturalWidth||source.width,h=source.videoHeight||source.naturalHeight||source.height;if(!w||!h)throw new Error('Media has no decodable frame.');const scale=Math.min(1,1280/w,1280/h);c.width=Math.max(1,Math.round(w*scale));c.height=Math.max(1,Math.round(h*scale));c.getContext('2d').drawImage(source,0,0,c.width,c.height);return c;}
function paint(){
  if(!state.source)return;canvas.width=state.source.width;canvas.height=state.source.height;ctx.drawImage(state.source,0,0);
  const box=state.candidate?.box;if(box){ctx.strokeStyle='#6ef3b5';ctx.lineWidth=2;ctx.strokeRect(box[0]*canvas.width,box[1]*canvas.height,box[2]*canvas.width,box[3]*canvas.height);}
  const r=state.roi;if(r){ctx.fillStyle='#0a101155';ctx.fillRect(0,0,canvas.width,canvas.height);ctx.drawImage(state.source,r.x,r.y,r.w,r.h,r.x,r.y,r.w,r.h);ctx.strokeStyle='#e5b963';ctx.setLineDash([6,4]);ctx.strokeRect(r.x,r.y,r.w,r.h);ctx.setLineDash([]);}
}
function render(){
  const c=state.candidate;$('#latency').textContent=state.activeRecord?Math.round(state.activeRecord.ocr_ms)+' ms OCR':'Ready';$('#reading').textContent=state.corrected||c?.text||'— — —';$('#score').textContent=c?(100*c.confidence).toFixed(1):'—';$('#score-bar').style.width=`${c?100*c.confidence:0}%`;
  $('.ps-confidence-track').setAttribute('aria-valuenow',String(c?100*c.confidence:0));
  const views=c?.totalViews||0, support=c?.support||0, conflict=!!c?.conflicting;
  $('#plate-studio').dataset.uncertain=String(conflict);
  $('#status').textContent=state.corrected?(state.reviewed?'Reviewed by user':'Review required'):c?(conflict?(state.reviewed?'Reviewed · OCR conflict':'Conflicting readings'):state.reviewed?'Reviewed by user':'Review required'):state.activeRecord?'No plate candidate':'Awaiting recognition';$('#status').className='ps-badge'+(conflict?' ps-warn':'');
  $('#review-state').textContent=state.reviewed?'Reviewed by user':c||state.corrected?'Unreviewed':'No result';$('#origin').textContent=state.corrected?'MANUAL TRANSCRIPTION':'OCR CANDIDATE';
  $('#confidence-copy').textContent=c?(conflict?`OCR passes disagree. This reading appears in ${support} of ${views} processed views of the same image. The score is not plate accuracy.`:`This reading appears in ${support} of ${views} processed views of the same image. Agreement is not plate accuracy.`):'This score is not calibrated plate accuracy. Check every character against the source.';
  $('#decision').textContent=conflict?'Compare the readings with the source':'Your review is part of the process';
  $('#characters').replaceChildren();$('#character-detail').textContent='Select a character to inspect OCR pass agreement. Individual character probabilities are unavailable.';
  for(const [i,char] of [...(state.corrected||c?.text||'')].entries()){
    const evidence=state.corrected?null:c?.characters?.[i],b=document.createElement('button'),s=document.createElement('small');
    b.className='ps-character';b.textContent=char;s.textContent=evidence?.total?`${evidence.support}/${evidence.total}`:'N/A';b.append(s);
    if(evidence?.alternatives?.length)b.classList.add('ps-low');
    b.title=`Character ${i+1}: ${char}. ${evidence?.total?`${evidence.support} of ${evidence.total} same-length OCR views agree.`:'No comparable OCR views.'} This is not a character probability.`;
    b.onclick=()=>{$('#characters').querySelectorAll('button').forEach(x=>x.classList.remove('selected'));b.classList.add('selected');$('#character-detail').textContent=evidence?.total?`Character ${i+1}: ${char}. ${evidence.support} of ${evidence.total} same-length OCR views agree.${evidence.alternatives.length?` Other readings: ${evidence.alternatives.join(', ')}.`:''} These views reuse the same pixels; agreement is not a probability.`:`Character ${i+1}: ${char}. No comparable OCR views. Individual character probability is unavailable.`;};
    $('#characters').append(b);
  }
  $('#alternatives').replaceChildren();const options=PlateCore.candidates(state.observations).slice(0,8);
  for(const alt of options){const b=document.createElement('button');b.textContent=alt.text;const s=document.createElement('span');s.textContent=`${alt.support}/${alt.totalViews} views · OCR ${(alt.confidence*100).toFixed(0)}%`;b.append(s);b.disabled=locked();b.onclick=()=>{if(locked())return;state.candidate=alt;state.reviewed=false;state.corrected=null;if(state.activeRecord){state.activeRecord.selected_candidate=alt;state.activeRecord.reviewed=false;state.activeRecord.manual_transcription=null;}render();paint();};$('#alternatives').append(b);}
  if(!options.length){const p=document.createElement('p');p.className='ps-muted';p.textContent=state.observations.length?'No text matched the candidate format. Select a tighter plate region and retry.':state.activeRecord?'No readable plate text was found. Try a tighter plate region.':'No recognition results yet.';$('#alternatives').append(p);}
  controls();paint();
}
function setSource(source,name,isVideo=false,nativeVideo=null){const frame=workingFrame(source);state.source=frame;state.inputType=isVideo?'video':'image';state.nativeVideo=nativeVideo;$('#native-timeline').hidden=!nativeVideo;$('#video').hidden=!!nativeVideo;state.sourceName=name;state.mediaName=name;state.inspectingHistory=false;state.video=isVideo;resetSelection();clearReading();$('#filename').textContent=name;$('#empty').hidden=true;$('#scene-badge').hidden=false;$('#scene-badge').textContent='ORIGINAL EVIDENCE';$('#source-type').textContent=/synthetic|simulation/i.test(name)?'SYNTHETIC DEMONSTRATION':isVideo?'VIDEO INPUT':'IMAGE INPUT';$('#video-controls').hidden=!isVideo;render();}
function pick(){if(locked())return;if(window.webkit?.messageHandlers?.importMedia){window.webkit.messageHandlers.importMedia.postMessage({});}else{$('#file').click();}}
$('#import').onclick=pick;$('#empty-import').onclick=pick;
$('#file').onchange=async event=>{
  const file=event.target.files[0];if(!file||locked())return;
  if(file.size>1024*1024*1024){notice('Choose a file smaller than 1 GB.');return;}
  state.loading=true;controls();const url=URL.createObjectURL(file);
  try{if(file.type.startsWith('video/')){const video=document.createElement('video');video.id='video';video.controls=true;video.preload='metadata';video.playsInline=true;await new Promise((resolve,reject)=>{video.onloadeddata=resolve;video.onerror=()=>reject(new Error('Unsupported video codec. Try H.264 MP4.'));video.src=url;});$('#video').pause();$('#video').replaceWith(video);setSource(video,file.name,true);}
  else {const img=new Image();img.src=url;await img.decode();setSource(img,file.name);$('#video').pause();$('#video').removeAttribute('src');$('#video').load();}
  if(state.objectURL)URL.revokeObjectURL(state.objectURL);state.objectURL=url;
  notice('Media ready. Recognize the full frame or select the plate region.');}catch(e){URL.revokeObjectURL(url);notice(e.message);}finally{state.loading=false;controls();event.target.value='';}
};
function addHistory(result){
  state.history.unshift(result);if(state.history.length>12)state.history.pop();
  $('#history').replaceChildren();$('#history-count').textContent=String(state.history.length).padStart(2,'0');
  for(const item of state.history){const b=document.createElement('button'),im=document.createElement('img'),label=document.createElement('span');im.src=item.preview;im.alt='Analyzed source frame';label.textContent=`${item.candidate?.text||'No candidate'} · ${item.time===null?'image':item.time.toFixed(1)+'s'}`;b.append(im,label);b.onclick=async()=>{if(locked())return;state.loading=true;controls();try{const img=new Image();img.src=item.preview;await img.decode();state.source=workingFrame(img);state.sourceName=item.name;state.inspectingHistory=true;state.observations=item.observations;state.candidate=item.record.selected_candidate||item.candidate;resetSelection();state.reviewed=item.record.reviewed;state.corrected=item.record.manual_transcription;state.activeRecord=item.record;$('#filename').textContent=item.name;$('#source-type').textContent=/synthetic|simulation/i.test(item.name)?'SYNTHETIC DEMONSTRATION':item.time===null?'IMAGE INPUT':'VIDEO INPUT';$('#scene-badge').textContent='HISTORY FRAME'+(item.time===null?'':' · '+item.time.toFixed(1)+'s');render();}catch(e){notice(`Could not load history frame: ${e.message}`);}finally{state.loading=false;controls();}};$('#history').append(b);}
}
async function analyze(fromScan=false,fromLive=false){
  if(state.busy||state.loading||!state.source||state.inspectingHistory||(state.scanning&&!fromScan)||(state.live.running&&!fromLive))return;state.busy=true;controls();$('#status').textContent='Recognizing…';notice('Reading image locally…');
  try{
    if(state.video&&!state.nativeVideo){$('#video').pause();state.source=workingFrame($('#video'));}
    const live=state.inputType==='live'?{time:state.live.time,capturedAt:state.live.capturedAt}:null;
    const time=live?live.time:state.video?(state.nativeVideo||$('#video')).currentTime:null;
    const r=state.roi;let input=state.source;
    clearReading();paint();
    if(r){input=document.createElement('canvas');const scale=Math.min(4,1280/r.w,1280/r.h);input.width=Math.max(1,Math.round(r.w*scale));input.height=Math.max(1,Math.round(r.h*scale));input.getContext('2d').drawImage(state.source,r.x,r.y,r.w,r.h,0,0,input.width,input.height);}
    const result=await recognize(input.toDataURL('image/jpeg',.94));
    state.observations=result.observations.map(o=>({...o,box:r?[(r.x+o.box[0]*r.w)/state.source.width,(r.y+o.box[1]*r.h)/state.source.height,o.box[2]*r.w/state.source.width,o.box[3]*r.h/state.source.height]:o.box}));
    state.candidate=PlateCore.candidates(state.observations)[0]||null;state.corrected=null;state.reviewed=false;
    $('#latency').textContent=`${Math.round(result.elapsedMs)} ms OCR`;$('#scene-badge').textContent=r?'USER-SELECTED PLATE REGION':live?'LIVE CAMERA FRAME':'ORIGINAL EVIDENCE';
    const record={source:state.sourceName,input_type:state.inputType,time_seconds:time,captured_at:live?.capturedAt||null,engine:'Apple Vision',candidate:state.candidate,observations:state.observations,ocr_ms:result.elapsedMs,calibrated:false,per_character_confidence:null,reviewed:false,manual_transcription:null,synthetic:/synthetic|simulation/i.test(state.sourceName),region:r};
    state.activeRecord=record;state.records.push(record);if(state.records.length>1000)state.records.shift();
    addHistory({record,name:state.sourceName,time,candidate:state.candidate,observations:state.observations,preview:state.source.toDataURL('image/jpeg',.72)});
    notice(state.candidate?'Reading ready. Inspect the source before marking it reviewed.':'No matching plate candidate. Try selecting a tighter plate region.');
  }catch(error){notice(`Recognition failed: ${error.message}`);if(fromScan)state.stop=true;if(fromLive){state.live.running=false;$('#live-pause').textContent='Resume recognition';$('#live-state').textContent='Recognition paused after an error';}}
  finally{state.busy=false;render();}
}
$('#analyze').onclick=()=>analyze();
$('#crop').onclick=()=>{if(locked()||state.inspectingHistory)return;$('#video').pause();state.selecting=!state.selecting;$('#crop').setAttribute('aria-pressed',String(state.selecting));canvas.style.cursor=state.selecting?'crosshair':'default';notice(state.selecting?'Drag a rectangle around the plate, then recognize again.':'Plate region selection off.');};
function point(event){const r=canvas.getBoundingClientRect();return {x:Math.max(0,Math.min(canvas.width,(event.clientX-r.left)*canvas.width/r.width)),y:Math.max(0,Math.min(canvas.height,(event.clientY-r.top)*canvas.height/r.height))};}
canvas.onpointerdown=e=>{if(!state.selecting||locked()||state.inspectingHistory)return;clearReading();state.roi=null;state.drag=point(e);$('#reset-crop').hidden=true;canvas.setPointerCapture(e.pointerId);render();};
canvas.onpointermove=e=>{if(!state.drag)return;const p=point(e);state.roi={x:Math.min(state.drag.x,p.x),y:Math.min(state.drag.y,p.y),w:Math.abs(state.drag.x-p.x),h:Math.abs(state.drag.y-p.y)};paint();};
canvas.onpointerup=()=>{if(!state.drag)return;state.drag=null;if(!state.roi||state.roi.w<8||state.roi.h<8){state.roi=null;notice('Select a larger plate region.');}else{notice('Plate region selected. Click Recognize frame.');}$('#reset-crop').hidden=!state.roi;paint();};
canvas.onpointercancel=()=>{state.drag=null;state.roi=null;$('#reset-crop').hidden=true;paint();};
$('#reset-crop').onclick=()=>{if(locked()||state.inspectingHistory)return;resetSelection();clearReading();render();};
async function seek(video,time){if(state.nativeVideo){await nativeFrame(time);return;}if(Math.abs(video.currentTime-time)<.02)return;await new Promise((resolve,reject)=>{const timer=setTimeout(()=>{video.removeEventListener('seeked',done);reject(new Error('Video seek timed out.'));},10000);function done(){clearTimeout(timer);resolve();}video.addEventListener('seeked',done,{once:true});video.currentTime=time;});}
$('#scan').onclick=async()=>{
 if(locked()||!state.video)return;const video=state.nativeVideo||$('#video');const times=PlateCore.sampleTimes(video.duration,video.currentTime);if(!times.length){notice('Video duration unavailable.');return;}
 state.scanning=true;state.inspectingHistory=false;state.sourceName=state.mediaName;$('#filename').textContent=state.mediaName;state.stop=false;video.pause?.();video.controls=false;controls();
 try{for(let i=0;i<times.length&&!state.stop;i++){await seek(video,times[i]);if(state.stop)break;$('#progress').textContent=`Frame ${i+1} / ${times.length}`;await analyze(true);}notice(state.stop?'Scan stopped. Completed readings are available for export.':'Scan complete. Each result is an independent frame reading.');}
 catch(e){notice(e.message);}finally{state.scanning=false;video.controls=true;render();}
};
$('#stop').onclick=()=>{state.stop=true;notice('Stopping after the current frame…');};
$('#review').onclick=()=>{state.reviewed=true;const last=state.activeRecord;if(last){last.reviewed=true;last.manual_transcription=state.corrected;last.selected_candidate=state.candidate;}render();notice('Marked reviewed by user. OCR confidence is unchanged.');};
$('#correct').onclick=()=>{if(locked()||!state.activeRecord)return;const area=document.createElement('div'),p=document.createElement('p'),input=document.createElement('input'),save=document.createElement('button');p.textContent='Manual transcription is stored separately from the original OCR reading and does not change its score.';input.value=state.corrected||state.candidate?.text||'';input.maxLength=16;input.setAttribute('aria-label','Manual plate transcription');save.className='ps-button ps-primary';save.textContent='Save transcription';save.onclick=()=>{const value=PlateCore.normalize(input.value);if(!value){input.focus();return;}state.corrected=value;state.reviewed=false;const last=state.activeRecord;if(last){last.manual_transcription=value;last.reviewed=false;}$('#dialog').close();render();};area.append(p,input,save);dialog('Edit transcription',area);input.focus();};
$('#export').onclick=()=>{if(!state.records.length){notice('Run recognition first to export measured results.');return;}const data=JSON.stringify({project:'PlateLab',author:'Akinwale Awoyele',exported_at:new Date().toISOString(),note:'Uncalibrated OCR scores. No training or accuracy claim. Synthetic videos are demonstrations only.',records:state.records},null,2);const area=document.createElement('div'),p=document.createElement('p'),text=document.createElement('textarea'),button=document.createElement('button');p.textContent=`${state.records.length} results. Copy the JSON below or save it as a report.`;text.value=data;text.rows=12;text.readOnly=true;button.textContent='Save JSON';button.className='ps-button ps-primary';button.onclick=()=>{if(window.webkit?.messageHandlers?.exportReport){window.webkit.messageHandlers.exportReport.postMessage(data);}else{const url=URL.createObjectURL(new Blob([data],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='platelab-results.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),2000);}};area.append(p,text,button);dialog('Recognition report',area);};
async function samples(){
 if(locked())return;
 try{const entries=window.DATASET_SAMPLES||await fetch('samples.json').then(r=>r.json());const grid=document.createElement('div');grid.className='sample-grid';for(const entry of entries){const b=document.createElement('button'),im=document.createElement('img'),label=document.createElement('span');im.src=entry.path;im.alt=entry.name;label.textContent=entry.name;b.append(im,label);b.onclick=async()=>{try{$('#video').pause();const photo=new Image();photo.src=entry.path;await photo.decode();setSource(photo,entry.name);$('#dialog').close();notice('Dataset sample loaded. Click Recognize frame.');}catch(e){notice(e.message);}};grid.append(b);}dialog('Your dataset · Akinwale Awoyele',grid);}catch(e){dialog('Dataset samples','No local samples are packaged. Import an image from your computer.');}
}
$('#samples').onclick=samples;$('#empty-sample').onclick=samples;$('#studio').onclick=()=>$('#scene').scrollIntoView({behavior:'smooth'});
$('#help').onclick=()=>dialog('About this baseline','PlateLab uses Apple Vision to read visible text locally. Candidate format checks prioritize plate-like strings but do not verify registration or recover fully hidden characters. OCR confidence is an uncalibrated score for a text observation, not the probability the full plate is correct; character confidence is unavailable. Select a tighter plate region for small text. Video is sampled sequentially at one frame per second with no tracking or temporal fusion. This prototype has not been trained on the dataset or evaluated for highway accuracy.');
render();

function nativeRequest(channel,payload,timeout=30000,timeoutMessage='Media request timed out.'){return new Promise((resolve,reject)=>{const handler=window.webkit?.messageHandlers?.[channel];if(!handler){reject(new Error('This operation is unavailable in the browser preview.'));return;}const id=String(++state.serial);const timer=setTimeout(()=>{pending.delete(id);reject(new Error(timeoutMessage));},timeout);pending.set(id,{resolve,reject,timer});try{handler.postMessage({id,...payload});}catch(e){clearTimeout(timer);pending.delete(id);reject(e);}});}
async function nativeFrame(time){const result=await nativeRequest('videoFrame',{time});const img=new Image();img.src=result.image;await img.decode();state.source=workingFrame(img);const actualTime=Number.isFinite(result.time)?result.time:time;state.nativeVideo.currentTime=actualTime;$('#native-time').value=actualTime;$('#native-timestamp').textContent=actualTime.toFixed(1)+'s';clearReading();paint();}
window.nativeMedia=async result=>{if(result.error){notice(result.error);return;}if(locked()||state.live.connected)return;state.loading=true;controls();try{let initial=result;if(result.type==='video'&&!result.image)initial=await nativeRequest('videoFrame',{time:0});const img=new Image();img.src=initial.image;await img.decode();if(result.type==='video'){const time=Number.isFinite(initial.time)?initial.time:0;setSource(img,result.name,true,{duration:result.duration,currentTime:time});$('#native-time').max=Math.max(0,result.duration-.05);$('#native-time').value=time;$('#native-timestamp').textContent=time.toFixed(1)+'s';}else{setSource(img,result.name);}notice('Media ready. Recognize a frame or select a plate region.');}catch(e){notice(e.message);}finally{state.loading=false;controls();}};
$('#native-time').onchange=async event=>{if(locked())return;state.busy=true;controls();try{await nativeFrame(Number(event.target.value));state.inspectingHistory=false;state.sourceName=state.mediaName;$('#filename').textContent=state.mediaName;$('#scene-badge').textContent='ORIGINAL EVIDENCE';state.inputType='video';clearReading();}catch(e){notice(e.message);}finally{state.busy=false;render();}};

function liveCurrent(generation){return generation===state.live.generation&&state.live.running;}
function scheduleLive(generation,delay){if(liveCurrent(generation))state.live.timer=setTimeout(()=>pollLive(generation),delay);}
async function applyLiveFrame(result,generation){
 const img=new Image();img.src=result.image;await img.decode();if(!liveCurrent(generation))return false;
 const frame=workingFrame(img);if(state.source&&(frame.width!==state.source.width||frame.height!==state.source.height))resetSelection();
 state.source=frame;state.sourceName=state.mediaName;state.inspectingHistory=false;state.inputType='live';
 state.live.sequence=result.sequence;state.live.time=result.time;state.live.capturedAt=result.captured_at;
 clearReading();$('#filename').textContent=state.mediaName;$('#source-type').textContent='LIVE CAMERA';$('#scene-badge').textContent='LIVE CAMERA FRAME';paint();return true;
}
async function pollLive(generation){
 if(!liveCurrent(generation))return;if(state.busy||state.loading){scheduleLive(generation,100);return;}const started=Date.now();
 try{const result=await nativeRequest('liveFrame',{sequence:state.live.sequence});if(!liveCurrent(generation))return;
  if(result.changed!==false&&await applyLiveFrame(result,generation)){if(!liveCurrent(generation))return;await analyze(false,true);}
  if(liveCurrent(generation))$('#live-state').textContent='Connected · recognizing latest frames';
 }catch(e){if(liveCurrent(generation)){state.live.running=false;$('#live-pause').textContent='Resume recognition';$('#live-state').textContent='Feed interrupted · reconnect to continue';notice(e.message);}}
 finally{if(liveCurrent(generation))scheduleLive(generation,Math.max(100,state.live.interval-(Date.now()-started)));controls();}
}
async function connectLive(url,name,interval){
 const generation=++state.live.generation;state.live.connecting=true;state.loading=true;$('#live-controls').hidden=false;$('#live-state').textContent='Connecting to camera…';controls();
 try{const result=await nativeRequest('connectLive',{url});if(generation!==state.live.generation)return;
  const img=new Image();img.src=result.image;await img.decode();if(generation!==state.live.generation)return;
  setSource(img,name);state.inputType='live';state.live.connected=true;state.live.running=true;state.live.interval=interval;state.live.sequence=result.sequence;state.live.time=result.time;state.live.capturedAt=result.captured_at;
  $('#source-type').textContent='LIVE CAMERA';$('#live-pause').textContent='Pause recognition';$('#live-state').textContent='Connected · recognizing latest frames';if($('#dialog-title').textContent==='Connect live camera')$('#dialog').close();
  scheduleLive(generation,0);
 }catch(e){if(generation===state.live.generation){$('#live-state').textContent='Connection failed';notice(e.message);try{await nativeRequest('disconnectLive',{});}catch{}}}
 finally{if(generation===state.live.generation){state.loading=false;state.live.connecting=false;controls();}}
}
$('#live-pause').onclick=()=>{
 if(!state.live.connected)return;state.live.generation++;clearTimeout(state.live.timer);state.live.running=!state.live.running;
 $('#live-pause').textContent=state.live.running?'Pause recognition':'Resume recognition';$('#live-state').textContent=state.live.running?'Connected · recognizing latest frames':'Connected · recognition paused';controls();
 if(state.live.running)scheduleLive(state.live.generation,0);
};
$('#live-disconnect').onclick=async()=>{
 state.live.generation++;clearTimeout(state.live.timer);state.live.running=false;state.live.connected=false;state.live.connecting=false;state.loading=false;$('#live-state').textContent='Disconnected';controls();
 try{await nativeRequest('disconnectLive',{});notice('Camera disconnected. Completed readings are available for review and export.');}catch(e){notice(e.message);}
};
$('#live').onclick=()=>{
 if(locked()||state.live.connected)return;
 if(!window.webkit?.messageHandlers?.connectLive){dialog('Live camera feed','Live feeds are available in the native macOS app. Build it with the live-feed dependency to connect an RTSP camera.');return;}
 const area=document.createElement('div'),p=document.createElement('p'),url=document.createElement('input'),name=document.createElement('input'),interval=document.createElement('select'),connect=document.createElement('button');
 p.textContent='Connect an RTSP street-camera feed and recognize plates from its latest frames.';
 url.type='password';url.placeholder='rtsp://camera-address:554/stream';url.autocomplete='off';url.setAttribute('aria-label','Camera feed URL');
 name.placeholder='Camera name';name.value='Street camera';name.maxLength=80;name.setAttribute('aria-label','Camera name');
 for(const [value,label] of [[1000,'Recognize up to once per second'],[2000,'Recognize up to once every 2 seconds']]){const option=document.createElement('option');option.value=String(value);option.textContent=label;interval.append(option);}interval.value='1000';interval.setAttribute('aria-label','Recognition interval');
 connect.className='ps-button ps-primary';connect.textContent='Connect';connect.onclick=async()=>{
  if(locked())return;let parsed;try{parsed=new URL(url.value.trim());if(!['rtsp:','rtsps:','http:','https:'].includes(parsed.protocol)||!parsed.hostname)throw new Error();}catch{p.textContent='Enter a valid RTSP or HTTP camera URL.';url.focus();return;}
  connect.disabled=true;const address=parsed.href;url.value='';await connectLive(address,name.value.trim()||'Street camera',Number(interval.value));connect.disabled=false;
 };area.append(p,url,name,interval,connect);dialog('Connect live camera',area);url.focus();
};

$('#support').onclick=()=>{
 const panel=document.createElement('div'),qr=document.createElement('div'),image=document.createElement('img'),caption=document.createElement('p'),body=document.createElement('div'),badge=document.createElement('span'),title=document.createElement('h3'),intro=document.createElement('p'),label=document.createElement('p'),address=document.createElement('code'),actions=document.createElement('div'),wallet=document.createElement('a'),copy=document.createElement('button'),status=document.createElement('p');
 panel.className='support-panel';qr.className='support-qr';body.className='support-body';badge.className='support-badge';
 image.src='support-qr.svg';image.alt='Scan to support PlateLab with Bitcoin';image.width=228;image.height=228;
 caption.textContent='Scan with your Bitcoin wallet';qr.append(image,caption);
 badge.textContent='BITCOIN · BTC';title.textContent='Help build what comes next.';
 intro.textContent='Your contribution supports better recognition, more testing, and a clearer experience for everyone using PlateLab.';
 label.className='support-label';label.textContent='Bitcoin address';address.className='support-address';address.textContent=PlateSupport.address;
 actions.className='support-actions';wallet.className='ps-button ps-primary';wallet.href=PlateSupport.uri;wallet.textContent='Open Bitcoin wallet';copy.className='ps-button';copy.textContent='Copy address';
 status.className='support-status';status.textContent='Send BTC on the Bitcoin network. Choose your donation amount in your wallet.';status.setAttribute('role','status');status.setAttribute('aria-live','polite');
 wallet.onclick=async event=>{event.preventDefault();if(!window.webkit?.messageHandlers?.supportWallet){status.textContent='Scan the QR code with your Bitcoin wallet or copy the address. To open an installed desktop wallet, use the PlateLab macOS app.';return;}try{await nativeRequest('supportWallet',{});status.textContent='Choose an amount in your wallet and confirm the donation there.';}catch(e){status.textContent=e.message;}};
 copy.onclick=async()=>{try{if(window.webkit?.messageHandlers?.copySupportAddress){await nativeRequest('copySupportAddress',{});}else if(navigator.clipboard?.writeText){await navigator.clipboard.writeText(PlateSupport.address);}else{status.textContent='Select and copy the Bitcoin address above.';return;}copy.textContent='✓ Copied';status.textContent='Bitcoin address copied.';}catch{status.textContent='Could not copy automatically. Select and copy the address above.';}};
 actions.append(wallet,copy);body.append(badge,title,intro,label,address,actions,status);panel.append(qr,body);dialog('Support PlateLab',panel);
};
