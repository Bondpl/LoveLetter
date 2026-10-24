package com.example.loveletter

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object DrawingUpdateBus {
    private val _drawingUpdates = MutableSharedFlow<ByteArray>(replay = 1, extraBufferCapacity = 1)
    val drawingUpdates: SharedFlow<ByteArray> = _drawingUpdates.asSharedFlow()

    fun notifyNewDrawing(imageBytes: ByteArray) {
        _drawingUpdates.tryEmit(imageBytes)
    }

    fun clear() {
        _drawingUpdates.resetReplayCache()
    }
}
