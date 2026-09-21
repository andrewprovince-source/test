package com.driveforchange.app.location

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity

/**
 * Entry point for arming and disarming background mileage tracking.
 *
 * Tracking is "wake on drive": while armed, the app holds a low-power subscription to
 * Android's activity recognition service instead of running GPS. Play services keeps that
 * subscription outside our process, so it survives the app being closed or swiped away and
 * will cold-start the app when a drive begins. GPS — and the foreground service that owns
 * it — only runs between the start and end of an actual drive.
 *
 * Being armed is therefore the normal all-day state, and costs nothing beyond the
 * classifier Android is already running. [LocationTrackingService] is the expensive part,
 * and it is short-lived by design.
 */
object LocationTrackingController {

    private const val TAG = "TrackingController"

    /** Periodic classifications, used only as a backstop for a missed `ENTER` transition. */
    private const val ACTIVITY_UPDATE_INTERVAL_MS = 60_000L

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    /**
     * "Allow all the time". Required for the tracking service to actually receive locations
     * when a drive transition starts it while the app is closed — which is the whole point
     * of background tracking. Without it the service still starts, but Android hands it no
     * location updates, so nothing gets counted unless the app happens to be open.
     */
    fun hasBackgroundLocationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return hasLocationPermission(context)
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Needed so the driving classifier can run; without it no distance is ever counted. */
    fun hasActivityRecognitionPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** True once tracking can run unattended, with the app closed. */
    fun canTrackInBackground(context: Context): Boolean =
        hasBackgroundLocationPermission(context) && hasActivityRecognitionPermission(context)

    /**
     * Subscribe to drive transitions so tracking starts on its own the next time the user
     * drives. Safe to call repeatedly — re-registering replaces the existing subscription.
     */
    fun arm(context: Context) {
        val appContext = context.applicationContext
        if (!hasLocationPermission(appContext) || !hasActivityRecognitionPermission(appContext)) return

        val client = ActivityRecognition.getClient(appContext)
        val transitions = listOf(
            vehicleTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER),
            vehicleTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT),
        )
        try {
            client.requestActivityTransitionUpdates(
                ActivityTransitionRequest(transitions),
                transitionPendingIntent(appContext),
            )
            client.requestActivityUpdates(
                ACTIVITY_UPDATE_INTERVAL_MS,
                classificationPendingIntent(appContext),
            )
        } catch (e: SecurityException) {
            // Physical activity permission was revoked between the check above and here.
            Log.w(TAG, "Could not subscribe to activity updates", e)
        }
    }

    /** Drop the drive-transition subscription and stop any drive currently being tracked. */
    fun disarm(context: Context) {
        val appContext = context.applicationContext
        val client = ActivityRecognition.getClient(appContext)
        try {
            client.removeActivityTransitionUpdates(transitionPendingIntent(appContext))
            client.removeActivityUpdates(classificationPendingIntent(appContext))
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not remove activity updates", e)
        }
        DrivingActivityState.markDrivingStopped()
        LocationTrackingService.stop(appContext)
    }

    /**
     * Bring the drive-transition subscription in line with the user's saved pause setting.
     * Called on boot, on app update, and whenever the app starts, so the subscription is
     * re-established after anything that could have dropped it.
     */
    fun applyTrackingState(context: Context, trackingPaused: Boolean) {
        if (trackingPaused) disarm(context) else arm(context)
    }

    /**
     * Start the tracking service for a drive that is underway. Called from the activity
     * recognition receivers, which Android exempts from its background foreground-service
     * start restrictions.
     */
    fun startTrackingDrive(context: Context) {
        if (!hasLocationPermission(context)) return
        LocationTrackingService.start(context.applicationContext)
    }

    private fun vehicleTransition(transition: Int): ActivityTransition =
        ActivityTransition.Builder()
            .setActivityType(DetectedActivity.IN_VEHICLE)
            .setActivityTransition(transition)
            .build()

    private fun transitionPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_TRANSITIONS,
            Intent(context, DriveTransitionReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    private fun classificationPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_CLASSIFICATIONS,
            Intent(context, ActivityRecognitionReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    // Distinct request codes so the two subscriptions get distinct PendingIntents.
    private const val REQUEST_CODE_TRANSITIONS = 1
    private const val REQUEST_CODE_CLASSIFICATIONS = 2
}
