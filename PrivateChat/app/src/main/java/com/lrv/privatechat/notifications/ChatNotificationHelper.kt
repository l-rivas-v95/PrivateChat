package com.lrv.privatechat.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.lrv.privatechat.MainActivity
import com.lrv.privatechat.R
import com.lrv.privatechat.util.AppVisibilityTracker

class ChatNotificationHelper(private val context: Context) {

    companion object {
        private const val MESSAGE_CHANNEL_ID = "privatechat_messages"
        private const val MESSAGE_CHANNEL_NAME = "Mensajes"
    }

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            MESSAGE_CHANNEL_ID,
            MESSAGE_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificaciones de mensajes recibidos"
            enableLights(true)
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun showMessageNotification(senderName: String, messageText: String) {
        if (AppVisibilityTracker.isInForeground) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            senderName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Person + MessagingStyle: necesario para notificaciones de conversación en Android 11+
        val sender = Person.Builder()
            .setName(senderName)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher_round))
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(sender)
            .addMessage(messageText, System.currentTimeMillis(), sender)

        val notification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(messagingStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // ID fijo por remitente → agrupa mensajes del mismo contacto en una sola notificación
        NotificationManagerCompat.from(context).notify(senderName.hashCode(), notification)
    }
}
