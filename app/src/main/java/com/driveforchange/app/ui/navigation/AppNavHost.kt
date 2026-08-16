package com.driveforchange.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.driveforchange.app.data.AppContainer
import com.driveforchange.app.ui.auth.SignUpScreen
import com.driveforchange.app.ui.dashboard.DashboardScreen
import com.driveforchange.app.ui.onboarding.OnboardingScreen
import com.driveforchange.app.ui.preferences.PreferencesSetupScreen
import com.driveforchange.app.ui.settings.SettingsScreen
import com.driveforchange.app.ui.stats.StatsScreen
import com.driveforchange.app.viewmodel.AppViewModelFactory
import com.driveforchange.app.viewmodel.DashboardViewModel
import com.driveforchange.app.viewmodel.OnboardingViewModel
import com.driveforchange.app.viewmodel.PreferencesViewModel
import com.driveforchange.app.viewmodel.SignUpViewModel
import com.driveforchange.app.viewmodel.StatsViewModel

@Composable
fun AppNavHost(container: AppContainer) {
    val factory = remember { AppViewModelFactory(container) }
    val userPreferences by container.userPreferencesRepository.userPreferencesFlow.collectAsState(initial = null)

    val prefs = userPreferences ?: return // wait for first DataStore emission before deciding start destination

    val startDestination = when {
        !prefs.onboardingComplete -> Routes.ONBOARDING
        !prefs.accountCreated -> Routes.SIGN_UP
        !prefs.preferencesConfigured -> Routes.PREFERENCES_SETUP
        else -> Routes.DASHBOARD
    }

    val navController: NavHostController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            val vm: OnboardingViewModel = viewModel(factory = factory)
            OnboardingScreen(
                onGetStarted = {
                    vm.completeOnboarding {
                        navController.navigate(Routes.SIGN_UP) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Routes.SIGN_UP) {
            val vm: SignUpViewModel = viewModel(factory = factory)
            SignUpScreen(
                viewModel = vm,
                onSignedUp = {
                    navController.navigate(Routes.PREFERENCES_SETUP) {
                        popUpTo(Routes.SIGN_UP) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.PREFERENCES_SETUP) {
            val vm: PreferencesViewModel = viewModel(factory = factory)
            PreferencesSetupScreen(
                viewModel = vm,
                onSaved = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.PREFERENCES_SETUP) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.DASHBOARD) {
            val vm: DashboardViewModel = viewModel(factory = factory)
            DashboardScreen(
                viewModel = vm,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenStats = { navController.navigate(Routes.STATS) }
            )
        }

        composable(Routes.SETTINGS) {
            val vm: PreferencesViewModel = viewModel(factory = factory)
            SettingsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(Routes.STATS) {
            val vm: StatsViewModel = viewModel(factory = factory)
            StatsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
