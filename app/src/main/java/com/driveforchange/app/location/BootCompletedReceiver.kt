package com.driveforchange.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.driveforchange.app.DriveForChangeApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-arms drive detection after the events that silently drop it: a reboot and an app
 * update both clear the activity recognition subscription Play services was holding for us.
 * Without this, tracking would stay dead until the user next opened the app — which is
 * exactly what background tracking is meant to avoid.
 *
 * This only re-subscribes to drive transitions; it does not start the tracking service, as
 * no drive is in progress at boot.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return

        val appContext = context.applicationContext
        val repository = (appContext as DriveForChangeApplication).container.userPreferencesRepository
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val paused = repository.userPreferencesFlow.first().trackingPaused
                LocationTrackingController.applyTrackingState(appContext, trackingPaused = paused, reason = "${intent.action?.substringAfterLast('.')}")
            } catch (e: Exception) {
                Log.w(TAG, "Could not re-arm tracking after ${intent.action}", e)
                TrackingLog.log(appContext, "Could not re-arm after ${intent.action}: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"

        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
