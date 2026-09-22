package com.driveforchange.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * Periodic activity classifications. Transition events alone are too unreliable, so these
 * fill the gaps:
 *
 * - A confident `IN_VEHICLE` reading confirms a drive — most importantly one the parking
 *   geofence started, which has no idea yet whether the user is driving or walking.
 * - A confident on-foot or cycling reading ends a confirmed drive whose `EXIT` transition
 *   never arrived, so walking around after parking isn't credited as driving.
 *
 * `STILL` never ends a drive: that's what the classifier reports at red lights and in traffic.
 */
class ActivityRecognitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val mostProbable = result.mostProbableActivity

        when {
            mostProbable.type == DetectedActivity.IN_VEHICLE && mostProbable.confidence >= MIN_VEHICLE_CONFIDENCE -> {
                if (LocationTrackingService.isRunning) {
                    if (!DrivingActivityState.isInVehicle) {
                        TrackingLog.log(context, "Classifier: in a vehicle (${mostProbable.confidence}% confident)")
                        DrivingActivityState.markDrivingStarted()
                    }
                } else {
                    // Usually refused from the background — the geofence or transition is
                    // what starts the service then — but works while the app is open.
                    // Throttled so a refused start doesn't fill the log every 30 seconds.
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastStartAttemptAtElapsedRealtimeMs >= START_ATTEMPT_INTERVAL_MS || lastStartAttemptAtElapsedRealtimeMs == 0L) {
                        lastStartAttemptAtElapsedRealtimeMs = now
                        TrackingLog.log(context, "Classifier: in a vehicle (${mostProbable.confidence}% confident), tracking not running")
                        LocationTrackingController.startTrackingDrive(context, reason = "classifier says in vehicle", inVehicle = true)
                    }
                }
            }
            mostProbable.type in ON_FOOT_TYPES && mostProbable.confidence >= MIN_ON_FOOT_CONFIDENCE -> {
                if (DrivingActivityState.isInVehicle && LocationTrackingService.isRunning) {
                    TrackingLog.log(context, "Classifier: on foot (${mostProbable.confidence}% confident) — ending drive")
                    DrivingActivityState.markDrivingStopped()
                    LocationTrackingService.stop(context, reason = "on foot")
                }
            }
        }
    }

    companion object {
        private const val MIN_VEHICLE_CONFIDENCE = 50
        private const val START_ATTEMPT_INTERVAL_MS = 5 * 60 * 1000L

        @Volatile
        private var lastStartAttemptAtElapsedRealtimeMs = 0L

        /** Higher bar: wrongly ending a drive loses miles, so demand a clear reading. */
        private const val MIN_ON_FOOT_CONFIDENCE = 75

        private val ON_FOOT_TYPES = setOf(
            DetectedActivity.ON_FOOT,
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
            DetectedActivity.ON_BICYCLE,
        )
    }
}
