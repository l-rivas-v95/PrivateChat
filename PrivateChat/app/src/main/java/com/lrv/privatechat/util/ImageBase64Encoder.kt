package com.lrv.privatechat.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

class ImageBase64Encoder(private val context: Context) {

    fun encodeAvatarToBase64(uri: Uri): String? {
        return runCatching {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 256, 256, true)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }
}
