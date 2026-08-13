package com.driveforchange.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driveforchange.app.data.Charities
import com.driveforchange.app.data.Charity
import com.driveforchange.app.data.DonationRepository
import com.driveforchange.app.data.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val todayMiles: Double = 0.0,
    val todayDonation: Double = 0.0,
    val monthDonation: Double = 0.0,
    val yearDonation: Double = 0.0,
    val lifetimeDonation: Double = 0.0,
    val charity: Charity = Charities.default,
    val trackingPaused: Boolean = false,
    val loaded: Boolean = false,
)

class DashboardViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    donationRepository: DonationRepository,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        donationRepository.totalsFlow(),
        userPreferencesRepository.userPreferencesFlow,
    ) { totals, prefs ->
        DashboardUiState(
            todayMiles = totals.todayMiles,
            todayDonation = totals.todayDonation,
            monthDonation = totals.monthDonation,
            yearDonation = totals.yearDonation,
            lifetimeDonation = totals.lifetimeDonation,
            charity = Charities.byId(prefs.charityId),
            trackingPaused = prefs.trackingPaused,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    fun setTrackingPaused(paused: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setTrackingPaused(paused)
        }
    }
}
