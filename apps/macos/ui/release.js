(function(root){
 'use strict';
 const release = {"version":"0.7.0","build":9};
 const info=Object.freeze(release);
 if(typeof module==='object'&&module.exports)module.exports=info;
 else root.PlateRelease=info;
})(typeof window==='object'?window:globalThis);
