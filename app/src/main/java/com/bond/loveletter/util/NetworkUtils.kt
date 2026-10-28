package com.bond.loveletter.util

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun Throwable.toUserFriendlyMessage(): String {
    var cause: Throwable? = this
    while (cause != null) {
        when (cause) {
            is UnknownHostException -> return "No internet connection. Please check your network."
            is SocketTimeoutException -> return "Connection timed out. Please try again."
            is ConnectException -> return "Unable to connect to server. Please check your internet connection."
            is IOException -> {
                val msg = cause.message.orEmpty()
                if (msg.contains("Unable to resolve host", ignoreCase = true) ||
                    msg.contains("No address associated", ignoreCase = true) ||
                    msg.contains("Network is unreachable", ignoreCase = true)
                ) {
                    return "No internet connection. Please check your network."
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
        return "No internet connection. Please check your network."
    }

    return msg.ifBlank { "Something went wrong. Please try again." }
}
