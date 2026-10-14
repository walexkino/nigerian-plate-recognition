package com.akinwale.platelab;
import android.graphics.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class RecognitionTest {
    @Test public void bundledModelRecognizesSyntheticPlate() throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(800,300,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.BLACK);paint.setTextSize(100);paint.setTypeface(Typeface.create("monospace",Typeface.BOLD));canvas.drawText("ABC 123 DE",85,180,paint);
        TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try{Text result=Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap,0)),30,TimeUnit.SECONDS);assertTrue(PlateRules.normalize(result.getText()).contains("ABC123DE"));}finally{recognizer.close();bitmap.recycle();}
    }
    @Test public void manualReviewPreservesOriginalOcrInSavedHistory() throws Exception {
        android.content.Context context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
        PlateRules.Reading candidate=new PlateRules.Reading("ABC 123 DE",.8f,new float[]{0,0,1,1});
        org.json.JSONObject record=ReadingStore.reading("Test fixture","photo",Collections.singletonList(candidate),Collections.singletonList(candidate),10);
        record.put("manual_transcription","ABC128DE");record.put("reviewed",true);
        ReadingStore store=new ReadingStore(context);java.util.List<org.json.JSONObject> previous=store.load();
        try{store.save(Collections.singletonList(record));org.json.JSONObject restored=store.load().get(0);assertEquals("ABC128DE",ReadingStore.selected(restored));assertEquals("ABC 123 DE",restored.getJSONArray("observations").getJSONObject(0).getString("raw"));assertFalse(restored.getBoolean("calibrated"));}finally{store.save(previous);}
    }
}
