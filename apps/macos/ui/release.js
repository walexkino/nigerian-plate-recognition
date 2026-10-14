(function(root){
 'use strict';
 const release = {"version":"0.3.1","build":5};
 const info=Object.freeze(release);
 if(typeof module==='object'&&module.exports)module.exports=info;
 else root.PlateRelease=info;
})(typeof window==='object'?window:globalThis);
