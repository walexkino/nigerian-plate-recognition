package com.akinwale.platelab;

/** Geometry in source-image coordinates, independent of preview zoom/letterboxing. */
final class PlateRegion {
    static float[] point(float x,float y,float vw,float vh,float iw,float ih){float scale=Math.min(vw/iw,vh/ih);return new float[]{clamp((x-(vw-iw*scale)/2)/(iw*scale)),clamp((y-(vh-ih*scale)/2)/(ih*scale))};}
    private static float clamp(float x){return Math.max(0,Math.min(1,x));}
    static float[] rectangle(float[] a,float[] b){return new float[]{Math.min(a[0],b[0]),Math.min(a[1],b[1]),Math.abs(a[0]-b[0]),Math.abs(a[1]-b[1])};}
    static float[] map(float[] b,float[] r){return r==null?b.clone():new float[]{r[0]+b[0]*r[2],r[1]+b[1]*r[3],b[2]*r[2],b[3]*r[3]};}
}
