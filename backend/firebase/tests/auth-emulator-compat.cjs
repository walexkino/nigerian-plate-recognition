// Opt-in local Auth Emulator compatibility patch, never used by either app or deployed.
// firebase-tools 15.34.0 rejects a same-email Google->password link before checking idToken.
// Preserve the documented signUp + idToken contract while still denying a different UID.
const fs=require('node:fs');
const file=require.resolve('firebase-tools/lib/emulator/auth/operations');
const version=require('firebase-tools/package.json').version;
const marker='// PlateLab local-only authenticated same-email linking compatibility';
const backup=file+'.platelab-original';
if(process.argv.includes('--restore')){
 if(fs.existsSync(backup)){fs.copyFileSync(backup,file);fs.unlinkSync(backup);console.log('Restored the original local Auth emulator.');}
 process.exit(0);
}
let source=fs.readFileSync(file,'utf8');
if(source.includes(marker)){console.log('Local Auth emulator compatibility is already applied.');process.exit(0);}
if(version!=='15.34.0')throw Error('Recheck the upstream emulator before applying this version-specific patch.');
const needle='(0, errors_1.assert)(!state.getUserByEmail(email), "EMAIL_EXISTS");\n        updates.email = email;';
if(source.split(needle).length!==2)throw Error('Upstream implementation changed; preserve it and review.');
const replacement=`${marker}
        const existingForEmail = state.getUserByEmail(email);
        const callerForLink = reqBody.idToken ? parseIdToken(state, reqBody.idToken).user : undefined;
        (0, errors_1.assert)(!existingForEmail || existingForEmail.localId === callerForLink?.localId, "EMAIL_EXISTS");
        if (existingForEmail && callerForLink?.localId === existingForEmail.localId) {
            updates.emailVerified = existingForEmail.emailVerified;
            if (reqBody.displayName === undefined) updates.displayName = existingForEmail.displayName;
        }
        updates.email = email;`;
fs.copyFileSync(file,backup,fs.constants.COPYFILE_EXCL);source=source.replace(needle,replacement);fs.writeFileSync(file,source);
console.log('Applied local Auth emulator same-UID linking compatibility. Production apps and Firebase are unchanged.');
