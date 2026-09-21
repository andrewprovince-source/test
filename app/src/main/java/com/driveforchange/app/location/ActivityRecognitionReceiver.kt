package com.driveforchange.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * Backstop for [DriveTransitionReceiver]. Transition events are occasionally missed — a
 * short trip, a device that was asleep, a classifier that only catches on once the drive is
 * already underway — which would otherwise mean a whole drive goes uncounted.
 *
 * So a confident periodic `IN_VEHICLE` classification also starts tracking. It deliberately
 * never stops it: periodic classifications report `STILL` at red lights and in traffic, and
 * acting on that would cut a drive short. Drives end on an `EXIT` transition or on the
 * tracking service's idle watchdog.
 */
class ActivityRecognitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val mostProbable = result.mostProbableActivity

        if (mostProbable.type == DetectedActivity.IN_VEHICLE && mostProbable.confidence >= MIN_CONFIDENCE) {
            DrivingActivityState.markDrivingStarted()
            LocationTrackingController.startTrackingDrive(context)
        }
    }

    companion object {
        private const val MIN_CONFIDENCE = 50
    }
}
