package com.bravoscribe.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bravoscribe.android.data.local.SessionManager
import com.bravoscribe.android.domain.model.User
import com.bravoscribe.android.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data class LoggedIn(val user: User) : AuthUiState
    data class LoggedOut(val sessionExpired: Boolean = false) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val user = authRepository.tryRestoreSession()
            _uiState.value = if (user != null) AuthUiState.LoggedIn(user) else AuthUiState.LoggedOut()
        }
        viewModelScope.launch {
            sessionManager.sessionExpired.collect {
                _uiState.value = AuthUiState.LoggedOut(sessionExpired = true)
            }
        }
    }

    fun onAuthenticated(user: User) {
        _uiState.value = AuthUiState.LoggedIn(user)
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = AuthUiState.LoggedOut()
        }
    }

    fun consumeSessionExpired() {
        val current = _uiState.value
        if (current is AuthUiState.LoggedOut && current.sessionExpired) {
            _uiState.value = AuthUiState.LoggedOut(sessionExpired = false)
        }
    }
}
