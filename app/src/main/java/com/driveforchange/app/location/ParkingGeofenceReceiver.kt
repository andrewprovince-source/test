package com.driveforchange.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent

/**
 * Fires when the phone leaves the spot where the car was last parked — usually the start of
 * the next drive. It can't tell driving away from walking away, so it starts the tracking
 * service unconfirmed, and [LocationTrackingService] waits for the activity classifier to
 * confirm a drive before crediting any miles.
 */
class ParkingGeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            TrackingLog.log(context, "Geofence error: ${GeofenceStatusCodes.getStatusCodeString(event.errorCode)}")
            return
        }
        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            TrackingLog.log(context, "Left the parking spot")
            LocationTrackingController.startTrackingDrive(context, reason = "left parking spot", inVehicle = false)
        }
    }
}
