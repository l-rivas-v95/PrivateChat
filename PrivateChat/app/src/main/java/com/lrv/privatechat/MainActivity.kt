package com.lrv.privatechat

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.lrv.privatechat.notifications.ChatNotificationHelper
import com.lrv.privatechat.ui.app.PrivateChatApp
import com.lrv.privatechat.ui.theme.PrivateChatTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ChatNotificationHelper(this).createChannels()
        requestNotificationPermissionIfNeeded()

        setContent {
            PrivateChatTheme {
                PrivateChatApp()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
