package com.lrv.privatechat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.lrv.privatechat.ui.app.PrivateChatApp
import com.lrv.privatechat.ui.theme.PrivateChatTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PrivateChatTheme {
                PrivateChatApp()
            }
        }
    }
}
