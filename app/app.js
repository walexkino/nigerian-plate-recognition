'use strict';
const $=s=>document.querySelector(s), canvas=$('#viewer'), ctx=canvas.getContext('2d');
const state={source:null, sourceName:'', video:false, roi:null, selecting:false, drag:null, observations:[], candidate:null, history:[], records:[], busy:false, scanning:false, stop:false, objectURL:null, selectedChar:null, reviewed:false, corrected:null, activeRecord:null, nativeVideo:null, inspectingHistory:false, mediaName:'', serial:0};
const pending=new Map();
function notice(message){$('#notice').textContent=message;}
function dialog(title,content){$('#dialog-title').textContent=title;$('#dialog-body').replaceChildren();if(typeof content==='string'){const p=document.createElement('p');p.textContent=content;$('#dialog-body').append(p);}else $('#dialog-body').append(content);$('#dialog').showModal();}
$('#close-dialog').onclick=()=>$('#dialog').close();
window.nativeResult=result=>{const entry=pending.get(result.id);if(entry){clearTimeout(entry.timer);pending.delete(result.id);result.error?entry.reject(new Error(result.error)):entry.resolve(result);}};
function recognize(image){
  if(window.webkit?.messageHandlers?.recognize){return new Promise((resolve,reject)=>{const id=String(++state.serial);const timer=setTimeout(()=>{pending.delete(id);reject(new Error('Recognition timed out. Reopen the app if this persists.'));},60000);pending.set(id,{resolve,reject,timer});window.webkit.messageHandlers.recognize.postMessage({id,image:image.split(',')[1]});});}
  return fetch('/api/recognize',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({image:image.split(',')[1]})}).then(async r=>{const data=await r.json();if(!r.ok)throw new Error(data.error||'Recognition unavailable');return data;});
}
function controls(){
  $('#analyze').disabled=!state.source||state.busy||state.scanning||state.inspectingHistory;
  $('#crop').disabled=!state.source||state.busy||state.scanning||state.inspectingHistory;
  $('#import').disabled=state.busy||state.scanning;$('#file').disabled=state.busy||state.scanning;
  $('#samples').disabled=state.busy||state.scanning;$('#scan').disabled=state.busy||state.scanning;
  $('#stop').disabled=!state.scanning;$('#native-time').disabled=state.busy||state.scanning;$('#reset-crop').disabled=state.busy||state.scanning;
  $('#review').disabled=!state.candidate||state.busy||state.scanning;$('#correct').disabled=!state.candidate||state.busy||state.scanning;
}
function workingFrame(source){const c=document.createElement('canvas');const w=source.videoWidth||source.naturalWidth||source.width,h=source.videoHeight||source.naturalHeight||source.height;if(!w||!h)throw new Error('Media has no decodable frame.');const scale=Math.min(1,1280/w,1280/h);c.width=Math.round(w*scale);c.height=Math.round(h*scale);c.getContext('2d').drawImage(source,0,0,c.width,c.height);return c;}
function paint(){
  if(!state.source)return;canvas.width=state.source.width;canvas.height=state.source.height;ctx.drawImage(state.source,0,0);
  const box=state.candidate?.box;if(box){ctx.strokeStyle='#6ef3b5';ctx.lineWidth=2;ctx.strokeRect(box[0]*canvas.width,box[1]*canvas.height,box[2]*canvas.width,box[3]*canvas.height);}
  const r=state.roi;if(r){ctx.fillStyle='#0a101155';ctx.fillRect(0,0,canvas.width,canvas.height);ctx.drawImage(state.source,r.x,r.y,r.w,r.h,r.x,r.y,r.w,r.h);ctx.strokeStyle='#e5b963';ctx.setLineDash([6,4]);ctx.strokeRect(r.x,r.y,r.w,r.h);ctx.setLineDash([]);}
}
function render(){
  const c=state.candidate;$('#latency').textContent=state.activeRecord?Math.round(state.activeRecord.ocr_ms)+' ms OCR':'Ready';$('#reading').textContent=state.corrected||c?.text||'— — —';$('#score').textContent=c?(100*c.confidence).toFixed(1):'—';$('#score-bar').style.width=`${c?100*c.confidence:0}%`;
  $('.ps-confidence-track').setAttribute('aria-valuenow',String(c?100*c.confidence:0));
  $('#status').textContent=c?(state.reviewed?'Reviewed by user':'Review required'):state.activeRecord?'No plate candidate':'Awaiting recognition';$('#status').className='ps-badge';
  $('#review-state').textContent=state.reviewed?'Reviewed by user':c?'Unreviewed':'No result';$('#origin').textContent=state.corrected?'MANUAL TRANSCRIPTION':'OCR CANDIDATE';
  $('#characters').replaceChildren();$('#character-detail').textContent='Individual character probabilities are unavailable from this baseline.';
  for(const [i,char] of [...(state.corrected||c?.text||'')].entries()){const b=document.createElement('button');b.textContent=char;const s=document.createElement('small');s.textContent='N/A';b.append(s);b.title=`Character ${i+1}. Per-character confidence unavailable.`;b.onclick=()=>{$('#characters').querySelectorAll('button').forEach(x=>x.classList.remove('selected'));b.classList.add('selected');$('#character-detail').textContent=`Character ${i+1}: ${char}. This baseline provides a score for the text observation, not a separate probability for this character.`;};$('#characters').append(b);}
  $('#alternatives').replaceChildren();const options=PlateCore.candidates(state.observations).slice(0,8);
  for(const alt of options){const b=document.createElement('button');b.textContent=alt.text;const s=document.createElement('span');s.textContent=`${(alt.confidence*100).toFixed(1)}%`;b.append(s);b.disabled=state.scanning||state.busy;b.onclick=()=>{state.candidate=alt;state.reviewed=false;state.corrected=null;if(state.activeRecord){state.activeRecord.selected_candidate=alt;state.activeRecord.reviewed=false;state.activeRecord.manual_transcription=null;}render();paint();};$('#alternatives').append(b);}
  if(!options.length){const p=document.createElement('p');p.className='ps-muted';p.textContent=state.observations.length?'No text matched the candidate format. Select a tighter plate region and retry.':state.activeRecord?'No readable plate text was found. Try a tighter plate region.':'No recognition results yet.';$('#alternatives').append(p);}
  controls();paint();
}
function setSource(source,name,isVideo=false){if(!isVideo){state.nativeVideo=null;$('#native-timeline').hidden=true;}state.source=workingFrame(source);state.sourceName=name;state.mediaName=name;state.inspectingHistory=false;state.video=isVideo;state.roi=null;state.observations=[];state.candidate=null;state.corrected=null;state.reviewed=false;state.activeRecord=null;$('#filename').textContent=name;$('#empty').hidden=true;$('#scene-badge').hidden=false;$('#reset-crop').hidden=true;$('#source-type').textContent=/synthetic|simulation/i.test(name)?'SYNTHETIC DEMONSTRATION':isVideo?'VIDEO INPUT':'IMAGE INPUT';$('#video-controls').hidden=!isVideo;render();}
function pick(){if(state.busy||state.scanning)return;if(window.webkit?.messageHandlers?.importMedia){window.webkit.messageHandlers.importMedia.postMessage({});}else{$('#file').click();}}
$('#import').onclick=pick;$('#empty-import').onclick=pick;
$('#file').onchange=async event=>{
  const file=event.target.files[0];if(!file)return;
  if(file.size>1024*1024*1024){notice('Choose a file smaller than 1 GB.');return;}
  if(state.objectURL)URL.revokeObjectURL(state.objectURL);state.objectURL=URL.createObjectURL(file);
  try{if(file.type.startsWith('video/')){const video=$('#video');video.src=state.objectURL;await new Promise((resolve,reject)=>{video.onloadeddata=resolve;video.onerror=()=>reject(new Error('Unsupported video codec. Try H.264 MP4.'));});setSource(video,file.name,true);}
  else {$('#video').pause();$('#video').removeAttribute('src');$('#video').load();const img=new Image();img.src=state.objectURL;await img.decode();setSource(img,file.name);}
  notice('Media ready. Recognize the full frame or select the plate region.');}catch(e){notice(e.message);}event.target.value='';
};
function addHistory(result){
  state.history.unshift(result);if(state.history.length>12)state.history.pop();
  $('#history').replaceChildren();$('#history-count').textContent=String(state.history.length).padStart(2,'0');
  for(const item of state.history){const b=document.createElement('button'),im=document.createElement('img'),label=document.createElement('span');im.src=item.preview;im.alt='Analyzed source frame';label.textContent=`${item.candidate?.text||'No candidate'} · ${item.time===null?'image':item.time.toFixed(1)+'s'}`;b.append(im,label);b.onclick=async()=>{if(state.busy||state.scanning)return;const img=new Image();img.src=item.preview;await img.decode();state.source=workingFrame(img);state.sourceName=item.name;state.inspectingHistory=true;state.observations=item.observations;state.candidate=item.record.selected_candidate||item.candidate;state.roi=null;state.corrected=null;state.reviewed=item.record.reviewed;state.corrected=item.record.manual_transcription;state.activeRecord=item.record;$('#reset-crop').hidden=true;$('#filename').textContent=item.name;$('#scene-badge').textContent='HISTORY FRAME'+(item.time===null?'':' · '+item.time.toFixed(1)+'s');render();};$('#history').append(b);}
}
async function analyze(fromScan=false){
  if(state.busy||!state.source)return;state.busy=true;controls();$('#status').textContent='Recognizing…';notice('Reading image locally…');
  try{
    if(state.video&&!state.nativeVideo){state.source=workingFrame($('#video'));}
    const r=state.roi;let input=state.source;
    if(r){input=document.createElement('canvas');const scale=Math.min(4,1280/r.w,1280/r.h);input.width=Math.max(1,Math.round(r.w*scale));input.height=Math.max(1,Math.round(r.h*scale));input.getContext('2d').drawImage(state.source,r.x,r.y,r.w,r.h,0,0,input.width,input.height);}
    const result=await recognize(input.toDataURL('image/jpeg',.94));
    state.observations=result.observations.map(o=>({...o,box:r?[(r.x+o.box[0]*r.w)/state.source.width,(r.y+o.box[1]*r.h)/state.source.height,o.box[2]*r.w/state.source.width,o.box[3]*r.h/state.source.height]:o.box}));
    state.candidate=PlateCore.candidates(state.observations)[0]||null;state.corrected=null;state.reviewed=false;
    $('#latency').textContent=`${Math.round(result.elapsedMs)} ms OCR`;$('#scene-badge').textContent=r?'USER-SELECTED PLATE REGION':'ORIGINAL EVIDENCE';
    const time=state.video?(state.nativeVideo||$('#video')).currentTime:null;
    const record={source:state.sourceName,time_seconds:time,engine:'Apple Vision',candidate:state.candidate,observations:state.observations,ocr_ms:result.elapsedMs,calibrated:false,per_character_confidence:null,reviewed:false,manual_transcription:null,synthetic:/synthetic|simulation/i.test(state.sourceName),region:r};
    state.activeRecord=record;state.records.push(record);if(state.records.length>1000)state.records.shift();
    addHistory({record,name:state.sourceName,time,candidate:state.candidate,observations:state.observations,preview:state.source.toDataURL('image/jpeg',.72)});
    notice(state.candidate?'Reading ready. Inspect the source before marking it reviewed.':'No matching plate candidate. Try selecting a tighter plate region.');
  }catch(error){notice(`Recognition failed: ${error.message}`);if(fromScan)state.stop=true;}
  finally{state.busy=false;render();}
}
$('#analyze').onclick=()=>analyze();
$('#crop').onclick=()=>{state.selecting=!state.selecting;$('#crop').setAttribute('aria-pressed',String(state.selecting));canvas.style.cursor=state.selecting?'crosshair':'default';notice(state.selecting?'Drag a rectangle around the plate, then recognize again.':'Plate region selection off.');};
function point(event){const r=canvas.getBoundingClientRect();return {x:Math.max(0,Math.min(canvas.width,(event.clientX-r.left)*canvas.width/r.width)),y:Math.max(0,Math.min(canvas.height,(event.clientY-r.top)*canvas.height/r.height))};}
canvas.onpointerdown=e=>{if(!state.selecting||state.busy||state.scanning)return;state.drag=point(e);canvas.setPointerCapture(e.pointerId);};
canvas.onpointermove=e=>{if(!state.drag)return;const p=point(e);state.roi={x:Math.min(state.drag.x,p.x),y:Math.min(state.drag.y,p.y),w:Math.abs(state.drag.x-p.x),h:Math.abs(state.drag.y-p.y)};paint();};
canvas.onpointerup=()=>{if(!state.drag)return;state.drag=null;if(!state.roi||state.roi.w<8||state.roi.h<8){state.roi=null;notice('Select a larger plate region.');}else{notice('Plate region selected. Click Recognize frame.');}$('#reset-crop').hidden=!state.roi;paint();};
$('#reset-crop').onclick=()=>{state.roi=null;$('#reset-crop').hidden=true;paint();};
async function seek(video,time){if(state.nativeVideo){await nativeFrame(time);return;}if(Math.abs(video.currentTime-time)<.02)return;await new Promise((resolve,reject)=>{const timer=setTimeout(()=>{video.removeEventListener('seeked',done);reject(new Error('Video seek timed out.'));},10000);function done(){clearTimeout(timer);resolve();}video.addEventListener('seeked',done,{once:true});video.currentTime=time;});}
$('#scan').onclick=async()=>{
 const video=state.nativeVideo||$('#video');if(!Number.isFinite(video.duration)||video.duration<=0){notice('Video duration unavailable.');return;}
 state.scanning=true;state.inspectingHistory=false;state.sourceName=state.mediaName;$('#filename').textContent=state.mediaName;state.stop=false;video.pause?.();video.controls=false;controls();
 try{const start=video.currentTime>=video.duration-.1?0:video.currentTime;const count=Math.min(120,Math.ceil(video.duration-start));for(let i=0;i<count&&!state.stop;i++){await seek(video,Math.min(start+i,video.duration-.05));$('#progress').textContent=`Frame ${i+1} / ${count}`;await analyze(true);}notice(state.stop?'Scan stopped. Completed readings are available for export.':'Scan complete. Each result is an independent frame reading.');}
 catch(e){notice(e.message);}finally{state.scanning=false;video.controls=true;render();}
};
$('#stop').onclick=()=>{state.stop=true;notice('Stopping after the current frame…');};
$('#review').onclick=()=>{state.reviewed=true;const last=state.activeRecord;if(last){last.reviewed=true;last.manual_transcription=state.corrected;last.selected_candidate=state.candidate;}render();notice('Marked reviewed by user. OCR confidence is unchanged.');};
$('#correct').onclick=()=>{const area=document.createElement('div'),p=document.createElement('p'),input=document.createElement('input'),save=document.createElement('button');p.textContent='Manual transcription is stored separately from the original OCR reading and does not change its score.';input.value=state.corrected||state.candidate.text;input.maxLength=16;input.setAttribute('aria-label','Manual plate transcription');save.className='ps-button ps-primary';save.textContent='Save transcription';save.onclick=()=>{const value=PlateCore.normalize(input.value);if(!value){input.focus();return;}state.corrected=value;state.reviewed=false;const last=state.activeRecord;if(last){last.manual_transcription=value;last.reviewed=false;}$('#dialog').close();render();};area.append(p,input,save);dialog('Edit transcription',area);input.focus();};
$('#export').onclick=()=>{if(!state.records.length){notice('Run recognition first to export measured results.');return;}const data=JSON.stringify({project:'PlateLab',author:'Akinwale Awoyele',exported_at:new Date().toISOString(),note:'Uncalibrated OCR scores. No training or accuracy claim. Synthetic videos are demonstrations only.',records:state.records},null,2);const area=document.createElement('div'),p=document.createElement('p'),text=document.createElement('textarea'),button=document.createElement('button');p.textContent=`${state.records.length} results. Copy the JSON below or save it as a report.`;text.value=data;text.rows=12;text.readOnly=true;button.textContent='Save JSON';button.className='ps-button ps-primary';button.onclick=()=>{if(window.webkit?.messageHandlers?.exportReport){window.webkit.messageHandlers.exportReport.postMessage(data);}else{const url=URL.createObjectURL(new Blob([data],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='platelab-results.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),2000);}};area.append(p,text,button);dialog('Recognition report',area);};
async function samples(){
 try{const entries=window.DATASET_SAMPLES||await fetch('samples.json').then(r=>r.json());const grid=document.createElement('div');grid.className='sample-grid';for(const entry of entries){const b=document.createElement('button'),im=document.createElement('img'),label=document.createElement('span');im.src=entry.path;im.alt=entry.name;label.textContent=entry.name;b.append(im,label);b.onclick=async()=>{try{$('#video').pause();const photo=new Image();photo.src=entry.path;await photo.decode();setSource(photo,entry.name);$('#dialog').close();notice('Dataset sample loaded. Click Recognize frame.');}catch(e){notice(e.message);}};grid.append(b);}dialog('Your dataset · Akinwale Awoyele',grid);}catch(e){dialog('Dataset samples','No local samples are packaged. Import an image from your computer.');}
}
$('#samples').onclick=samples;$('#empty-sample').onclick=samples;$('#studio').onclick=()=>$('#scene').scrollIntoView({behavior:'smooth'});
$('#help').onclick=()=>dialog('About this baseline','PlateLab uses Apple Vision to read visible text locally. Candidate format checks prioritize plate-like strings but do not verify registration or recover fully hidden characters. OCR confidence is an uncalibrated score for a text observation, not the probability the full plate is correct; character confidence is unavailable. Select a tighter plate region for small text. Video is sampled sequentially at one frame per second with no tracking or temporal fusion. This prototype has not been trained on the dataset or evaluated for highway accuracy.');
render();

function nativeRequest(channel,payload){return new Promise((resolve,reject)=>{const id=String(++state.serial);const timer=setTimeout(()=>{pending.delete(id);reject(new Error('Media request timed out.'));},30000);pending.set(id,{resolve,reject,timer});window.webkit.messageHandlers[channel].postMessage({id,...payload});});}
async function nativeFrame(time){const result=await nativeRequest('videoFrame',{time});const img=new Image();img.src=result.image;await img.decode();state.source=workingFrame(img);state.nativeVideo.currentTime=time;$('#native-time').value=time;$('#native-timestamp').textContent=time.toFixed(1)+'s';paint();}
window.nativeMedia=async result=>{if(result.error){notice(result.error);return;}try{if(result.type==='video'){state.nativeVideo={duration:result.duration,currentTime:0};await nativeFrame(0);setSource(state.source,result.name,true);$('#video').hidden=true;$('#native-timeline').hidden=false;$('#native-time').max=Math.max(0,result.duration-.05);}else{const img=new Image();img.src=result.image;await img.decode();setSource(img,result.name);}notice('Media ready. Recognize a frame or select a plate region.');}catch(e){notice(e.message);}};
$('#native-time').onchange=async event=>{if(state.busy||state.scanning)return;state.busy=true;controls();try{await nativeFrame(Number(event.target.value));state.inspectingHistory=false;state.sourceName=state.mediaName;$('#filename').textContent=state.mediaName;state.candidate=null;state.observations=[];state.activeRecord=null;state.corrected=null;state.reviewed=false;}catch(e){notice(e.message);}finally{state.busy=false;render();}};
