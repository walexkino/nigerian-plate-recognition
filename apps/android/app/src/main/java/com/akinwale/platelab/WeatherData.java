package com.akinwale.platelab;

import org.json.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Forecast formatting is separate from plate evidence and never writes to ReadingStore. */
final class WeatherData {
    static double rounded(double n){return Math.round(n*100)/100.0;}
    static void coordinates(double lat,double lon){if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new IllegalArgumentException("Choose a valid location.");}
    static String forecastUrl(double lat,double lon){coordinates(lat,lon);return String.format(Locale.ROOT,"https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=3&temperature_unit=celsius&wind_speed_unit=kmh",rounded(lat),rounded(lon));}
    static String searchUrl(String query){String q=query.trim();if(q.length()<2||q.length()>100)throw new IllegalArgumentException("Enter between 2 and 100 characters of a city name.");try{return "https://photon.komoot.io/api/?q="+URLEncoder.encode(q,StandardCharsets.UTF_8.name())+"&limit=5&lang=en";}catch(java.io.UnsupportedEncodingException e){throw new AssertionError(e);}}
    static JSONArray places(JSONObject response) throws JSONException {
        JSONArray old=response.optJSONArray("results");if(old!=null)return old;
        JSONArray output=new JSONArray(),features=response.optJSONArray("features");
        if(features!=null)for(int i=0;i<Math.min(5,features.length());i++){JSONObject f=features.getJSONObject(i),p=f.optJSONObject("properties"),g=f.optJSONObject("geometry");JSONArray c=g==null?null:g.optJSONArray("coordinates");if(p==null||c==null||c.length()<2)continue;
            java.util.LinkedHashSet<String> parts=new java.util.LinkedHashSet<>();String address=(p.optString("housenumber")+" "+p.optString("street")).trim();for(String v:new String[]{p.optString("name"),address,p.optString("district"),p.optString("city",p.optString("town",p.optString("village"))),p.optString("state"),p.optString("country")})if(!v.isEmpty())parts.add(v);
            StringBuilder name=new StringBuilder();for(String v:parts){if(name.length()>0)name.append(", ");name.append(v);}JSONObject place=new JSONObject();place.put("name",name.toString());place.put("longitude",c.optDouble(0,Double.NaN));place.put("latitude",c.optDouble(1,Double.NaN));output.put(place);
        }return output;
    }
    static String value(Object n,String unit){if(!(n instanceof Number)||!Double.isFinite(((Number)n).doubleValue()))return "—";return Math.round(((Number)n).doubleValue())+unit;}
    static JSONObject current(JSONObject response) throws JSONException {JSONObject c=response.getJSONObject("current");Object temp=c.opt("temperature_2m");if(response.optBoolean("error")||!(temp instanceof Number)||!Double.isFinite(((Number)temp).doubleValue())||!c.optString("time").matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}.*"))throw new JSONException("Weather data is unavailable.");return c;}
    static String condition(int code){switch(code){case 0:return "Clear sky";case 1:return "Mainly clear";case 2:return "Partly cloudy";case 3:return "Overcast";case 45:case 48:return "Fog";case 51:return "Light drizzle";case 53:return "Drizzle";case 55:return "Heavy drizzle";case 56:case 57:return "Freezing drizzle";case 61:return "Light rain";case 63:return "Rain";case 65:return "Heavy rain";case 66:case 67:return "Freezing rain";case 71:return "Light snow";case 73:return "Snow";case 75:return "Heavy snow";case 77:return "Snow grains";case 80:return "Light showers";case 81:return "Showers";case 82:return "Heavy showers";case 85:case 86:return "Snow showers";case 95:return "Thunderstorm";case 96:case 99:return "Thunderstorm with hail";default:return "Conditions unavailable";}}
}
