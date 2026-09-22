package com.driveforchange.app.ui.log

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.driveforchange.app.location.LocationTrackingController
import com.driveforchange.app.location.LocationTrackingService
import com.driveforchange.app.location.TrackingLog

/**
 * Shows [TrackingLog] newest first, with the current setup summarized on top so a copied
 * log carries everything needed to work out why a drive wasn't counted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    fun snapshot(): String {
        val header = buildString {
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Setup: ${LocationTrackingController.permissionSummary(context)}")
            appendLine("Tracking service running: ${if (LocationTrackingService.isRunning) "yes" else "no"}")
        }
        val entries = TrackingLog.read(context).lines().filter { it.isNotBlank() }.asReversed()
        val body = if (entries.isEmpty()) "Nothing logged yet." else entries.joinToString("\n")
        return "$header\n$body"
    }

    var text by remember { mutableStateOf(snapshot()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tracking log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { text = snapshot() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { clipboard.setText(AnnotatedString(text)) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy log")
                    }
                    IconButton(onClick = {
                        TrackingLog.clear(context)
                        text = snapshot()
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear log")
                    }
                }
            )
        }
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                SelectionContainer {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
