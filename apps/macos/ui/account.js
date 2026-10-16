(function(){
 'use strict';
 let account={configured:false,signedIn:false,busy:false},opened=false;
 const trigger=document.querySelector('#account');
 function send(action){const handler=window.webkit?.messageHandlers?.[action];if(handler)handler.postMessage({});}
 function render(){
  const body=document.createElement('div');body.className='account-panel';
  const intro=document.createElement('p');intro.textContent='Your workspace. Your choice. Sign in with your email and password. Google and guest mode are optional.';
  const identity=document.createElement('p');identity.className='account-identity';identity.textContent=account.signedIn?account.name+(account.email?'\n'+account.email:''):'Guest mode';
  const status=document.createElement('p');status.setAttribute('role','status');status.textContent=account.message||(!account.configured?'Sign-in is not configured in this build. Guest recognition is available.':account.signedIn?'Signed in for this session.':'Signing in is optional.');
  const privacy=document.createElement('p');privacy.className='ps-muted';privacy.textContent='Firebase Authentication securely hashes your PlateLab password. PlateLab does not save passwords in local history or the database. Display names are not unique login names. Cloud backup is off; signing in does not upload your plates or photos. Local readings are shared within this workspace.';
  const duration=document.createElement('p');duration.className='ps-muted';duration.textContent='For this local Mac build, sign-in lasts up to one hour and ends when you close the app.';
  function button(label,action){const b=document.createElement('button');b.className='ps-button';b.textContent=label;b.disabled=account.busy;b.onclick=()=>send(action);return b;}
  const actions=document.createElement('div');actions.className='toolbar';
  if(account.signedIn){
   const verified=document.createElement('p');verified.className='ps-muted';verified.textContent=account.emailVerified?'Email verified':'Email not verified';body.append(verified);
   actions.append(button('Sign out','accountSignOut'),button('Delete PlateLab account','accountDelete'));
   if(!account.hasPassword&&account.email)actions.append(button('Add email & password','accountAddPassword'));
   if(!account.emailVerified){const verify=button('Send verification email','accountVerify');verify.disabled=account.busy||account.canVerify===false;actions.append(verify,button("I've verified my email",'accountRefresh'));}
  }
  else {
   for(const [label,action] of [['Sign in with email','accountEmail'],['Create account','accountCreate'],['Forgot password?','accountReset'],['Sign in with Google','accountSignIn']]){
    const item=button(label,action);item.disabled=account.busy||!account.configured||(action==='accountSignIn'&&account.googleConfigured===false);if(action==='accountEmail')item.classList.add('ps-primary');actions.append(item);
   }
  }
  if(account.busy){const cancel=button('Cancel sign-in','accountSignOut');cancel.disabled=false;actions.append(cancel);}
  const done=document.createElement('button');done.className='ps-button';done.textContent=account.signedIn?'Return to workspace':'Continue as guest';done.onclick=()=>{if(account.busy)send('accountSignOut');document.querySelector('#dialog').close();opened=false;};
  body.append(intro,identity,status,actions,privacy,duration,done);
  if(document.querySelector('#dialog').open&&opened){document.querySelector('#dialog-body').replaceChildren(body);}
  else {opened=true;dialog('Your PlateLab account',body);}
 }
 window.nativeAccount=payload=>{account=payload;trigger.setAttribute('aria-label',account.signedIn?'Account · Signed in':'Account · Guest');trigger.title=account.signedIn?'Signed in':'Guest mode';if(opened&&document.querySelector('#dialog').open)render();else opened=false;};
 trigger.onclick=()=>{render();send('accountStatus');};
 send('accountStatus');
})();
