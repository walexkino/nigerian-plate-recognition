package com.akinwale.platelab;

import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.*;
import androidx.core.content.ContextCompat;
import androidx.core.view.*;
import androidx.credentials.*;
import androidx.credentials.exceptions.*;
import androidx.credentials.exceptions.ClearCredentialException;
import com.google.android.gms.common.SignInButton;
import com.google.android.libraries.identity.googleid.*;
import com.google.firebase.auth.*;

/** Optional foreground sign-in. No Firestore writes or plate-history uploads happen here. */
public final class AccountActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private CredentialManager manager;
    private CancellationSignal pending;
    private boolean dark,busy,disposed;
    private TextView identity,message;
    private SignInButton signIn;
    private Button signOut,remove,back;
    private AlertDialog deleteDialog;
    private final FirebaseAuth.AuthStateListener listener=a->{if(!disposed){render();}};

    @Override public void onCreate(Bundle saved) {
        dark=getSharedPreferences("appearance",MODE_PRIVATE).getBoolean("dark",(getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES);
        getDelegate().setLocalNightMode(dark?AppCompatDelegate.MODE_NIGHT_YES:AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(saved);
        auth=GoogleAccount.auth(this);manager=CredentialManager.create(this);build();render();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private int ink(){return dark?0xffedf2ef:0xff172720;}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(size);t.setPadding(0,dp(7),0,dp(7));return t;}
    private void add(LinearLayout p,View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);p.addView(v,lp);}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(ink());b.setMinHeight(dp(48));b.setTextSize(14);b.setPadding(dp(12),dp(10),dp(12),dp(10));GradientDrawable s=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{dark?0xff304e40:0xfffbfffc,dark?0xff21352c:0xffe6f2eb});s.setCornerRadius(dp(16));s.setStroke(dp(1),dark?0xff587567:0xffc7ded0);b.setBackground(s);b.setOnClickListener(v->action.run());return b;}
    private void build(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{dark?0xff263936:0xfffafffc,dark?0xff101719:0xffedf5f2});scroll.setBackground(bg);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(24),dp(24),dp(24));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER_HORIZONTAL);scroll.addView(body,lp);
        scroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int w=Math.min(r-l-scroll.getPaddingLeft()-scroll.getPaddingRight(),dp(600));if(w>0&&body.getLayoutParams().width!=w){body.getLayoutParams().width=w;body.requestLayout();}});
        setContentView(scroll);ViewCompat.setOnApplyWindowInsetsListener(scroll,(v,insets)->{androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        ImageView logo=new ImageView(this);logo.setImageResource(R.mipmap.ic_launcher_image);logo.setContentDescription("PlateLab logo");body.addView(logo,new LinearLayout.LayoutParams(dp(64),dp(64)));
        TextView heading=text("Your PlateLab account",28,ink());heading.setTypeface(null,Typeface.BOLD);ViewCompat.setAccessibilityHeading(heading,true);add(body,heading);
        add(body,text("Your workspace. Your choice.",17,dark?0xffb5e7c2:0xff206346));
        add(body,text("Sign in with Google, or keep using PlateLab as a guest. Photo and camera recognition work without an account.",15,ink()));
        identity=text("Guest mode",18,ink());identity.setTextIsSelectable(true);add(body,identity);
        signIn=new SignInButton(this);signIn.setSize(SignInButton.SIZE_WIDE);signIn.setColorScheme(dark?SignInButton.COLOR_DARK:SignInButton.COLOR_LIGHT);signIn.setContentDescription("Sign in with Google");signIn.setOnClickListener(v->begin());add(body,signIn);
        signOut=button("Sign out",this::signOut);add(body,signOut);
        message=text("",14,dark?0xffb5e7c2:0xff206346);message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(body,message);
        add(body,text("Google and Firebase handle sign-in and your basic account details. PlateLab never asks for or stores your Google password.",13,ink()));
        add(body,text("Cloud backup is not enabled in this release. Plate readings and photos are not uploaded when you sign in. History stays on this device and is shared by anyone using this app on the device.",13,ink()));
        remove=button("Delete PlateLab account",this::confirmDelete);add(body,remove);
        back=button("Continue as guest",this::finish);add(body,back);
        add(body,text("© 2026 Akinwale",12,dark?0xffa2b4ab:0xff596b61));
        WindowInsetsControllerCompat bars=WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView());bars.setAppearanceLightStatusBars(!dark);bars.setAppearanceLightNavigationBars(!dark);
        getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){@Override public void handleOnBackPressed(){if(busy){message.setText("Finish or cancel the Google sign-in prompt first.");return;}finish();}});
    }
    private boolean configured(){return auth!=null&&!GoogleAccount.webClientId(this).isEmpty();}
    private void render(){
        FirebaseUser user=auth==null?null:auth.getCurrentUser();boolean signed=user!=null;
        identity.setText(signed?((user.getDisplayName()==null||user.getDisplayName().isEmpty()?"Signed in":user.getDisplayName())+(user.getEmail()==null?"":"\n"+user.getEmail())):"Guest mode");
        signIn.setVisibility(signed?View.GONE:View.VISIBLE);signIn.setEnabled(!busy&&configured());
        signOut.setVisibility(signed?View.VISIBLE:View.GONE);signOut.setEnabled(!busy);remove.setVisibility(signed?View.VISIBLE:View.GONE);remove.setEnabled(!busy);
        back.setText(signed?"Return to workspace":"Continue as guest");back.setEnabled(!busy);
        if(!busy)message.setText(!configured()?"Google sign-in is not configured in this build. You can continue as a guest.":signed?"Signed in. Cloud backup is off; your readings remain local.":"Signing in is optional. Choose Google or continue as a guest.");
    }
    private void begin(){
        if(busy||!configured())return;busy=true;render();message.setText("Choose a Google account to continue…");pending=new CancellationSignal();
        GetSignInWithGoogleOption option=new GetSignInWithGoogleOption.Builder(GoogleAccount.webClientId(this)).build();
        GetCredentialRequest request=new GetCredentialRequest.Builder().addCredentialOption(option).build();
        try{manager.getCredentialAsync(new MutableContextWrapper(this),request,pending,ContextCompat.getMainExecutor(this),new CredentialManagerCallback<GetCredentialResponse,GetCredentialException>(){
            @Override public void onResult(GetCredentialResponse result){
                if(disposed)return;Credential credential=result.getCredential();
                // Firebase verifies the Google token before any account is treated as authenticated.
                if(!(credential instanceof CustomCredential)||(!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())&&!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL.equals(credential.getType()))){failed("Google returned an unsupported sign-in response. Please try again.");return;}
                try{GoogleIdTokenCredential google=GoogleIdTokenCredential.createFrom(credential.getData());message.setText("Verifying your account with Firebase…");auth.signInWithCredential(GoogleAuthProvider.getCredential(google.getIdToken(),null)).addOnCompleteListener(ContextCompat.getMainExecutor(AccountActivity.this),task->{if(disposed)return;busy=false;render();if(!task.isSuccessful())message.setText("Could not finish signing in. Check your connection and try again. You can still use guest mode.");});}
                catch(Exception error){failed("Google sign-in could not be verified. Please try again.");}
            }
            @Override public void onError(GetCredentialException error){if(disposed)return;failed(error instanceof GetCredentialCancellationException?"Sign-in canceled. You can continue as a guest.":error instanceof NoCredentialException||error instanceof GetCredentialProviderConfigurationException?"Google sign-in is unavailable. Check Google Play services and add a Google account on this device, or continue as a guest.":"Could not open Google sign-in. Check your connection and try again, or continue as a guest.");}
        });}catch(Exception unavailable){failed("Google sign-in is unavailable on this device. You can continue as a guest.");}
    }
    private void failed(String text){busy=false;render();message.setText(text);}
    private void clearCredentials(Runnable done){manager.clearCredentialStateAsync(new ClearCredentialStateRequest(),new CancellationSignal(),ContextCompat.getMainExecutor(this),new CredentialManagerCallback<Void,ClearCredentialException>(){
        @Override public void onResult(Void result){if(!disposed)done.run();}
        @Override public void onError(ClearCredentialException error){if(!disposed)done.run();}
    });}
    private void signOut(){if(busy||auth==null)return;busy=true;auth.signOut();render();clearCredentials(()->{busy=false;render();message.setText("Signed out. Local readings remain on this device.");});}
    private void confirmDelete(){if(busy||auth==null||auth.getCurrentUser()==null)return;deleteDialog=new AlertDialog.Builder(this).setTitle("Delete your PlateLab account?").setMessage("This permanently deletes your PlateLab sign-in account. It does not delete your Google account. Local plate history remains on this device; uninstalling or clearing app storage removes it. Cloud backup is not enabled.").setNegativeButton("Cancel",null).setPositiveButton("Delete account",(d,w)->delete()).show();}
    private void delete(){FirebaseUser user=auth.getCurrentUser();if(user==null)return;busy=true;render();message.setText("Deleting your PlateLab account…");user.delete().addOnCompleteListener(ContextCompat.getMainExecutor(this),task->{if(disposed)return;busy=false;render();if(task.isSuccessful()){clearCredentials(()->{render();message.setText("PlateLab account deleted. Local readings remain on this device.");});}else message.setText(task.getException() instanceof FirebaseAuthRecentLoginRequiredException?"For your security, sign out and sign in again, then retry deleting the account.":"Account could not be deleted. Check your connection and try again.");});}
    @Override protected void onStart(){super.onStart();if(auth!=null)auth.addAuthStateListener(listener);}
    @Override protected void onStop(){if(auth!=null)auth.removeAuthStateListener(listener);super.onStop();}
    @Override protected void onDestroy(){disposed=true;if(deleteDialog!=null)deleteDialog.dismiss();if(pending!=null)pending.cancel();super.onDestroy();}
}
