package com.bond.loveletter.notification

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class PhotoUpdate(
    val bytes: ByteArray,
    val isViewOnce: Boolean,
    val iv: String? = null
)

object DrawingUpdateBus {
    private val _drawingUpdates = MutableSharedFlow<ByteArray>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val drawingUpdates: SharedFlow<ByteArray> = _drawingUpdates.asSharedFlow()

    private val _photoUpdates = MutableSharedFlow<PhotoUpdate>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val photoUpdates: SharedFlow<PhotoUpdate> = _photoUpdates.asSharedFlow()

    fun notifyNewDrawing(imageBytes: ByteArray) {
        _drawingUpdates.tryEmit(imageBytes)
    }

    fun notifyNewPhoto(imageBytes: ByteArray, isViewOnce: Boolean = false, iv: String? = null) {
        _photoUpdates.tryEmit(PhotoUpdate(imageBytes, isViewOnce, iv))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun clear() {
        _drawingUpdates.resetReplayCache()
        _photoUpdates.resetReplayCache()
    }
}
