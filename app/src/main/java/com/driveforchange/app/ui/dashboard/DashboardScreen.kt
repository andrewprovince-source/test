package com.driveforchange.app.ui.dashboard

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.driveforchange.app.location.LocationTrackingController
import com.driveforchange.app.ui.components.IconTile
import com.driveforchange.app.ui.components.PrimaryPillButton
import com.driveforchange.app.ui.components.ProgressBar
import com.driveforchange.app.ui.components.ProgressRing
import com.driveforchange.app.ui.components.SecondaryPillButton
import com.driveforchange.app.ui.components.SkyBackground
import com.driveforchange.app.ui.components.SkyCard
import com.driveforchange.app.ui.components.StatusPill
import com.driveforchange.app.ui.components.TopPill
import com.driveforchange.app.ui.theme.CardWhite
import com.driveforchange.app.ui.theme.ErrorSoft
import com.driveforchange.app.ui.theme.InkMuted
import com.driveforchange.app.ui.theme.PillGray
import com.driveforchange.app.ui.theme.SkyBlueSoft
import com.driveforchange.app.viewmodel.DashboardViewModel
import java.util.Locale

private fun formatMoney(value: Double): String = String.format(Locale.US, "$%.2f", value)
private fun formatMiles(value: Double): String = String.format(Locale.US, "%.1f mi", value)

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenTrackingLog: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var hasLocationPermission by remember {
        mutableStateOf(LocationTrackingController.hasLocationPermission(context))
    }
    var hasActivityRecognitionPermission by remember {
        mutableStateOf(LocationTrackingController.hasActivityRecognitionPermission(context))
    }
    var hasBackgroundLocationPermission by remember {
        mutableStateOf(LocationTrackingController.hasBackgroundLocationPermission(context))
    }
    var isBatteryUnrestricted by remember {
        mutableStateOf(LocationTrackingController.isBatteryUnrestricted(context))
    }

    fun refreshPermissions() {
        hasLocationPermission = LocationTrackingController.hasLocationPermission(context)
        hasActivityRecognitionPermission = LocationTrackingController.hasActivityRecognitionPermission(context)
        hasBackgroundLocationPermission = LocationTrackingController.hasBackgroundLocationPermission(context)
        isBatteryUnrestricted = LocationTrackingController.isBatteryUnrestricted(context)
    }

    // "Allow all the time" and the battery exemption are both granted outside the app, so
    // the only way to notice is to re-check when the user comes back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshPermissions()
        if (hasLocationPermission) {
            viewModel.setTrackingPaused(false)
        }
    }

    // Android requires background location to be asked for separately, and only after
    // foreground location has already been granted.
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshPermissions() }

    fun requestBackgroundLocation() {
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> Unit
            // Android 11+ refuses to show a dialog for this one; it has to be granted in
            // the app's own settings page.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
                context.startActivity(intent)
            }
            else -> backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    fun requestBatteryExemption() {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.fromParts("package", context.packageName, null),
        )
        try {
            context.startActivity(direct)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    // Prompt for permissions as soon as the dashboard first appears if any are still missing.
    LaunchedEffect(Unit) {
        if (!hasLocationPermission || !hasActivityRecognitionPermission) {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    // Arm drive detection once the permissions it needs are in place, and tear it down when
    // the user pauses. Re-arming is harmless, so this can run on any of these changing.
    LaunchedEffect(
        state.trackingPaused,
        state.loaded,
        hasLocationPermission,
        hasActivityRecognitionPermission,
        hasBackgroundLocationPermission,
    ) {
        if (!state.loaded) return@LaunchedEffect
        LocationTrackingController.applyTrackingState(context, trackingPaused = state.trackingPaused, reason = "dashboard")
    }

    val backgroundReady = hasBackgroundLocationPermission && hasActivityRecognitionPermission
    val capFraction = if (state.dailyCap > 0) (state.todayDonation / state.dailyCap).toFloat() else 0f

    fun onToggleTracking() {
        if (state.trackingPaused) {
            if (hasLocationPermission) viewModel.setTrackingPaused(false) else permissionLauncher.launch(requiredPermissions())
        } else {
            viewModel.setTrackingPaused(true)
        }
    }

    SkyBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopPill {
                    ProgressRing(fraction = capFraction, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = buildAnnotatedString {
                            append(formatMoney(state.todayDonation))
                            withStyle(SpanStyle(color = InkMuted)) { append(" / " + formatMoney(state.dailyCap)) }
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Surface(shape = CircleShape, color = CardWhite, shadowElevation = 1.dp) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            }

            SkyCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Today", style = MaterialTheme.typography.titleLarge)
                    when {
                        state.trackingPaused -> StatusPill("Paused", PillGray, InkMuted)
                        backgroundReady -> StatusPill("Auto-tracking on", SkyBlueSoft, MaterialTheme.colorScheme.primary, showDot = true)
                        else -> StatusPill("Needs setup", ErrorSoft, MaterialTheme.colorScheme.error)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(formatMoney(state.todayDonation), style = MaterialTheme.typography.displayLarge)
                    Text(
                        text = "${formatMiles(state.todayMiles)} · ${formatMoney(state.perMileRate)} per mile",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted,
                    )
                }
                ProgressBar(fraction = capFraction, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = if (capFraction >= 1f) "Daily cap reached — nice driving." else "${formatMoney(state.dailyCap)} daily cap",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
                PrimaryPillButton(
                    text = if (state.trackingPaused) "Resume tracking" else "Pause tracking",
                    icon = if (state.trackingPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    onClick = { onToggleTracking() },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryPillButton("Stats", onClick = onOpenStats, icon = Icons.Filled.BarChart, modifier = Modifier.weight(1f))
                    SecondaryPillButton("Drive log", onClick = onOpenTrackingLog, icon = Icons.Outlined.History, modifier = Modifier.weight(1f))
                }
                if (!hasLocationPermission) {
                    Text("Location permission needed to track mileage", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                } else if (!hasActivityRecognitionPermission) {
                    Text("Physical activity permission needed so only driving gets counted", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }

            // Foreground location alone only counts drives taken with the app open,
            // which is the thing background tracking is meant to fix.
            if (hasLocationPermission && !hasBackgroundLocationPermission) {
                SetupPrompt(
                    title = "Allow location all the time",
                    body = "Without this, Android won't let the app use GPS while it's closed, so " +
                        "drives only count with the app open. In the screen that opens, tap " +
                        "Permissions → Location → \"Allow all the time\".",
                    action = "Open settings",
                    onAction = { requestBackgroundLocation() },
                )
            }

            // Samsung in particular puts apps to sleep unless they're exempted, and a
            // sleeping app never hears that a drive started.
            if (!isBatteryUnrestricted && !state.trackingPaused) {
                SetupPrompt(
                    title = "Let the app run in the background",
                    body = "Your phone's battery saver can put the app to sleep and stop it from " +
                        "noticing when a drive starts. Tap Allow on the next screen.",
                    action = "Allow",
                    onAction = { requestBatteryExemption() },
                )
            }

            Text("Your impact", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))

            SkyCard {
                Text("Lifetime", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                Text(formatMoney(state.lifetimeDonation), style = MaterialTheme.typography.headlineLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    BreakdownStat(label = "This month", value = formatMoney(state.monthDonation))
                    BreakdownStat(label = "This year", value = formatMoney(state.yearDonation))
                }
            }

            SkyCard(contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(Icons.Outlined.FavoriteBorder, tint = MaterialTheme.colorScheme.primary, background = SkyBlueSoft)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Supporting", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                        Text(state.charity.name, style = MaterialTheme.typography.titleMedium)
                        Text(state.charity.cause, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                    }
                    SecondaryPillButton("Change", onClick = onOpenSettings, height = 40.dp)
                }
            }
        }
    }
}

@Composable
private fun SetupPrompt(title: String, body: String, action: String, onAction: () -> Unit) {
    SkyCard(borderColor = MaterialTheme.colorScheme.error) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        PrimaryPillButton(text = action, onClick = onAction, height = 48.dp)
    }
}

@Composable
private fun BreakdownStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

private fun requiredPermissions(): Array<String> {
    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
    }
    return permissions.toTypedArray()
}
