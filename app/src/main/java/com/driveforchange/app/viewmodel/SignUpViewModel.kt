package com.driveforchange.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driveforchange.app.data.UserPreferencesRepository
import kotlinx.coroutines.launch

class SignUpViewModel(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun onEmailChange(value: String) {
        email = value
        errorMessage = null
    }

    fun onPasswordChange(value: String) {
        password = value
        errorMessage = null
    }

    fun signUp(onSuccess: () -> Unit) {
        val trimmedEmail = email.trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            errorMessage = "Enter a valid email address."
            return
        }
        if (password.length < 6) {
            errorMessage = "Password must be at least 6 characters."
            return
        }
        viewModelScope.launch {
            userPreferencesRepository.createAccount(trimmedEmail)
            onSuccess()
        }
    }
}
