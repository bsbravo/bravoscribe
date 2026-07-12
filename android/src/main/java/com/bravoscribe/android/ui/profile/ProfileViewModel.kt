package com.bravoscribe.android.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bravoscribe.android.domain.model.Stats
import com.bravoscribe.android.domain.model.User
import com.bravoscribe.android.domain.repository.AuthRepository
import com.bravoscribe.android.domain.repository.JournalRepository
import com.bravoscribe.android.ui.util.isValidName
import com.bravoscribe.android.ui.util.isValidPassword
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val stats: Stats? = null,
    val isSavingName: Boolean = false,
    val nameError: String? = null,
    val isChangingPassword: Boolean = false,
    val passwordError: String? = null,
    val snackbarMessage: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val journalRepository: JournalRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val user = authRepository.getMe().getOrNull()
            val stats = journalRepository.getStats().getOrNull()
            _uiState.update { it.copy(isLoading = false, user = user, stats = stats) }
        }
    }

    fun updateName(name: String, onDone: () -> Unit) {
        if (!isValidName(name)) {
            _uiState.update { it.copy(nameError = "Name must be at least 2 characters") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingName = true, nameError = null) }
            authRepository.updateProfile(name.trim())
                .onSuccess { user ->
                    _uiState.update { it.copy(isSavingName = false, user = user) }
                    onDone()
                }
                .onFailure {
                    _uiState.update { it.copy(isSavingName = false, nameError = "Couldn't update your name.") }
                }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String, confirmPassword: String, onDone: () -> Unit) {
        val error = when {
            currentPassword.isBlank() -> "Enter your current password"
            !isValidPassword(newPassword) -> "Password must be 8–128 characters"
            confirmPassword != newPassword -> "Passwords do not match"
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(passwordError = error) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isChangingPassword = true, passwordError = null) }
            authRepository.changePassword(currentPassword, newPassword)
                .onSuccess {
                    _uiState.update {
                        it.copy(isChangingPassword = false, snackbarMessage = "Password changed.")
                    }
                    onDone()
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isChangingPassword = false, passwordError = "Couldn't change your password.")
                    }
                }
        }
    }

    fun consumeSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun clearNameError() {
        _uiState.update { it.copy(nameError = null) }
    }

    fun clearPasswordError() {
        _uiState.update { it.copy(passwordError = null) }
    }
}
