const {before,after,beforeEach,test}=require('node:test');
const {readFileSync}=require('node:fs');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,updateDoc,deleteDoc,collection,getDocs,serverTimestamp,Timestamp}=require('firebase/firestore');
let env;
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-platelab',firestore:{host:'127.0.0.1',port:8080,rules:readFileSync(__dirname+'/../firestore.profiles-proposal.rules','utf8')}});});
after(async()=>{await env?.cleanup();});
beforeEach(async()=>{await env.clearFirestore();});
const user=(uid,provider='google.com')=>env.authenticatedContext(uid,{firebase:{sign_in_provider:provider}}).firestore();
const data=(name='Synthetic Tester')=>({displayName:name,createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
test('a Google user can create, read, update and delete only their own profile',async()=>{
  const db=user('alice'),p=doc(db,'users/alice');await assertSucceeds(setDoc(p,data()));await assertSucceeds(getDoc(p));
  await assertSucceeds(updateDoc(p,{displayName:'Updated fixture',updatedAt:serverTimestamp()}));await assertSucceeds(deleteDoc(p));
});
test('guests and another user cannot read or change a profile',async()=>{
  await assertSucceeds(setDoc(doc(user('alice'),'users/alice'),data()));
  for(const db of [env.unauthenticatedContext().firestore(),user('bob')]){
    const p=doc(db,'users/alice');await assertFails(getDoc(p));await assertFails(setDoc(p,data()));await assertFails(deleteDoc(p));
  }
});
test('listing accounts, unrelated paths and nested reading backups remain denied',async()=>{
  const db=user('alice');await assertFails(getDocs(collection(db,'users')));
  for(const path of ['users/alice/readings/fixture','public/fixture','admins/alice']){await assertFails(setDoc(doc(db,path),data()));await assertFails(getDoc(doc(db,path)));}
});
test('roles, extra fields, invalid names and invented timestamps cannot be saved',async()=>{
  const db=user('alice'),p=doc(db,'users/alice');
  for(const record of [{...data(),role:'admin'},{...data(),photo:'base64'},{...data(),displayName:'x'.repeat(101)},{...data(),displayName:42},{displayName:'Missing timestamps'},{...data(),createdAt:Timestamp.fromMillis(0)},{...data(),updatedAt:Timestamp.fromMillis(0)}])await assertFails(setDoc(p,record));
});
test('creation time is immutable and password/anonymous identities have no profile access',async()=>{
  await assertSucceeds(setDoc(doc(user('alice'),'users/alice'),data()));
  await assertFails(updateDoc(doc(user('alice'),'users/alice'),{createdAt:Timestamp.fromMillis(0),updatedAt:serverTimestamp()}));
  for(const provider of ['password','anonymous'])await assertFails(getDoc(doc(user('alice',provider),'users/alice')));
});
