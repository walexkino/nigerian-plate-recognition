(function (root) {
  'use strict';
  const codes = {0:'Clear sky',1:'Mainly clear',2:'Partly cloudy',3:'Overcast',45:'Fog',48:'Rime fog',51:'Light drizzle',53:'Drizzle',55:'Heavy drizzle',56:'Freezing drizzle',57:'Freezing drizzle',61:'Light rain',63:'Rain',65:'Heavy rain',66:'Freezing rain',67:'Freezing rain',71:'Light snow',73:'Snow',75:'Heavy snow',77:'Snow grains',80:'Light showers',81:'Showers',82:'Heavy showers',85:'Snow showers',86:'Heavy snow showers',95:'Thunderstorm',96:'Thunderstorm with hail',99:'Thunderstorm with hail'};
  const number = value => typeof value === 'number' && Number.isFinite(value);
  function condition(code) { return codes[code] || 'Conditions unavailable'; }
  function value(n, unit = '') { return number(n) ? Math.round(n) + unit : '—'; }
  function coordinates(latitude, longitude) {
    if (!number(latitude) || !number(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) throw new Error('Location unavailable. Search for a city instead.');
    return {latitude:Math.round(latitude*100)/100,longitude:Math.round(longitude*100)/100};
  }
  function forecastURL(latitude, longitude) {
    const p = coordinates(latitude,longitude);
    return 'https://api.open-meteo.com/v1/forecast?latitude='+p.latitude+'&longitude='+p.longitude+'&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=3&temperature_unit=celsius&wind_speed_unit=kmh';
  }
  function parse(data) {
    if (!data || data.error || !number(data.current?.temperature_2m) || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(data.current.time || '')) throw new Error('Weather data is unavailable. Try again later.');
    const d = data.daily || {};
    const dates=Array.isArray(d.time)?d.time:[];
    return {current:data.current,zone:data.timezone_abbreviation || data.timezone || '',days:dates.slice(0,3).map((date,i)=>({date,code:d.weather_code?.[i],high:d.temperature_2m_max?.[i],low:d.temperature_2m_min?.[i],rain:d.precipitation_probability_max?.[i]}))};
  }
  function places(data) {
    if(Array.isArray(data?.results))return data.results;
    return (Array.isArray(data?.features)?data.features:[]).map(f=>{const p=f.properties||{},c=f.geometry?.coordinates||[];const address=[p.housenumber,p.street].filter(Boolean).join(' ');const label=[p.name,address,p.district,p.city||p.town||p.village,p.state,p.country].filter(Boolean).filter((v,i,a)=>a.indexOf(v)===i).join(', ');return {label,name:label,latitude:c[1],longitude:c[0]};});
  }
  const api = {condition,value,coordinates,forecastURL,parse,places};
  if (typeof module === 'object' && module.exports) { module.exports=api; return; }
  root.PlateWeather=api;
  let generation=0, abort=null, panel=null, selection=null, saved=null, suggestionTimer=null;
  const searchCache=new Map();
  const el=(tag,text,cls)=>{const e=document.createElement(tag);if(text!==undefined)e.textContent=text;if(cls)e.className=cls;return e;};
  async function request(channel,body,url) {
    if(root.webkit?.messageHandlers?.[channel]) return nativeRequest(channel,body,35000,'Weather request timed out. Try a city search.');
    const controller=new AbortController();abort=controller;const timer=setTimeout(()=>controller.abort(),18000);
    try { const r=await fetch(url,{signal:controller.signal,credentials:'omit',cache:'no-store'});if(!r.ok)throw new Error(r.status===429?'Weather service is busy. Please try again later.':'Could not load weather. Check your connection and try again.');return await r.json(); }
    catch(e){if(e.name==='AbortError')throw new Error('Weather request canceled or timed out. Try again.');throw e;}
    finally {clearTimeout(timer);if(abort===controller)abort=null;}
  }
  function close() {
    generation++;clearTimeout(suggestionTimer);abort?.abort();panel=null;
    if(root.webkit?.messageHandlers?.weatherCancel)nativeRequest('weatherCancel',{}).catch(()=>{});
  }
  function busy(message,typing=false) {panel.status.textContent=message;for(const b of panel.actions)b.disabled=!(typing&&b===panel.input);panel.results.replaceChildren();}
  function ready() {if(panel)for(const b of panel.actions)b.disabled=false;}
  function showForecast(data,label) {
    const model=parse(data), c=model.current, box=panel.forecast;box.replaceChildren();
    box.append(el('div',label,'weather-place'),el('div',value(c.temperature_2m,'°C'),'weather-temperature'),el('div',condition(c.weather_code),'weather-condition'));
    const stats=el('div',undefined,'weather-stats');
    for(const [name,text]of [['Feels like',value(c.apparent_temperature,'°C')],['Wind',value(c.wind_speed_10m,' km/h')],['Humidity',value(c.relative_humidity_2m,'%')]]){const item=el('div');item.append(el('span',name),el('strong',text));stats.append(item);}box.append(stats);
    box.append(el('p','Current forecast · '+c.time.replace('T',' · ')+' '+model.zone,'weather-time'));
    const days=el('div',undefined,'weather-days');
    for(const day of model.days){const card=el('div',undefined,'weather-day');const date=new Date(day.date+'T12:00:00Z');const name=Number.isFinite(date.getTime())?date.toLocaleDateString(undefined,{weekday:'short',month:'short',day:'numeric',timeZone:'UTC'}):day.date;card.append(el('strong',name),el('span',condition(day.code)),el('b',value(day.high,'°')+' / '+value(day.low,'°')),el('small','Rain chance '+value(day.rain,'%')));days.append(card);}box.append(days);
  }
  async function load(p,label) {
    const token=++generation;selection={...coordinates(p.latitude,p.longitude),label};busy('Loading forecast…');
    try{const d=await request('weatherForecast',selection,forecastURL(selection.latitude,selection.longitude));if(token!==generation||!panel)return;showForecast(d,label);saved={data:d,label};panel.status.textContent='Forecast updated. Weather is a model estimate for this area.';}
    catch(e){if(token===generation&&panel)panel.status.textContent=e.message+(saved?' Previous forecast remains below.':'');}
    finally{if(token===generation)ready();}
  }
  async function locate() {
    const token=++generation;busy('Finding your approximate location…');
    try{
      const p=root.webkit?.messageHandlers?.weatherLocation?await request('weatherLocation',{}):await new Promise((resolve,reject)=>{
        if(!navigator.geolocation){reject(new Error('Location is unavailable here. Search for a city instead.'));return;}
        navigator.geolocation.getCurrentPosition(p=>resolve(p.coords),()=>reject(new Error('Location was not available or permission was denied. Search for a city, or check Location Services.')),{enableHighAccuracy:false,timeout:15000,maximumAge:60000});
      });
      if(token!==generation||!panel)return;await load(p,'Your approximate location');
    }catch(e){if(token===generation&&panel){panel.status.textContent=e.message;ready();}}
  }
  async function search() {
    const name=panel.input.value.trim();if(name.length<2){panel.status.textContent='Enter at least two characters of a city name.';return;}
    const token=++generation;busy('Finding streets and places…',true);
    try {const bias=selection?{latitude:selection.latitude,longitude:selection.longitude}:{};const key=name.toLowerCase()+JSON.stringify(bias);let d=searchCache.get(key);if(!d){d=await request('weatherSearch',{query:name,...bias},'https://photon.komoot.io/api/?q='+encodeURIComponent(name)+'&limit=5&lang=en'+(selection?'&lat='+selection.latitude+'&lon='+selection.longitude:''));if(searchCache.size>=20)searchCache.delete(searchCache.keys().next().value);searchCache.set(key,d);}if(token!==generation||!panel)return;
      const matches=places(d).filter(p=>number(p.latitude)&&number(p.longitude)&&Math.abs(p.latitude)<=90&&Math.abs(p.longitude)<=180);panel.status.textContent=matches.length?'Select your street or place below.':'No matching place. Add your city or try a nearby street.';
      for(const p of matches){const label=p.label||[p.name,p.admin1,p.country].filter(Boolean).join(', ');const b=el('button',label,'ps-button weather-city');b.onclick=()=>load(p,label);panel.results.append(b);}
    }catch(e){if(token===generation&&panel)panel.status.textContent='City search unavailable. Check your connection and try again.';}
    finally{if(token===generation)ready();}
  }
  api.open=()=>{
    close();const area=el('section',undefined,'weather-panel');area.setAttribute('aria-label','Local weather forecast');
    area.append(el('p','A little context for your day.','weather-kicker'),el('p','Use your approximate location or choose a street or city for current conditions and a three-day forecast.','ps-muted'));
    const location=el('button','◎ Use my location','ps-button ps-primary');location.onclick=locate;
    const form=el('form',undefined,'weather-search'),input=el('input');input.placeholder='Street, address or city…';input.maxLength=100;input.setAttribute('aria-label','Street, address or city');input.setAttribute('autocomplete','off');input.setAttribute('aria-controls','weather-suggestions');
    const find=el('button','Search','ps-button');find.type='submit';form.append(input,find);form.onsubmit=e=>{e.preventDefault();clearTimeout(suggestionTimer);search();};input.oninput=()=>{clearTimeout(suggestionTimer);generation++;abort?.abort();if(root.webkit?.messageHandlers?.weatherCancel)nativeRequest('weatherCancel',{}).catch(()=>{});ready();panel.results.replaceChildren();panel.status.textContent=input.value.trim().length<3?'Type at least three characters. Add your city for better street suggestions.':'Finding suggestions when you pause typing…';if(input.value.trim().length>=3)suggestionTimer=setTimeout(search,650);};
    const status=el('p','Choose a location to begin.','weather-status');status.setAttribute('role','status');status.setAttribute('aria-live','polite');const results=el('div',undefined,'weather-results');results.id='weather-suggestions';const forecast=el('div',undefined,'weather-forecast');
    const refresh=el('button','↻ Refresh forecast','ps-button');refresh.onclick=()=>selection?load(selection,selection.label):locate();
    area.append(location,form,status,results,forecast,refresh,el('p','Weather needs internet. Address suggestions use Photon; weather uses Open-Meteo. Your search and approximate area are shared for these requests. It is not saved in plate reports.','weather-privacy'));
    const credits=el('p',undefined,'weather-credit');credits.append(el('span','Weather: Open-Meteo · Addresses: Photon / OpenStreetMap · '));const license=el('a','CC BY 4.0');license.href='https://open-meteo.com/en/licence';license.target='_blank';license.rel='noopener';license.onclick=e=>{if(root.webkit?.messageHandlers?.weatherSource){e.preventDefault();nativeRequest('weatherSource',{}).catch(()=>{});}};credits.append(license);const osm=el('a',' · © OpenStreetMap contributors');osm.href='https://www.openstreetmap.org/copyright';osm.target='_blank';osm.rel='noopener';osm.onclick=e=>{if(root.webkit?.messageHandlers?.weatherSource){e.preventDefault();nativeRequest('weatherSource',{osm:true}).catch(()=>{});}};credits.append(osm);area.append(credits);
    panel={status,input,results,forecast,actions:[location,find,refresh,input]};dialog('Local weather',area);
    if(saved){showForecast(saved.data,saved.label);status.textContent='Forecast for your last selected location. Refresh to update.';}
    document.querySelector('#dialog').addEventListener('close',close,{once:true});
  };
  document.querySelector('#weather').onclick=api.open;
})(typeof window==='object'?window:globalThis);
