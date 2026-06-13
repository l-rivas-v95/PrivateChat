package com.lrv.privatechat.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class ChatWebSocketClient(
    private val onMessageReceived: (String) -> Unit,
    private val onStatusChanged: (String) -> Unit
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    fun connect(username: String) {
        val previousSocket = webSocket
        webSocket = null
        previousSocket?.close(1000, "Cierre normal")

        val request = Request.Builder()
            .url("ws://x:8080/chat?user=$username")
            .build()

        val nextSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (this@ChatWebSocketClient.webSocket == webSocket) {
                    onStatusChanged("Conectado")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (this@ChatWebSocketClient.webSocket == webSocket) {
                    onMessageReceived(text)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (this@ChatWebSocketClient.webSocket == webSocket) {
                    this@ChatWebSocketClient.webSocket = null
                    onStatusChanged("Error: ${t.message}")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (this@ChatWebSocketClient.webSocket == webSocket) {
                    this@ChatWebSocketClient.webSocket = null
                    onStatusChanged("Desconectado")
                }
            }
        })

        webSocket = nextSocket
    }

    fun send(message: String): Boolean {
        return webSocket?.send(message) == true
    }

    fun disconnect() {
        val currentSocket = webSocket
        webSocket = null
        currentSocket?.close(1000, "Cierre normal")
        onStatusChanged("Desconectado")
    }
}
