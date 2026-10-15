package com.akinwale.platelab;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import static org.junit.Assert.*;
import java.util.concurrent.atomic.AtomicInteger;
import android.widget.TextView;
@RunWith(AndroidJUnit4.class)
public class WeatherTest {
    private JSONObject fixture() throws JSONException{return new JSONObject("{\"current\":{\"temperature_2m\":28.4,\"apparent_temperature\":31,\"weather_code\":63,\"relative_humidity_2m\":80,\"wind_speed_10m\":9,\"time\":\"2026-10-15T14:00\"},\"timezone_abbreviation\":\"WAT\",\"daily\":{\"time\":[\"2026-10-15\",\"2026-10-16\",\"2026-10-17\"],\"weather_code\":[63,2,0],\"temperature_2m_max\":[30,31,32],\"temperature_2m_min\":[24,25,25],\"precipitation_probability_max\":[70,null,0]}}");}
    @Test public void missingCurrentTemperatureIsNotAZeroReading() throws Exception {JSONObject data=fixture();data.getJSONObject("current").put("temperature_2m",JSONObject.NULL);try{WeatherData.current(data);fail("Missing current temperature accepted");}catch(JSONException expected){}assertEquals("—",WeatherData.value(JSONObject.NULL,"%"));}
    @Test public void streetAddressesAndGlassForecastRenderAfterLayout() throws Exception {
        JSONObject addresses=new JSONObject("{\"features\":[{\"properties\":{\"name\":\"Broad Street\",\"city\":\"Lagos\",\"country\":\"Nigeria\"},\"geometry\":{\"coordinates\":[3.39,6.45]}}]}");assertTrue(WeatherData.places(addresses).getJSONObject(0).getString("name").contains("Broad Street"));
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(TourTestSupport::dismissWelcome);
            for(boolean dark:new boolean[]{true,false}){
                WeatherPanel[] panel={null};androidx.appcompat.app.AlertDialog[] dialog={null};
                scenario.onActivity(a->{try{panel[0]=new WeatherPanel(a,dark,()->fail("Unexpected permission request"));panel[0].show();panel[0].render(fixture(),"Broad Street, Lagos");java.lang.reflect.Field f=WeatherPanel.class.getDeclaredField("dialog");f.setAccessible(true);dialog[0]=(androidx.appcompat.app.AlertDialog)f.get(panel[0]);}catch(Exception e){throw new AssertionError(e);}});
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(a->{android.view.View decor=dialog[0].getWindow().getDecorView();assertTrue(decor.getWidth()>0&&decor.getHeight()>0);assertNotNull(TourTestSupport.label(decor,"28°C"));android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);decor.draw(new android.graphics.Canvas(image));try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(a.getCacheDir(),"weather-glass-"+(dark?"dark":"light")+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}catch(java.io.IOException e){throw new AssertionError(e);}finally{image.recycle();panel[0].close();}});
            }
        }
    }
    @Test public void openingWeatherDoesNotAskForPermissionAndFixturesRenderInBothThemes() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(activity->{TourTestSupport.dismissWelcome(activity);for(boolean dark:new boolean[]{true,false}){
                AtomicInteger requested=new AtomicInteger();WeatherPanel weather=new WeatherPanel(activity,dark,requested::incrementAndGet);weather.show();assertEquals(0,requested.get());
                try{weather.render(fixture(),"Test city");java.lang.reflect.Field field=WeatherPanel.class.getDeclaredField("dialog");field.setAccessible(true);androidx.appcompat.app.AlertDialog dialog=(androidx.appcompat.app.AlertDialog)field.get(weather);android.view.View decor=dialog.getWindow().getDecorView();assertNotNull(TourTestSupport.label(decor,"28°C"));assertNotNull(TourTestSupport.label(decor,"Rain"));assertNotNull(TourTestSupport.label(decor,"Current forecast · 2026-10-15 14:00 WAT"));assertNotNull(TourTestSupport.label(decor,"Test city"));}catch(Exception e){throw new AssertionError(e);}finally{weather.close();}
            }});
        }
    }
}
