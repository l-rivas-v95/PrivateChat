package com.lrv.privatechat.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

class ImageBase64Encoder(private val context: Context) {

    companion object {
        const val MAX_AVATAR_BASE64_CHARS = 8_000
        private const val AVATAR_SIZE_PX = 96
        private val JPEG_QUALITIES = listOf(55, 45, 35, 25)
    }

    fun encodeAvatarToBase64(uri: Uri): String? {
        return runCatching {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, AVATAR_SIZE_PX, AVATAR_SIZE_PX, true)

            JPEG_QUALITIES.asSequence()
                .mapNotNull { quality -> encodeJpeg(scaledBitmap, quality) }
                .firstOrNull { encoded -> encoded.length <= MAX_AVATAR_BASE64_CHARS }
        }.getOrNull()
    }

    private fun encodeJpeg(bitmap: Bitmap, quality: Int): String? {
        val outputStream = ByteArrayOutputStream()
        return if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)) {
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } else {
            null
        }
    }
}
