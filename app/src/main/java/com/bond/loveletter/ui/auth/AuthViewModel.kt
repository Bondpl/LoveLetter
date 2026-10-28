package com.bond.loveletter.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bond.loveletter.data.repository.AuthRepository
import com.bond.loveletter.data.storage.AuthState
import com.bond.loveletter.data.storage.SessionManager
import com.bond.loveletter.di.NetworkModule
import com.bond.loveletter.util.toUserFriendlyMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val authRepository: AuthRepository = NetworkModule.getAuthRepository()
    private val sessionManager: SessionManager = NetworkModule.getSessionManager()

    val authState: StateFlow<AuthState> = sessionManager.authState

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private var pollingJob: Job? = null

    init {
        viewModelScope.launch {
            authState.collect { state ->
                when (state) {
                    is AuthState.WaitingForPartner -> startWaitingPolling()
                    is AuthState.Paired -> stopPolling()
                    is AuthState.Unauthenticated -> {
                        stopPolling()
                        sessionManager.consumeDisconnectReason()?.let { reason ->
                            _errorMessage.value = reason
                        }
                    }
                }
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun createPair() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = authRepository.createPair()
            _isLoading.value = false
            result.onFailure { error ->
                _errorMessage.value = error.toUserFriendlyMessage()
            }
        }
    }

    fun joinPair(pairCode: String) {
        val trimmed = pairCode.trim()
        if (trimmed.length != 6) {
            _errorMessage.value = "Pair code must be 6 digits"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = authRepository.joinPair(trimmed)
            _isLoading.value = false
            result.onFailure { error ->
                _errorMessage.value = error.toUserFriendlyMessage()
            }
        }
    }

    fun cancelWaiting() {
        stopPolling()
        viewModelScope.launch {
            authRepository.leavePair()
        }
    }

    fun leavePair() {
        stopPolling()
        viewModelScope.launch {
            authRepository.leavePair()
        }
    }

    private fun startWaitingPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(3000)
                try {
                    authRepository.checkPairStatus()
                } catch (_: Exception) {
                    // Suppress polling network errors
                }
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
