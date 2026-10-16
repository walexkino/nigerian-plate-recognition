package com.akinwale.platelab;

import android.content.Context;
import android.net.*;
import android.os.*;
import android.widget.TextView;

/** Uses Android's validated internet state, rather than Wi-Fi association alone. */
final class ConnectionStatus implements AutoCloseable {
    private final ConnectivityManager connectivity;
    private final TextView label;
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean started;
    private Network observed;
    private final ConnectivityManager.NetworkCallback callback=new ConnectivityManager.NetworkCallback(){
        @Override public void onAvailable(Network network){main.post(()->{if(!started)return;if(Build.VERSION.SDK_INT>=24){observed=network;render(null);}else refreshLegacy();});}
        @Override public void onLost(Network network){main.post(()->{if(!started)return;if(Build.VERSION.SDK_INT>=24){if(network.equals(observed)){observed=null;render(null);}}else refreshLegacy();});}
        @Override public void onCapabilitiesChanged(Network network,NetworkCapabilities capabilities){main.post(()->{if(!started)return;if(Build.VERSION.SDK_INT>=24){if(network.equals(observed))render(capabilities);}else refreshLegacy();});}
        @Override public void onUnavailable(){main.post(()->{if(started)render(null);});}
    };
    ConnectionStatus(Context context,TextView label){this.connectivity=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);this.label=label;}
    void start(){
        if(started)return;started=true;
        try{if(Build.VERSION.SDK_INT>=24)connectivity.registerDefaultNetworkCallback(callback);else connectivity.registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),callback);}
        catch(RuntimeException unavailable){/* Resume still checks the current connection. */}
        refresh();
    }
    // API 23's callback observes all networks; re-read the default after framework caches settle.
    private final Runnable legacyRefresh=this::refresh;
    private void refreshLegacy(){refresh();main.removeCallbacks(legacyRefresh);main.postDelayed(legacyRefresh,500);}
    void refresh(){main.post(()->{
        if(!started)return;
        observed=connectivity.getActiveNetwork();
        render(connectivity.getNetworkCapabilities(observed));
    });}
    private void render(NetworkCapabilities caps){
        boolean online=caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        label.setText(online?"● Online":"● Offline");
        boolean dark=label.getContext().getSharedPreferences("appearance",Context.MODE_PRIVATE).getBoolean("dark",(label.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES);
        label.setTextColor(online?(dark?0xff65d59a:0xff187a43):(dark?0xffff8d94:0xffb52d38));
        label.setContentDescription(online?"Online. Internet access available.":"Offline. On-device recognition still works.");
    }
    @Override public void close(){if(!started)return;started=false;try{connectivity.unregisterNetworkCallback(callback);}catch(RuntimeException ignored){}main.removeCallbacksAndMessages(null);}
}
