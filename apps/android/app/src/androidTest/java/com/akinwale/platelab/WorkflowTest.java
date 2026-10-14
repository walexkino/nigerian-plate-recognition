package com.akinwale.platelab;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Exercises photo decoding, real OCR, review, persistence, and a fresh activity launch. */
@RunWith(AndroidJUnit4.class)
public class WorkflowTest {
    private View label(View view,String text){if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=label(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private void await(ActivityScenario<MainActivity> scenario,String text) throws Exception {
        long deadline=System.currentTimeMillis()+35000;
        while(System.currentTimeMillis()<deadline){AtomicBoolean found=new AtomicBoolean();scenario.onActivity(activity->found.set(label(activity.getWindow().getDecorView(),text)!=null));if(found.get())return;Thread.sleep(100);}
        fail("UI did not reach: "+text);
    }
    @Test public void photoRecognizeReviewAndReload() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();ReadingStore store=new ReadingStore(context);List<JSONObject> previous=store.load();store.save(Collections.emptyList());
        android.content.SharedPreferences preferences=context.getSharedPreferences("appearance",Context.MODE_PRIVATE);boolean hadTheme=preferences.contains("dark"),previousTheme=preferences.getBoolean("dark",true);preferences.edit().putBoolean("dark",true).commit();
        File image=new File(context.getFilesDir(),"workflow-synthetic.png");Bitmap bitmap=Bitmap.createBitmap(800,300,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);ink.setTextSize(100);ink.setColor(Color.BLACK);ink.setTypeface(Typeface.create("monospace",Typeface.BOLD));canvas.drawText("ABC 123 DE",85,180,ink);try(FileOutputStream out=new FileOutputStream(image)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
        try{
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
                await(scenario,"Ready. Import a photo or start your camera.");
                scenario.onActivity(TourTestSupport::dismissWelcome);
                scenario.onActivity(activity->{try{Method method=MainActivity.class.getDeclaredMethod("importPhoto",Uri.class);method.setAccessible(true);method.invoke(activity,Uri.fromFile(image));}catch(Exception e){throw new RuntimeException(e);}});
                await(scenario,"Photo ready. Tap Recognize photo.");
                scenario.onActivity(activity->label(activity.getWindow().getDecorView(),"Recognize photo").performClick());
                await(scenario,"ABC123DE");
                scenario.onActivity(activity->{View review=label(activity.getWindow().getDecorView(),"Mark reviewed");assertTrue(review.isEnabled());review.performClick();});
                await(scenario,"Marked reviewed by you.");
                // Changing appearance preserves the exact imported bitmap and reviewed record.
                scenario.onActivity(activity->{try{
                    Field p=MainActivity.class.getDeclaredField("photo"),a=MainActivity.class.getDeclaredField("active");p.setAccessible(true);a.setAccessible(true);Object originalPhoto=p.get(activity),originalReading=a.get(activity);
                    label(activity.getWindow().getDecorView(),"☀ Light mode").performClick();
                    assertNotNull(label(activity.getWindow().getDecorView(),"☾ Dark mode"));assertSame(originalPhoto,p.get(activity));assertSame(originalReading,a.get(activity));
                    Method replay=MainActivity.class.getDeclaredMethod("replayTour");replay.setAccessible(true);replay.invoke(activity);assertNotNull(label(activity.getWindow().getDecorView(),"Take a quick tour"));label(activity.getWindow().getDecorView(),"Skip for now").performClick();assertSame(originalPhoto,p.get(activity));assertSame(originalReading,a.get(activity));assertTrue(((JSONObject)a.get(activity)).optBoolean("reviewed"));assertEquals("ABC123DE",((TextView)label(activity.getWindow().getDecorView(),"ABC123DE")).getText().toString());
                }catch(Exception e){throw new RuntimeException(e);}});
                // ATD images optimize away display composition; draw the real measured views for layout QA.
                Bitmap[] screen={null};scenario.onActivity(activity->{View decor=activity.getWindow().getDecorView();screen[0]=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);decor.draw(new Canvas(screen[0]));});
                try(FileOutputStream out=new FileOutputStream(new File(context.getCacheDir(),"platelab-android-demo.png"))){screen[0].compress(Bitmap.CompressFormat.PNG,100,out);}screen[0].recycle();
            }
            // A recreated activity reads persisted history after its ordered save queue completes.
            long deadline=System.currentTimeMillis()+5000;
            while((store.load().isEmpty()||!store.load().get(0).optBoolean("reviewed"))&&System.currentTimeMillis()<deadline)Thread.sleep(100);
            assertEquals("ABC123DE",ReadingStore.selected(store.load().get(0)));assertTrue(store.load().get(0).getBoolean("reviewed"));
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
                await(scenario,"History · 1 reading");
                scenario.onActivity(activity->{assertNotNull(label(activity.getWindow().getDecorView(),"☾ Dark mode"));assertTrue(label(activity.getWindow().getDecorView(),"View history").isEnabled());assertTrue(label(activity.getWindow().getDecorView(),"Export JSON").isEnabled());});
            }
        }finally{store.save(previous);image.delete();if(hadTheme)preferences.edit().putBoolean("dark",previousTheme).commit();else preferences.edit().remove("dark").commit();}
    }
}
