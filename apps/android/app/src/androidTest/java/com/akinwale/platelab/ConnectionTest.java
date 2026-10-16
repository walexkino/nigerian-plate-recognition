package com.akinwale.platelab;

import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;

/** Changes connectivity on the disposable local emulator only, never on a user's phone. */
@RunWith(AndroidJUnit4.class)
public class ConnectionTest {
    private String command(String command) throws Exception {
        try(ParcelFileDescriptor p=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(p);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[1024];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toString("UTF-8").trim();
        }
    }
    private TextView label(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return (TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView found=label(g.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private void await(ActivityScenario<MainActivity> scenario,String value) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+15000;
        while(SystemClock.elapsedRealtime()<deadline){boolean[] found={false};scenario.onActivity(a->{TourTestSupport.dismissWelcome(a);TextView t=label(a.getWindow().getDecorView(),value);found[0]=t!=null;if(t!=null){int color=t.getCurrentTextColor();assertTrue(value.endsWith("Online")?Color.green(color)>Color.red(color):Color.red(color)>Color.green(color));}});if(found[0])return;Thread.sleep(100);}
        fail("Connection label did not become "+value);
    }
    @Test public void indicatorChangesWhenInternetDisconnectsAndReturns() throws Exception {
        Assume.assumeTrue("Only run network toggles on the local emulator","1".equals(command("getprop ro.kernel.qemu")));
        boolean wifi="1".equals(command("settings get global wifi_on"));String data=command("settings get global mobile_data");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            await(scenario,"● Online");
            try{command("svc data disable");command("svc wifi disable");System.out.println("After disabling Wi-Fi: "+command("settings get global wifi_on"));await(scenario,"● Offline");
                scenario.onActivity(a->assertTrue(label(a.getWindow().getDecorView(),"Import photo").isEnabled()));
            }finally{if(wifi)command("svc wifi enable");if("1".equals(data))command("svc data enable");}
            await(scenario,"● Online");
        }
    }
}
