package com.example.attendify.geofence;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

public class GeofenceReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event.hasError()) return;

        int transition = event.getGeofenceTransition();

        if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            Log.d("GEOFENCE", "Entered classroom area");
        }

        if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            Log.d("GEOFENCE", "Exited classroom area");
        }
    }
}
