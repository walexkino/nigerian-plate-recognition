package com.akinwale.platelab;

import android.content.Context;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Invented accounts only, with explicit local emulator opt-in; never sends real emails. */
@RunWith(AndroidJUnit4.class)
public class EmailAccountTest {
    private FirebaseAuth auth;private Context context;
    @Before public void localOnly(){
        Assume.assumeTrue(Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("firebaseAuthEmulator","false")));
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();auth=GoogleAccount.auth(context);Assume.assumeNotNull(auth);auth.useEmulator("10.0.2.2",9099);auth.signOut();
    }
    @After public void cleanup(){if(auth!=null)auth.signOut();}
    private String address(){return "android-"+UUID.randomUUID()+"@example.test";}
    private String secret(){return "Synthetic-"+UUID.randomUUID();}
    private View label(AccountActivity a,String text){return TourTestSupport.label(a.getWindow().getDecorView(),text);}
    private <T> T field(AccountActivity a,String name,Class<T> type){try{java.lang.reflect.Field f=AccountActivity.class.getDeclaredField(name);f.setAccessible(true);return type.cast(f.get(a));}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private void value(AccountActivity a,String name,String text){field(a,name,EditText.class).setText(text);}
    private void await(ActivityScenario<AccountActivity> scenario){long end=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<end){boolean[] done={false};scenario.onActivity(a->done[0]=!field(a,"busy",Boolean.class));if(done[0])return;try{Thread.sleep(50);}catch(InterruptedException e){throw new AssertionError(e);}}fail("Account operation timed out");}
    private JSONObject codes() throws Exception {HttpURLConnection c=(HttpURLConnection)new URL("http://10.0.2.2:9099/emulator/v1/projects/demo-platelab/oobCodes").openConnection();c.setConnectTimeout(5000);c.setReadTimeout(5000);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new JSONObject(out.toString("UTF-8"));}finally{c.disconnect();}}
    private String code(String email,String type) throws Exception {JSONArray list=codes().getJSONArray("oobCodes");for(int i=list.length()-1;i>=0;i--){JSONObject o=list.getJSONObject(i);if(email.equals(o.getString("email"))&&type.equals(o.getString("requestType")))return o.getString("oobCode");}throw new AssertionError("Local action code missing");}
    @Test public void createAccountVerifiesEmailAndKeepsHistoryUnchanged() throws Exception {
        String email=address(),password=secret();ReadingStore store=new ReadingStore(context);String before=ReadingStore.report(store.load()).getJSONArray("records").toString();
        try(ActivityScenario<AccountActivity> scenario=ActivityScenario.launch(AccountActivity.class)){
            scenario.onActivity(a->{label(a,"Create an account").performClick();value(a,"displayName","Synthetic Tester");value(a,"email",email);value(a,"password",password);value(a,"confirm",password);label(a,"Create account").performClick();});await(scenario);
            assertNotNull(auth.getCurrentUser());assertEquals("Synthetic Tester",auth.getCurrentUser().getDisplayName());assertFalse(auth.getCurrentUser().isEmailVerified());
            scenario.onActivity(a->{assertEquals("",field(a,"password",EditText.class).getText().toString());assertNotNull(label(a,"I've verified my email"));assertFalse(label(a,"Send verification email").isEnabled());});
            Tasks.await(auth.applyActionCode(code(email,"VERIFY_EMAIL")),10,TimeUnit.SECONDS);
            scenario.onActivity(a->label(a,"I've verified my email").performClick());await(scenario);assertTrue(auth.getCurrentUser().isEmailVerified());
        }
        assertEquals(before,ReadingStore.report(store.load()).getJSONArray("records").toString());
    }
    @Test public void wrongPasswordCannotSignInAndCorrectPasswordCan() throws Exception {
        String email=address(),password=secret();Tasks.await(auth.createUserWithEmailAndPassword(email,password),10,TimeUnit.SECONDS);String uid=auth.getCurrentUser().getUid();auth.signOut();
        try(ActivityScenario<AccountActivity> scenario=ActivityScenario.launch(AccountActivity.class)){
            scenario.onActivity(a->{value(a,"email",email);value(a,"password","wrong fixture");label(a,"Sign in").performClick();});await(scenario);assertNull(auth.getCurrentUser());
            scenario.onActivity(a->{assertEquals("",field(a,"password",EditText.class).getText().toString());value(a,"password",password);label(a,"Sign in").performClick();});await(scenario);assertEquals(uid,auth.getCurrentUser().getUid());
            scenario.onActivity(a->label(a,"Sign out").performClick());await(scenario);assertNull(auth.getCurrentUser());
        }
    }
    @Test public void invalidRegistrationStaysLocalAndDoesNotCreateAnAccount() throws Exception {
        try(ActivityScenario<AccountActivity> scenario=ActivityScenario.launch(AccountActivity.class)){
            scenario.onActivity(a->{label(a,"Create an account").performClick();value(a,"displayName","Synthetic Tester");value(a,"email","invalid");value(a,"password",secret());value(a,"confirm",secret());label(a,"Create account").performClick();assertNotNull(field(a,"email",EditText.class).getError());
                value(a,"email",address());value(a,"password","short");value(a,"confirm","short");label(a,"Create account").performClick();assertNotNull(field(a,"password",EditText.class).getError());
                value(a,"password",secret());value(a,"confirm","different fixture");label(a,"Create account").performClick();assertNotNull(field(a,"confirm",EditText.class).getError());assertNull(auth.getCurrentUser());
            });
        }
    }
    @Test public void passwordFieldsAreSecureAndNotRestoredOnRecreation() throws Exception {
        try(ActivityScenario<AccountActivity> scenario=ActivityScenario.launch(AccountActivity.class)){
            scenario.onActivity(a->{assertTrue((a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0);EditText password=field(a,"password",EditText.class);assertFalse(password.isSaveEnabled());assertTrue(password.getFilterTouchesWhenObscured());password.setText(secret());});scenario.recreate();
            scenario.onActivity(a->assertEquals("",field(a,"password",EditText.class).getText().toString()));
        }
    }
    @Test public void passwordResetWorksAndUnknownEmailGetsTheSameNeutralMessage() throws Exception {
        String email=address(),password=secret();Tasks.await(auth.createUserWithEmailAndPassword(email,password),10,TimeUnit.SECONDS);auth.signOut();String[] messages=new String[2];
        try(ActivityScenario<AccountActivity> scenario=ActivityScenario.launch(AccountActivity.class)){
            scenario.onActivity(a->{value(a,"email",email);label(a,"Forgot password?").performClick();});await(scenario);scenario.onActivity(a->messages[0]=field(a,"message",TextView.class).getText().toString());
            String replacement=secret();Tasks.await(auth.confirmPasswordReset(code(email,"PASSWORD_RESET"),replacement),10,TimeUnit.SECONDS);Tasks.await(auth.signInWithEmailAndPassword(email,replacement),10,TimeUnit.SECONDS);assertNotNull(auth.getCurrentUser());auth.signOut();
            scenario.onActivity(a->{value(a,"email",address());label(a,"Forgot password?").performClick();});await(scenario);scenario.onActivity(a->messages[1]=field(a,"message",TextView.class).getText().toString());assertEquals(messages[0],messages[1]);assertTrue(messages[0].startsWith("If this address"));
        }
    }
    @Test public void addingPasswordPreservesTheExistingGoogleUid() throws Exception {
        String email=address(),subject=UUID.randomUUID().toString();String header="{\"alg\":\"none\",\"typ\":\"JWT\"}",payload=new JSONObject().put("sub",subject).put("email",email).put("email_verified",true).put("name","Synthetic Google Tester").toString();
        String token=android.util.Base64.encodeToString(header.getBytes(StandardCharsets.UTF_8),11)+"."+android.util.Base64.encodeToString(payload.getBytes(StandardCharsets.UTF_8),11)+".";
        Tasks.await(auth.signInWithCredential(GoogleAuthProvider.getCredential(token,null)),10,TimeUnit.SECONDS);String uid=auth.getCurrentUser().getUid(),password=secret();
        try(ActivityScenario<AccountActivity> scenario=ActivityScenario.launch(AccountActivity.class)){
            scenario.onActivity(a->{label(a,"Add email & password").performClick();assertFalse(field(a,"email",EditText.class).isEnabled());value(a,"password",password);value(a,"confirm",password);label(a,"Save password").performClick();});await(scenario);assertEquals(uid,auth.getCurrentUser().getUid());
        }
        auth.signOut();Tasks.await(auth.signInWithEmailAndPassword(email,password),10,TimeUnit.SECONDS);assertEquals(uid,auth.getCurrentUser().getUid());
    }
}
