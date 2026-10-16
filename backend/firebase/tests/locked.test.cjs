const {test}=require('node:test');
const {readFileSync}=require('node:fs');
const {initializeTestEnvironment,assertFails}=require('@firebase/rules-unit-testing');
const {doc,getDoc,setDoc,deleteDoc,collection,getDocs}=require('firebase/firestore');
test('the deployable rules deny all reads, writes, deletes and account lists, including signed-in users',async()=>{
 const env=await initializeTestEnvironment({projectId:'demo-platelab-locked',firestore:{host:'127.0.0.1',port:8080,rules:readFileSync(__dirname+'/../firestore.rules','utf8')}});
 try{await env.withSecurityRulesDisabled(async context=>setDoc(doc(context.firestore(),'users/alice'),{displayName:'Synthetic fixture'}));
  for(const db of [env.unauthenticatedContext().firestore(),env.authenticatedContext('alice',{firebase:{sign_in_provider:'google.com'}}).firestore(),env.authenticatedContext('bob').firestore(),env.authenticatedContext('alice',{email_verified:false,firebase:{sign_in_provider:'password'}}).firestore()]){
   for(const path of ['users/alice','users/alice/readings/fixture','public/fixture']){await assertFails(getDoc(doc(db,path)));await assertFails(setDoc(doc(db,path),{role:'admin'}));await assertFails(deleteDoc(doc(db,path)));}
   await assertFails(getDocs(collection(db,'users')));
  }
 }finally{await env.cleanup();}
});
