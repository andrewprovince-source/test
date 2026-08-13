package com.driveforchange.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.driveforchange.app.ui.navigation.AppNavHost
import com.driveforchange.app.ui.theme.DriveForChangeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as DriveForChangeApplication).container

        setContent {
            DriveForChangeTheme {
                AppNavHost(container = container)
            }
        }
    }
}
