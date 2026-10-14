package com.akinwale.platelab;

import java.text.Normalizer;
import java.util.*;

/** Format filtering is a heuristic, never a registration check or missing-character recovery. */
public final class PlateRules {
    private PlateRules() {}
    public static String normalize(String text) {
        String t=Normalizer.normalize(text.toUpperCase(Locale.ROOT),Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        String from="АВЕКМНОРСТУХΑΒΕΖΗΙΚΜΝΟΡΤΥΧ", to="ABEKMHOPCTYXABEZHIKMNOPTYX";
        StringBuilder b=new StringBuilder();
        for(char c:t.toCharArray()){ int i=from.indexOf(c); if(i>=0)c=to.charAt(i); if(c>='A'&&c<='Z'||c>='0'&&c<='9')b.append(c); }
        return b.toString();
    }
    public static boolean plateLike(String text) {
        String t=normalize(text); return t.length()>=7&&t.length()<=9&&t.matches("[A-Z]{2,3}[0-9]{2,4}[A-Z]{1,3}");
    }
    public static boolean commonLayout(String text) {return text.matches("(?:[A-Z]{3}[0-9]{3}[A-Z]{2}|[A-Z]{2}[0-9]{3}[A-Z]{3})");}
    public static final class Reading {
        public final String raw, text;
        public final Float confidence;
        public final float[] box;
        public Reading(String raw,Float confidence,float[] box){this.raw=raw;this.text=normalize(raw);this.confidence=confidence;this.box=box.clone();}
    }
    public static List<Reading> candidates(List<Reading> observations) {
        List<Reading> expanded=new ArrayList<>(observations);
        for(Reading a:observations)for(Reading b:observations){
            if(a==b)continue;
            float gap=b.box[0]-(a.box[0]+a.box[2]);
            float overlap=Math.min(a.box[1]+a.box[3],b.box[1]+b.box[3])-Math.max(a.box[1],b.box[1]);
            if(b.box[0]<=a.box[0]||gap<-.005f||gap>Math.max(a.box[3],b.box[3])*1.6f||overlap<Math.min(a.box[3],b.box[3])*.5f)continue;
            String raw=a.raw+" "+b.raw;if(!plateLike(raw))continue;
            float x=Math.min(a.box[0],b.box[0]),y=Math.min(a.box[1],b.box[1]);
            Float score=a.confidence==null||b.confidence==null?null:Math.min(a.confidence,b.confidence);
            expanded.add(new Reading(raw,score,new float[]{x,y,Math.max(a.box[0]+a.box[2],b.box[0]+b.box[2])-x,Math.max(a.box[1]+a.box[3],b.box[1]+b.box[3])-y}));
        }
        List<Reading> result=new ArrayList<>();
        for(Reading r:expanded){if(!plateLike(r.text))continue; boolean duplicate=false;
            for(Reading old:result)if(old.text.equals(r.text)&&overlap(old.box,r.box)>.35f){duplicate=true;break;}
            if(!duplicate)result.add(r);
        }
        // OCR scores are uncalibrated. Missing scores are retained as unavailable, not invented.
        Collections.sort(result,(a,b)->{int byScore=Float.compare(b.confidence==null?-1:b.confidence,a.confidence==null?-1:a.confidence);return byScore!=0?byScore:Boolean.compare(commonLayout(b.text),commonLayout(a.text));});
        return result;
    }
    private static float overlap(float[] a,float[] b){float w=Math.max(0,Math.min(a[0]+a[2],b[0]+b[2])-Math.max(a[0],b[0]));float h=Math.max(0,Math.min(a[1]+a[3],b[1]+b[3])-Math.max(a[1],b[1]));return w*h/Math.max(.000000001f,Math.min(a[2]*a[3],b[2]*b[3]));}
}
