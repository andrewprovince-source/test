package com.driveforchange.app.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driveforchange.app.viewmodel.PreferencesViewModel

@Composable
fun PreferencesSetupScreen(
    viewModel: PreferencesViewModel,
    onSaved: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text(
                text = "Set up your giving",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            PerMileRateSection(rate = viewModel.perMileRate, onRateChange = viewModel::onRateChange)
            DailyCapSection(cap = viewModel.dailyCap, onCapChange = viewModel::onCapChange)
            CharitySelectionSection(selectedId = viewModel.charityId, onSelect = viewModel::onCharitySelected)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "This is a demo version. No real donations are being processed yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }

            Button(
                onClick = { viewModel.save(onSaved) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Continue")
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
