package com.akinwale.platelab;

import android.content.Context;
import android.util.AtomicFile;
import android.security.keystore.*;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Text-only history in app-private storage; source photos are never copied into history. */
public final class ReadingStore {
    public static final int LIMIT=200;
    private static final byte[] MAGIC={'P','L','H',1};
    private static final String KEY="PlateLab.history.v1";
    private final AtomicFile file;
    private boolean writable;
    public ReadingStore(Context context){file=new AtomicFile(new File(context.getFilesDir(),"readings.json"));}
    private javax.crypto.SecretKey key(boolean create) throws GeneralSecurityException,IOException {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(KEY))return (javax.crypto.SecretKey)store.getKey(KEY,null);
        if(!create)throw new KeyStoreException("History encryption key is unavailable");
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    public synchronized List<JSONObject> load() throws IOException,JSONException {
        writable=false;
        List<JSONObject> result=new ArrayList<>();
        // openRead recovers an interrupted AtomicFile write before checking for an empty store.
        byte[] bytes;
        try(InputStream in=file.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>4*1024*1024)throw new IOException("History is too large");out.write(buffer,0,n);}bytes=out.toByteArray();
        }catch(FileNotFoundException missing){if(file.getBaseFile().exists()||new File(file.getBaseFile()+".bak").exists())throw missing;writable=true;return result;}
        boolean encrypted=bytes.length>=4&&Arrays.equals(Arrays.copyOf(bytes,4),MAGIC);
        if(encrypted){
            try{if(bytes.length<32)throw new GeneralSecurityException("Truncated history");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,bytes,4,12));cipher.updateAAD(MAGIC);bytes=cipher.doFinal(bytes,16,bytes.length-16);}
            catch(GeneralSecurityException e){throw new IOException("History could not be decrypted. Existing data was preserved.",e);}
        }else if(bytes.length==0||bytes[0]!='['){throw new IOException("Unrecognized history format. Existing data was preserved.");}
        JSONArray data=new JSONArray(new String(bytes,StandardCharsets.UTF_8));
        for(int i=Math.max(0,data.length()-LIMIT);i<data.length();i++)result.add(data.getJSONObject(i));
        writable=true;
        if(!encrypted){try{save(result);}catch(IOException error){writable=false;throw error;}}
        return result;
    }
    public synchronized void save(List<JSONObject> records) throws IOException {
        if(!writable)throw new IOException("History unavailable; export new readings before closing. Existing data was preserved.");
        byte[] bytes;
        try{
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));cipher.updateAAD(MAGIC);
            List<JSONObject> retained=records.subList(Math.max(0,records.size()-LIMIT),records.size());
            byte[] plain=new JSONArray(retained).toString().getBytes(StandardCharsets.UTF_8);
            if(plain.length>4*1024*1024-64)throw new IOException("History is too large; export the readings instead.");
            ByteArrayOutputStream data=new ByteArrayOutputStream();data.write(MAGIC);data.write(cipher.getIV());data.write(cipher.doFinal(plain));bytes=data.toByteArray();
        }catch(GeneralSecurityException e){throw new IOException("Could not encrypt history",e);}
        FileOutputStream out=null;
        try{out=file.startWrite();out.write(bytes);file.finishWrite(out);}
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
