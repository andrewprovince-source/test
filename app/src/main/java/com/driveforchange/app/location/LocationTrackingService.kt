package com.driveforchange.app.location

import android.app.NotificationChannel
import android.app.NotificationManager
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
import kotlinx.coroutines.launch

/**
 * Foreground service that tracks GPS location while active and converts distance traveled
 * into today's mock donation ledger via [DonationRepository]. Runs only while the user has
 * not paused tracking (see Home dashboard).
 */
class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastLocation: Location? = null
    private var lastUpdateAtElapsedRealtimeMs: Long = 0L

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
                // rather than counting them as real distance.
                if (elapsedSeconds > 0 && meters / elapsedSeconds <= MAX_PLAUSIBLE_SPEED_MPS) {
                    val miles = meters * METERS_TO_MILES
                    if (miles > 0.0) {
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
        startForeground(NOTIFICATION_ID, buildNotification())
        startLocationUpdates()
        return START_STICKY
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

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    android.app.PendingIntent.FLAG_IMMUTABLE
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
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "drive_tracking_channel"
        private const val NOTIFICATION_ID = 1001
        private const val UPDATE_INTERVAL_MS = 10_000L
        private const val MIN_UPDATE_DISTANCE_METERS = 10f
        private const val METERS_TO_MILES = 0.000621371

        /** ~120 mph — generous upper bound for real driving; anything faster is a GPS glitch. */
        private const val MAX_PLAUSIBLE_SPEED_MPS = 54.0

        fun start(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
    }
}
