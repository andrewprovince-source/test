package com.driveforchange.app.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.driveforchange.app.DriveForChangeApplication
import com.driveforchange.app.MainActivity
import com.driveforchange.app.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Foreground service that runs GPS for the duration of a single (possible) drive and credits
 * the distance to today's mock donation ledger.
 *
 * It's started by one of the drive-detection events set up in [LocationTrackingController].
 * The parking geofence can't tell driving away from walking away, so the service may start
 * before a drive is confirmed. Until the activity classifier confirms one
 * ([DrivingActivityState]), distance is held back as *pending* rather than discarded: once
 * driving is confirmed it's all credited, so the start of the drive isn't lost; if it never
 * is, it's dropped and the service stops.
 *
 * When it stops, it re-plants the parking geofence where the drive ended, ready for the next.
 */
class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isTracking = false
    private var lastLocation: Location? = null
    private var lastUpdateAtElapsedRealtimeMs = 0L
    // Written on the main thread by the location callback, read by the watchdog coroutine.
    @Volatile private var startedAtElapsedRealtimeMs = 0L
    @Volatile private var lastMovementAtElapsedRealtimeMs = 0L
    @Volatile private var driveConfirmed = false
    private var gpsFixCount = 0
    private var creditedMiles = 0.0
    private var pendingMiles = 0.0
    private var stopReason: String? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val newLocation = result.lastLocation ?: return
            val now = SystemClock.elapsedRealtime()
            val previous = lastLocation

            gpsFixCount++
            if (gpsFixCount == 1) {
                log("First GPS fix (${newLocation.accuracy.toInt()} m accuracy)")
            }

            if (previous != null) {
                // Time our own receipt of each update rather than trusting the Location's
                // embedded timestamp, which emulators/mocked providers don't always populate.
                val elapsedSeconds = (now - lastUpdateAtElapsedRealtimeMs) / 1000.0
                val meters = previous.distanceTo(newLocation)

                // Discard GPS jumps that would imply an impossible driving speed (e.g. a
                // cold-start fix or a teleported test route) rather than counting them.
                if (elapsedSeconds > 0 && meters / elapsedSeconds <= MAX_PLAUSIBLE_SPEED_MPS) {
                    val miles = meters * METERS_TO_MILES
                    if (miles > 0.0) {
                        lastMovementAtElapsedRealtimeMs = now
                        onDistanceTraveled(miles)
                    }
                }
            }
            lastLocation = newLocation
            lastUpdateAtElapsedRealtimeMs = now
        }
    }

    private fun onDistanceTraveled(miles: Double) {
        if (!DrivingActivityState.isInVehicle) {
            pendingMiles += miles
            return
        }
        if (!driveConfirmed) {
            driveConfirmed = true
            log(if (pendingMiles > 0.0) "Drive confirmed — crediting ${formatMiles(pendingMiles)} traveled before confirmation" else "Drive confirmed")
            updateNotification()
        }
        val toCredit = miles + pendingMiles
        pendingMiles = 0.0
        creditedMiles += toCredit
        val repository = (application as DriveForChangeApplication).container.donationRepository
        serviceScope.launch { repository.recordDistanceMiles(toCredit) }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires the notification to go up promptly on every start command,
        // including a restart after the process was killed mid-drive.
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            // Android 14+ throws here if the app is in the background without "Allow all
            // the time" location, or if the start wasn't one of the allowed exemptions.
            log("Android refused to run tracking: ${e.javaClass.simpleName}: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        // Later starts (e.g. a transition arriving after the geofence already started us)
        // are no-ops: re-requesting updates would reset the last location and lose distance.
        if (!isTracking) {
            isTracking = true
            isRunning = true
            pendingStopReason = null
            shouldReplantGeofence = true
            val now = SystemClock.elapsedRealtime()
            startedAtElapsedRealtimeMs = now
            lastMovementAtElapsedRealtimeMs = now
            val reason = intent?.getStringExtra(EXTRA_REASON) ?: "restarted by Android after the app was killed"
            // Start every session from what the triggering event itself established, never
            // from a leftover verdict: a stale "in vehicle" would credit a walk as a drive.
            if (intent?.getBooleanExtra(EXTRA_IN_VEHICLE, false) == true) {
                DrivingActivityState.markDrivingStarted()
            } else {
                DrivingActivityState.markDrivingStopped()
            }
            updateNotification()
            log("Tracking started ($reason); drive confirmed=${if (DrivingActivityState.isInVehicle) "yes" else "not yet"}")
            stopIfTrackingPaused()
            startLocationUpdates()
            startWatchdog()
        }
        return START_STICKY
    }

    /**
     * A drive can be detected after the user has paused tracking — tearing down the
     * subscriptions is asynchronous, and an event may already be in flight. The saved setting
     * is the authority, so re-check it here.
     */
    private fun stopIfTrackingPaused() {
        val repository = (application as DriveForChangeApplication).container.userPreferencesRepository
        serviceScope.launch {
            val paused = runCatching { repository.userPreferencesFlow.first().trackingPaused }.getOrDefault(false)
            if (paused) {
                withContext(Dispatchers.Main) { stopWithReason("tracking is paused", replantGeofence = false) }
            }
        }
    }

    /**
     * Ends a drive that was never confirmed (the geofence fired because the user walked
     * away), or one that went quiet without an `EXIT` transition ever arriving.
     */
    private fun startWatchdog() {
        serviceScope.launch {
            while (isActive) {
                delay(WATCHDOG_CHECK_INTERVAL_MS)
                val now = SystemClock.elapsedRealtime()
                val reason = when {
                    !driveConfirmed && !DrivingActivityState.isInVehicle &&
                        now - startedAtElapsedRealtimeMs >= UNCONFIRMED_TIMEOUT_MS ->
                        "no drive confirmed within ${UNCONFIRMED_TIMEOUT_MS / 60_000} min"
                    now - lastMovementAtElapsedRealtimeMs >= IDLE_TIMEOUT_MS ->
                        "no movement for ${IDLE_TIMEOUT_MS / 60_000} min"
                    else -> null
                }
                if (reason != null) {
                    withContext(Dispatchers.Main) { stopWithReason(reason, replantGeofence = true) }
                    return@launch
                }
            }
        }
    }

    private fun stopWithReason(reason: String, replantGeofence: Boolean) {
        stopReason = reason
        shouldReplantGeofence = replantGeofence
        stopSelf()
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METERS)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            stopWithReason("location permission was revoked", replantGeofence = false)
        }
    }

    /**
     * Keep tracking when the app is swiped out of recents. The drive is still happening,
     * and the ongoing notification stays up, so there is nothing to tear down here.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        // Intentionally does not call stopSelf().
    }

    private fun buildNotification(): Notification {
        val confirmed = driveConfirmed || DrivingActivityState.isInVehicle
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(
                getString(if (confirmed) R.string.tracking_notification_title else R.string.tracking_notification_title_checking)
            )
            .setContentText(
                getString(if (confirmed) R.string.tracking_notification_text else R.string.tracking_notification_text_checking)
            )
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.tracking_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()

        if (isTracking) {
            val reason = stopReason ?: pendingStopReason ?: "stopped"
            val discarded = if (pendingMiles > 0.0) ", discarded ${formatMiles(pendingMiles)} unconfirmed" else ""
            log("Tracking stopped ($reason): credited ${formatMiles(creditedMiles)}$discarded, $gpsFixCount GPS fixes")

            val parkedAt = lastLocation
            if (shouldReplantGeofence && parkedAt != null) {
                LocationTrackingController.plantParkingGeofence(applicationContext, parkedAt)
            }
        }

        DrivingActivityState.markDrivingStopped()
        pendingStopReason = null
        shouldReplantGeofence = true
        isTracking = false
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun log(message: String) = TrackingLog.log(applicationContext, message)

    private fun formatMiles(miles: Double) = String.format(Locale.US, "%.2f mi", miles)

    companion object {
        private const val CHANNEL_ID = "drive_tracking_channel"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_IN_VEHICLE = "in_vehicle"
        private const val UPDATE_INTERVAL_MS = 4_000L
        private const val MIN_UPDATE_DISTANCE_METERS = 5f
        private const val METERS_TO_MILES = 0.000621371

        /** ~120 mph — generous upper bound for real driving; anything faster is a GPS glitch. */
        private const val MAX_PLAUSIBLE_SPEED_MPS = 54.0

        /** How long the classifier gets to confirm a drive the parking geofence started. */
        private const val UNCONFIRMED_TIMEOUT_MS = 5 * 60 * 1000L

        /** Long enough to sit out heavy traffic or a level crossing without ending the drive. */
        private const val IDLE_TIMEOUT_MS = 10 * 60 * 1000L
        private const val WATCHDOG_CHECK_INTERVAL_MS = 30_000L

        /** True from the first start command until the service is destroyed. */
        @Volatile
        var isRunning = false
            private set

        // Set by [stop] callers, which can't pass extras through stopService().
        @Volatile
        private var pendingStopReason: String? = null

        @Volatile
        private var shouldReplantGeofence = true

        fun start(context: Context, reason: String, inVehicle: Boolean) {
            val intent = Intent(context, LocationTrackingService::class.java)
                .putExtra(EXTRA_REASON, reason)
                .putExtra(EXTRA_IN_VEHICLE, inVehicle)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                // Android 12+ refuses background starts outside its exemptions. Logged
                // because this is exactly the kind of silent failure the log exists for.
                TrackingLog.log(context, "Android refused to start tracking ($reason): ${e.javaClass.simpleName}")
            }
        }

        fun stop(context: Context, reason: String, replantGeofence: Boolean = true) {
            pendingStopReason = reason
            shouldReplantGeofence = replantGeofence
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
    }
}
