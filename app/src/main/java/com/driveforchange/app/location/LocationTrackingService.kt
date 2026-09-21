package com.driveforchange.app.location

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
import android.util.Log
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that runs GPS for the duration of a single drive and converts the
 * distance traveled into today's mock donation ledger.
 *
 * It is started by [DriveTransitionReceiver] when Android detects the user has entered a
 * vehicle and stopped when they leave it, so it does not run all day — see
 * [LocationTrackingController] for how that is arranged. It re-checks the user's pause
 * setting on every start, so a drive detected after tracking was paused is ignored even
 * though the subscription may not have been torn down yet.
 */
class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastLocation: Location? = null
    private var lastUpdateAtElapsedRealtimeMs: Long = 0L
    private var lastMovementAtElapsedRealtimeMs: Long = 0L
    private var isTracking = false
    private var watchdogJob: Job? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val newLocation = result.lastLocation ?: return
            val now = SystemClock.elapsedRealtime()
            val previous = lastLocation

            if (previous != null) {
                // Time our own receipt of each update rather than trusting the Location's
                // embedded timestamp, which emulators/mocked providers don't always populate.
                val elapsedSeconds = (now - lastUpdateAtElapsedRealtimeMs) / 1000.0
                val meters = previous.distanceTo(newLocation)

                // Discard GPS jumps that would imply an impossible driving speed (e.g. the
                // emulator's default location, a cold-start fix, or a teleported test route)
                // rather than counting them as real distance. Also require the activity
                // classifier to currently agree we're in a vehicle, so walking/cycling/transit
                // don't get credited as driving.
                if (elapsedSeconds > 0 &&
                    meters / elapsedSeconds <= MAX_PLAUSIBLE_SPEED_MPS &&
                    DrivingActivityState.isInVehicle
                ) {
                    val miles = meters * METERS_TO_MILES
                    if (miles > 0.0) {
                        lastMovementAtElapsedRealtimeMs = now
                        val repository = (application as DriveForChangeApplication).container.donationRepository
                        serviceScope.launch { repository.recordDistanceMiles(miles) }
                    }
                }
            }
            lastLocation = newLocation
            lastUpdateAtElapsedRealtimeMs = now
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android 14 requires the notification to go up promptly on every start command,
        // including a restart after the process was killed mid-drive.
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            // Location permission was revoked, or the start wasn't allowed after all.
            Log.w(TAG, "Could not enter the foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }

        // A null intent means Android restarted us after killing the process. The service
        // only ever runs during a drive, so assume that drive is still in progress rather
        // than dropping the rest of it; the idle watchdog stops us if it has actually ended.
        if (intent == null) {
            DrivingActivityState.markDrivingStarted()
        }

        // The backstop receiver re-starts us roughly once a minute for the length of a
        // drive. Re-requesting updates each time would reset the last known location and
        // lose the distance covered in between, so later starts are a no-op.
        if (!isTracking) {
            isTracking = true
            lastMovementAtElapsedRealtimeMs = SystemClock.elapsedRealtime()
            stopIfTrackingPaused()
            startLocationUpdates()
            startIdleWatchdog()
        }
        return START_STICKY
    }

    /**
     * A drive can be detected after the user has paused tracking — the transition
     * subscription is torn down asynchronously, and Play services can deliver an event that
     * was already in flight. The saved setting is the authority, so re-check it here.
     */
    private fun stopIfTrackingPaused() {
        val repository = (application as DriveForChangeApplication).container.userPreferencesRepository
        serviceScope.launch {
            val paused = runCatching { repository.userPreferencesFlow.first().trackingPaused }.getOrDefault(false)
            if (paused) {
                DrivingActivityState.markDrivingStopped()
                stopSelf()
            }
        }
    }

    /**
     * Stop the service if no distance has been recorded for a while. An `EXIT` transition
     * normally ends a drive, but if one is missed — the classifier is not perfect — this is
     * what keeps GPS from running for the rest of the day.
     */
    private fun startIdleWatchdog() {
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(WATCHDOG_CHECK_INTERVAL_MS)
                val idleMs = SystemClock.elapsedRealtime() - lastMovementAtElapsedRealtimeMs
                if (idleMs >= IDLE_TIMEOUT_MS) {
                    DrivingActivityState.markDrivingStopped()
                    stopSelf()
                    return@launch
                }
            }
        }
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METERS)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            // Location permission was revoked after the service started; stop tracking.
            stopSelf()
        }
    }

    /**
     * Keep tracking when the app is swiped out of recents. The drive is still happening,
     * and the ongoing notification stays up, so there is nothing to tear down here.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        // Intentionally does not call stopSelf().
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_text))
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
            .build()

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
        watchdogJob?.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isTracking = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LocationTrackingService"
        private const val CHANNEL_ID = "drive_tracking_channel"
        private const val NOTIFICATION_ID = 1001
        private const val UPDATE_INTERVAL_MS = 4_000L
        private const val MIN_UPDATE_DISTANCE_METERS = 5f
        private const val METERS_TO_MILES = 0.000621371

        /** ~120 mph — generous upper bound for real driving; anything faster is a GPS glitch. */
        private const val MAX_PLAUSIBLE_SPEED_MPS = 54.0

        /** Long enough to sit out heavy traffic or a level crossing without ending the drive. */
        private const val IDLE_TIMEOUT_MS = 10 * 60 * 1000L
        private const val WATCHDOG_CHECK_INTERVAL_MS = 60_000L

        fun start(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                // Android 12+ can refuse a background start outside of its exemptions. The
                // next drive transition will try again, so this is not worth surfacing.
                Log.w(TAG, "Could not start mileage tracking", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
    }
}
