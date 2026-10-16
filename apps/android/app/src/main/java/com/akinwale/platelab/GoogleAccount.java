package com.akinwale.platelab;

import android.content.Context;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

/** Firebase owns identity and tokens; plate evidence never passes through this class. */
final class GoogleAccount {
    static FirebaseAuth auth(Context context) {
        FirebaseApp app;
        try { app=FirebaseApp.getInstance(); }
        catch (IllegalStateException missing) { app=FirebaseApp.initializeApp(context.getApplicationContext()); }
        return app==null?null:FirebaseAuth.getInstance(app);
    }
    static String webClientId(Context context) {
        int id=context.getResources().getIdentifier("default_web_client_id","string",context.getPackageName());
        return id==0?"":context.getString(id);
    }
    static String label(Context context) {
        FirebaseAuth auth=auth(context);
        return auth!=null&&auth.getCurrentUser()!=null?"Account · Signed in":"Account · Guest";
    }
}
