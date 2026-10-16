(function(){
 'use strict';
 const label=document.querySelector('#connection-status');
 const native=window.webkit?.messageHandlers?.connectionStatus;
 function render(status){
  const value=['online','offline'].includes(status)?status:'checking';
  label.dataset.status=value;label.textContent=value==='online'?'Online':value==='offline'?'Offline':'Checking…';
  label.title=value==='online'?'Internet access available. Individual services may still be unavailable.':value==='offline'?'No verified internet access. On-device recognition still works.':'Checking internet access…';
 }
 window.nativeConnection=payload=>render(payload.status);
 if(native){render('checking');native.postMessage({});window.addEventListener('focus',()=>native.postMessage({}));}
 else { // Browser previews have only the browser's network hint; do not claim verified internet.
  const update=()=>{render(navigator.onLine===false?'offline':'checking');if(navigator.onLine!==false){label.textContent='Connected';label.title='Browser reports a network connection. Internet access is not verified in this preview.';}};
  window.addEventListener('online',update);window.addEventListener('offline',update);update();
 }
})();
