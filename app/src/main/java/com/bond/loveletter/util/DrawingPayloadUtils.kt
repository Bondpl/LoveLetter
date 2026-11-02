package com.bond.loveletter.util

object DrawingPayloadUtils {
    val PHOTO_PREFIX = "LL_PHOTO_V1:".toByteArray(Charsets.UTF_8)
    val PHOTO_ONCE_PREFIX = "LL_PHOTO_ONCE_V1:".toByteArray(Charsets.UTF_8)

    fun isPhotoPayload(bytes: ByteArray): Boolean {
        return isRegularPhoto(bytes) || isViewOncePhoto(bytes)
    }

    fun isViewOncePhoto(bytes: ByteArray): Boolean {
        if (bytes.size < PHOTO_ONCE_PREFIX.size) return false
        for (i in PHOTO_ONCE_PREFIX.indices) {
            if (bytes[i] != PHOTO_ONCE_PREFIX[i]) return false
        }
        return true
    }

    private fun isRegularPhoto(bytes: ByteArray): Boolean {
        if (bytes.size < PHOTO_PREFIX.size) return false
        for (i in PHOTO_PREFIX.indices) {
            if (bytes[i] != PHOTO_PREFIX[i]) return false
        }
        return true
    }

    fun extractPhotoBytes(bytes: ByteArray): ByteArray {
        val prefixSize = if (isViewOncePhoto(bytes)) {
            PHOTO_ONCE_PREFIX.size
        } else if (isRegularPhoto(bytes)) {
            PHOTO_PREFIX.size
        } else {
            0
        }
        return bytes.copyOfRange(prefixSize, bytes.size)
    }

    fun wrapPhotoPayload(photoBytes: ByteArray, isViewOnce: Boolean): ByteArray {
        val prefix = if (isViewOnce) PHOTO_ONCE_PREFIX else PHOTO_PREFIX
        val result = ByteArray(prefix.size + photoBytes.size)
        System.arraycopy(prefix, 0, result, 0, prefix.size)
        System.arraycopy(photoBytes, 0, result, prefix.size, photoBytes.size)
        return result
    }
}
