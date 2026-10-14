package com.akinwale.platelab;

import android.content.ContentResolver;
import android.graphics.*;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;

final class PhotoDecoder {
    static Bitmap decode(ContentResolver resolver,Uri uri) throws IOException {
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        try(InputStream in=resolver.openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
        if(options.outWidth<1||options.outHeight<1)throw new IOException("This image could not be decoded. Try a JPEG or PNG.");
        int longest=Math.max(options.outWidth,options.outHeight);options.inSampleSize=1;
        while(longest/options.inSampleSize>2560)options.inSampleSize*=2;
        options.inJustDecodeBounds=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap source;try(InputStream in=resolver.openInputStream(uri)){source=BitmapFactory.decodeStream(in,null,options);}
        if(source==null)throw new IOException("This image format is unavailable on this device. Try a JPEG or PNG.");
        int orientation=ExifInterface.ORIENTATION_NORMAL;
        try(InputStream in=resolver.openInputStream(uri)){orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}catch(IOException ignored){}
        Matrix m=new Matrix();switch(orientation){
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:m.setScale(-1,1);break;
            case ExifInterface.ORIENTATION_ROTATE_180:m.setRotate(180);break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:m.setScale(1,-1);break;
            case ExifInterface.ORIENTATION_TRANSPOSE:m.setRotate(90);m.postScale(-1,1);break;
            case ExifInterface.ORIENTATION_ROTATE_90:m.setRotate(90);break;
            case ExifInterface.ORIENTATION_TRANSVERSE:m.setRotate(270);m.postScale(-1,1);break;
            case ExifInterface.ORIENTATION_ROTATE_270:m.setRotate(270);break;
        }
        Bitmap upright=Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),m,true);if(upright!=source)source.recycle();
        float scale=Math.min(1f,1280f/Math.max(upright.getWidth(),upright.getHeight()));
        if(scale<1){Bitmap resized=Bitmap.createScaledBitmap(upright,Math.max(1,Math.round(upright.getWidth()*scale)),Math.max(1,Math.round(upright.getHeight()*scale)),true);upright.recycle();return resized;}return upright;
    }
}
