(function(global){
  const address='3DhdtBbkE7TsiZzAU5JJuV3M7dYzygj8R2';
  const support=Object.freeze({address,uri:'bitcoin:'+address});
  global.PlateSupport=support;
  if(typeof module!=='undefined')module.exports=support;
})(typeof window!=='undefined'?window:globalThis);
