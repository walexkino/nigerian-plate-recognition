package com.akinwale.platelab;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlateRegionTest {
    @Test public void touchCoordinatesAccountForLetterboxedSource(){assertArrayEquals(new float[]{.5f,.5f},PlateRegion.point(150,150,300,300,1920,1080),.0001f);assertArrayEquals(new float[]{0,0},PlateRegion.point(0,0,300,300,1920,1080),.0001f);}
    @Test public void reverseDragCreatesTheSameClippedRegion(){assertArrayEquals(new float[]{.2f,.3f,.6f,.4f},PlateRegion.rectangle(new float[]{.8f,.7f},new float[]{.2f,.3f}),.0001f);}
    @Test public void cropOcrBoxesReturnToOriginalEvidenceCoordinates(){assertArrayEquals(new float[]{.3f,.45f,.1f,.1f},PlateRegion.map(new float[]{.1f,.25f,.2f,.5f},new float[]{.25f,.4f,.5f,.2f}),.0001f);}
}
