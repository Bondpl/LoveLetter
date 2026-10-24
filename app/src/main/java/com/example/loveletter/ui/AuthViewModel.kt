package com.example.loveletter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loveletter.NetworkModule
import com.example.loveletter.data.repository.AuthRepository
import com.example.loveletter.data.storage.AuthState
import com.example.loveletter.data.storage.SessionManager
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
            result.onFailure {
                _errorMessage.value = it.message ?: "Failed to create pair"
            }
            _isLoading.value = false
        }
    }

    fun joinPair(pairCode: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = authRepository.joinPair(pairCode)
            result.onFailure {
                _errorMessage.value = it.message ?: "Failed to join pair"
            }
            _isLoading.value = false
        }
    }

    fun leavePair() {
        viewModelScope.launch {
            _isLoading.value = true
            stopPolling()
            authRepository.leavePair()
            _isLoading.value = false
        }
    }

    private fun startWaitingPolling() {
        stopPolling()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(3000)
                val result = authRepository.checkPairStatus()
                if (result.isSuccess && result.getOrNull()?.isPaired == true) {
                    break
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
