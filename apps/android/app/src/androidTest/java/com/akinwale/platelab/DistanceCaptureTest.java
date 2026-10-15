package com.akinwale.platelab;
import android.Manifest;
import android.content.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.lang.reflect.Field;
@RunWith(AndroidJUnit4.class)
public class DistanceCaptureTest {
    @Rule public GrantPermissionRule permission=GrantPermissionRule.grant(Manifest.permission.CAMERA);
    private Object field(Object o,String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private void waitFor(ActivityScenario<MainActivity> scenario,String fieldName) throws Exception {long until=System.currentTimeMillis()+30000;while(System.currentTimeMillis()<until){boolean[] ok={false};scenario.onActivity(a->{Object v=field(a,fieldName);ok[0]=v instanceof Button?((Button)v).isEnabled():v!=null;});if(ok[0])return;Thread.sleep(100);}fail("Not ready: "+fieldName);}
    @Test public void detailedCameraCapturePausesAndCropMapsBackToSource() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();SharedPreferences prefs=c.getSharedPreferences("capture",Context.MODE_PRIVATE);boolean had=prefs.contains("distant"),old=prefs.getBoolean("distant",false);prefs.edit().putBoolean("distant",false).commit();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            Thread.sleep(800);scenario.onActivity(TourTestSupport::dismissWelcome);waitFor(scenario,"cameraButton");
            scenario.onActivity(a->{((CompoundButton)field(a,"distanceSwitch")).setChecked(true);((Button)field(a,"cameraButton")).performClick();});waitFor(scenario,"captureButton");
            scenario.onActivity(a->{assertNotNull(field(a,"camera"));((Button)field(a,"captureButton")).performClick();});waitFor(scenario,"photo");
            scenario.onActivity(a->{assertEquals(false,field(a,"live"));Bitmap image=(Bitmap)field(a,"photo");assertTrue(Math.max(image.getWidth(),image.getHeight())<=4096);View view=(View)field(a,"photoView");long t=android.os.SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,view.getWidth()*.2f,view.getHeight()*.35f,0),up=MotionEvent.obtain(t,t+100,MotionEvent.ACTION_UP,view.getWidth()*.8f,view.getHeight()*.65f,0);view.dispatchTouchEvent(down);view.dispatchTouchEvent(up);down.recycle();up.recycle();float[] selected=(float[])field(a,"region");assertNotNull(selected);assertTrue(selected[2]>0&&selected[3]>0);assertTrue(((Button)field(a,"recognizeButton")).isEnabled());});
        }finally{if(had)prefs.edit().putBoolean("distant",old).commit();else prefs.edit().remove("distant").commit();}
    }
}
