package com.driveforchange.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * Entry point for arming and disarming background mileage tracking.
 *
 * Tracking is "wake on drive": while armed, the app holds two low-power subscriptions that
 * Play services keeps outside our process, so they survive the app being closed or swiped
 * away and will cold-start the app when a drive begins:
 *
 * 1. **Vehicle transitions** (Activity Recognition) — the classifier says "entered a vehicle".
 *    Tells us a drive started, but the classifier is slow and sometimes never fires.
 * 2. **A geofence around where the car was last parked** — fires when the phone leaves it.
 *    Doesn't know *how* you left (walking or driving), but it's dependable, so it starts the
 *    tracking service in an unconfirmed state and the classifier then has a few minutes to
 *    confirm it's a drive (see [LocationTrackingService]).
 *
 * Both are among the few events Android allows to start a location foreground service from
 * the background. GPS — and the service that owns it — only runs during a (possible) drive.
 */
object LocationTrackingController {

    /** Periodic classifications: confirm a drive the geofence started, or notice it ended. */
    private const val ACTIVITY_UPDATE_INTERVAL_MS = 30_000L

    private const val PARKING_GEOFENCE_ID = "parking_spot"

    /** Wide enough that GPS drift while parked doesn't look like leaving. */
    private const val PARKING_GEOFENCE_RADIUS_METERS = 200f

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    /**
     * "Allow all the time". Without it, Android 14+ refuses outright to start a location
     * foreground service while the app is closed, and won't register the parking geofence —
     * so drives only get counted with the app open.
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

    /**
     * Battery "Unrestricted". Samsung in particular puts apps to sleep in the background
     * unless they're exempted, which can stop drive events from ever reaching the app.
     */
    fun isBatteryUnrestricted(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** True once tracking can run unattended, with the app closed. */
    fun canTrackInBackground(context: Context): Boolean =
        hasBackgroundLocationPermission(context) && hasActivityRecognitionPermission(context)

    fun permissionSummary(context: Context): String =
        "location=${yesNo(hasLocationPermission(context))} " +
            "all-the-time=${yesNo(hasBackgroundLocationPermission(context))} " +
            "activity=${yesNo(hasActivityRecognitionPermission(context))} " +
            "battery-unrestricted=${yesNo(isBatteryUnrestricted(context))}"

    private fun yesNo(value: Boolean) = if (value) "yes" else "NO"

    /**
     * Subscribe to drive detection so tracking starts on its own the next time the user
     * drives. Safe to call repeatedly — re-registering replaces the existing subscriptions.
     */
    fun arm(context: Context, reason: String) {
        val appContext = context.applicationContext
        TrackingLog.log(appContext, "Arming ($reason): ${permissionSummary(appContext)}")
        if (!hasLocationPermission(appContext) || !hasActivityRecognitionPermission(appContext)) {
            TrackingLog.log(appContext, "Not armed — missing location or physical activity permission")
            return
        }

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
                .addOnSuccessListener { TrackingLog.log(appContext, "Vehicle transition updates registered") }
                .addOnFailureListener { TrackingLog.log(appContext, "Vehicle transition registration FAILED: ${it.message}") }
            client.requestActivityUpdates(
                ACTIVITY_UPDATE_INTERVAL_MS,
                classificationPendingIntent(appContext),
            )
                .addOnFailureListener { TrackingLog.log(appContext, "Periodic activity registration FAILED: ${it.message}") }
        } catch (e: SecurityException) {
            TrackingLog.log(appContext, "Activity registration refused: ${e.message}")
        }

        // Mid-drive, the service plants the geofence itself when the drive ends.
        if (!LocationTrackingService.isRunning) {
            plantParkingGeofenceAtCurrentLocation(appContext)
        }
    }

    /** Drop all drive-detection subscriptions and stop any drive currently being tracked. */
    fun disarm(context: Context, reason: String) {
        val appContext = context.applicationContext
        TrackingLog.log(appContext, "Disarming ($reason)")
        val client = ActivityRecognition.getClient(appContext)
        try {
            client.removeActivityTransitionUpdates(transitionPendingIntent(appContext))
            client.removeActivityUpdates(classificationPendingIntent(appContext))
        } catch (e: SecurityException) {
            TrackingLog.log(appContext, "Could not remove activity updates: ${e.message}")
        }
        LocationServices.getGeofencingClient(appContext).removeGeofences(listOf(PARKING_GEOFENCE_ID))
        DrivingActivityState.markDrivingStopped()
        LocationTrackingService.stop(appContext, reason = "tracking paused", replantGeofence = false)
    }

    /**
     * Bring drive detection in line with the user's saved pause setting. Called on boot, on
     * app update, and whenever the app starts, so the subscriptions are re-established after
     * anything that could have dropped them.
     */
    fun applyTrackingState(context: Context, trackingPaused: Boolean, reason: String) {
        if (trackingPaused) disarm(context, reason) else arm(context, reason)
    }

    /**
     * Start the tracking service because a drive may be underway. Android only lets this
     * succeed from the background for transition and geofence events (or while the app is
     * open); from anywhere else it's refused, which gets logged rather than thrown.
     *
     * [inVehicle] says whether the triggering event itself confirmed driving. If it didn't
     * (the parking geofence), the service starts unconfirmed and waits for the classifier.
     */
    fun startTrackingDrive(context: Context, reason: String, inVehicle: Boolean) {
        val appContext = context.applicationContext
        if (!hasLocationPermission(appContext)) {
            TrackingLog.log(appContext, "Can't start tracking ($reason) — no location permission")
            return
        }
        if (LocationTrackingService.isRunning) {
            if (inVehicle) DrivingActivityState.markDrivingStarted()
            return
        }
        LocationTrackingService.start(appContext, reason, inVehicle)
    }

    /** Watch for the phone leaving this spot — i.e. the start of the next drive. */
    @SuppressLint("MissingPermission") // checked via hasBackgroundLocationPermission
    fun plantParkingGeofence(context: Context, location: Location) {
        val appContext = context.applicationContext
        if (!hasBackgroundLocationPermission(appContext)) {
            TrackingLog.log(appContext, "Parking geofence skipped — needs \"Allow all the time\" location")
            return
        }
        val geofence = Geofence.Builder()
            .setRequestId(PARKING_GEOFENCE_ID)
            .setCircularRegion(location.latitude, location.longitude, PARKING_GEOFENCE_RADIUS_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0) // only fire on actually leaving, not on being outside already
            .addGeofence(geofence)
            .build()
        try {
            LocationServices.getGeofencingClient(appContext)
                .addGeofences(request, geofencePendingIntent(appContext))
                .addOnSuccessListener {
                    TrackingLog.log(appContext, "Parking geofence set (${location.accuracy.toInt()} m GPS accuracy)")
                }
                .addOnFailureListener {
                    TrackingLog.log(appContext, "Parking geofence FAILED: ${it.message}")
                }
        } catch (e: SecurityException) {
            TrackingLog.log(appContext, "Parking geofence refused: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission") // checked via hasBackgroundLocationPermission
    private fun plantParkingGeofenceAtCurrentLocation(context: Context) {
        if (!hasBackgroundLocationPermission(context)) {
            TrackingLog.log(context, "Parking geofence skipped — needs \"Allow all the time\" location")
            return
        }
        try {
            LocationServices.getFusedLocationProviderClient(context).lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        plantParkingGeofence(context, location)
                    } else {
                        TrackingLog.log(context, "Parking geofence skipped — no recent location fix")
                    }
                }
        } catch (e: SecurityException) {
            TrackingLog.log(context, "Could not read location for geofence: ${e.message}")
        }
    }

    private fun vehicleTransition(transition: Int): ActivityTransition =
        ActivityTransition.Builder()
            .setActivityType(DetectedActivity.IN_VEHICLE)
            .setActivityTransition(transition)
            .build()

    // Play services fills these intents in with its results, so they have to be mutable.
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

    private fun geofencePendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_GEOFENCE,
            Intent(context, ParkingGeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    // Distinct request codes so each subscription gets its own PendingIntent.
    private const val REQUEST_CODE_TRANSITIONS = 1
    private const val REQUEST_CODE_CLASSIFICATIONS = 2
    private const val REQUEST_CODE_GEOFENCE = 3
}
