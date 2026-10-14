const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const {address,uri}=require('../ui/support.js');
const {createApp}=require('./helpers/app-harness.js');
function setup(t){const app=createApp();t.after(()=>app.dispose());app.get('#support').click();return app;}

test('donation destination has a valid mainnet checksum and agrees with the native wallet handler',()=>{
 assert.equal(address,'3DhdtBbkE7TsiZzAU5JJuV3M7dYzygj8R2');
 const alphabet='123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
 let n=0n;for(const char of address)n=n*58n+BigInt(alphabet.indexOf(char));
 const bytes=Buffer.from(n.toString(16).padStart(50,'0'),'hex');
 const hash=data=>crypto.createHash('sha256').update(data).digest();
 assert.equal(bytes[0],5);assert.deepEqual(hash(hash(bytes.subarray(0,-4))).subarray(0,4),bytes.subarray(-4));
 assert.ok(fs.readFileSync(path.join(__dirname,'../native/main.swift'),'utf8').includes(`let address = "${address}"`));
});

test('support panel displays a scannable asset, exact address, and real wallet URI',t=>{
 const app=setup(t),panel=app.get('#dialog-body').children[0],qr=panel.children[0],body=panel.children[1];
 assert.equal(app.get('#dialog-title').textContent,'Support PlateLab');
 assert.equal(qr.children[0].src,'support-qr.svg');assert.equal(body.children.find(x=>x.tagName==='code').textContent,address);
 const actions=body.children.find(x=>x.className==='support-actions');assert.equal(actions.children[0].href,uri);
 assert.equal(uri,'bitcoin:'+address);assert.ok(fs.existsSync(path.join(__dirname,'../ui/support-qr.svg')));
});

test('copy address reports success only after the native clipboard result',async t=>{
 const app=setup(t),body=app.get('#dialog-body').children[0].children[1];
 const actions=body.children.find(x=>x.className==='support-actions');const task=actions.children[1].click();
 assert.equal(actions.children[1].textContent,'Copy address');
 app.deliver({id:app.requests.copySupportAddress[0].id});await task;
 assert.equal(actions.children[1].textContent,'✓ Copied');assert.equal(body.children.at(-1).textContent,'Bitcoin address copied.');
});

test('a missing wallet keeps the QR/address available and never claims a payment completed',async t=>{
 const app=setup(t),body=app.get('#dialog-body').children[0].children[1];
 const actions=body.children.find(x=>x.className==='support-actions');let prevented=false;
 const task=actions.children[0].onclick({preventDefault(){prevented=true;}});
 app.deliver({id:app.requests.supportWallet[0].id,error:'No Bitcoin wallet is available. Scan the QR code or copy the address.'});await task;
 assert.equal(prevented,true);assert.match(body.children.at(-1).textContent,/No Bitcoin wallet/);
 assert.equal(actions.children[0].href,uri);
});

test('browser wallet action keeps the page open instead of navigating Safari to an unsupported URI',async t=>{
 const app=setup(t);app.useBrowser();
 const body=app.get('#dialog-body').children[0].children[1];
 const actions=body.children.find(x=>x.className==='support-actions');let prevented=false;
 await actions.children[0].onclick({preventDefault(){prevented=true;}});
 assert.equal(prevented,true);assert.equal(app.requests.supportWallet.length,0);
 assert.equal(app.get('#dialog').open,true);
 assert.match(body.children.at(-1).textContent,/Scan the QR code/);
});
