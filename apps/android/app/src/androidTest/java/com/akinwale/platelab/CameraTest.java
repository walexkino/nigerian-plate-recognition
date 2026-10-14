package com.akinwale.platelab;

import android.Manifest;
import android.view.*;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CameraTest {
    @Rule public GrantPermissionRule permission=GrantPermissionRule.grant(Manifest.permission.CAMERA);
    private View label(View view,String value){if(view instanceof TextView&&value.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=label(group.getChildAt(i),value);if(found!=null)return found;}}return null;}
    @Test public void cameraReadsFramesAndPauseKeepsOriginalEvidence() throws Exception {
        ReadingStore store=new ReadingStore(InstrumentationRegistry.getInstrumentation().getTargetContext());List<JSONObject> previous=store.load();store.save(Collections.emptyList());
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            long deadline=System.currentTimeMillis()+10000;
            while(System.currentTimeMillis()<deadline){boolean[] ready={false};scenario.onActivity(a->{View button=label(a.getWindow().getDecorView(),"Start camera");TourTestSupport.dismissWelcome(a);ready[0]=button!=null&&button.isEnabled();});if(ready[0])break;Thread.sleep(100);}
            scenario.onActivity(a->{View button=label(a.getWindow().getDecorView(),"Start camera");assertTrue(button.isEnabled());button.performClick();});
            deadline=System.currentTimeMillis()+35000;
            while(store.load().isEmpty()&&System.currentTimeMillis()<deadline)Thread.sleep(100);
            assertFalse("Camera did not produce a stored OCR result",store.load().isEmpty());
            assertEquals("camera",store.load().get(0).getString("input_type"));
            scenario.onActivity(a->{View button=label(a.getWindow().getDecorView(),"Pause camera");assertNotNull(button);button.performClick();assertNotNull(label(a.getWindow().getDecorView(),"Camera paused · original reading frame"));});
            int count=store.load().size();Thread.sleep(1500);assertEquals("Paused camera should not append late results",count,store.load().size());
        }finally{store.save(previous);}
    }
}
