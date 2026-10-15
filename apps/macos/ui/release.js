(function(root){
 'use strict';
 const release = {"version":"0.5.0","build":7};
 const info=Object.freeze(release);
 if(typeof module==='object'&&module.exports)module.exports=info;
 else root.PlateRelease=info;
})(typeof window==='object'?window:globalThis);
