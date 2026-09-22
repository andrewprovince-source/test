package com.driveforchange.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * Wakes the app when Android's activity classifier says a drive started or ended. Play
 * services delivers these even when the app is closed, and Android 12+ allows a location
 * foreground service to be started in response to them.
 */
class DriveTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return

        // Events arrive oldest first; only the final state for this batch matters.
        val latestVehicleEvent = result.transitionEvents
            .lastOrNull { it.activityType == DetectedActivity.IN_VEHICLE }
            ?: return

        when (latestVehicleEvent.transitionType) {
            ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                TrackingLog.log(context, "Classifier: entered a vehicle")
                LocationTrackingController.startTrackingDrive(context, reason = "entered vehicle", inVehicle = true)
            }
            ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                TrackingLog.log(context, "Classifier: left the vehicle")
                DrivingActivityState.markDrivingStopped()
                LocationTrackingService.stop(context, reason = "left vehicle")
            }
        }
    }
}
