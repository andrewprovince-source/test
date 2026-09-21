package com.driveforchange.app

import android.app.Application
import com.driveforchange.app.data.AppContainer
import com.driveforchange.app.location.LocationTrackingController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DriveForChangeApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        observeTrackingPreference()
    }

    /**
     * Keep drive detection in step with the saved pause setting for as long as the process
     * is alive — including when the app is started in the background by a drive transition.
     * This is also the repair path for a subscription that was dropped without a broadcast
     * we could catch, such as the user force-stopping the app.
     */
    private fun observeTrackingPreference() {
        applicationScope.launch {
            container.userPreferencesRepository.userPreferencesFlow
                .map { it.trackingPaused }
                .distinctUntilChanged()
                .collect { paused ->
                    LocationTrackingController.applyTrackingState(this@DriveForChangeApplication, paused)
                }
        }
    }
}
