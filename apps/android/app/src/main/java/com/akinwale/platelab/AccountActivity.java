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
    private ConnectionStatus connection;
    private CredentialManager manager;
    private CancellationSignal pending;
    private boolean dark,busy,disposed;
    private TextView identity,message;
    private SignInButton signIn;
    private Button signOut,remove,back,emailSubmit,switchMode,reset,verify,refresh,addPassword;
    private EditText displayName,email,password,confirm;
    private LinearLayout emailForm;
    private boolean registering,linking;
    private long verificationSentAt;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private AlertDialog deleteDialog;
    private final FirebaseAuth.AuthStateListener listener=a->{if(!disposed){render();}};

    @Override public void onCreate(Bundle saved) {
        dark=getSharedPreferences("appearance",MODE_PRIVATE).getBoolean("dark",(getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES);
        getDelegate().setLocalNightMode(dark?AppCompatDelegate.MODE_NIGHT_YES:AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        auth=GoogleAccount.auth(this);manager=CredentialManager.create(this);build();render();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private int ink(){return dark?0xffedf2ef:0xff172720;}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(size);t.setPadding(0,dp(7),0,dp(7));return t;}
    private void add(LinearLayout p,View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);p.addView(v,lp);}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(ink());b.setMinHeight(dp(48));b.setTextSize(14);b.setPadding(dp(12),dp(10),dp(12),dp(10));GradientDrawable s=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{dark?0xff304e40:0xfffbfffc,dark?0xff21352c:0xffe6f2eb});s.setCornerRadius(dp(16));s.setStroke(dp(1),dark?0xff587567:0xffc7ded0);b.setBackground(s);b.setFilterTouchesWhenObscured(true);b.setOnClickListener(v->action.run());return b;}
    private void build(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{dark?0xff263936:0xfffafffc,dark?0xff101719:0xffedf5f2});scroll.setBackground(bg);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(24),dp(24),dp(24));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER_HORIZONTAL);scroll.addView(body,lp);
        scroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int w=Math.min(r-l-scroll.getPaddingLeft()-scroll.getPaddingRight(),dp(600));if(w>0&&body.getLayoutParams().width!=w){body.getLayoutParams().width=w;body.requestLayout();}});
        setContentView(scroll);ViewCompat.setOnApplyWindowInsetsListener(scroll,(v,insets)->{androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        ImageView logo=new ImageView(this);logo.setImageResource(R.mipmap.ic_launcher_image);logo.setContentDescription("PlateLab logo");body.addView(logo,new LinearLayout.LayoutParams(dp(64),dp(64)));
        TextView heading=text("Your PlateLab account",28,ink());heading.setTypeface(null,Typeface.BOLD);ViewCompat.setAccessibilityHeading(heading,true);add(body,heading);
        TextView connectionLabel=text("Checking connection…",12,ink());connectionLabel.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(body,connectionLabel);connection=new ConnectionStatus(this,connectionLabel);
        add(body,text("Your workspace. Your choice.",17,dark?0xffb5e7c2:0xff206346));
        add(body,text("Sign in with your email and password, use Google, or continue as a guest. Recognition works without an account.",15,ink()));
        identity=text("Guest mode",18,ink());identity.setTextIsSelectable(true);add(body,identity);
        emailForm=new LinearLayout(this);emailForm.setOrientation(LinearLayout.VERTICAL);
        displayName=field("Display name",false);displayName.setSingleLine(true);add(emailForm,displayName);
        email=field("Email address",false);email.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);email.setSingleLine(true);add(emailForm,email);
        password=field("Password",true);add(emailForm,password);confirm=field("Confirm password",true);add(emailForm,confirm);
        emailSubmit=button("Sign in",this::emailAction);add(emailForm,emailSubmit);add(body,emailForm);
        switchMode=button("Create an account",()->{registering=!registering;clearPasswords();render();});add(body,switchMode);
        reset=button("Forgot password?",this::resetPassword);add(body,reset);
        add(body,text("Or continue with Google",13,ink()));
        signIn=new SignInButton(this);signIn.setSize(SignInButton.SIZE_WIDE);signIn.setColorScheme(dark?SignInButton.COLOR_DARK:SignInButton.COLOR_LIGHT);signIn.setContentDescription("Sign in with Google");signIn.setFilterTouchesWhenObscured(true);signIn.setOnClickListener(v->begin());add(body,signIn);
        signOut=button("Sign out",this::signOut);add(body,signOut);
        verify=button("Send verification email",this::sendVerification);add(body,verify);
        refresh=button("I've verified my email",this::refreshAccount);add(body,refresh);
        addPassword=button("Add email & password",()->{linking=true;registering=false;clearPasswords();render();});add(body,addPassword);
        message=text("",14,dark?0xffb5e7c2:0xff206346);message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(body,message);
        add(body,text("Firebase Authentication handles account passwords. Use a separate PlateLab password, never your Google password. Passwords are not saved in plate history or our database. Display names are not unique login names.",13,ink()));
        add(body,text("Cloud backup is not enabled in this release. Plate readings and photos are not uploaded when you sign in. History stays on this device and is shared by anyone using this app on the device.",13,ink()));
        remove=button("Delete PlateLab account",this::confirmDelete);add(body,remove);
        back=button("Continue as guest",this::finish);add(body,back);
        add(body,text("© 2026 Akinwale",12,dark?0xffa2b4ab:0xff596b61));
        WindowInsetsControllerCompat bars=WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView());bars.setAppearanceLightStatusBars(!dark);bars.setAppearanceLightNavigationBars(!dark);
        getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){@Override public void handleOnBackPressed(){if(busy){message.setText("Please wait for the current account request to finish.");return;}finish();}});
    }
    private EditText field(String label,boolean secret){EditText input=new EditText(this);input.setHint(label);input.setContentDescription(label);input.setTextColor(ink());input.setHintTextColor(dark?0xffa2b4ab:0xff596b61);input.setTextSize(16);input.setPadding(dp(12),dp(10),dp(12),dp(10));input.setMinHeight(dp(48));input.setSingleLine(true);input.setInputType(secret?android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD:android.text.InputType.TYPE_CLASS_TEXT);input.setSaveEnabled(!secret);if(secret)input.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING|android.view.inputmethod.EditorInfo.IME_ACTION_DONE);input.setFilterTouchesWhenObscured(true);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(secret?4096:254)});return input;}
    private void clearPasswords(){password.setText("");confirm.setText("");}
    private boolean configured(){return auth!=null;}
    private boolean hasPassword(FirebaseUser user){if(user==null)return false;for(UserInfo info:user.getProviderData())if("password".equals(info.getProviderId()))return true;return false;}
    private void render(){
        FirebaseUser user=auth==null?null:auth.getCurrentUser();boolean signed=user!=null;
        if(linking&&!signed)linking=false;
        boolean form=!signed||linking;
        identity.setText(signed?((user.getDisplayName()==null||user.getDisplayName().isEmpty()?"Signed in":user.getDisplayName())+(user.getEmail()==null?"":"\n"+user.getEmail())+(user.isEmailVerified()?"\nEmail verified":"\nEmail not verified")):"Guest mode");
        emailForm.setVisibility(form?View.VISIBLE:View.GONE);displayName.setVisibility(registering?View.VISIBLE:View.GONE);confirm.setVisibility(registering||linking?View.VISIBLE:View.GONE);
        if(linking)email.setText(user.getEmail());email.setEnabled(!busy&&!linking);password.setEnabled(!busy);confirm.setEnabled(!busy);displayName.setEnabled(!busy);
        emailSubmit.setText(linking?"Save password":registering?"Create account":"Sign in");emailSubmit.setEnabled(!busy&&configured());
        switchMode.setVisibility(!signed?View.VISIBLE:View.GONE);switchMode.setText(registering?"Already have an account? Sign in":"Create an account");switchMode.setEnabled(!busy);
        reset.setVisibility(!signed&&!registering?View.VISIBLE:View.GONE);reset.setEnabled(!busy&&configured());
        signIn.setVisibility(signed?View.GONE:View.VISIBLE);signIn.setEnabled(!busy&&configured()&&!GoogleAccount.webClientId(this).isEmpty());
        signOut.setVisibility(signed?View.VISIBLE:View.GONE);signOut.setEnabled(!busy);remove.setVisibility(signed?View.VISIBLE:View.GONE);remove.setEnabled(!busy);
        boolean unverified=signed&&!user.isEmailVerified();verify.setVisibility(unverified?View.VISIBLE:View.GONE);refresh.setVisibility(unverified?View.VISIBLE:View.GONE);verify.setEnabled(!busy&&SystemClock.elapsedRealtime()>=verificationSentAt);refresh.setEnabled(!busy);
        addPassword.setVisibility(signed&&!hasPassword(user)&&!linking&&user.getEmail()!=null?View.VISIBLE:View.GONE);addPassword.setEnabled(!busy);
        back.setText(signed?linking?"Cancel adding password":"Return to workspace":"Continue as guest");back.setEnabled(!busy);back.setOnClickListener(v->{if(linking){linking=false;clearPasswords();render();}else finish();});
        if(!busy)message.setText(!configured()?"Sign-in is not configured in this build. Continue as a guest.":linking?"Add a separate PlateLab password of 12–128 characters. Your Google sign-in will still work.":signed?user.isEmailVerified()?"Signed in. Cloud backup is off; readings stay local.":"Account created. Verify your email to confirm you own this address. Recognition remains available.":registering?"Choose a display name and a password of 12–128 characters.":"Use your email and password, Google, or guest mode.");
    }
    private String address(){return email.getText().toString().trim();}
    private boolean validEmail(String value){return value.length()<=254&&android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches();}
    private void emailAction(){
        if(busy||!configured())return;String address=address(),secret=password.getText().toString(),confirmation=confirm.getText().toString(),name=displayName.getText().toString().trim();
        if(!validEmail(address)){email.setError("Enter a valid email address.");email.requestFocus();return;}
        if(secret.isEmpty()){password.setError("Enter your password.");password.requestFocus();return;}
        if(registering||linking){if(secret.length()<12||secret.length()>128){password.setError("Use 12–128 characters.");password.requestFocus();return;}if(!secret.equals(confirmation)){confirm.setError("Passwords do not match.");confirm.requestFocus();return;}}
        if(registering&&(name.isEmpty()||name.length()>100)){displayName.setError("Use a display name of 1–100 characters.");displayName.requestFocus();return;}
        final boolean create=registering,link=linking;clearPasswords();busy=true;render();message.setText(create?"Creating your account…":link?"Adding your password…":"Signing in…");
        if(link){FirebaseUser user=auth.getCurrentUser();if(user==null){failed("Sign in again before adding a password.");return;}
            user.linkWithCredential(EmailAuthProvider.getCredential(user.getEmail(),secret)).addOnCompleteListener(ContextCompat.getMainExecutor(this),task->{if(disposed)return;busy=false;if(task.isSuccessful()){linking=false;render();message.setText("Password added. You can now sign in with email or Google.");}else{render();message.setText(authError(task.getException()));}});return;}
        com.google.android.gms.tasks.Task<AuthResult> operation=create?auth.createUserWithEmailAndPassword(address,secret):auth.signInWithEmailAndPassword(address,secret);
        operation.addOnCompleteListener(ContextCompat.getMainExecutor(this),task->{if(disposed)return;if(!task.isSuccessful()){failed(authError(task.getException()));return;}
            if(!create){busy=false;render();return;}
            FirebaseUser user=auth.getCurrentUser();if(user==null){failed("Account could not be opened. Try signing in.");return;}
            user.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(name).build()).addOnCompleteListener(ContextCompat.getMainExecutor(this),profile->{if(disposed)return;busy=false;registering=false;render();sendVerification();});
        });
    }
    private String authError(Exception error){
        if(error instanceof FirebaseAuthRecentLoginRequiredException)return "For your security, sign out and sign in again, then retry.";
        if(error instanceof com.google.firebase.FirebaseTooManyRequestsException)return "Too many attempts. Please wait before trying again.";
        if(error instanceof FirebaseAuthException&&"ERROR_OPERATION_NOT_ALLOWED".equals(((FirebaseAuthException)error).getErrorCode()))return "Email/password sign-in is not enabled yet. Google and guest mode remain available.";
        if(error instanceof FirebaseAuthWeakPasswordException)return "Choose a stronger password with at least 12 characters.";
        return "Could not complete the request. Check your details and connection, then try signing in or resetting your password.";
    }
    private void resetPassword(){
        if(busy||!configured())return;String address=address();if(!validEmail(address)){email.setError("Enter your email address first.");email.requestFocus();return;}
        clearPasswords();busy=true;render();message.setText("Requesting a password reset…");
        auth.sendPasswordResetEmail(address).addOnCompleteListener(ContextCompat.getMainExecutor(this),task->{if(disposed)return;busy=false;render();message.setText(task.getException() instanceof com.google.firebase.FirebaseNetworkException?"Could not send the request. Check your connection and retry.":task.getException() instanceof com.google.firebase.FirebaseTooManyRequestsException?"Too many attempts. Please wait before trying again.":"If this address has an eligible account, a password-reset email will arrive shortly. Check your spam folder too.");});
    }
    private void sendVerification(){
        FirebaseUser user=auth==null?null:auth.getCurrentUser();if(busy||user==null||user.isEmailVerified()||SystemClock.elapsedRealtime()<verificationSentAt)return;
        busy=true;render();message.setText("Sending verification email…");user.sendEmailVerification().addOnCompleteListener(ContextCompat.getMainExecutor(this),task->{if(disposed)return;busy=false;if(task.isSuccessful()){verificationSentAt=SystemClock.elapsedRealtime()+60000;ui.postDelayed(()->{if(!disposed)render();},60000);}render();message.setText(task.isSuccessful()?"Verification email sent. Open its link, then choose I've verified my email.":"Account exists, but verification email could not be sent. Wait briefly and try again.");});
    }
    private void refreshAccount(){FirebaseUser user=auth==null?null:auth.getCurrentUser();if(busy||user==null)return;busy=true;render();user.reload().addOnCompleteListener(ContextCompat.getMainExecutor(this),task->{if(disposed)return;busy=false;render();if(!task.isSuccessful())message.setText("Could not check verification. Check your connection and retry.");});}
    private void begin(){
        if(busy||!configured()||GoogleAccount.webClientId(this).isEmpty())return;clearPasswords();busy=true;render();message.setText("Choose a Google account to continue…");pending=new CancellationSignal();
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
    private void clearCredentials(Runnable done){
        // A stalled credential provider must not leave the local sign-out screen locked.
        Handler handler=new Handler(Looper.getMainLooper());boolean[] finished={false};
        Runnable once=()->{if(finished[0])return;finished[0]=true;handler.removeCallbacksAndMessages(null);if(!disposed)done.run();};
        CancellationSignal cancel=new CancellationSignal();handler.postDelayed(()->{cancel.cancel();once.run();},5000);
        try{manager.clearCredentialStateAsync(new ClearCredentialStateRequest(),cancel,ContextCompat.getMainExecutor(this),new CredentialManagerCallback<Void,ClearCredentialException>(){
            @Override public void onResult(Void result){once.run();}
            @Override public void onError(ClearCredentialException error){once.run();}
        });}catch(RuntimeException unavailable){once.run();}
    }
    private void signOut(){if(busy||auth==null)return;clearPasswords();linking=false;busy=true;auth.signOut();render();clearCredentials(()->{busy=false;render();message.setText("Signed out. Local readings remain on this device.");});}
    private void confirmDelete(){if(busy||auth==null||auth.getCurrentUser()==null)return;deleteDialog=new AlertDialog.Builder(this).setTitle("Delete your PlateLab account?").setMessage("This permanently deletes your PlateLab sign-in account. It does not delete your Google account. Local plate history remains on this device; uninstalling or clearing app storage removes it. Cloud backup is not enabled.").setNegativeButton("Cancel",null).setPositiveButton("Delete account",(d,w)->delete()).show();}
    private void delete(){FirebaseUser user=auth.getCurrentUser();if(user==null)return;busy=true;render();message.setText("Deleting your PlateLab account…");user.delete().addOnCompleteListener(ContextCompat.getMainExecutor(this),task->{if(disposed)return;busy=false;render();if(task.isSuccessful()){clearCredentials(()->{render();message.setText("PlateLab account deleted. Local readings remain on this device.");});}else message.setText(task.getException() instanceof FirebaseAuthRecentLoginRequiredException?"For your security, sign out and sign in again, then retry deleting the account.":"Account could not be deleted. Check your connection and try again.");});}
    @Override protected void onStart(){super.onStart();if(connection!=null)connection.start();if(auth!=null)auth.addAuthStateListener(listener);}
    @Override protected void onStop(){if(password!=null)clearPasswords();if(connection!=null)connection.close();if(auth!=null)auth.removeAuthStateListener(listener);super.onStop();}
    @Override protected void onDestroy(){disposed=true;ui.removeCallbacksAndMessages(null);if(deleteDialog!=null)deleteDialog.dismiss();if(pending!=null)pending.cancel();super.onDestroy();}
}
