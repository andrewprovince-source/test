package com.driveforchange.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "drive_for_change_prefs")

data class UserPreferences(
    val email: String = "",
    val accountCreated: Boolean = false,
    val onboardingComplete: Boolean = false,
    val preferencesConfigured: Boolean = false,
    val perMileRate: Double = 0.10,
    val dailyCap: Double = 10.0,
    val charityId: String = Charities.default.id,
    val trackingPaused: Boolean = false,
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val EMAIL = stringPreferencesKey("email")
        val ACCOUNT_CREATED = booleanPreferencesKey("account_created")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val PREFERENCES_CONFIGURED = booleanPreferencesKey("preferences_configured")
        val PER_MILE_RATE = doublePreferencesKey("per_mile_rate")
        val DAILY_CAP = doublePreferencesKey("daily_cap")
        val CHARITY_ID = stringPreferencesKey("charity_id")
        val TRACKING_PAUSED = booleanPreferencesKey("tracking_paused")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            email = prefs[Keys.EMAIL] ?: "",
            accountCreated = prefs[Keys.ACCOUNT_CREATED] ?: false,
            onboardingComplete = prefs[Keys.ONBOARDING_COMPLETE] ?: false,
            preferencesConfigured = prefs[Keys.PREFERENCES_CONFIGURED] ?: false,
            perMileRate = prefs[Keys.PER_MILE_RATE] ?: 0.10,
            dailyCap = prefs[Keys.DAILY_CAP] ?: 10.0,
            charityId = prefs[Keys.CHARITY_ID] ?: Charities.default.id,
            trackingPaused = prefs[Keys.TRACKING_PAUSED] ?: false,
        )
    }

    suspend fun setOnboardingComplete() {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = true }
    }

    suspend fun createAccount(email: String) {
        context.dataStore.edit {
            it[Keys.EMAIL] = email
            it[Keys.ACCOUNT_CREATED] = true
        }
    }

    suspend fun savePreferences(perMileRate: Double, dailyCap: Double, charityId: String) {
        context.dataStore.edit {
            it[Keys.PER_MILE_RATE] = perMileRate
            it[Keys.DAILY_CAP] = dailyCap
            it[Keys.CHARITY_ID] = charityId
            it[Keys.PREFERENCES_CONFIGURED] = true
        }
    }

    suspend fun setCharity(charityId: String) {
        context.dataStore.edit { it[Keys.CHARITY_ID] = charityId }
    }

    suspend fun setTrackingPaused(paused: Boolean) {
        context.dataStore.edit { it[Keys.TRACKING_PAUSED] = paused }
    }
}
