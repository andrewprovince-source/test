package com.driveforchange.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

/** Receives periodic activity classifications and updates [DrivingActivityState]. */
class ActivityRecognitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val mostProbable = result.mostProbableActivity

        DrivingActivityState.isInVehicle =
            mostProbable.type == DetectedActivity.IN_VEHICLE && mostProbable.confidence >= MIN_CONFIDENCE
    }

    companion object {
        private const val MIN_CONFIDENCE = 50
    }
}
