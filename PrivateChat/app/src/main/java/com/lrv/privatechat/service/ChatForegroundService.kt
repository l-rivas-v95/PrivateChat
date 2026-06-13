package com.lrv.privatechat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lrv.privatechat.R

class ChatForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.lrv.privatechat.action.START_CHAT_SERVICE"
        const val ACTION_STOP = "com.lrv.privatechat.action.STOP_CHAT_SERVICE"
        private const val SERVICE_CHANNEL_ID = "privatechat_connection"
        private const val SERVICE_CHANNEL_NAME = "Conexión de PrivateChat"
        private const val SERVICE_NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createServiceChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification())
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createServiceChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            SERVICE_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantiene PrivateChat activo en segundo plano"
        }

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildServiceNotification(): Notification {
        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("PrivateChat conectado")
            .setContentText("Manteniendo la conexión activa en segundo plano")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
