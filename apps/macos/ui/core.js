/* Votes describe correlated views of the same pixels, never calibrated probability. */
(function(global){
  const lookalikes={'А':'A','В':'B','Е':'E','К':'K','М':'M','Н':'H','О':'O','Р':'P','С':'C','Т':'T','У':'Y','Х':'X','Α':'A','Β':'B','Ε':'E','Ζ':'Z','Η':'H','Ι':'I','Κ':'K','Μ':'M','Ν':'N','Ο':'O','Ρ':'P','Τ':'T','Υ':'Y','Χ':'X'};
  function normalize(text){return [...String(text).toUpperCase().normalize('NFKD').replace(/[\u0300-\u036f]/g,'')].map(c=>lookalikes[c]||c).join('').replace(/[^A-Z0-9]/g,'');}
  function plateLike(text){const t=normalize(text);return /^[A-Z]{2,3}[0-9]{2,4}[A-Z]{1,3}$/.test(t)&&t.length>=7&&t.length<=9;}
  function plateSignal(text){const t=normalize(text);return t.length>=5&&t.length<=12&&/[A-Z]/.test(t)&&(t.match(/[0-9]/g)||[]).length>=2;}
  function commonLayout(text){return /^(?:[A-Z]{3}[0-9]{3}[A-Z]{2}|[A-Z]{2}[0-9]{3}[A-Z]{3})$/.test(text);}
  function overlap(a,b){const w=Math.max(0,Math.min(a[0]+a[2],b[0]+b[2])-Math.max(a[0],b[0]));const h=Math.max(0,Math.min(a[1]+a[3],b[1]+b[3])-Math.max(a[1],b[1]));return w*h/Math.max(1e-9,Math.min(a[2]*a[3],b[2]*b[3]));}
  function union(a,b){const x=Math.min(a[0],b[0]),y=Math.min(a[1],b[1]);return [x,y,Math.max(a[0]+a[2],b[0]+b[2])-x,Math.max(a[1]+a[3],b[1]+b[3])-y];}
  function joinFragments(observations){
    const extra=[];
    for(const a of observations){for(const b of observations){
      if(a===b||(a.pass||'original')!==(b.pass||'original'))continue;
      const [x,y,w,h]=a.box,[bx,by,bw,bh]=b.box;
      const gap=bx-(x+w),vertical=Math.min(y+h,by+bh)-Math.max(y,by);
      if(bx<=x||gap<-.005||gap>Math.max(h,bh)*1.6||vertical<Math.min(h,bh)*.5)continue;
      const first=a.alternatives?.[0],second=b.alternatives?.[0];if(!first||!second)continue;
      const raw=first.text+' '+second.text;
      if(plateLike(raw))extra.push({box:union(a.box,b.box),pass:a.pass||'original',joined:true,alternatives:[{text:raw,confidence:Math.min(first.confidence,second.confidence)}]});
    }}return extra;
  }
  function candidates(observations){
    const source=(observations||[]).filter(o=>Array.isArray(o.box)&&o.box.length===4&&o.box.every(Number.isFinite));
    const expanded=[...source,...joinFragments(source)],groups=[];
    for(const o of expanded){
      for(const [index,a] of (o.alternatives||[]).entries()){
        if(!plateSignal(a.text))continue;
        let group=groups.find(g=>overlap(g.box,o.box)>.35);
        if(!group){group={box:o.box,items:[]};groups.push(group);}
        group.items.push({...a,text:normalize(a.text),raw:a.text,box:o.box,pass:o.pass||'original',top:index===0,joined:!!o.joined});
      }
    }
    const result=[];
    for(const group of groups){
      const options=new Map();
      for(const item of group.items.filter(x=>plateLike(x.text))){let option=options.get(item.text);if(!option){option={...item,passes:new Set(),topPasses:new Set(),readings:[]};options.set(item.text,option);}option.passes.add(item.pass);if(item.top)option.topPasses.add(item.pass);option.confidence=Math.max(option.confidence,item.confidence);option.readings.push({raw:item.raw,pass:item.pass,confidence:item.confidence,top:item.top,joined:item.joined});}
      // Each pass contributes at most one character vote, preventing duplicates/alternatives from inflating agreement.
      const votes=new Map();
      for(const item of group.items.filter(x=>x.top)){const old=votes.get(item.pass);if(!old||Number(commonLayout(item.text))>Number(commonLayout(old.text))||(commonLayout(item.text)===commonLayout(old.text)&&item.confidence>old.confidence))votes.set(item.pass,item);}
      const voteTexts=[...votes.values()].map(x=>x.text);
      for(const option of options.values()){
        const support=voteTexts.filter(t=>t===option.text).length;
        const sameLength=voteTexts.filter(t=>t.length===option.text.length);
        const chars=[...option.text].map((char,i)=>({char,support:sameLength.filter(t=>t[i]===char).length,total:sameLength.length,alternatives:[...new Set(sameLength.map(t=>t[i]))].filter(x=>x!==char)}));
        result.push({...option,passes:[...option.passes],topPasses:[...option.topPasses],support,totalViews:votes.size,conflicting:voteTexts.some(t=>t!==option.text),characters:chars,commonLayout:commonLayout(option.text),rank:support*100+Number(commonLayout(option.text))*10+option.confidence});
      }
    }
    return result.sort((a,b)=>b.rank-a.rank);
  }
  function sampleTimes(duration,currentTime=0){
    if(!Number.isFinite(duration)||duration<=0)return [];
    const end=Math.max(0,duration-.05);
    const start=Number.isFinite(currentTime)&&currentTime>=0&&currentTime<duration-.1?Math.min(currentTime,end):0;
    return Array.from({length:Math.min(120,Math.ceil(duration-start))},(_,i)=>Math.min(start+i,end));
  }
  const api={normalize,plateLike,plateSignal,commonLayout,overlap,union,joinFragments,candidates,sampleTimes};global.PlateCore=api;if(typeof module!=='undefined')module.exports=api;
})(typeof window!=='undefined'?window:globalThis);
