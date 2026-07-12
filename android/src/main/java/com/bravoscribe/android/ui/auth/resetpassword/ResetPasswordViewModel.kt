package com.bravoscribe.android.ui.auth.resetpassword

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bravoscribe.android.domain.repository.AuthRepository
import com.bravoscribe.android.ui.util.isValidPassword
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResetPasswordUiState(
    val newPassword: String = "",
    val confirmPassword: String = "",
    val passwordError: String? = null,
    val confirmError: String? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false,
)

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val token: String = checkNotNull(savedStateHandle["token"])

    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    fun onNewPasswordChange(value: String) {
        _uiState.update { it.copy(newPassword = value, passwordError = null, errorMessage = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, confirmError = null, errorMessage = null) }
    }

    fun resetPassword() {
        val state = _uiState.value
        val passwordError = if (!isValidPassword(state.newPassword)) "Password must be 8–128 characters" else null
        val confirmError = if (state.confirmPassword != state.newPassword) "Passwords do not match" else null

        if (passwordError != null || confirmError != null) {
            _uiState.update { it.copy(passwordError = passwordError, confirmError = confirmError) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            authRepository.confirmPasswordReset(token, state.newPassword)
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false, success = true) }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = "This link is invalid or expired. Request a new one.",
                        )
                    }
                }
        }
    }
}
