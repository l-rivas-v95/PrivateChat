package com.lrv.privatechat.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lrv.privatechat.model.AppColor

@Composable
fun AvatarView(
    displayName: String,
    avatarBase64: String?,
    appColor: AppColor,
    size: Dp = 52.dp
) {
    val bitmap = remember(avatarBase64) {
        avatarBase64
            ?.takeIf { it.isNotBlank() }
            ?.let { value ->
                runCatching {
                    val bytes = Base64.decode(value, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }
    }

    Surface(
        shape = CircleShape,
        color = appColor.light,
        modifier = Modifier.size(size)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Avatar",
                modifier = Modifier.clip(CircleShape)
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = displayName.firstOrNull()?.uppercase() ?: "?",
                    fontWeight = FontWeight.Bold,
                    color = appColor.main
                )
            }
        }
    }
}
