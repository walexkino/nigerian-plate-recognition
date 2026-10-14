package com.akinwale.platelab;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.core.view.*;

/** A first-run welcome and a tour of real controls. No media, camera, or export actions run here. */
final class OnboardingTour {
    static final String PREFERENCES="onboarding", COMPLETED="completed";
    static final int WELCOME=-1;
    static final class Step {
        final View target;
        final String title,copy;
        Step(View target,String title,String copy){this.target=target;this.title=title;this.copy=copy;}
    }
    private final Activity activity;
    private final FrameLayout host;
    private final ScrollView workspace;
    private final Step[] steps;
    private final boolean dark;
    private final Runnable finished;
    private final int originalScrollY;
    private FrameLayout layer;
    private Spotlight spotlight;
    private ViewGroup card;
    private int position=WELCOME;
    private int safeTop=0,safeBottom=0,safeLeft=0,safeRight=0;
    private final View.OnLayoutChangeListener layoutListener=(v,l,t,r,b,ol,ot,or,ob)->{
        if(layer!=null&&position>=0&&(r-l!=or-ol||b-t!=ob-ot))layer.post(this::positionCard);
    };

    OnboardingTour(Activity activity,FrameLayout host,ScrollView workspace,boolean dark,Step[] steps,Runnable finished){
        this.activity=activity;this.host=host;this.workspace=workspace;this.dark=dark;this.steps=steps;this.finished=finished;this.originalScrollY=workspace.getScrollY();
    }
    boolean isShowing(){return layer!=null;}
    int position(){return position;}
    private int dp(float n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private int ink(){return dark?0xffedf2ef:0xff172720;}
    private int muted(){return dark?0xffafc0b6:0xff596b61;}
    private int accent(){return dark?0xffb5e7c2:0xff206346;}
    private int panel(){return dark?0xff1b2924:Color.WHITE;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout column(){LinearLayout c=new LinearLayout(activity);c.setOrientation(LinearLayout.VERTICAL);return c;}
    private TextView text(String value,int size,int color){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);return t;}
    private void gap(LinearLayout p,int size){p.addView(new View(activity),new LinearLayout.LayoutParams(1,dp(size)));}
    private Button action(String label,boolean primary,Runnable click){
        Button b=new Button(activity);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setMinHeight(dp(48));b.setPadding(dp(16),dp(10),dp(16),dp(10));b.setTextColor(primary?(dark?0xff163323:Color.WHITE):ink());
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3067bd90),shape(primary?accent():(dark?0xff293d32:0xffeaf2ec),14),null));b.setOnClickListener(v->click.run());return b;
    }
    private boolean animate(){return Settings.Global.getFloat(activity.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1)>0;}
    void show(int requestedPosition){
        dispose();position=Math.max(WELCOME,Math.min(steps.length-1,requestedPosition));
        layer=new FrameLayout(activity);layer.setClickable(true);layer.setFocusable(true);layer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        workspace.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        host.addView(layer,new FrameLayout.LayoutParams(-1,-1));host.addOnLayoutChangeListener(layoutListener);
        ViewCompat.setOnApplyWindowInsetsListener(layer,(view,insets)->{
            androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());safeTop=bars.top;safeBottom=bars.bottom;safeLeft=bars.left;safeRight=bars.right;
            if(position==WELCOME&&card!=null)layer.setPadding(safeLeft,safeTop,safeRight,safeBottom);else layer.post(this::positionCard);return insets;
        });
        ViewCompat.requestApplyInsets(layer);drawPage();
    }
    private void drawPage(){
        layer.removeAllViews();layer.setPadding(0,0,0,0);spotlight=null;
        if(position==WELCOME)welcome();else step();
        if(animate()){card.setAlpha(0);card.animate().alpha(1).setDuration(180).start();}
    }
    private void welcome(){
        layer.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{dark?0xff0d1917:0xfff8fbf9,dark?0xff19382c:0xffe2f1e8}));
        layer.setPadding(safeLeft,safeTop,safeRight,safeBottom);
        LinearLayout page=column();card=page;
        ScrollView intro=new ScrollView(activity);intro.setFillViewport(true);intro.setClipToPadding(false);intro.setPadding(dp(24),dp(20),dp(24),dp(12));page.addView(intro,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content=column();content.setGravity(Gravity.CENTER_VERTICAL);FrameLayout.LayoutParams bodyParams=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER_HORIZONTAL);intro.addView(content,bodyParams);
        // Keep the welcome composition comfortable on tablets, with scroll access at large font scales.
        intro.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int width=Math.min(Math.max(1,r-l-intro.getPaddingLeft()-intro.getPaddingRight()),dp(540));if(content.getLayoutParams().width!=width){content.getLayoutParams().width=width;content.requestLayout();}});
        LinearLayout brand=new LinearLayout(activity);brand.setGravity(Gravity.CENTER_VERTICAL);ImageView logo=new ImageView(activity);logo.setImageResource(R.mipmap.ic_launcher_image);logo.setContentDescription("PlateLab logo");brand.addView(logo,new LinearLayout.LayoutParams(dp(42),dp(42)));
        TextView name=text("PlateLab",22,ink());name.setTypeface(null,Typeface.BOLD);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-2,-2);np.setMarginStart(dp(12));brand.addView(name,np);content.addView(brand);gap(content,20);
        TextView eyebrow=text("YOUR FIRST LOOK",11,accent());eyebrow.setLetterSpacing(.16f);content.addView(eyebrow);gap(content,12);
        content.addView(new PlateIllustration(activity),new LinearLayout.LayoutParams(-1,dp(190)));gap(content,22);
        TextView heading=text("See the plate.\nRead the evidence.",30,ink());heading.setTypeface(null,Typeface.BOLD);ViewCompat.setAccessibilityHeading(heading,true);content.addView(heading);gap(content,14);
        content.addView(text("Welcome to your recognition workspace. Start with a photo or your phone camera, then review every character with confidence in the evidence.",15,muted()));gap(content,20);
        TextView capabilities=text("PHOTO IMPORT  ·  LIVE CAMERA  ·  YOUR REVIEW",11,accent());capabilities.setLetterSpacing(.04f);content.addView(capabilities);
        FrameLayout footer=new FrameLayout(activity);LinearLayout actions=column();actions.setPadding(dp(24),dp(12),dp(24),dp(20));footer.addView(actions,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER_HORIZONTAL));
        footer.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int width=Math.min(r-l,dp(588));if(actions.getLayoutParams().width!=width){actions.getLayoutParams().width=width;actions.requestLayout();}});
        actions.addView(action("Take a quick tour",true,()->{position=0;drawPage();}),new LinearLayout.LayoutParams(-1,-2));gap(actions,8);
        actions.addView(action("Skip for now",false,this::complete),new LinearLayout.LayoutParams(-1,-2));gap(actions,10);
        TextView foot=text("You can replay the tour from About.\n© 2026 Akinwale",12,muted());foot.setGravity(Gravity.CENTER);actions.addView(foot);page.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        layer.addView(card,new FrameLayout.LayoutParams(-1,-1));heading.requestFocus();layer.announceForAccessibility("Welcome to PlateLab. Take a quick tour or skip for now.");
    }
    private void step(){
        layer.setBackgroundColor(Color.TRANSPARENT);spotlight=new Spotlight(activity);layer.addView(spotlight,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout shell=column();card=shell;card.setBackground(shape(panel(),22));card.setElevation(dp(16));card.setClipToOutline(true);card.setPadding(dp(20),dp(20),dp(20),dp(16));
        ScrollView explanation=new ScrollView(activity);LinearLayout content=column();explanation.addView(content);shell.addView(explanation,new LinearLayout.LayoutParams(-1,0,1));
        TextView progress=text("QUICK TOUR  ·  "+(position+1)+" OF "+steps.length,11,accent());progress.setLetterSpacing(.1f);content.addView(progress);gap(content,12);
        LinearLayout dots=new LinearLayout(activity);dots.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        for(int i=0;i<steps.length;i++){View dot=new View(activity);dot.setBackground(shape(i<=position?accent():(dark?0xff3a4c42:0xffdce8df),3));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(3),1);if(i<steps.length-1)lp.setMarginEnd(dp(5));dots.addView(dot,lp);}content.addView(dots);gap(content,18);
        Step step=steps[position];TextView heading=text(step.title,23,ink());heading.setTypeface(null,Typeface.BOLD);ViewCompat.setAccessibilityHeading(heading,true);content.addView(heading);gap(content,10);
        content.addView(text(step.copy,14,muted()));gap(content,20);
        shell.addView(action(position==steps.length-1?"Finish tour":"Next",true,()->{if(position==steps.length-1)complete();else{position++;drawPage();}}),new LinearLayout.LayoutParams(-1,-2));gap(shell,8);
        LinearLayout navigation=new LinearLayout(activity);Button back=action("Back",false,this::back),skip=action("Skip tour",false,this::complete);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-2,1);bp.setMarginEnd(dp(8));navigation.addView(back,bp);navigation.addView(skip,new LinearLayout.LayoutParams(0,-2,1));shell.addView(navigation);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(Math.min(dp(560),Math.max(1,host.getWidth()-dp(32))),-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);layer.addView(card,cp);
        layer.post(()->{if(!isShowing()||position<0)return;scrollToTarget();layer.post(this::positionCard);});heading.requestFocus();layer.announceForAccessibility("Step "+(position+1)+" of "+steps.length+". "+step.title+". "+step.copy);
    }
    private void scrollToTarget(){
        View target=steps[position].target;int[] at=new int[2],scroll=new int[2];target.getLocationInWindow(at);workspace.getLocationInWindow(scroll);
        int available=workspace.getHeight()-workspace.getPaddingTop()-workspace.getPaddingBottom();int y=workspace.getScrollY()+at[1]-scroll[1]-workspace.getPaddingTop()-available/4;
        workspace.scrollTo(0,Math.max(0,y));
    }
    private void positionCard(){
        if(!isShowing()||position<0||card==null||spotlight==null)return;
        int width=layer.getWidth(),height=layer.getHeight();if(width==0||height==0)return;
        int[] at=new int[2],origin=new int[2];steps[position].target.getLocationInWindow(at);layer.getLocationInWindow(origin);
        RectF focus=new RectF(at[0]-origin[0]-dp(6),at[1]-origin[1]-dp(6),at[0]-origin[0]+steps[position].target.getWidth()+dp(6),at[1]-origin[1]+steps[position].target.getHeight()+dp(6));
        int minY=safeTop+dp(16),maxY=height-safeBottom-dp(16);
        if(!focus.intersect(safeLeft+dp(8),minY,width-safeRight-dp(8),maxY))focus.setEmpty();
        int cardWidth=Math.max(1,Math.min(dp(560),width-safeLeft-safeRight-dp(32)));
        card.measure(View.MeasureSpec.makeMeasureSpec(cardWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));int natural=card.getMeasuredHeight();
        int below=Math.max(0,maxY-(int)focus.bottom-dp(18)),above=Math.max(0,(int)focus.top-minY-dp(18));
        boolean useBelow=below>=natural||below>=above;int available=useBelow?below:above;
        // In compact landscape or very large text, let the explanatory card scroll across the full safe height.
        boolean compact=focus.isEmpty()||available<dp(220);if(compact){focus.setEmpty();available=Math.max(1,maxY-minY);}
        int cardHeight=Math.min(natural,available);FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)card.getLayoutParams();lp.width=cardWidth;lp.height=cardHeight;lp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
        lp.topMargin=compact?minY:useBelow?(int)focus.bottom+dp(18):(int)focus.top-dp(18)-cardHeight;card.setLayoutParams(lp);spotlight.focus=focus;spotlight.invalidate();
    }
    void back(){if(!isShowing())return;if(position>=0){position--;drawPage();}else complete();}
    private void complete(){activity.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE).edit().putBoolean(COMPLETED,true).apply();dispose();workspace.scrollTo(0,originalScrollY);finished.run();}
    void dispose(){
        host.removeOnLayoutChangeListener(layoutListener);
        if(layer!=null){layer.animate().cancel();host.removeView(layer);layer=null;workspace.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);}
    }
    private final class Spotlight extends View {
        RectF focus=new RectF();private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Spotlight(Context context){super(context);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);Path mask=new Path();mask.setFillType(Path.FillType.EVEN_ODD);mask.addRect(0,0,getWidth(),getHeight(),Path.Direction.CW);if(!focus.isEmpty())mask.addRoundRect(focus,dp(16),dp(16),Path.Direction.CW);
            paint.setStyle(Paint.Style.FILL);paint.setColor(dark?0xd909110d:0xc9233c2e);canvas.drawPath(mask,paint);
            if(!focus.isEmpty()){paint.setColor(accent());paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));canvas.drawRoundRect(focus,dp(16),dp(16),paint);}
        }
    }
    /** An explicitly labelled illustration, never an OCR reading or accuracy claim. */
    private final class PlateIllustration extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path lettering=new Path(),caption=new Path();
        PlateIllustration(Context context){
            super(context);setContentDescription("Illustration of sample plate ABC 123 DE inside a scanner frame. Not a recognition result.");
            // Draw letter outlines with the artwork so rendering does not depend on a cached text layer.
            Paint font=new Paint(Paint.ANTI_ALIAS_FLAG);font.setTypeface(Typeface.create("monospace",Typeface.BOLD));font.setTextSize(27);String sample="ABC 123 DE";font.getTextPath(sample,0,sample.length(),165-font.measureText(sample)/2,103,lettering);
            font.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));font.setTextSize(9);String label="SAMPLE ILLUSTRATION";font.getTextPath(label,0,label.length(),160-font.measureText(label)/2,173,caption);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);canvas.save();float scale=Math.min(getWidth()/320f,getHeight()/190f);canvas.translate((getWidth()-320*scale)/2,(getHeight()-190*scale)/2);canvas.scale(scale,scale);
            paint.setStyle(Paint.Style.FILL);paint.setColor(dark?0xff224936:0xffcee8d7);canvas.drawRoundRect(10,10,310,180,28,28,paint);
            paint.setColor(dark?0xff173425:0xffeaf6ee);canvas.drawRoundRect(30,30,290,160,20,20,paint);
            paint.setColor(0xfff7fbf8);canvas.drawRoundRect(51,61,269,125,10,10,paint);paint.setColor(0xff206346);canvas.drawRoundRect(51,61,64,125,6,6,paint);canvas.drawPath(lettering,paint);
            paint.setColor(accent());paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);float[][] corners={{42,53,42,40,59,40},{261,40,279,40,279,53},{42,133,42,146,59,146},{261,146,279,146,279,133}};for(float[] c:corners){Path path=new Path();path.moveTo(c[0],c[1]);path.lineTo(c[2],c[3]);path.lineTo(c[4],c[5]);canvas.drawPath(path,paint);}
            paint.setStyle(Paint.Style.FILL);paint.setColor(muted());canvas.drawPath(caption,paint);canvas.restore();
        }
    }
}
