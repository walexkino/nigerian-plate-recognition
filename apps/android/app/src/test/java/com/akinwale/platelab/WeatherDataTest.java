package com.akinwale.platelab;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.*;
import java.util.Locale;
public class WeatherDataTest {
    @Test public void roundedCoordinatesAreLocaleIndependentAndLimitedToWeather(){Locale old=Locale.getDefault();try{Locale.setDefault(Locale.GERMANY);String url=WeatherData.forecastUrl(6.51234,-0.12345);assertTrue(url.startsWith("https://api.open-meteo.com/"));assertTrue(url.contains("latitude=6.51&longitude=-0.12"));assertTrue(url.contains("forecast_days=3"));assertTrue(WeatherData.forecastUrl(0,0).contains("latitude=0.00&longitude=0.00"));}finally{Locale.setDefault(old);}}
    @Test public void nullAndNonfiniteValuesNeverBecomeZeroWeather(){assertEquals("—",WeatherData.value(null,"%"));assertEquals("—",WeatherData.value(Double.NaN,"°"));assertEquals("0%",WeatherData.value(0,"%"));assertEquals("Thunderstorm",WeatherData.condition(95));assertEquals("Conditions unavailable",WeatherData.condition(-1));}
    @Test public void cityInputCannotInjectAdditionalQueryParameters(){String url=WeatherData.searchUrl("Lagos &count=100");assertTrue(url.contains("%26count%3D100"));assertTrue(url.endsWith("&limit=5&lang=en"));}
    @Test(expected=IllegalArgumentException.class) public void invalidLatitudeRejected(){WeatherData.forecastUrl(91,0);}
    @Test(expected=IllegalArgumentException.class) public void invalidLongitudeRejected(){WeatherData.forecastUrl(0,Double.NaN);}
}
