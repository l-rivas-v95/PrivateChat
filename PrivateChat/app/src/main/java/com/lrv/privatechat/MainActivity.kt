package com.lrv.privatechat

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.lrv.privatechat.notifications.ChatNotificationHelper
import com.lrv.privatechat.service.ChatForegroundService
import com.lrv.privatechat.ui.app.PrivateChatApp
import com.lrv.privatechat.ui.theme.PrivateChatTheme
import com.lrv.privatechat.util.AppVisibilityTracker
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ChatNotificationHelper(this).createChannels()
        requestNotificationPermissionIfNeeded()
        startChatForegroundService()

        setContent {
            PrivateChatTheme {
                PrivateChatApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibilityTracker.markForeground()
    }

    override fun onStop() {
        AppVisibilityTracker.markBackground()
        super.onStop()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startChatForegroundService() {
        val userId = getSharedPreferences("private_chat_settings", MODE_PRIVATE)
            .getString("local_user_id", null)
            ?: UUID.randomUUID().toString().also { generated ->
                getSharedPreferences("private_chat_settings", MODE_PRIVATE)
                    .edit()
                    .putString("local_user_id", generated)
                    .apply()
            }

        val intent = Intent(this, ChatForegroundService::class.java).apply {
            action = ChatForegroundService.ACTION_START
            putExtra(ChatForegroundService.EXTRA_USER_ID, userId)
        }
        ContextCompat.startForegroundService(this, intent)
    }
}
