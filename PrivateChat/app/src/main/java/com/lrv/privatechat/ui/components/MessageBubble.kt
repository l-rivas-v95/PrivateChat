package com.lrv.privatechat.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lrv.privatechat.model.AppColor
import com.lrv.privatechat.model.MESSAGE_STATUS_DELIVERED
import com.lrv.privatechat.model.UiMessage

@Composable
fun MessageBubble(
    message: UiMessage,
    appColor: AppColor
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 1.dp,
            color = if (message.mine) appColor.light else Color.White,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF111111)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = when {
                        !message.mine -> message.from.take(8) + "..."
                        message.status == MESSAGE_STATUS_DELIVERED -> "entregado"
                        else -> "enviado"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF777777),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
