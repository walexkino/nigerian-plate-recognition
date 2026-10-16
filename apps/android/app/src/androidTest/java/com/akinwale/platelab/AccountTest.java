package com.akinwale.platelab;

import android.content.*;
import android.graphics.*;
import android.util.Base64;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Uses an explicitly enabled local Auth emulator, never a real Google account. */
@RunWith(AndroidJUnit4.class)
public class AccountTest {
    private Context context;
    private FirebaseAuth auth;
    @Before public void localIdentityOnly(){
        Assume.assumeTrue("Pass -e firebaseAuthEmulator true with the local emulator running",Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("firebaseAuthEmulator","false")));
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();auth=GoogleAccount.auth(context);Assume.assumeNotNull(auth);
        auth.useEmulator("10.0.2.2",9099);auth.signOut();
    }
    @After public void cleanup(){if(auth!=null)auth.signOut();}
    private String jwt(String subject){String header="{\"alg\":\"none\",\"typ\":\"JWT\"}";String payload="{\"sub\":\""+subject+"\",\"email\":\"synthetic@example.test\",\"email_verified\":true,\"name\":\"Synthetic Tester\"}";return encode(header)+"."+encode(payload)+".";}
    private String encode(String s){return Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8),Base64.URL_SAFE|Base64.NO_PADDING|Base64.NO_WRAP);}
    private View label(View view,String text){return TourTestSupport.label(view,text);}
    private void screenshot(AccountActivity a,String name){View v=a.getWindow().getDecorView();Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));try(FileOutputStream out=new FileOutputStream(new File(context.getCacheDir(),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}catch(IOException e){throw new RuntimeException(e);}finally{b.recycle();}}
    private com.google.android.gms.common.SignInButton googleButton(View v){if(v instanceof com.google.android.gms.common.SignInButton)return (com.google.android.gms.common.SignInButton)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){com.google.android.gms.common.SignInButton found=googleButton(g.getChildAt(i));if(found!=null)return found;}}return null;}
    @Test public void missingGoogleServicesOffersGuestModeAfterSignInAttempt() throws Exception {
        boolean missing=false;try{context.getPackageManager().getPackageInfo("com.google.android.gms",0);}catch(android.content.pm.PackageManager.NameNotFoundException e){missing=true;}Assume.assumeTrue("AOSP emulator only",missing);
        try(ActivityScenario<AccountActivity> s=ActivityScenario.launch(AccountActivity.class)){
            Thread.sleep(250);s.onActivity(a->googleButton(a.getWindow().getDecorView()).performClick());
            Thread.sleep(1500);s.onActivity(a->{assertTrue(label(a.getWindow().getDecorView(),"Continue as guest").isEnabled());assertNull(auth.getCurrentUser());screenshot(a,"account-provider-unavailable.png");});
        }
    }
    @Test public void guestScreenUsesBothThemesAndDoesNotChangeHistory() throws Exception {
        ReadingStore store=new ReadingStore(context);String before=ReadingStore.report(store.load()).getJSONArray("records").toString();
        android.content.SharedPreferences p=context.getSharedPreferences("appearance",Context.MODE_PRIVATE);boolean had=p.contains("dark"),old=p.getBoolean("dark",true);
        try{for(boolean dark:new boolean[]{true,false}){p.edit().putBoolean("dark",dark).commit();try(ActivityScenario<AccountActivity> s=ActivityScenario.launch(AccountActivity.class)){
            Thread.sleep(350);s.onActivity(a->{assertNotNull(label(a.getWindow().getDecorView(),"Guest mode"));assertNotNull(label(a.getWindow().getDecorView(),"Continue as guest"));screenshot(a,"account-guest-"+(dark?"dark":"light")+".png");});
        }}}finally{if(had)p.edit().putBoolean("dark",old).commit();else p.edit().remove("dark").commit();}
        assertEquals(before,ReadingStore.report(store.load()).getJSONArray("records").toString());
    }
    @Test public void firebaseVerifiesSyntheticGoogleIdentityAndSignOutPreservesLocalHistory() throws Exception {
        ReadingStore store=new ReadingStore(context);String before=ReadingStore.report(store.load()).getJSONArray("records").toString();
        Tasks.await(auth.signInWithCredential(GoogleAuthProvider.getCredential(jwt(UUID.randomUUID().toString()),null)),15,TimeUnit.SECONDS);
        assertNotNull(auth.getCurrentUser());boolean google=false;for(UserInfo p:auth.getCurrentUser().getProviderData())if("google.com".equals(p.getProviderId()))google=true;assertTrue(google);
        try(ActivityScenario<AccountActivity> s=ActivityScenario.launch(AccountActivity.class)){
            Thread.sleep(350);s.onActivity(a->{assertNotNull(label(a.getWindow().getDecorView(),"Return to workspace"));screenshot(a,"account-synthetic-signed-in.png");label(a.getWindow().getDecorView(),"Sign out").performClick();});
            Thread.sleep(1000);assertNull(auth.getCurrentUser());s.onActivity(a->assertNotNull(label(a.getWindow().getDecorView(),"Continue as guest")));
        }
        assertEquals(before,ReadingStore.report(store.load()).getJSONArray("records").toString());
    }
    private androidx.appcompat.app.AlertDialog deletionDialog(AccountActivity activity){try{java.lang.reflect.Field f=AccountActivity.class.getDeclaredField("deleteDialog");f.setAccessible(true);return (androidx.appcompat.app.AlertDialog)f.get(activity);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    @Test public void deleteHasConfirmationAndDeletesOnlyTheSyntheticAccount() throws Exception {
        Tasks.await(auth.signInWithCredential(GoogleAuthProvider.getCredential(jwt(UUID.randomUUID().toString()),null)),15,TimeUnit.SECONDS);
        try(ActivityScenario<AccountActivity> s=ActivityScenario.launch(AccountActivity.class)){
            Thread.sleep(350);s.onActivity(a->label(a.getWindow().getDecorView(),"Delete PlateLab account").performClick());
            Thread.sleep(250);s.onActivity(a->{androidx.appcompat.app.AlertDialog d=deletionDialog(a);assertTrue(d.isShowing());assertEquals("Delete account",d.getButton(-1).getText().toString());d.getButton(-2).performClick();});
            assertNotNull(auth.getCurrentUser());
            s.onActivity(a->{label(a.getWindow().getDecorView(),"Delete PlateLab account").performClick();deletionDialog(a).getButton(-1).performClick();});
            long deadline=android.os.SystemClock.elapsedRealtime()+10000;while(auth.getCurrentUser()!=null&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(100);assertNull(auth.getCurrentUser());
        }
    }
}
