const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');
const weather=require('../ui/weather.js');
const fixture={current:{temperature_2m:28.4,apparent_temperature:31,weather_code:63,relative_humidity_2m:80,wind_speed_10m:9,time:'2026-10-15T14:00'},timezone_abbreviation:'WAT',daily:{time:['2026-10-15','2026-10-16','2026-10-17'],weather_code:[63,2,0],temperature_2m_max:[30,31,32],temperature_2m_min:[24,25,25],precipitation_probability_max:[70,null,0]}};
test('weather requests use HTTPS, three days and rounded coordinates, including zero and negatives',()=>{
 const u=new URL(weather.forecastURL(-0.12345,0));assert.equal(u.protocol,'https:');assert.equal(u.hostname,'api.open-meteo.com');assert.equal(u.searchParams.get('latitude'),'-0.12');assert.equal(u.searchParams.get('longitude'),'0');assert.equal(u.searchParams.get('forecast_days'),'3');
 for(const args of [[NaN,0],[91,0],[0,181],[null,0],['6',3]])assert.throws(()=>weather.forecastURL(...args));
});
test('missing weather is unavailable, rather than invented zero degrees or zero rain',()=>{
 assert.equal(weather.value(null,'%'),'—');assert.equal(weather.value(0,'%'),'0%');assert.equal(weather.condition(95),'Thunderstorm');assert.equal(weather.condition(-1),'Conditions unavailable');
 assert.throws(()=>weather.parse({...fixture,current:{...fixture.current,temperature_2m:null}}));assert.throws(()=>weather.parse({error:true}));assert.equal(weather.parse(fixture).days[1].rain,null);assert.deepEqual(weather.parse({...fixture,daily:{time:null}}).days,[]);
});
function ui(){
 const elements=new Map(),calls=[],pending=[];
 class Element{constructor(tag){this.tag=tag;this.children=[];this.listeners={};this.value='';}append(...children){this.children.push(...children);}replaceChildren(...children){this.children=children;}setAttribute(){}addEventListener(name,fn){this.listeners[name]=fn;}}
 const get=s=>{if(!elements.has(s))elements.set(s,new Element('div'));return elements.get(s);};
 const context={URL,AbortController,encodeURIComponent,setTimeout,clearTimeout,navigator:{},document:{querySelector:get,createElement:tag=>new Element(tag)},nativeRequest(channel,body){calls.push({channel,body});if(channel==='weatherCancel')return Promise.resolve({});return new Promise((resolve,reject)=>pending.push({channel,resolve,reject}));},dialog(title,area){get('#dialog-body').replaceChildren(area);}};
 context.window={webkit:{messageHandlers:Object.fromEntries(['weatherCancel','weatherLocation','weatherSearch','weatherForecast'].map(n=>[n,{}]))}};
 vm.runInNewContext(fs.readFileSync(require.resolve('../ui/weather.js'),'utf8'),context);
 return {get,calls,pending,open(){get('#weather').onclick();return get('#dialog-body').children[0];},close(){get('#dialog').listeners.close();}};
}
const tick=()=>new Promise(resolve=>setImmediate(resolve));
test('Photon street results preserve address and country, including zero coordinates',()=>{
 const p=weather.places({features:[{properties:{name:'Broad Street',street:'Broad Street',city:'Lagos',country:'Nigeria'},geometry:{coordinates:[0,0]}}]})[0];assert.equal(p.latitude,0);assert.equal(p.longitude,0);assert.match(p.label,/Broad Street, Lagos, Nigeria/);
});
test('typing suggestions wait for a pause and ignore a response for older text',async()=>{
 const app=ui(),panel=app.open(),input=panel.children.find(e=>e.tag==='form').children[0],results=panel.children.find(e=>e.className==='weather-results');
 input.value='Bro';input.oninput();input.value='Broad';input.oninput();assert.equal(app.pending.length,0);await new Promise(r=>setTimeout(r,700));assert.equal(app.pending.length,1);assert.equal(input.disabled,false);
 input.value='Broad Street Lagos';input.oninput();app.pending[0].resolve({results:[{name:'Wrong old match',latitude:1,longitude:1}]});await tick();assert.equal(results.children.length,0);app.close();await new Promise(r=>setTimeout(r,700));assert.equal(app.pending.length,1);
});
test('opening weather requests no location or forecast; closing ignores a late location',async()=>{
 const app=ui(),panel=app.open();assert.deepEqual(app.calls.map(c=>c.channel),['weatherCancel']);panel.children.find(e=>e.textContent==='◎ Use my location').onclick();assert.equal(app.pending[0].channel,'weatherLocation');app.close();app.pending[0].resolve({latitude:6.51234,longitude:3.39234});await tick();assert.equal(app.calls.some(c=>c.channel==='weatherForecast'),false);
});
test('denied location restores controls and city search does not silently select an ambiguous result',async()=>{
 const app=ui(),panel=app.open(),locate=panel.children.find(e=>e.textContent==='◎ Use my location');locate.onclick();app.pending[0].reject(new Error('Permission denied. Search for a city.'));await tick();assert.equal(locate.disabled,false);assert.match(panel.children.find(e=>e.className==='weather-status').textContent,/Permission denied/);
 const form=panel.children.find(e=>e.tag==='form');form.children[0].value='Lagos';form.onsubmit({preventDefault(){}});app.pending[1].resolve({results:[{name:'Lagos',latitude:6.52,longitude:3.38,country:'Nigeria'},{name:'Lagos',latitude:37.1,longitude:-8.67,country:'Portugal'}]});await tick();const choices=panel.children.find(e=>e.className==='weather-results').children;assert.equal(choices.length,2);assert.equal(app.calls.some(c=>c.channel==='weatherForecast'),false);choices[1].onclick();assert.equal(app.calls.at(-1).body.longitude,-8.67);app.pending[2].resolve(fixture);await tick();assert.match(panel.children.find(e=>e.className==='weather-status').textContent,/updated/);
});
