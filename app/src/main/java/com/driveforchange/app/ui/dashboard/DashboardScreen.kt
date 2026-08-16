package com.driveforchange.app.ui.dashboard

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driveforchange.app.location.LocationTrackingController
import com.driveforchange.app.viewmodel.DashboardViewModel
import java.util.Locale

private fun formatMoney(value: Double): String = String.format(Locale.US, "$%.2f", value)
private fun formatMiles(value: Double): String = String.format(Locale.US, "%.1f mi", value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var hasLocationPermission by remember {
        mutableStateOf(LocationTrackingController.hasLocationPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasLocationPermission = results.values.any { it }
        if (hasLocationPermission) {
            viewModel.setTrackingPaused(false)
        }
    }

    // Prompt for location permission as soon as the dashboard first appears if it's still missing.
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(locationPermissions())
        }
    }

    // Keep the tracking service in sync with the paused flag and permission state.
    LaunchedEffect(state.trackingPaused, hasLocationPermission, state.loaded) {
        if (!state.loaded) return@LaunchedEffect
        if (!state.trackingPaused && hasLocationPermission) {
            LocationTrackingController.start(context)
        } else {
            LocationTrackingController.stop(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Drive for Change") },
                actions = {
                    IconButton(onClick = onOpenStats) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Your stats")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Today's donation",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Text(
                    text = formatMoney(state.todayDonation),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${formatMiles(state.todayMiles)} driven today",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )

                Spacer(modifier = Modifier.height(32.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Lifetime total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = formatMoney(state.lifetimeDonation),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            BreakdownStat(label = "This month", value = formatMoney(state.monthDonation))
                            BreakdownStat(label = "This year", value = formatMoney(state.yearDonation))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SuggestionChip(
                    onClick = onOpenSettings,
                    label = { Text("Supporting: ${state.charity.name}") },
                    colors = SuggestionChipDefaults.suggestionChipColors(),
                )

                Spacer(modifier = Modifier.height(32.dp))

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = if (state.trackingPaused) "Tracking paused" else "Tracking active",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (state.trackingPaused) "Resume to keep counting your miles" else "Pause anytime, like on a long trip",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                            if (!hasLocationPermission) {
                                Text(
                                    text = "Location permission needed to track mileage",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Switch(
                            checked = !state.trackingPaused,
                            onCheckedChange = { resume ->
                                if (resume) {
                                    if (hasLocationPermission) {
                                        viewModel.setTrackingPaused(false)
                                    } else {
                                        permissionLauncher.launch(locationPermissions())
                                    }
                                } else {
                                    viewModel.setTrackingPaused(true)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

private fun locationPermissions(): Array<String> {
    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    return permissions.toTypedArray()
}
