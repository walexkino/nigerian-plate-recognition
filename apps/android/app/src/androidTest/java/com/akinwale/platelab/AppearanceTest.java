package com.akinwale.platelab;

import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Runs at the emulator's configured size and font scale; validates both palettes and measured text. */
@RunWith(AndroidJUnit4.class)
public class AppearanceTest {
    private View find(View v,String label){if(v instanceof TextView&&label.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),label);if(found!=null)return found;}}return null;}
    private void check(View v){
        if(v.getVisibility()!=View.VISIBLE)return;
        if(v instanceof TextView){TextView t=(TextView)v;if(t.getLayout()!=null&&t.getWidth()>0)assertTrue("Clipped text: "+t.getText(),t.getLayout().getHeight()<=t.getHeight()-t.getCompoundPaddingTop()-t.getCompoundPaddingBottom());}
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View c=g.getChildAt(i);if(c.getVisibility()!=View.VISIBLE)continue;assertTrue("Child exceeds parent width: "+c,c.getRight()<=g.getWidth());check(c);}}
    }
    @Test public void themesAndResponsiveText() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();android.content.SharedPreferences pref=context.getSharedPreferences("appearance",Context.MODE_PRIVATE);boolean had=pref.contains("dark"),previous=pref.getBoolean("dark",true);pref.edit().putBoolean("dark",true).commit();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            Thread.sleep(1500);scenario.onActivity(TourTestSupport::dismissWelcome);
            for(boolean light:new boolean[]{false,true}){
                if(light)scenario.onActivity(a->find(a.getWindow().getDecorView(),"☀ Light mode").performClick());
                Thread.sleep(250);
                scenario.onActivity(a->{View decor=a.getWindow().getDecorView();check(decor);TextView heading=(TextView)find(decor,"PlateLab");assertEquals(light?0xff172720:0xffedf2ef,heading.getCurrentTextColor());Bitmap image=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);decor.draw(new Canvas(image));String label=(light?"light":"dark")+"-"+a.getResources().getConfiguration().screenWidthDp+"-"+a.getResources().getConfiguration().fontScale;
                    try(FileOutputStream out=new FileOutputStream(new File(context.getCacheDir(),"appearance-"+label+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}catch(IOException e){throw new RuntimeException(e);}finally{image.recycle();}
                });
            }
        }finally{if(had)pref.edit().putBoolean("dark",previous).commit();else pref.edit().remove("dark").commit();}
    }
}
