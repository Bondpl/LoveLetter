package com.bond.loveletter.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bond.loveletter.LoveLetterApplication
import com.bond.loveletter.R
import com.bond.loveletter.data.repository.AuthRepository
import com.bond.loveletter.data.storage.AuthState
import com.bond.loveletter.data.storage.SessionManager
import com.bond.loveletter.di.NetworkModule
import com.bond.loveletter.util.toUserFriendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val authRepository: AuthRepository = NetworkModule.getAuthRepository()
    private val sessionManager: SessionManager = NetworkModule.getSessionManager()

    val authState: StateFlow<AuthState> = sessionManager.authState

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            authState.collect { state ->
                if (state is AuthState.Unauthenticated) {
                    sessionManager.consumeDisconnectReason()?.let { reason ->
                        _errorMessage.value = reason
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
            val msg = try {
                LoveLetterApplication.instance.getString(R.string.error_pair_code_length)
            } catch (_: Exception) {
                "Pair code must be 6 digits"
            }
            _errorMessage.value = msg
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

    fun leavePair() {
        viewModelScope.launch {
            authRepository.leavePair()
        }
    }
}
