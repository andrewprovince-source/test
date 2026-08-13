package com.driveforchange.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.driveforchange.app.ui.preferences.CharitySelectionSection
import com.driveforchange.app.ui.preferences.DailyCapSection
import com.driveforchange.app.ui.preferences.PerMileRateSection
import com.driveforchange.app.viewmodel.PreferencesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PreferencesViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                if (viewModel.isLoaded) {
                    PerMileRateSection(rate = viewModel.perMileRate, onRateChange = viewModel::onRateChange)
                    DailyCapSection(cap = viewModel.dailyCap, onCapChange = viewModel::onCapChange)
                    CharitySelectionSection(selectedId = viewModel.charityId, onSelect = viewModel::onCharitySelected)

                    Button(
                        onClick = { viewModel.save(onSaved) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("Save changes")
                    }
                }
            }
        }
    }
}
