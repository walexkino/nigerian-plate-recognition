package com.akinwale.platelab;
import android.view.*;
import android.widget.TextView;
final class TourTestSupport {
    static View label(View view,String text){if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=label(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    static void dismissWelcome(MainActivity activity){View skip=label(activity.getWindow().getDecorView(),"Skip for now");if(skip!=null)skip.performClick();}
}
