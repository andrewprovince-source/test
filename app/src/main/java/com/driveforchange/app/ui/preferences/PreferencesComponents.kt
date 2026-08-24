package com.driveforchange.app.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driveforchange.app.data.Charities
import com.driveforchange.app.data.Charity
import java.util.Locale
import kotlin.math.roundToInt

private fun formatMoney(value: Double, decimals: Int = 2): String =
    String.format(Locale.US, "$%.${decimals}f", value)

@Composable
fun PerMileRateSection(rate: Double, onRateChange: (Double) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Per-mile donation rate", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${formatMoney(rate, 2)} per mile",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Slider(
            value = rate.toFloat(),
            onValueChange = { onRateChange(it.toDouble()) },
            valueRange = 0.01f..0.50f,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.05, 0.10, 0.25).forEach { preset ->
                FilterChip(
                    selected = rate == preset,
                    onClick = { onRateChange(preset) },
                    label = { Text(formatMoney(preset, 2)) },
                )
            }
        }
    }
}

@Composable
fun DailyCapSection(cap: Double, onCapChange: (Double) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Daily donation cap", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${formatMoney(cap, 0)} per day max",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Caps what you donate on unusually long driving days, like road trips.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Slider(
            value = cap.toFloat(),
            onValueChange = { onCapChange(it.roundToInt().toDouble()) },
            valueRange = 1f..20f,
            steps = 18, // whole-dollar stops between $1 and $20, matching the rounded-dollar label above
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5.0, 10.0, 15.0).forEach { preset ->
                FilterChip(
                    selected = cap == preset,
                    onClick = { onCapChange(preset) },
                    label = { Text(formatMoney(preset, 0)) },
                )
            }
        }
    }
}

@Composable
fun CharitySelectionSection(selectedId: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Choose your charity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Charities.starterList.forEach { charity ->
            CharityRow(
                charity = charity,
                selected = charity.id == selectedId,
                onSelect = { onSelect(charity.id) },
            )
        }
    }
}

@Composable
private fun CharityRow(charity: Charity, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(charity.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                charity.cause,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}
