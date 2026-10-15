package com.akinwale.platelab;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.*;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Optional online weather; one-shot approximate location, bounded requests, no disk cache. */
final class WeatherPanel {
    private final AppCompatActivity activity;
    private final Context context;
    private final Runnable askPermission;
    private final int ink,muted,mint,panel,bg;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private volatile HttpURLConnection connection;
    private Future<?> work;
    private androidx.core.os.CancellationSignal locationCancel;
    private AlertDialog dialog;
    private TextView status;
    private EditText input;
    private LinearLayout places,forecast;
    private Button locate,search,refresh;
    private int generation=0;
    private boolean foreground=true,permissionPending=false;
    private Double latitude,longitude;
    private String label;
    WeatherPanel(AppCompatActivity activity,boolean dark,Runnable askPermission){this.activity=activity;this.askPermission=askPermission;context=new androidx.appcompat.view.ContextThemeWrapper(activity,dark?R.style.Theme_PlateLab_Dark:R.style.Theme_PlateLab_Light);ink=dark?0xffedf2ef:0xff172720;muted=dark?0xffa2b4ab:0xff596b61;mint=dark?0xffb5e7c2:0xff206346;panel=dark?0xff1b2526:0xffe8f3ec;bg=dark?0xff101719:0xffffffff;}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(context);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView text(String s,int size,int color){TextView v=new TextView(context);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(4),0,dp(4));return v;}
    private void add(LinearLayout parent,View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);parent.addView(v,lp);}
    private Button button(String title,Runnable action){Button b=new Button(context);b.setAllCaps(false);b.setText(title);b.setTextColor(ink);b.setMinHeight(dp(48));b.setOnClickListener(v->action.run());return b;}
    void show(){
        ScrollView scroll=new ScrollView(context);LinearLayout root=column();root.setPadding(dp(20),dp(8),dp(20),dp(16));root.setBackgroundColor(bg);scroll.addView(root);
        add(root,text("A little context for your day.",20,ink));add(root,text("Use your approximate location or choose a city for current conditions and a three-day forecast.",13,muted));
        locate=button("Use my location",this::useLocation);add(root,locate);
        input=new EditText(context);input.setHint("Search a city, e.g. Lagos");input.setTextColor(ink);input.setHintTextColor(muted);input.setSingleLine(true);input.setContentDescription("City name");input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);input.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){search();return true;}return false;});add(root,input);
        search=button("Search city",this::search);add(root,search);status=text("Choose a location to begin.",13,mint);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(root,status);
        places=column();add(root,places);forecast=column();add(root,forecast);refresh=button("Refresh forecast",()->{if(latitude!=null)load(latitude,longitude,label);else useLocation();});add(root,refresh);
        add(root,text("Weather needs internet. Your approximate location or city search is sent to Open-Meteo. It is not saved in plate reports.",12,muted));
        TextView credit=text("Weather: Open-Meteo · Locations: GeoNames\nCC BY 4.0 · View attribution",12,mint);credit.setOnClickListener(v->{try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://open-meteo.com/en/licence")));}catch(ActivityNotFoundException e){status.setText("Attribution: open-meteo.com/en/licence");}});credit.setContentDescription("Open weather attribution and license");add(root,credit);
        dialog=new AlertDialog.Builder(context).setTitle("Local weather").setView(scroll).setNegativeButton("Close",null).create();dialog.setOnDismissListener(d->close());dialog.show();
    }
    private boolean visible(){return foreground&&dialog!=null&&dialog.isShowing()&&!activity.isDestroyed();}
    private void controls(boolean enabled){for(View v:new View[]{locate,search,refresh,input})if(v!=null)v.setEnabled(enabled);}
    private int start(String message){cancel();controls(false);places.removeAllViews();status.setText(message);return generation;}
    private void cancel(){generation++;if(locationCancel!=null){locationCancel.cancel();locationCancel=null;}main.removeCallbacks(locationTimeout);if(work!=null)work.cancel(true);HttpURLConnection c=connection;if(c!=null)c.disconnect();}
    private final Runnable locationTimeout=()->{if(visible()){cancel();status.setText("Location timed out. Search for a city instead.");controls(true);}};
    void suspend(){foreground=false;permissionPending=false;cancel();if(status!=null){status.setText("Weather paused. Choose a location or refresh when you return.");controls(true);}}
    void resume(){foreground=true;}
    void close(){cancel();permissionPending=false;if(dialog!=null&&dialog.isShowing())dialog.dismiss();network.shutdownNow();}
    private void useLocation(){
        if(!visible())return;
        if(ContextCompat.checkSelfPermission(activity,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){permissionPending=true;askPermission.run();return;}
        locate();
    }
    void permissionResult(boolean granted){if(!permissionPending||!visible())return;permissionPending=false;if(granted)locate();else status.setText("Location permission was not granted. You can search for a city instead.");}
    @SuppressWarnings("MissingPermission") private void locate(){
        int token=start("Finding your approximate location…");LocationManager manager=(LocationManager)activity.getSystemService(Context.LOCATION_SERVICE);
        try{
            String provider=manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)?LocationManager.NETWORK_PROVIDER:manager.getProviders(true).contains("fused")?"fused":null;
            if(provider==null){status.setText("Approximate location is unavailable. Enable Location Services or search for a city.");controls(true);return;}
            locationCancel=new androidx.core.os.CancellationSignal();main.postDelayed(locationTimeout,18000);
            LocationManagerCompat.getCurrentLocation(manager,provider,locationCancel,ContextCompat.getMainExecutor(activity),location->{
                if(token!=generation||!visible())return;main.removeCallbacks(locationTimeout);
                if(location==null||SystemClock.elapsedRealtimeNanos()-location.getElapsedRealtimeNanos()>TimeUnit.MINUTES.toNanos(5)){status.setText("A recent location is unavailable. Search for a city instead.");controls(true);return;}
                load(location.getLatitude(),location.getLongitude(),"Your approximate location");
            });
        }catch(SecurityException|IllegalArgumentException e){cancel();status.setText("Location unavailable. Check location permission or search for a city.");controls(true);}
    }
    private interface Result {void accept(JSONObject result) throws JSONException;}
    private void fetch(String url,int token,Result success){
        work=network.submit(()->{
            HttpURLConnection c=null;
            try{
                c=(HttpURLConnection)new URL(url).openConnection();connection=c;c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setUseCaches(false);c.setInstanceFollowRedirects(false);int code=c.getResponseCode();
                if(code!=200)throw new IOException(code==429?"Weather service is busy. Please try again later.":"Could not load weather. Check your connection and try again.");
                StringBuilder body=new StringBuilder();try(Reader reader=new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8)){char[] b=new char[4096];int n;while((n=reader.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Canceled");body.append(b,0,n);if(body.length()>1_000_000)throw new IOException("Weather response unavailable.");}}
                JSONObject result=new JSONObject(body.toString());if(result.optBoolean("error"))throw new IOException("Weather data is unavailable. Try again later.");
                main.post(()->{if(token!=generation||!visible())return;try{success.accept(result);}catch(JSONException e){status.setText("Weather data is unavailable. Try again later.");}finally{controls(true);}});
            }catch(Exception e){String message=e instanceof IOException&&e.getMessage()!=null&&e.getMessage().startsWith("Weather service")?e.getMessage():"Could not load weather. Check your connection and try again.";main.post(()->{if(token==generation&&visible()){status.setText(message+(forecast.getChildCount()>0?" Previous forecast remains below.":""));controls(true);}});}
            finally{if(c!=null)c.disconnect();if(connection==c)connection=null;}
        });
    }
    private void search(){if(!visible()||!search.isEnabled())return;final String url;try{url=WeatherData.searchUrl(input.getText().toString());}catch(IllegalArgumentException e){status.setText(e.getMessage());return;}int token=start("Searching cities…");fetch(url,token,result->{JSONArray found=result.optJSONArray("results");int count=0;if(found!=null)for(int i=0;i<Math.min(5,found.length());i++){JSONObject p=found.getJSONObject(i);double lat=p.optDouble("latitude",Double.NaN),lon=p.optDouble("longitude",Double.NaN);if(!Double.isFinite(lat)||!Double.isFinite(lon))continue;String name=p.optString("name")+(p.optString("admin1").isEmpty()?"":", "+p.optString("admin1"))+(p.optString("country").isEmpty()?"":", "+p.optString("country"));add(places,button(name,()->load(lat,lon,name)));count++;}status.setText(count>0?"Select your city below.":"No matching city. Try another name or add a country.");});}
    private void load(double lat,double lon,String name){if(!visible())return;latitude=WeatherData.rounded(lat);longitude=WeatherData.rounded(lon);label=name;int token=start("Loading forecast…");fetch(WeatherData.forecastUrl(latitude,longitude),token,result->{render(result,name);status.setText("Forecast updated. Weather is a model estimate for this area.");});}
    void render(JSONObject result,String name) throws JSONException {
        JSONObject current=WeatherData.current(result);forecast.removeAllViews();forecast.setPadding(dp(16),dp(16),dp(16),dp(16));android.graphics.drawable.GradientDrawable surface=new android.graphics.drawable.GradientDrawable();surface.setColor(panel);surface.setCornerRadius(dp(16));forecast.setBackground(surface);
        add(forecast,text(name,16,ink));TextView temperature=text(WeatherData.value(current.opt("temperature_2m"),"°C"),48,mint);temperature.setTypeface(null,Typeface.BOLD);add(forecast,temperature);add(forecast,text(WeatherData.condition(current.optInt("weather_code",-1)),20,ink));
        add(forecast,text("Feels like "+WeatherData.value(current.opt("apparent_temperature"),"°C")+"\nWind "+WeatherData.value(current.opt("wind_speed_10m")," km/h")+" · Humidity "+WeatherData.value(current.opt("relative_humidity_2m"),"%"),14,ink));
        add(forecast,text("Current forecast · "+current.getString("time").replace('T',' ') +" "+result.optString("timezone_abbreviation",result.optString("timezone")),12,muted));
        JSONObject daily=result.optJSONObject("daily");JSONArray dates=daily==null?null:daily.optJSONArray("time");
        if(dates!=null)for(int i=0;i<Math.min(3,dates.length());i++){String date=dates.optString(i);try{SimpleDateFormat source=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT),format=new SimpleDateFormat("EEE, MMM d",Locale.getDefault());source.setTimeZone(TimeZone.getTimeZone("UTC"));format.setTimeZone(TimeZone.getTimeZone("UTC"));date=format.format(source.parse(date));}catch(java.text.ParseException ignored){}TextView day=text(date+"\n"+WeatherData.condition(integer(daily,"weather_code",i))+" · "+WeatherData.value(item(daily,"temperature_2m_max",i),"°")+" / "+WeatherData.value(item(daily,"temperature_2m_min",i),"°")+"\nRain chance "+WeatherData.value(item(daily,"precipitation_probability_max",i),"%"),14,ink);day.setPadding(0,dp(12),0,dp(12));add(forecast,day);}
    }
    private Object item(JSONObject daily,String key,int i){JSONArray a=daily.optJSONArray(key);return a==null?null:a.opt(i);}
    private int integer(JSONObject daily,String key,int i){Object n=item(daily,key,i);return n instanceof Number?((Number)n).intValue():-1;}
}
