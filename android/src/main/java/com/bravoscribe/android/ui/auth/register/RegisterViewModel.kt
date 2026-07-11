package com.bravoscribe.android.ui.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bravoscribe.android.domain.model.User
import com.bravoscribe.android.domain.repository.AuthRepository
import com.bravoscribe.android.ui.util.isValidEmail
import com.bravoscribe.android.ui.util.isValidName
import com.bravoscribe.android.ui.util.isValidPassword
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, nameError = null, errorMessage = null) }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, emailError = null, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, errorMessage = null) }
    }

    fun register(onSuccess: (User) -> Unit) {
        val state = _uiState.value
        val nameError = if (!isValidName(state.name)) "Name must be at least 2 characters" else null
        val emailError = if (!isValidEmail(state.email)) "Enter a valid email address" else null
        val passwordError = if (!isValidPassword(state.password)) "Password must be 8–128 characters" else null

        if (nameError != null || emailError != null || passwordError != null) {
            _uiState.update { it.copy(nameError = nameError, emailError = emailError, passwordError = passwordError) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            authRepository.register(state.name.trim(), state.email.trim(), state.password)
                .onSuccess { user ->
                    _uiState.update { it.copy(isSubmitting = false) }
                    onSuccess(user)
                }
                .onFailure { error ->
                    val message = when ((error as? HttpException)?.code()) {
                        409 -> "An account with that email already exists"
                        400 -> "Password must be at least 8 characters"
                        else -> "Something went wrong. Please try again."
                    }
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = message) }
                }
        }
    }
}
