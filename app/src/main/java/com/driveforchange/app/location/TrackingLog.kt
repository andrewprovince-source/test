package com.driveforchange.app.location

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small on-device diary of what background tracking did and why, readable from the
 * "Tracking log" screen.
 *
 * Background tracking fails silently by nature — the app is closed, so there is nothing on
 * screen when Android declines to deliver an event or refuses to start the service. This
 * log is how a drive that went uncounted can be diagnosed afterwards without a debugger
 * attached. It records events, never coordinates.
 */
object TrackingLog {

    private const val TAG = "TrackingLog"
    private const val FILE_NAME = "tracking_log.txt"
    private const val MAX_LINES = 400
    private const val TRIM_THRESHOLD_BYTES = 48 * 1024L

    private val timestampFormat = SimpleDateFormat("MMM d HH:mm:ss", Locale.US)

    @Synchronized
    fun log(context: Context, message: String) {
        Log.i(TAG, message)
        try {
            val file = file(context)
            file.appendText("${timestampFormat.format(Date())}  $message\n")
            if (file.length() > TRIM_THRESHOLD_BYTES) {
                file.writeText(file.readLines().takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not write tracking log", e)
        }
    }

    @Synchronized
    fun read(context: Context): String =
        try {
            file(context).takeIf { it.exists() }?.readText().orEmpty()
        } catch (e: Exception) {
            ""
        }

    @Synchronized
    fun clear(context: Context) {
        file(context).delete()
    }

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)
}
