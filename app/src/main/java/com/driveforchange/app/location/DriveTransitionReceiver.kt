package com.driveforchange.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * Wakes the app when a drive starts or ends.
 *
 * This is the primary driver of background tracking: Play services delivers these
 * transitions even when the app has been closed or swiped away, which is what lets mileage
 * be tracked all day without opening the app. Starting a foreground service from here is
 * allowed on Android 12+, which exempts activity recognition transition events from its
 * background foreground-service start restrictions.
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
                DrivingActivityState.markDrivingStarted()
                LocationTrackingController.startTrackingDrive(context)
            }
            ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                DrivingActivityState.markDrivingStopped()
                LocationTrackingService.stop(context.applicationContext)
            }
        }
    }
}
