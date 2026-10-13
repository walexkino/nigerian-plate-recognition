/* Format matching prioritizes candidates; it is not evidence that a plate is valid. */
(function(global){
  function normalize(text){return String(text).toUpperCase().replace(/[^A-Z0-9]/g,'');}
  function plateLike(text){return /^[A-Z]{2,3}[0-9]{2,4}[A-Z]{1,3}$/.test(normalize(text)) && normalize(text).length>=7 && normalize(text).length<=9;}
  function candidates(observations){
    const result=[];
    for(const observation of observations||[]){
      for(const alternative of observation.alternatives||[]){
        if(plateLike(alternative.text)) result.push({...alternative,text:normalize(alternative.text),raw:alternative.text,box:observation.box});
      }
    }
    result.sort((a,b)=>b.confidence-a.confidence);
    const seen=new Set(); return result.filter(x=>{const key=x.text+JSON.stringify(x.box);if(seen.has(key))return false;seen.add(key);return true;});
  }
  const api={normalize,plateLike,candidates};global.PlateCore=api;
  if(typeof module!=='undefined')module.exports=api;
})(typeof window!=='undefined'?window:globalThis);
