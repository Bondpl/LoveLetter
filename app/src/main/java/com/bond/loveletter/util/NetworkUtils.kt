package com.bond.loveletter.util

import com.bond.loveletter.LoveLetterApplication
import com.bond.loveletter.R
import com.bond.loveletter.data.model.ApiErrorCode
import com.bond.loveletter.data.model.ApiErrorResponse
import com.bond.loveletter.data.network.NetworkClient
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class AppException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data class Network(val original: Throwable) : AppException(original.message.orEmpty(), original)
    data class Timeout(val original: Throwable) : AppException("Connection timed out", original)
    data class Api(
        val errorCode: ApiErrorCode,
        val serverMessage: String,
        val httpStatus: Int
    ) : AppException(serverMessage)
    data class PartnerDisconnected(val reason: String = "You are not in an active pair. Your partner has disconnected") : AppException(reason)
    data class General(val errorText: String) : AppException(errorText)
}

fun Throwable.toAppException(): AppException {
    if (this is AppException) return this

    var cause: Throwable? = this
    while (cause != null) {
        when (cause) {
            is UnknownHostException -> return AppException.Network(cause)
            is SocketTimeoutException -> return AppException.Timeout(cause)
            is ConnectException -> return AppException.Network(cause)
            is IOException -> {
                val msg = cause.message.orEmpty()
                if (msg.contains("Unable to resolve host", ignoreCase = true) ||
                    msg.contains("No address associated", ignoreCase = true) ||
                    msg.contains("Network is unreachable", ignoreCase = true)
                ) {
                    return AppException.Network(cause)
                }
            }
        }
        cause = cause.cause
    }

    val msg = this.message.orEmpty()
    if (msg.contains("Unable to resolve host", ignoreCase = true) ||
        msg.contains("Failed to connect", ignoreCase = true) ||
        msg.contains("No address associated", ignoreCase = true) ||
        msg.contains("route to host", ignoreCase = true) ||
        msg.contains("Network is unreachable", ignoreCase = true) ||
        msg.contains("Software caused connection abort", ignoreCase = true)
    ) {
        return AppException.Network(this)
    }

    return AppException.General(msg.ifBlank { "An unexpected error occurred" })
}

fun <T> Response<T>.toApiException(): AppException.Api {
    val rawJson = try {
        errorBody()?.string()
    } catch (_: Exception) {
        null
    }

    val parsed = if (!rawJson.isNullOrBlank()) {
        try {
            NetworkClient.json.decodeFromString<ApiErrorResponse>(rawJson)
        } catch (_: Exception) {
            null
        }
    } else null

    val code = parsed?.apiErrorCode ?: when (code()) {
        400 -> ApiErrorCode.VALIDATION_ERROR
        401 -> ApiErrorCode.UNAUTHORIZED
        403 -> ApiErrorCode.FORBIDDEN
        404 -> ApiErrorCode.DRAWING_NOT_FOUND
        429 -> ApiErrorCode.RATE_LIMIT_EXCEEDED
        in 500..599 -> ApiErrorCode.INTERNAL_ERROR
        else -> ApiErrorCode.UNKNOWN
    }

    val message = parsed?.error ?: message().ifBlank { "HTTP error ${code()}" }

    return AppException.Api(
        errorCode = code,
        serverMessage = message,
        httpStatus = code()
    )
}

private fun getStringSafe(resId: Int, default: String): String {
    return try {
        LoveLetterApplication.instance.getString(resId)
    } catch (_: Exception) {
        default
    }
}

fun Throwable.toUserFriendlyMessage(): String {
    return when (val appEx = this.toAppException()) {
        is AppException.Network -> getStringSafe(R.string.error_no_internet, "No internet connection. Please check your network.")
        is AppException.Timeout -> getStringSafe(R.string.error_timeout, "Connection timed out. Please try again.")
        is AppException.PartnerDisconnected -> appEx.reason
        is AppException.Api -> when (appEx.errorCode) {
            ApiErrorCode.PAIR_NOT_FOUND -> getStringSafe(R.string.error_pair_not_found, "Invalid or expired pair code.")
            ApiErrorCode.PAIR_FULL -> getStringSafe(R.string.error_pair_full, "This pair is already full.")
            ApiErrorCode.PAIR_RACE_CONDITION -> getStringSafe(R.string.error_pair_race_condition, "This pair was just filled by someone else.")
            ApiErrorCode.NOT_IN_PAIR -> getStringSafe(R.string.error_not_in_pair, "You are not in an active pair. Your partner has disconnected.")
            ApiErrorCode.RATE_LIMIT_EXCEEDED -> getStringSafe(R.string.error_rate_limit_exceeded, "You are doing this too fast. Please wait a minute.")
            ApiErrorCode.FILE_TOO_LARGE -> getStringSafe(R.string.error_file_too_large, "The image is too large to send.")
            ApiErrorCode.TOKEN_REVOKED -> getStringSafe(R.string.error_token_revoked, "Session expired. Please connect again.")
            ApiErrorCode.UNAUTHORIZED -> getStringSafe(R.string.error_unauthorized, "Authentication failed. Please reconnect.")
            ApiErrorCode.VALIDATION_ERROR -> appEx.serverMessage.ifBlank { getStringSafe(R.string.error_validation_default, "Invalid request data.") }
            ApiErrorCode.INTERNAL_ERROR -> getStringSafe(R.string.error_internal_default, "Server error. Please try again later.")
            else -> {
                if (appEx.httpStatus in 500..599) {
                    getStringSafe(R.string.error_server_unavailable, "Server is currently unavailable. Please try again later.")
                } else {
                    appEx.serverMessage.ifBlank { getStringSafe(R.string.error_unexpected, "Something went wrong. Please try again.") }
                }
            }
        }
        is AppException.General -> appEx.errorText.ifBlank { getStringSafe(R.string.error_unexpected, "Something went wrong. Please try again.") }
    }
}
