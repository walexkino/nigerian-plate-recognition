package com.akinwale.platelab;

import android.Manifest;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.lang.reflect.Method;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class OnboardingTest {
    private Context context;
    private SharedPreferences onboarding,appearance;
    private boolean hadCompletion,oldCompletion,hadTheme,oldTheme;
    @Before public void reset(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();onboarding=context.getSharedPreferences(OnboardingTour.PREFERENCES,Context.MODE_PRIVATE);appearance=context.getSharedPreferences("appearance",Context.MODE_PRIVATE);hadCompletion=onboarding.contains(OnboardingTour.COMPLETED);oldCompletion=onboarding.getBoolean(OnboardingTour.COMPLETED,false);hadTheme=appearance.contains("dark");oldTheme=appearance.getBoolean("dark",true);onboarding.edit().remove(OnboardingTour.COMPLETED).commit();appearance.edit().putBoolean("dark",true).commit();}
    @After public void restore(){if(hadCompletion)onboarding.edit().putBoolean(OnboardingTour.COMPLETED,oldCompletion).commit();else onboarding.edit().remove(OnboardingTour.COMPLETED).commit();if(hadTheme)appearance.edit().putBoolean("dark",oldTheme).commit();else appearance.edit().remove("dark").commit();}
    private void await(ActivityScenario<MainActivity> scenario,String label) throws Exception {long end=System.currentTimeMillis()+10000;while(System.currentTimeMillis()<end){boolean[] found={false};scenario.onActivity(a->found[0]=TourTestSupport.label(a.getWindow().getDecorView(),label)!=null);if(found[0])return;Thread.sleep(100);}fail("Missing: "+label);}
    private void tap(ActivityScenario<MainActivity> scenario,String label){scenario.onActivity(a->{View button=TourTestSupport.label(a.getWindow().getDecorView(),label);assertNotNull(button);assertTrue(button.isEnabled());button.performClick();});}
    private View illustration(View view){if(view.getClass().getSimpleName().equals("PlateIllustration"))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=illustration(group.getChildAt(i));if(found!=null)return found;}}return null;}
    private void capture(ActivityScenario<MainActivity> scenario,String name) throws Exception {
        Thread.sleep(300);scenario.onActivity(a->{View decor=a.getWindow().getDecorView();
            for(String label:new String[]{"Take a quick tour","Skip for now","Next","Back","Skip tour","Finish tour"}){View button=TourTestSupport.label(decor,label);if(button==null)continue;Rect visible=new Rect();assertTrue(label+" is not visible",button.getGlobalVisibleRect(visible));assertEquals(label+" is clipped vertically",button.getHeight(),visible.height());assertEquals(label+" is clipped horizontally",button.getWidth(),visible.width());}
            Bitmap bitmap=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);decor.draw(new Canvas(bitmap));
            View art=illustration(decor);if(art!=null){Rect visible=new Rect();if(art.getGlobalVisibleRect(visible)&&visible.height()==art.getHeight()){
                int[] at=new int[2],origin=new int[2];art.getLocationInWindow(at);decor.getLocationInWindow(origin);float scale=Math.min(art.getWidth()/320f,art.getHeight()/190f),left=at[0]-origin[0]+(art.getWidth()-320*scale)/2,top=at[1]-origin[1]+(art.getHeight()-190*scale)/2;
                int green=0;for(int y=Math.max(0,(int)(top+72*scale));y<Math.min(bitmap.getHeight(),(int)(top+114*scale));y++)for(int x=Math.max(0,(int)(left+75*scale));x<Math.min(bitmap.getWidth(),(int)(left+250*scale));x++)if((bitmap.getPixel(x,y)&0x00ffffff)==0x00206346)green++;
                assertTrue("Sample plate lettering did not render",green>75);
            }}
            try(FileOutputStream out=new FileOutputStream(new File(context.getCacheDir(),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}catch(IOException e){throw new RuntimeException(e);}finally{bitmap.recycle();}});
    }
    @Test public void welcomeSkipIsRememberedAndReplayIsAvailable() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            await(scenario,"Take a quick tour");capture(scenario,"welcome-dark");tap(scenario,"Skip for now");assertTrue(onboarding.getBoolean(OnboardingTour.COMPLETED,false));scenario.recreate();await(scenario,"Ready. Import a photo or start your camera.");
            scenario.onActivity(a->{assertNull(TourTestSupport.label(a.getWindow().getDecorView(),"Take a quick tour"));try{Method replay=MainActivity.class.getDeclaredMethod("replayTour");replay.setAccessible(true);replay.invoke(a);}catch(Exception e){throw new RuntimeException(e);}});
            await(scenario,"Take a quick tour");tap(scenario,"Skip for now");
        }
    }
    @Test public void nextBackRotationAndMiddleSkipDoNotActivateFeatures() throws Exception {
        int cameraPermission=context.checkSelfPermission(Manifest.permission.CAMERA);int readings=new ReadingStore(context).load().size();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            await(scenario,"Take a quick tour");tap(scenario,"Take a quick tour");await(scenario,"QUICK TOUR  ·  1 OF 8");capture(scenario,"tour-photo-dark");tap(scenario,"Next");await(scenario,"QUICK TOUR  ·  2 OF 8");scenario.recreate();await(scenario,"QUICK TOUR  ·  2 OF 8");tap(scenario,"Back");await(scenario,"QUICK TOUR  ·  1 OF 8");tap(scenario,"Skip tour");assertTrue(onboarding.getBoolean(OnboardingTour.COMPLETED,false));
            scenario.recreate();await(scenario,"Ready. Import a photo or start your camera.");scenario.onActivity(a->assertNull(TourTestSupport.label(a.getWindow().getDecorView(),"Skip tour")));
        }
        assertEquals(cameraPermission,context.checkSelfPermission(Manifest.permission.CAMERA));assertEquals(readings,new ReadingStore(context).load().size());
    }
    @Test public void completeAllEightStepsInLightMode() throws Exception {
        appearance.edit().putBoolean("dark",false).commit();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            await(scenario,"Take a quick tour");capture(scenario,"welcome-light");tap(scenario,"Take a quick tour");await(scenario,"QUICK TOUR  ·  1 OF 8");capture(scenario,"tour-photo-light");
            for(int i=1;i<8;i++){await(scenario,"QUICK TOUR  ·  "+i+" OF 8");tap(scenario,"Next");}
            await(scenario,"QUICK TOUR  ·  8 OF 8");capture(scenario,"tour-finish-light");tap(scenario,"Finish tour");assertTrue(onboarding.getBoolean(OnboardingTour.COMPLETED,false));scenario.onActivity(a->assertNull(TourTestSupport.label(a.getWindow().getDecorView(),"Skip tour")));
        }
    }
}
