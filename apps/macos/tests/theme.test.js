const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync(require('node:path').join(__dirname,'../ui/theme.js'),'utf8');
function start(saved,systemLight=false,brokenStorage=false){
 const attrs={},button={setAttribute(k,v){attrs[k]=v;}};
 const root={dataset:{},style:{}};
 const storage={getItem(){if(brokenStorage)throw Error('unavailable');return saved;},setItem(k,v){if(brokenStorage)throw Error('unavailable');saved=v;}};
 vm.runInNewContext(source,{document:{documentElement:root,querySelector(){return button;}},window:{localStorage:storage,matchMedia(){return {matches:systemLight};}}});
 return {root,button,attrs,saved:()=>saved};
}
test('appearance follows system on first launch, then persists explicit selection',()=>{
 const app=start(null,true);assert.equal(app.root.dataset.theme,'light');app.button.onclick();assert.equal(app.root.dataset.theme,'dark');assert.equal(app.saved(),'dark');assert.equal(app.attrs['aria-pressed'],'true');assert.equal(start(app.saved(),true).root.dataset.theme,'dark');app.button.onclick();assert.equal(app.root.dataset.theme,'light');assert.equal(app.attrs['aria-label'],'Switch to dark mode');
});
test('invalid preferences and unavailable storage do not prevent switching',()=>{
 for(const app of [start('invalid'),start(null,false,true)]){assert.equal(app.root.dataset.theme,'dark');assert.doesNotThrow(()=>app.button.onclick());assert.equal(app.root.dataset.theme,'light');}
});

test('switching themes keeps recognition, review, and exports unchanged',async()=>{
 const {createApp,validOCR}=require('./helpers/app-harness');const app=createApp();
 try{await app.loadImage();await app.analyze(validOCR);app.get('#review').click();const before=app.report();app.get('#theme-toggle').click();assert.equal(app.get('#reading').textContent,'ABC123DE');assert.deepEqual(app.report().records,before.records);app.get('#theme-toggle').click();assert.deepEqual(app.report().records,before.records);}finally{app.dispose();}
});
