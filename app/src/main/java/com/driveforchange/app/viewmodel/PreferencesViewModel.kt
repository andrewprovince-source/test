package com.driveforchange.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driveforchange.app.data.Charities
import com.driveforchange.app.data.DonationRepository
import com.driveforchange.app.data.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class PreferencesViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val donationRepository: DonationRepository,
) : ViewModel() {

    var perMileRate by mutableStateOf(0.10)
        private set
    var dailyCap by mutableStateOf(10.0)
        private set
    var charityId by mutableStateOf(Charities.default.id)
        private set
    var isLoaded by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            perMileRate = prefs.perMileRate
            // Rounds any pre-existing fractional cap (from before the cap slider snapped to
            // whole dollars) back onto a clean value as soon as this screen loads.
            dailyCap = prefs.dailyCap.roundToInt().toDouble()
            charityId = prefs.charityId
            isLoaded = true
        }
    }

    fun onRateChange(value: Double) {
        perMileRate = value
    }

    fun onCapChange(value: Double) {
        dailyCap = value
    }

    fun onCharitySelected(id: String) {
        charityId = id
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            userPreferencesRepository.savePreferences(perMileRate, dailyCap, charityId)
            donationRepository.reclampToday()
            onSaved()
        }
    }
}
