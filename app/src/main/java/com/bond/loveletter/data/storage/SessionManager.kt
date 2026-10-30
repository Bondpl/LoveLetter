package com.bond.loveletter.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.bond.loveletter.widget.LoveLetterWidget
import com.bond.loveletter.data.crypto.CryptoManager
import com.bond.loveletter.data.crypto.SecureKeyStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AuthState {
    data object Unauthenticated : AuthState
    data class WaitingForPartner(val pairCode: String) : AuthState
    data class Paired(val partnerPublicKey: String) : AuthState
}

class SessionManager private constructor(context: Context) {
    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _authState = MutableStateFlow(computeCurrentAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private fun computeCurrentAuthState(): AuthState {
        val token = getToken()
        if (token.isNullOrBlank()) {
            return AuthState.Unauthenticated
        }
        val isPaired = prefs.getBoolean(KEY_IS_PAIRED, false)
        val partnerKey = getPartnerPublicKey()
        if (isPaired && !partnerKey.isNullOrBlank()) {
            return AuthState.Paired(partnerKey)
        }
        val pairCode = getPairCode()
        if (!pairCode.isNullOrBlank()) {
            return AuthState.WaitingForPartner(pairCode)
        }
        return AuthState.Unauthenticated
    }

    private fun refreshAuthState() {
        _authState.value = computeCurrentAuthState()
    }

    fun getToken(): String? {
        val raw = prefs.getString(KEY_TOKEN, null) ?: return null
        return SecureKeyStorage.decrypt(raw)
    }

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun getPairCode(): String? = prefs.getString(KEY_PAIR_CODE, null)

    fun getPairId(): String? = prefs.getString(KEY_PAIR_ID, null)

    fun savePairId(pairId: String) {
        prefs.edit().putString(KEY_PAIR_ID, pairId).apply()
    }

    fun isPaired(): Boolean = prefs.getBoolean(KEY_IS_PAIRED, false)

    fun getMyPublicKey(): String? = prefs.getString(KEY_MY_PUBLIC_KEY, null)

    fun getMyPrivateKey(): String? {
        val raw = prefs.getString(KEY_MY_PRIVATE_KEY, null) ?: return null
        return SecureKeyStorage.decrypt(raw)
    }

    fun getPartnerPublicKey(): String? = prefs.getString(KEY_PARTNER_PUBLIC_KEY, null)

    fun getFcmToken(): String? = prefs.getString(KEY_FCM_TOKEN, null)

    fun setFcmToken(token: String?) {
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun getOrCreateKeyPair(): Pair<String, String> {
        val pub = getMyPublicKey()
        val priv = getMyPrivateKey()
        if (!pub.isNullOrBlank() && !priv.isNullOrBlank()) {
            return Pair(pub, priv)
        }
        val generated = CryptoManager.generateKeyPair()
        saveMyKeyPair(generated.publicKeyBase64, generated.privateKeyBase64)
        return Pair(generated.publicKeyBase64, generated.privateKeyBase64)
    }

    fun saveMyKeyPair(publicKeyBase64: String, privateKeyBase64: String) {
        prefs.edit()
            .putString(KEY_MY_PUBLIC_KEY, publicKeyBase64)
            .putString(KEY_MY_PRIVATE_KEY, SecureKeyStorage.encrypt(privateKeyBase64))
            .apply()
    }

    fun savePairCreated(token: String, userId: String, pairCode: String) {
        prefs.edit()
            .putString(KEY_TOKEN, SecureKeyStorage.encrypt(token))
            .putString(KEY_USER_ID, userId)
            .putString(KEY_PAIR_CODE, pairCode)
            .putBoolean(KEY_IS_PAIRED, false)
            .remove(KEY_PARTNER_PUBLIC_KEY)
            .remove(KEY_PAIR_ID)
            .apply()
        refreshAuthState()
    }

    fun savePairJoined(token: String, userId: String, partnerPublicKey: String, pairId: String? = null) {
        val editor = prefs.edit()
            .putString(KEY_TOKEN, SecureKeyStorage.encrypt(token))
            .putString(KEY_USER_ID, userId)
            .putBoolean(KEY_IS_PAIRED, true)
            .putString(KEY_PARTNER_PUBLIC_KEY, partnerPublicKey)
            .remove(KEY_PAIR_CODE)
        if (!pairId.isNullOrBlank()) {
            editor.putString(KEY_PAIR_ID, pairId)
        } else {
            editor.remove(KEY_PAIR_ID)
        }
        editor.apply()
        refreshAuthState()
    }

    fun updatePartnerConnected(partnerPublicKey: String, pairId: String? = null) {
        val editor = prefs.edit()
            .putBoolean(KEY_IS_PAIRED, true)
            .putString(KEY_PARTNER_PUBLIC_KEY, partnerPublicKey)
            .remove(KEY_PAIR_CODE)
        if (!pairId.isNullOrBlank()) {
            editor.putString(KEY_PAIR_ID, pairId)
        }
        editor.apply()
        refreshAuthState()
    }

    private val _lastDisconnectReason = MutableStateFlow<String?>(null)

    fun clearSession(reason: String? = null) {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_PAIR_CODE)
            .remove(KEY_IS_PAIRED)
            .remove(KEY_PARTNER_PUBLIC_KEY)
            .remove(KEY_MY_PUBLIC_KEY)
            .remove(KEY_MY_PRIVATE_KEY)
            .remove(KEY_PAIR_ID)
            .apply()
        _lastDisconnectReason.value = reason
        LoveLetterWidget.clearWidget(appContext)
        refreshAuthState()
    }

    fun consumeDisconnectReason(): String? {
        val reason = _lastDisconnectReason.value
        _lastDisconnectReason.value = null
        return reason
    }

    fun onSessionExpired() {
        clearSession()
    }

    companion object {
        private const val PREFS_NAME = "loveletter_session"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_PAIR_CODE = "pair_code"
        private const val KEY_PAIR_ID = "pair_id"
        private const val KEY_IS_PAIRED = "is_paired"
        private const val KEY_MY_PUBLIC_KEY = "my_public_key"
        private const val KEY_MY_PRIVATE_KEY = "my_private_key"
        private const val KEY_PARTNER_PUBLIC_KEY = "partner_public_key"
        private const val KEY_FCM_TOKEN = "fcm_token"

        @Volatile
        private var INSTANCE: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
