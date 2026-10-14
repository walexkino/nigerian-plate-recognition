package com.akinwale.platelab;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.*;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {
    private static final int BG=0xff101719,PANEL=0xff1b2526,INK=0xffedf2ef,MUTED=0xffa2b4ab,MINT=0xffb5e7c2;
    private boolean dark=true;
    private ScrollView workspaceScroll;
    private FrameLayout workspaceHost;
    private OnboardingTour tour;
    private Button supportButton;
    private static final String TOUR_POSITION="tour_position";
    private Button themeButton;
    private final Map<android.graphics.drawable.GradientDrawable,Integer> themedSurfaces=new WeakHashMap<>();
    private int tone(int role){
        if(dark)return role;
        if(role==BG)return 0xfff5f7f7;if(role==PANEL)return 0xffffffff;
        if(role==INK)return 0xff172720;if(role==MUTED)return 0xff596b61;
        if(role==MINT)return 0xff206346;if(role==0xff253332)return 0xffe8efeb;
        if(role==0xff163323)return Color.WHITE;return role;
    }
    private Context dialogContext(){return new androidx.appcompat.view.ContextThemeWrapper(this,dark?R.style.Theme_PlateLab_Dark:R.style.Theme_PlateLab_Light);}
    private void recolor(View view){
        if(view instanceof TextView&&view.getTag() instanceof Integer)((TextView)view).setTextColor(tone((Integer)view.getTag()));
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)recolor(group.getChildAt(i));}
    }
    private void applyAppearance(){
        workspaceScroll.setBackgroundColor(tone(BG));recolor(workspaceScroll);
        for(Map.Entry<android.graphics.drawable.GradientDrawable,Integer> entry:themedSurfaces.entrySet())entry.getKey().setColor(tone(entry.getValue()));
        themeButton.setText(dark?"☀ Light mode":"☾ Dark mode");themeButton.setContentDescription(dark?"Switch to light mode":"Switch to dark mode");
        WindowInsetsControllerCompat bars=WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView());bars.setAppearanceLightStatusBars(!dark);bars.setAppearanceLightNavigationBars(!dark);
        getWindow().setStatusBarColor(tone(BG));getWindow().setNavigationBarColor(tone(BG));
    }
    private void toggleTheme(){dark=!dark;getSharedPreferences("appearance",MODE_PRIVATE).edit().putBoolean("dark",dark).apply();applyAppearance();}
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean frameBusy=new AtomicBoolean();
    private final List<JSONObject> records=new ArrayList<>();
    private final TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private ReadingStore store;
    private PreviewView preview;
    private ImageView photoView;
    private BoxOverlay overlay;
    private TextView title,reading,score,status,note,source,historyCount;
    private Button cameraButton,photoButton,recognizeButton,reviewButton,editButton,historyButton,exportButton;
    private LinearLayout alternatives;
    private ProcessCameraProvider cameraProvider;
    private Bitmap photo;
    private JSONObject active;
    private volatile boolean live=false;
    private boolean loading=false, photoBusy=false, ready=false, cameraStarting=false;
    private int generation=0;
    private long lastFrame=0;
    private String exportSnapshot;
    private final ActivityResultLauncher<String[]> pickPhoto=registerForActivityResult(new ActivityResultContracts.OpenDocument(),this::importPhoto);
    private final ActivityResultLauncher<String> askCamera=registerForActivityResult(new ActivityResultContracts.RequestPermission(),granted->{
        if(granted)startCamera();else{status.setText("Camera permission was not granted. You can still import photos.");controls();}
    });
    private final ActivityResultLauncher<String> saveReport=registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),uri->{
        if(uri==null||exportSnapshot==null)return;String snapshot=exportSnapshot;exportSnapshot=null;
        worker.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Destination unavailable");out.write(snapshot.getBytes(StandardCharsets.UTF_8));runOnUiThread(()->status.setText("Report saved."));}catch(Exception e){runOnUiThread(()->status.setText("Could not save report. Choose another destination."));}});
    });

    @Override public void onCreate(Bundle saved){
        getDelegate().setLocalNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);super.onCreate(saved);dark=getSharedPreferences("appearance",MODE_PRIVATE).getBoolean("dark",(android.content.res.Resources.getSystem().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES);store=new ReadingStore(this);makeUi();
        getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){@Override public void handleOnBackPressed(){if(tour!=null&&tour.isShowing())tour.back();else{setEnabled(false);getOnBackPressedDispatcher().onBackPressed();setEnabled(true);}}});
        workspaceHost.post(()->{if(saved!=null&&saved.containsKey(TOUR_POSITION))showTour(saved.getInt(TOUR_POSITION));else if(!getSharedPreferences(OnboardingTour.PREFERENCES,MODE_PRIVATE).getBoolean(OnboardingTour.COMPLETED,false))showTour(OnboardingTour.WELCOME);});
        worker.execute(()->{
            try{List<JSONObject> loaded=store.load();runOnUiThread(()->{records.addAll(loaded);ready=true;status.setText("Ready. Import a photo or start your camera.");controls();});}
            catch(Exception e){runOnUiThread(()->{ready=true;status.setText("Saved history could not be loaded. Export any new readings before closing the app.");controls();});}
        });
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextColor(tone(color));v.setTag(color);v.setTextSize(size);return v;}
    private android.graphics.drawable.GradientDrawable surface(int color,int radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(tone(color));themedSurfaces.put(d,color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void space(LinearLayout parent,int height){View s=new View(this);parent.addView(s,new LinearLayout.LayoutParams(1,dp(height)));}
    private Button button(String label,boolean primary,Runnable action){Button v=new Button(this);v.setText(label);v.setAllCaps(false);v.setTextSize(13);v.setTextColor(tone(primary?0xff163323:INK));v.setTag(primary?0xff163323:INK);v.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x3067bd90),surface(primary?MINT:0xff253332,12),null));v.setMinHeight(dp(48));v.setPadding(dp(12),dp(6),dp(12),dp(6));v.setOnClickListener(x->action.run());return v;}
    private void pair(LinearLayout parent,Button a,Button b){
        boolean stacked=getResources().getConfiguration().screenWidthDp<380||getResources().getConfiguration().fontScale>1.3f;
        LinearLayout line=stacked?column():row();
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(stacked?-1:0,-2,stacked?0:1);
        if(stacked)left.bottomMargin=dp(8);else left.setMarginEnd(dp(8));
        line.addView(a,left);line.addView(b,new LinearLayout.LayoutParams(stacked?-1:0,-2,stacked?0:1));parent.addView(line);
    }
    private void makeUi(){
        ScrollView scroll=new ScrollView(this);workspaceScroll=scroll;scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout root=column();root.setPadding(dp(20),dp(16),dp(20),dp(24));FrameLayout.LayoutParams rootParams=new FrameLayout.LayoutParams(-1,-2);rootParams.gravity=Gravity.CENTER_HORIZONTAL;scroll.addView(root,rootParams);
        scroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int available=scroll.getWidth()-scroll.getPaddingLeft()-scroll.getPaddingRight();int width=Math.min(available,dp(900));if(width>0&&root.getLayoutParams().width!=width){root.getLayoutParams().width=width;root.requestLayout();}});workspaceHost=new FrameLayout(this);workspaceHost.addView(scroll,new FrameLayout.LayoutParams(-1,-1));setContentView(workspaceHost);
        ViewCompat.setOnApplyWindowInsetsListener(scroll,(view,insets)->{androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        LinearLayout brand=row();ImageView logo=new ImageView(this);logo.setImageResource(R.mipmap.ic_launcher_image);logo.setContentDescription("PlateLab logo");brand.addView(logo,new LinearLayout.LayoutParams(dp(42),dp(42)));
        title=text("PlateLab",24,INK);title.setTypeface(null,Typeface.BOLD);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMarginStart(dp(10));brand.addView(title,tp);brand.addView(text("v"+BuildConfig.VERSION_NAME,11,MUTED));root.addView(brand);space(root,12);themeButton=button("Light mode",false,this::toggleTheme);root.addView(themeButton,new LinearLayout.LayoutParams(-1,-2));space(root,20);
        root.addView(text("NIGERIAN PLATE RECOGNITION",11,MINT));space(root,6);root.addView(text("Recognition workspace",24,INK));space(root,8);
        root.addView(text("Read the evidence. Review every character.",13,MUTED));space(root,20);
        LinearLayout scene=column();scene.setPadding(dp(12),dp(12),dp(12),dp(12));scene.setBackground(surface(PANEL,18));
        source=text("Choose a photo or start your camera",12,MUTED);scene.addView(source);space(scene,10);
        FrameLayout viewer=new FrameLayout(this);viewer.setBackground(surface(0xff0a1514,12));
        photoView=new ImageView(this);photoView.setScaleType(ImageView.ScaleType.FIT_CENTER);photoView.setContentDescription("Source photo");viewer.addView(photoView,new FrameLayout.LayoutParams(-1,-1));
        preview=new PreviewView(this);preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);preview.setVisibility(View.GONE);viewer.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        overlay=new BoxOverlay(this);overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);viewer.addView(overlay,new FrameLayout.LayoutParams(-1,-1));viewer.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int height=Math.max(dp(180),Math.min(dp(420),Math.round((r-l)*0.625f)));if(viewer.getLayoutParams().height!=height){viewer.getLayoutParams().height=height;viewer.requestLayout();}});scene.addView(viewer,new LinearLayout.LayoutParams(-1,dp(240)));root.addView(scene);space(root,12);
        photoButton=button("Import photo",false,()->{if(!locked()){stopCamera();pickPhoto.launch(new String[]{"image/*"});}});
        cameraButton=button("Start camera",true,()->{if(live||cameraStarting)stopCamera();else if(!locked()){if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera();else askCamera.launch(Manifest.permission.CAMERA);}});
        pair(root,photoButton,cameraButton);space(root,10);
        recognizeButton=button("Recognize photo",true,this::recognizePhoto);root.addView(recognizeButton,new LinearLayout.LayoutParams(-1,-2));space(root,12);
        status=text("Loading saved readings…",13,MINT);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);space(root,20);
        LinearLayout result=column();result.setBackground(surface(PANEL,18));result.setPadding(dp(18),dp(18),dp(18),dp(18));result.addView(text("CANDIDATE PLATE",11,MUTED));space(result,8);
        reading=text("— — —",32,INK);reading.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);reading.setTextIsSelectable(true);result.addView(reading);space(result,8);
        score=text("Raw OCR score · unavailable",13,MINT);result.addView(score);space(result,8);
        note=text("Scores are not calibrated plate accuracy. Covered characters may remain unreadable.",12,MUTED);result.addView(note);space(result,14);
        result.addView(text("Alternative readings",13,INK));alternatives=column();result.addView(alternatives);space(result,14);
        reviewButton=button("Mark reviewed",true,this::review);editButton=button("Edit reading",false,this::edit);pair(result,reviewButton,editButton);root.addView(result);space(root,18);
        historyCount=text("History · 0 readings",13,MUTED);root.addView(historyCount);space(root,8);historyButton=button("View history",false,this::showHistory);exportButton=button("Export JSON",false,this::export);pair(root,historyButton,exportButton);space(root,20);
        root.addView(text("On-device OCR · photos stay on your phone\nNewest 200 text records retained",12,MUTED));space(root,10);
        supportButton=button("Support us",false,this::support);pair(root,button("About",false,this::about),supportButton);space(root,16);TextView copyright=text("© 2026 Akinwale",12,MUTED);copyright.setGravity(Gravity.CENTER);root.addView(copyright);applyAppearance();controls();
    }
    private boolean locked(){return (tour!=null&&tour.isShowing())||!ready||loading||photoBusy||live||cameraStarting||frameBusy.get();}
    private void controls(){if(photoButton==null)return;photoButton.setEnabled(!locked());cameraButton.setEnabled(ready&&(live||cameraStarting||!locked()));cameraButton.setText(live||cameraStarting?"Pause camera":"Start camera");recognizeButton.setEnabled(photo!=null&&!locked());recognizeButton.setVisibility(live||cameraStarting?View.GONE:View.VISIBLE);reviewButton.setEnabled(active!=null&&!ReadingStore.selected(active).isEmpty()&&!locked());editButton.setEnabled(active!=null&&!locked());historyButton.setEnabled(!records.isEmpty()&&!locked());exportButton.setEnabled(!records.isEmpty()&&!loading&&!photoBusy);historyCount.setText("History · "+records.size()+(records.size()==1?" reading":" readings"));for(int i=0;i<alternatives.getChildCount();i++)alternatives.getChildAt(i).setEnabled(!locked());}
    private void importPhoto(Uri uri){
        if(uri==null)return;stopCamera();loading=true;controls();status.setText("Preparing photo…");final int token=++generation;
        worker.execute(()->{try{Bitmap decoded=PhotoDecoder.decode(getContentResolver(),uri);runOnUiThread(()->{if(isDestroyed()||token!=generation){decoded.recycle();return;}if(photo!=null)photo.recycle();photo=decoded;photoView.setImageBitmap(photo);preview.setVisibility(View.GONE);photoView.setVisibility(View.VISIBLE);source.setText("Imported photo · "+photo.getWidth()+" × "+photo.getHeight());active=null;overlay.clear();loading=false;render();status.setText("Photo ready. Tap Recognize photo.");});}catch(Exception e){runOnUiThread(()->{if(token!=generation)return;loading=false;controls();status.setText(e.getMessage()==null?"Could not open this photo.":e.getMessage());});}});
    }
    private List<PlateRules.Reading> observations(Text text,int width,int height){
        List<PlateRules.Reading> observations=new ArrayList<>();for(Text.TextBlock block:text.getTextBlocks())for(Text.Line line:block.getLines()){Rect box=line.getBoundingBox();if(box==null)continue;Float confidence=line.getConfidence();if(confidence!=null&&(Float.isNaN(confidence)||Float.isInfinite(confidence)||confidence<0||confidence>1))confidence=null;observations.add(new PlateRules.Reading(line.getText(),confidence,new float[]{box.left/(float)width,box.top/(float)height,box.width()/(float)width,box.height()/(float)height}));}return observations;
    }
    private void recognizePhoto(){
        if(photo==null||locked())return;photoBusy=true;controls();active=null;overlay.clear();render();status.setText("Reading photo on your phone…");long started=SystemClock.elapsedRealtime(),capturedAt=System.currentTimeMillis();int token=generation;
        recognizer.process(InputImage.fromBitmap(photo,0)).addOnSuccessListener(text->{if(token==generation&&!isDestroyed())accept(text,photo.getWidth(),photo.getHeight(),"Imported photo","photo",SystemClock.elapsedRealtime()-started,capturedAt);}).addOnFailureListener(e->{if(!isDestroyed())status.setText("Recognition failed. Try a clearer JPEG or PNG.");}).addOnCompleteListener(task->{photoBusy=false;if(!isDestroyed())controls();});
    }
    private void startCamera(){
        if(cameraStarting||live||locked())return;cameraStarting=true;final int token=++generation;active=null;render();controls();status.setText("Connecting to the phone camera…");
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{if(token!=generation||isDestroyed())return;try{cameraProvider=future.get();Preview p=new Preview.Builder().build();p.setSurfaceProvider(preview.getSurfaceProvider());ImageAnalysis analysis=new ImageAnalysis.Builder().setTargetResolution(new android.util.Size(1280,720)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();analysis.setAnalyzer(worker,this::analyzeFrame);cameraProvider.unbindAll();cameraProvider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,p,analysis);cameraStarting=false;live=true;lastFrame=0;source.setText("Phone camera · live recognition");preview.setVisibility(View.VISIBLE);photoView.setVisibility(View.GONE);overlay.clear();status.setText("Recognizing latest frames. Pause to review a reading.");controls();}catch(Exception e){cameraStarting=false;live=false;status.setText("Camera unavailable. You can import a photo instead.");controls();}},ContextCompat.getMainExecutor(this));
    }
    private void analyzeFrame(ImageProxy image){
        long now=SystemClock.elapsedRealtime();if(!live||now-lastFrame<1000||!frameBusy.compareAndSet(false,true)){image.close();return;}lastFrame=now;final int token=generation;final long capturedAt=System.currentTimeMillis();
        Bitmap captured;
        try{Bitmap raw=image.toBitmap();Matrix rotation=new Matrix();rotation.postRotate(image.getImageInfo().getRotationDegrees());captured=Bitmap.createBitmap(raw,0,0,raw.getWidth(),raw.getHeight(),rotation,true);if(captured!=raw)raw.recycle();}
        catch(Exception e){image.close();frameBusy.set(false);runOnUiThread(()->{stopCamera();status.setText("Could not read a camera frame. Try importing a photo.");});return;}
        image.close(); // ML Kit owns a bitmap copy, so CameraX can release its buffer immediately.
        final Bitmap snapshot=captured;
        try{recognizer.process(InputImage.fromBitmap(snapshot,0)).addOnSuccessListener(text->{
            if(live&&token==generation&&!isDestroyed()){
                photoView.setImageDrawable(null);if(photo!=null)photo.recycle();photo=snapshot;
                accept(text,snapshot.getWidth(),snapshot.getHeight(),"Phone camera","camera",SystemClock.elapsedRealtime()-now,capturedAt);
            }else snapshot.recycle();
        }).addOnFailureListener(e->{snapshot.recycle();if(token==generation&&!isDestroyed()){stopCamera();status.setText("Camera reading failed. Restart the camera or import a photo.");}}).addOnCompleteListener(task->{frameBusy.set(false);if(!isDestroyed())controls();});}
        catch(Exception e){snapshot.recycle();frameBusy.set(false);runOnUiThread(()->{stopCamera();status.setText("Could not read a camera frame. Try importing a photo.");});}
    }
    private void stopCamera(){
        boolean wasCamera=live||cameraStarting;
        live=false;cameraStarting=false;generation++;if(cameraProvider!=null)cameraProvider.unbindAll();
        if(preview!=null){preview.setVisibility(View.GONE);photoView.setVisibility(View.VISIBLE);overlay.clear();photoView.setImageBitmap(photo);if(active!=null&&"camera".equals(active.optString("input_type"))){source.setText("Camera paused · original reading frame");showActiveBoxes();}else if(wasCamera){source.setText(photo==null?"Camera paused · import a photo or start again":"Current photo · camera paused");}}
        if(status!=null&&wasCamera){status.setText("Camera paused. Completed readings remain in history.");controls();}
    }
    private void accept(Text text,int width,int height,String name,String type,long elapsed,long capturedAt){
        List<PlateRules.Reading> raw=observations(text,width,height),candidates=PlateRules.candidates(raw);
        try{active=ReadingStore.reading(name,type,candidates,raw,elapsed);active.put("captured_at",ReadingStore.timestamp(capturedAt));records.add(active);if(records.size()>ReadingStore.LIMIT)records.remove(0);persist();if(live)overlay.clear();else overlay.setBoxes(candidates,width,height);render();status.setText(candidates.isEmpty()?"No plate candidate. Try a closer, sharper view or edit the reading.":live?"Reading found · pause the camera to review":"Reading ready. Compare it with the source.");}catch(JSONException e){status.setText("Could not store the recognition result.");}
    }
    private void showActiveBoxes(){
        if(active==null||photo==null)return;List<PlateRules.Reading> boxes=new ArrayList<>();JSONArray choices=active.optJSONArray("candidates");
        if(choices!=null)for(int i=0;i<choices.length();i++){JSONObject c=choices.optJSONObject(i);JSONArray b=c.optJSONArray("box");if(b!=null&&b.length()==4)boxes.add(new PlateRules.Reading(c.optString("raw"),null,new float[]{(float)b.optDouble(0),(float)b.optDouble(1),(float)b.optDouble(2),(float)b.optDouble(3)}));}
        overlay.setBoxes(boxes,photo.getWidth(),photo.getHeight());
    }
    private void persist(){
        List<JSONObject> snapshot=new ArrayList<>();try{for(JSONObject r:records)snapshot.add(new JSONObject(r.toString()));}catch(JSONException e){status.setText("Could not prepare saved history.");return;}
        worker.execute(()->{try{store.save(snapshot);}catch(IOException e){runOnUiThread(()->{if(!isDestroyed())status.setText("Could not save history. Export readings before closing the app.");});}});
    }
    private void render(){
        if(reading==null)return;String selected=ReadingStore.selected(active);reading.setText(selected.isEmpty()?"— — —":selected);alternatives.removeAllViews();score.setText("Raw OCR score · unavailable");
        if(active!=null){JSONArray choices=active.optJSONArray("candidates");int index=active.optInt("selected_index",-1);JSONObject candidate=choices.optJSONObject(index);
            if(candidate!=null&&!candidate.isNull("confidence"))score.setText(String.format(Locale.ROOT,"Raw OCR score · %.1f%%",candidate.optDouble("confidence")*100));
            boolean corrected=!active.isNull("manual_transcription");note.setText((active.optBoolean("reviewed")?"Reviewed by you. ":"Review required. ")+(corrected?"Manual transcription is separate from OCR. ":"")+"OCR scores are not calibrated plate accuracy.");
            for(int i=0;i<choices.length();i++){JSONObject c=choices.optJSONObject(i);final int chosen=i;Button b=button(c.optString("text"),false,()->{if(locked())return;try{active.put("selected_index",chosen);active.put("manual_transcription",JSONObject.NULL);active.put("reviewed",false);persist();render();}catch(JSONException ignored){}});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(6);alternatives.addView(b,lp);}
        }else note.setText("Scores are not calibrated plate accuracy. Covered characters may remain unreadable.");
        controls();
    }
    private void review(){if(locked()||active==null||ReadingStore.selected(active).isEmpty())return;try{active.put("reviewed",true);persist();render();status.setText("Marked reviewed by you.");}catch(JSONException ignored){}}
    private void edit(){
        if(locked()||active==null)return;JSONObject target=active;EditText input=new EditText(dialogContext());input.setSingleLine(true);input.setText(ReadingStore.selected(target));input.setHint("Visible plate characters");input.setContentDescription("Manual plate transcription");input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(16)});
        androidx.appcompat.app.AlertDialog dialog=new androidx.appcompat.app.AlertDialog.Builder(dialogContext()).setTitle("Edit transcription").setMessage("Store your transcription separately. This does not change the OCR score.").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String value=PlateRules.normalize(input.getText().toString());if(value.isEmpty()){input.setError("Enter visible plate characters");return;}try{target.put("manual_transcription",value);target.put("reviewed",false);persist();render();dialog.dismiss();}catch(JSONException ignored){}}));dialog.show();
    }
    private void showHistory(){
        if(locked()||records.isEmpty())return;List<JSONObject> snapshot=new ArrayList<>(records);Collections.reverse(snapshot);String[] labels=new String[snapshot.size()];for(int i=0;i<labels.length;i++){JSONObject r=snapshot.get(i);String plate=ReadingStore.selected(r);labels[i]=(plate.isEmpty()?"No candidate":plate)+(r.optBoolean("reviewed")?" · reviewed":"")+"\n"+r.optString("captured_at");}
        new androidx.appcompat.app.AlertDialog.Builder(dialogContext()).setTitle("Reading history").setItems(labels,(d,which)->{active=snapshot.get(which);photoView.setImageDrawable(null);source.setText("Saved text record · source photo not retained");if(photo!=null){photo.recycle();photo=null;}overlay.clear();render();status.setText("Historical reading loaded. Original images are not retained.");}).setNegativeButton("Close",null).show();
    }
    private void export(){if(records.isEmpty()||loading||photoBusy)return;try{exportSnapshot=ReadingStore.report(records).toString(2);saveReport.launch("platelab-readings.json");}catch(JSONException e){status.setText("Could not prepare the report.");}}
    private void showTour(int position){
        if(isDestroyed()||isFinishing())return;
        if(tour!=null)tour.dispose();
        tour=new OnboardingTour(this,workspaceHost,workspaceScroll,dark,new OnboardingTour.Step[]{
            new OnboardingTour.Step(photoButton,"Start with a clear photo","Tap Import photo to choose a vehicle image. Keep the plate large, sharp, and well lit so you can inspect the visible characters."),
            new OnboardingTour.Step(cameraButton,"Read from your phone camera","Start camera reads the newest frame up to once per second. Camera access is requested when you start it. Pause to inspect the completed reading."),
            new OnboardingTour.Step(recognizeButton,"Turn the image into evidence","After importing a photo, tap Recognize photo. Visible text is read on your phone. Covered or blurred characters may remain unreadable."),
            new OnboardingTour.Step(reviewButton,"You make the final call","Compare the candidate and alternatives with the source. Edit reading stores your own transcription; Mark reviewed records your check. The OCR score is not verified plate accuracy."),
            new OnboardingTour.Step(historyButton,"Keep track of your readings","View history opens your latest 200 saved text records. Your review travels with the reading. Original photos are not saved in history."),
            new OnboardingTour.Step(exportButton,"Save a report where you choose","Export JSON creates a report with the original OCR, selected reading, and review status. Choose where to save it using your phone's file picker."),
            new OnboardingTour.Step(themeButton,"Make the workspace yours","Use Light mode or Dark mode to suit your surroundings. Your choice is remembered, and switching keeps your current photo and reading."),
            new OnboardingTour.Step(supportButton,"You're ready to explore","Support us offers an optional Bitcoin donation. You can start with any photo, or replay this tour from About whenever you need a refresher.")
        },()->{controls();title.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);});
        tour.show(position);controls();
    }
    private void replayTour(){if(loading||photoBusy||frameBusy.get()){status.setText("Wait for the current reading to finish, then replay the tour.");return;}stopCamera();showTour(OnboardingTour.WELCOME);}
    @Override protected void onSaveInstanceState(Bundle out){if(tour!=null&&tour.isShowing())out.putInt(TOUR_POSITION,tour.position());super.onSaveInstanceState(out);}
    private void about(){new androidx.appcompat.app.AlertDialog.Builder(dialogContext()).setTitle("PlateLab for Android").setMessage("Version "+BuildConfig.VERSION_NAME+" · Akinwale Awoyele\n© 2026 Akinwale\n\nBundled ML Kit reads visible text on your phone. Plate formatting is a heuristic; it does not verify registration or recover hidden characters. Review every reading.\n\nCamera mode processes the latest frame up to once per second. This version supports photos and the phone camera; video files and RTSP cameras are not connected yet.\n\nText history is saved in this app's private storage. Photos are not retained in history. Exports are saved only where you choose.").setNeutralButton("Replay tour",(dialog,which)->replayTour()).setPositiveButton("Close",null).show();}
    private void support(){
        String address="3DhdtBbkE7TsiZzAU5JJuV3M7dYzygj8R2";LinearLayout panel=column();panel.setPadding(dp(24),dp(12),dp(24),dp(12));ImageView qr=new ImageView(this);qr.setContentDescription("Bitcoin donation QR code");
        try{com.google.zxing.common.BitMatrix matrix=new com.google.zxing.MultiFormatWriter().encode("bitcoin:"+address,com.google.zxing.BarcodeFormat.QR_CODE,420,420);Bitmap b=Bitmap.createBitmap(420,420,Bitmap.Config.ARGB_8888);for(int y=0;y<420;y++)for(int x=0;x<420;x++)b.setPixel(x,y,matrix.get(x,y)?Color.BLACK:Color.WHITE);qr.setImageBitmap(b);panel.addView(qr,new LinearLayout.LayoutParams(-1,dp(200)));}catch(Exception ignored){}
        TextView copy=text(address,13,INK);copy.setTextIsSelectable(true);space(panel,10);panel.addView(copy);space(panel,10);panel.addView(text("Send BTC on the Bitcoin network. Choose and confirm the amount in your wallet.",12,MUTED));
        ScrollView supportScroll=new ScrollView(this);supportScroll.addView(panel);
        new androidx.appcompat.app.AlertDialog.Builder(dialogContext()).setTitle("Support PlateLab").setMessage("Help build what comes next.").setView(supportScroll).setPositiveButton("Open wallet",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("bitcoin:"+address)));}catch(ActivityNotFoundException e){new androidx.appcompat.app.AlertDialog.Builder(dialogContext()).setMessage("No Bitcoin wallet is installed. Use Support us to scan the QR code or copy the address.").setPositiveButton("Close",null).show();}}).setNeutralButton("Copy address",(d,w)->{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText("Bitcoin address",address));status.setText("Bitcoin address copied.");}).setNegativeButton("Close",null).show();
    }
    @Override protected void onStop(){stopCamera();super.onStop();}
    @Override protected void onDestroy(){if(tour!=null)tour.dispose();generation++;if(cameraProvider!=null)cameraProvider.unbindAll();recognizer.close();worker.shutdown();super.onDestroy();}
    private final class BoxOverlay extends View {
        private List<PlateRules.Reading> boxes=Collections.emptyList();private int frameWidth=1,frameHeight=1;private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
        BoxOverlay(Context context){super(context);pen.setColor(MINT);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(dp(2));}
        void setBoxes(List<PlateRules.Reading> candidates,int width,int height){boxes=candidates;frameWidth=width;frameHeight=height;invalidate();}
        void clear(){boxes=Collections.emptyList();invalidate();}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float scale=Math.min(getWidth()/(float)frameWidth,getHeight()/(float)frameHeight),w=frameWidth*scale,h=frameHeight*scale,x=(getWidth()-w)/2,y=(getHeight()-h)/2;for(PlateRules.Reading r:boxes){float[] b=r.box;canvas.drawRoundRect(x+b[0]*w,y+b[1]*h,x+(b[0]+b[2])*w,y+(b[1]+b[3])*h,dp(4),dp(4),pen);}}
    }
}
