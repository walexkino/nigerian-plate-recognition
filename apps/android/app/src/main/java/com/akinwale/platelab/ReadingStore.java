package com.akinwale.platelab;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Text-only history in app-private storage; source photos are never copied into history. */
public final class ReadingStore {
    public static final int LIMIT=200;
    private final AtomicFile file;
    public ReadingStore(Context context){file=new AtomicFile(new File(context.getFilesDir(),"readings.json"));}
    public synchronized List<JSONObject> load() throws IOException,JSONException {
        List<JSONObject> result=new ArrayList<>(); if(!file.getBaseFile().exists())return result;
        JSONArray data=new JSONArray(new String(file.readFully(),StandardCharsets.UTF_8));
        for(int i=Math.max(0,data.length()-LIMIT);i<data.length();i++)result.add(data.getJSONObject(i));return result;
    }
    public synchronized void save(List<JSONObject> records) throws IOException {
        FileOutputStream out=null;
        try{out=file.startWrite();out.write(new JSONArray(records).toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}
        catch(IOException e){if(out!=null)file.failWrite(out);throw e;}
    }
    public static String timestamp(long milliseconds){java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.ROOT);format.setTimeZone(TimeZone.getTimeZone("UTC"));return format.format(new Date(milliseconds));}
    public static JSONObject reading(String source,String type,List<PlateRules.Reading> candidates,List<PlateRules.Reading> observations,long milliseconds) throws JSONException {
        JSONObject r=new JSONObject();r.put("id",UUID.randomUUID().toString());r.put("source",source);r.put("input_type",type);
        r.put("captured_at",timestamp(System.currentTimeMillis()));
        r.put("engine","ML Kit Latin text recognition");r.put("ocr_ms",milliseconds);r.put("calibrated",false);r.put("reviewed",false);
        r.put("manual_transcription",JSONObject.NULL);r.put("selected_index",candidates.isEmpty()?-1:0);
        r.put("candidates",encode(candidates));r.put("observations",encode(observations));return r;
    }
    private static JSONArray encode(List<PlateRules.Reading> readings) throws JSONException {
        JSONArray a=new JSONArray();for(PlateRules.Reading reading:readings){JSONObject r=new JSONObject();r.put("text",reading.text);r.put("raw",reading.raw);r.put("confidence",reading.confidence==null?JSONObject.NULL:reading.confidence);JSONArray b=new JSONArray();for(float x:reading.box)b.put(x);r.put("box",b);a.put(r);}return a;
    }
    public static String selected(JSONObject record){if(record==null)return "";String corrected=record.optString("manual_transcription","");if(!record.isNull("manual_transcription")&&!corrected.isEmpty())return corrected;int index=record.optInt("selected_index",-1);JSONObject c=record.optJSONArray("candidates").optJSONObject(index);return c==null?"":c.optString("text");}
    public static JSONObject report(List<JSONObject> records) throws JSONException {JSONObject report=new JSONObject();report.put("project","PlateLab");report.put("platform","Android");report.put("version",BuildConfig.VERSION_NAME);report.put("exported_at",new Date().toString());report.put("note","Uncalibrated OCR scores; review each reading. No custom training or missing-character recovery.");report.put("records",new JSONArray(records));return report;}
}
