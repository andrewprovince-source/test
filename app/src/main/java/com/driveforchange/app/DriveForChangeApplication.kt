package com.driveforchange.app

import android.app.Application
import com.driveforchange.app.data.AppContainer

class DriveForChangeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
