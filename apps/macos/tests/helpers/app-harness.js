const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const PlateCore=require('../../ui/core.js');
const PlateSupport=require('../../ui/support.js');
const PlateRelease=require('../../ui/release.js');

// A minimal DOM/media adapter. Tests run the real app handlers and native bridge.
function createApp(){
 const elements=new Map(),timers=new Set(),watchers=[],requests={recognize:[],videoFrame:[],importMedia:[],connectLive:[],liveFrame:[],disconnectLive:[],supportWallet:[],copySupportAddress:[]};
 let decodeNext=null,videoErrorNext=false;
 class Element{
  constructor(tag='div'){this.tagName=tag;this.children=[];this.style={};this.dataset={};this.attributes={};this.width=1280;this.height=720;this.disabled=false;this.hidden=false;this.currentTime=0;this.duration=3;this.videoWidth=1280;this.videoHeight=720;this.classList={add(){},remove(){}};}
  append(...nodes){this.children.push(...nodes);}
  replaceChildren(...nodes){this.children=[...nodes];}
  setAttribute(name,value){this.attributes[name]=String(value);}
  getAttribute(name){return this.attributes[name];}
  removeAttribute(name){delete this.attributes[name];}
  querySelectorAll(tag){return this.children.flatMap(c=>[...(c.tagName===tag?[c]:[]),...c.querySelectorAll(tag)]);}
  getContext(){return {drawImage(){},strokeRect(){},fillRect(){},setLineDash(){}};}
  getBoundingClientRect(){return {left:0,top:0,width:this.width,height:this.height};}
  toDataURL(){return 'data:image/jpeg;base64,ZmFrZQ==';}
  setPointerCapture(){}
  scrollIntoView(){}
  focus(){}
  click(){if(!this.disabled)return this.onclick?.();}
  pause(){this.paused=true;}
  load(){}
  showModal(){this.open=true;}
  close(){this.open=false;}
  replaceWith(node){elements.set('#'+node.id,node);}
  set src(value){this._src=value;if(this.tagName==='video'){const error=videoErrorNext;videoErrorNext=false;queueMicrotask(()=>error?this.onerror?.():this.onloadeddata?.());}}
  get src(){return this._src;}
 }
 const get=selector=>{if(!elements.has(selector))elements.set(selector,new Element(selector==='#video'?'video':'div'));return elements.get(selector);};
 class FakeImage extends Element{
  constructor(){super('img');this.naturalWidth=1280;this.naturalHeight=720;}
  decode(){const next=decodeNext;decodeNext=null;return next||Promise.resolve();}
 }
 const context=vm.createContext({document:{documentElement:{dataset:{},style:{}},querySelector:get,createElement:tag=>new Element(tag)},PlateCore,PlateSupport,PlateRelease,Image:FakeImage,
  URL:class extends URL{static createObjectURL(){return `blob:${Math.random()}`;}static revokeObjectURL(){}},Blob,console,
  setTimeout(fn,ms){const id=setTimeout(fn,ms);timers.add(id);return id;},clearTimeout(id){clearTimeout(id);timers.delete(id);}});
 context.window={webkit:{messageHandlers:{}}};
 for(const channel of Object.keys(requests))context.window.webkit.messageHandlers[channel]={postMessage:payload=>{
  requests[channel].push(payload);
  for(const waiter of watchers){if(waiter.channel===channel&&requests[channel][waiter.index])waiter.resolve(requests[channel][waiter.index]);}
  if(channel==='videoFrame'&&app.autoFrame)queueMicrotask(()=>context.window.nativeResult({id:payload.id,...app.autoFrame(payload)}));
  if(channel==='recognize'&&app.autoOCR)queueMicrotask(()=>context.window.nativeResult({id:payload.id,...app.autoOCR(payload)}));
  if(channel==='connectLive'&&app.autoConnect)queueMicrotask(()=>context.window.nativeResult({id:payload.id,...app.autoConnect(payload)}));
  if(channel==='liveFrame'&&app.autoLive)queueMicrotask(()=>context.window.nativeResult({id:payload.id,...app.autoLive(payload)}));
  if(channel==='disconnectLive')queueMicrotask(()=>context.window.nativeResult({id:payload.id}));
 }};
 const app={get,requests,autoFrame:null,autoOCR:null,autoConnect:null,autoLive:null,
  useBrowser(){context.window.webkit=undefined;},
  dispose(){get('#live-disconnect').onclick?.();for(const timer of timers)clearTimeout(timer);},
  nextRequest(channel,index=0){if(requests[channel][index])return Promise.resolve(requests[channel][index]);return new Promise(resolve=>watchers.push({channel,index,resolve}));},
  connectLive(url='rtsp://camera.example/stream',name='Street camera'){get('#live').click();const area=get('#dialog-body').children[0];const inputs=area.children.filter(x=>x.tagName==='input');inputs[0].value=url;inputs[1].value=name;return area.children.find(x=>x.tagName==='button').click();},
  deferDecode(){let resolve,reject;decodeNext=new Promise((yes,no)=>{resolve=yes;reject=no;});return {resolve,reject};},
  failNextVideo(){videoErrorNext=true;},
  loadImage(name='demo.png'){return context.window.nativeMedia({type:'image',name,image:'data:image/png;base64,ZmFrZQ=='});},
  loadVideo(duration=3,time=0){return context.window.nativeMedia({type:'video',name:'demo.mp4',duration,time,image:'data:image/png;base64,ZmFrZQ=='});},
  importBrowserVideo(){return get('#file').onchange({target:{files:[{name:'browser.mp4',type:'video/mp4',size:100}],value:'file'}});},
  deliver(payload){context.window.nativeResult(payload);},
  async analyze(observations=[],error=null){const task=get('#analyze').click();const request=requests.recognize.at(-1);if(!request)throw new Error('No OCR request sent');app.deliver({id:request.id,observations,elapsedMs:10,...(error?{error}:{})});await task;},
  report(){get('#export').click();const area=get('#dialog-body').children[0];return JSON.parse(area.children.find(x=>x.tagName==='textarea').value);},
 };
 vm.runInContext(fs.readFileSync(path.join(__dirname,'../../ui/theme.js'),'utf8'),context);
 vm.runInContext(fs.readFileSync(path.join(__dirname,'../../ui/app.js'),'utf8'),context);
 return app;
}
const validOCR=[{box:[.1,.2,.3,.1],pass:'original',alternatives:[{text:'ABC123DE',confidence:.9}]}];
module.exports={createApp,validOCR};
