package com.lrv.privatechat.network

import android.util.Base64
import com.lrv.privatechat.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Cliente HTTP para subida y descarga de ficheros cifrados.
 *
 * Deriva la URL base HTTPS a partir de CHAT_SERVER_URL:
 *   wss://tu-dominio.com  ->  https://tu-dominio.com
 *   ws://10.0.2.2:8080    ->  http://10.0.2.2:8080  (dev sin TLS)
 */
object ChatFileClient {

    private val client = OkHttpClient()

    private val baseHttpUrl: String by lazy {
        BuildConfig.CHAT_SERVER_URL
            .replace("wss://", "https://")
            .replace("ws://", "http://")
    }

    /**
     * Sube [cipherBytes] al servidor.
     * @return fileId devuelto por el servidor, o null si falla.
     */
    fun upload(recipient: String, cipherBytes: ByteArray): String? {
        return runCatching {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("recipient", recipient)
                .addFormDataPart(
                    "file",
                    "attachment",
                    cipherBytes.toRequestBody("application/octet-stream".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("$baseHttpUrl/upload")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string() ?: return null)
                json.optString("fileId").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    /**
     * Descarga y borra del servidor el fichero con [fileId].
     * @return bytes cifrados, o null si no existe o falla.
     */
    fun download(fileId: String): ByteArray? {
        return runCatching {
            val request = Request.Builder()
                .url("$baseHttpUrl/file/$fileId")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.bytes()
            }
        }.getOrNull()
    }
}
