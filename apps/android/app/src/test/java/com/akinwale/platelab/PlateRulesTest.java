package com.akinwale.platelab;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class PlateRulesTest {
    private PlateRules.Reading r(String text,float x,float y,float w,float h){return new PlateRules.Reading(text,.9f,new float[]{x,y,w,h});}
    @Test public void normalizationDoesNotGuessHiddenCharacters(){assertEquals("ABC123DE",PlateRules.normalize("ÀBC 123-DE"));assertEquals("ABCO23DE",PlateRules.normalize("ABC O23 DE"));assertEquals("ABC123DE",PlateRules.normalize("АВС 123 DЕ"));}
    @Test public void formatChecksKeepOnlyPlateCandidates(){assertTrue(PlateRules.plateLike("ABC 123 DE"));assertFalse(PlateRules.plateLike("LAGOS"));assertFalse(PlateRules.plateLike("ABC"));assertFalse(PlateRules.plateLike("ABC 1 DE"));}
    @Test public void unreadableTextDoesNotProduceFabricatedPlate(){assertTrue(PlateRules.candidates(Arrays.asList(r("COROLLA",.1f,.1f,.2f,.05f),r("LAGOS",.1f,.2f,.1f,.05f))).isEmpty());}
    @Test public void adjacentFragmentsJoinOnSameLine(){List<PlateRules.Reading> result=PlateRules.candidates(Arrays.asList(r("ABC",.1f,.2f,.1f,.05f),r("123 DE",.21f,.2f,.2f,.05f)));assertEquals("ABC123DE",result.get(0).text);}
    @Test public void differentLinesDoNotJoin(){assertTrue(PlateRules.candidates(Arrays.asList(r("ABC",.1f,.1f,.1f,.05f),r("123 DE",.21f,.4f,.2f,.05f))).isEmpty());}
    @Test public void separateVehiclesDoNotMerge(){assertEquals(2,PlateRules.candidates(Arrays.asList(r("ABC123DE",.1f,.2f,.2f,.05f),r("ABC123DE",.7f,.2f,.2f,.05f))).size());}
    @Test public void duplicateOcrIsNotRepeatedCandidate(){assertEquals(1,PlateRules.candidates(Arrays.asList(r("ABC123DE",.1f,.2f,.2f,.05f),r("ABC 123 DE",.1f,.2f,.2f,.05f))).size());}
    @Test public void unavailableConfidenceRemainsUnavailable(){List<PlateRules.Reading> result=PlateRules.candidates(Collections.singletonList(new PlateRules.Reading("ABC 123 DE",null,new float[]{.1f,.1f,.5f,.1f})));assertNull(result.get(0).confidence);}
}
