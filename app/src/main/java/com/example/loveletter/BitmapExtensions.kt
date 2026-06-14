package com.example.loveletter

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream


fun ImageBitmap.toPngByteArray(): ByteArray {
	val androidBitmap = this.asAndroidBitmap()
	val stream = ByteArrayOutputStream()
	androidBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
	return stream.toByteArray()
}
