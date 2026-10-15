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
    @Test public void openingWeatherDoesNotAskForPermissionAndFixturesRenderInBothThemes() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(activity->{TourTestSupport.dismissWelcome(activity);for(boolean dark:new boolean[]{true,false}){
                AtomicInteger requested=new AtomicInteger();WeatherPanel weather=new WeatherPanel(activity,dark,requested::incrementAndGet);weather.show();assertEquals(0,requested.get());
                try{weather.render(fixture(),"Test city");java.lang.reflect.Field field=WeatherPanel.class.getDeclaredField("dialog");field.setAccessible(true);androidx.appcompat.app.AlertDialog dialog=(androidx.appcompat.app.AlertDialog)field.get(weather);android.view.View decor=dialog.getWindow().getDecorView();assertNotNull(TourTestSupport.label(decor,"28°C"));assertNotNull(TourTestSupport.label(decor,"Rain"));assertNotNull(TourTestSupport.label(decor,"Current forecast · 2026-10-15 14:00 WAT"));assertNotNull(TourTestSupport.label(decor,"Test city"));}catch(Exception e){throw new AssertionError(e);}finally{weather.close();}
            }});
        }
    }
}
