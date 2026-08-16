package com.lrv.privatechat.ui.components

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lrv.privatechat.model.AppColor
import com.lrv.privatechat.model.MESSAGE_STATUS_DELIVERED
import com.lrv.privatechat.model.UiMessage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: UiMessage,
    appColor: AppColor,
    onDelete: (UiMessage) -> Unit = {}
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Borrar mensaje") },
            text = { Text("Se borrará solo en este dispositivo.") },
            confirmButton = {
                TextButton(onClick = { onDelete(message); showDeleteDialog = false }) {
                    Text("Borrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 1.dp,
            color = if (message.mine) appColor.light else Color.White,
            modifier = Modifier
                .widthIn(max = 290.dp)
                .combinedClickable(onClick = {}, onLongClick = { showDeleteDialog = true })
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                when {
                    message.isMedia && message.mimeType?.startsWith("image/") == true -> {
                        ImagePreviewBubble(
                            filePath = message.mediaLocalPath,
                            mimeType = message.mimeType,
                            accentColor = if (message.mine) appColor.main else Color(0xFF128C7E)
                        )
                    }
                    message.isMedia && message.mimeType?.startsWith("audio/") == true -> {
                        AudioPlayerBubble(
                            filePath = message.mediaLocalPath,
                            accentColor = if (message.mine) appColor.main else Color(0xFF128C7E)
                        )
                    }
                    message.isMedia && message.mimeType?.startsWith("video/") == true -> {
                        VideoPreviewBubble(
                            filePath = message.mediaLocalPath,
                            mimeType = message.mimeType,
                            accentColor = if (message.mine) appColor.main else Color(0xFF128C7E)
                        )
                    }
                    message.isMedia && message.mimeType == "application/pdf" -> {
                        PdfPreviewBubble(
                            filePath = message.mediaLocalPath,
                            accentColor = if (message.mine) appColor.main else Color(0xFF128C7E)
                        )
                    }
                    message.isMedia -> {
                        FileBubble(
                            filePath = message.mediaLocalPath,
                            mimeType = message.mimeType,
                            accentColor = if (message.mine) appColor.main else Color(0xFF128C7E)
                        )
                    }
                    else -> {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF111111)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = buildMessageFooter(message),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF777777),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun ImagePreviewBubble(filePath: String?, mimeType: String?, accentColor: Color) {
    val context = LocalContext.current
    val file = filePath?.let { java.io.File(it) }
    val fileExists = file?.exists() == true
    val fileName = file?.name ?: "imagen"
    val bitmap = remember(filePath) {
        filePath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
    }
    Column {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Imagen adjunta",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { if (fileExists) openFile(context, file!!, mimeType ?: "image/*") }
            )
        } else {
            Text("📷 Imagen adjunta", style = MaterialTheme.typography.bodyLarge, color = Color(0xFF111111))
        }
        if (fileExists) {
            TextButton(
                onClick = { saveToDownloads(context, file!!, fileName, mimeType ?: "image/*") },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) { Text("Guardar", color = accentColor, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun AudioPlayerBubble(filePath: String?, accentColor: Color) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf(false) }

    val player = remember { MediaPlayer() }

    DisposableEffect(filePath) {
        if (filePath != null) {
            try {
                player.reset()
                player.setDataSource(filePath)
                player.prepare()
                duration = player.duration
                player.setOnCompletionListener {
                    isPlaying = false
                    progress = 0f
                    player.seekTo(0)
                }
            } catch (e: Exception) {
                error = true
            }
        } else {
            error = true
        }
        onDispose {
            player.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying && duration > 0) {
            progress = player.currentPosition.toFloat() / duration
            delay(100)
        }
    }

    if (error) {
        Text("🎤 Audio no disponible", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF777777))
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.widthIn(min = 180.dp, max = 240.dp)
    ) {
        // Botón play/pausa
        Surface(
            shape = CircleShape,
            color = accentColor,
            modifier = Modifier
                .size(40.dp)
                .combinedClickable(
                    onClick = {
                        if (isPlaying) {
                            player.pause()
                            isPlaying = false
                        } else {
                            player.start()
                            isPlaying = true
                        }
                    },
                    onLongClick = {}
                )
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isPlaying) "⏸" else "▶",
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = accentColor,
                trackColor = Color(0xFFCCCCCC)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isPlaying)
                    formatMs(player.currentPosition) + " / " + formatMs(duration)
                else
                    formatMs(duration),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF777777)
            )
        }
    }
}

@Composable
private fun FileBubble(filePath: String?, mimeType: String?, accentColor: Color) {
    val context = LocalContext.current
    val ext = mimeType?.substringAfter("/")?.substringBefore(";")?.uppercase() ?: "FILE"
    val file = filePath?.let { java.io.File(it) }
    val fileExists = file?.exists() == true
    val fileName = file?.name ?: "archivo.${ext.lowercase()}"

    Column(modifier = Modifier.widthIn(min = 180.dp, max = 240.dp)) {
        Row(
            modifier = Modifier
                .then(if (fileExists) Modifier.clickable { openFile(context, file!!, mimeType ?: "*/*") } else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📄", fontSize = 28.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ext,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF777777)
                )
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF111111),
                    maxLines = 2
                )
            }
        }
        if (fileExists) {
            TextButton(
                onClick = { saveToDownloads(context, file!!, fileName, mimeType ?: "*/*") },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) { Text("Guardar", color = accentColor, style = MaterialTheme.typography.labelMedium) }
        } else {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Archivo no disponible",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF999999)
            )
        }
    }
}

@Composable
private fun VideoPreviewBubble(filePath: String?, mimeType: String?, accentColor: Color) {
    val context = LocalContext.current
    val file = filePath?.let { java.io.File(it) }
    val fileExists = file?.exists() == true
    val fileName = file?.name ?: "video"

    val thumbnail = remember(filePath) {
        if (filePath == null || !fileExists) return@remember null
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val bmp = retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            bmp?.asImageBitmap()
        } catch (_: Exception) { null }
    }

    Column {
        Box(modifier = Modifier.widthIn(max = 240.dp)) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = "Vista previa del vídeo",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { if (fileExists) openFile(context, file!!, mimeType ?: "video/*") }
                )
            } else {
                Surface(
                    color = Color(0xFF222222),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(width = 220.dp, height = 130.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🎬", fontSize = 40.sp)
                    }
                }
            }
            // Botón play centrado
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.50f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(52.dp)
                    .clickable { if (fileExists) openFile(context, file!!, mimeType ?: "video/*") }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("▶", fontSize = 20.sp, color = Color.White)
                }
            }
        }
        if (fileExists) {
            TextButton(
                onClick = { saveToDownloads(context, file!!, fileName, mimeType ?: "video/*") },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) { Text("Guardar", color = accentColor, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun PdfPreviewBubble(filePath: String?, accentColor: Color) {
    val context = LocalContext.current
    val file = filePath?.let { java.io.File(it) }
    val fileExists = file?.exists() == true
    val fileName = file?.name ?: "documento.pdf"

    val pdfBitmap = remember(filePath) {
        if (filePath == null || !fileExists) return@remember null
        try {
            val pfd = android.os.ParcelFileDescriptor.open(
                java.io.File(filePath), android.os.ParcelFileDescriptor.MODE_READ_ONLY
            )
            val renderer = android.graphics.pdf.PdfRenderer(pfd)
            val page = renderer.openPage(0)
            val targetWidth = 640
            val scale = targetWidth.toFloat() / page.width
            val targetHeight = (page.height * scale).toInt()
            val bitmap = android.graphics.Bitmap.createBitmap(
                targetWidth, targetHeight, android.graphics.Bitmap.Config.ARGB_8888
            )
            android.graphics.Canvas(bitmap).drawColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            bitmap.asImageBitmap()
        } catch (_: Exception) { null }
    }

    if (pdfBitmap == null) {
        FileBubble(filePath = filePath, mimeType = "application/pdf", accentColor = accentColor)
        return
    }

    Column {
        Box(
            modifier = Modifier
                .widthIn(max = 240.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { if (fileExists) openFile(context, file!!, "application/pdf") }
        ) {
            Image(
                bitmap = pdfBitmap,
                contentDescription = "Vista previa del PDF",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.widthIn(max = 240.dp)
            )
            // Etiqueta PDF en esquina
            Surface(
                color = Color(0xFFE53935).copy(alpha = 0.9f),
                shape = RoundedCornerShape(bottomStart = 6.dp),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    "PDF",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = fileName,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF555555),
            maxLines = 1
        )
        if (fileExists) {
            TextButton(
                onClick = { saveToDownloads(context, file!!, fileName, "application/pdf") },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) { Text("Guardar", color = accentColor, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

private fun openFile(context: android.content.Context, file: java.io.File, mimeType: String) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        android.widget.Toast.makeText(context, "No hay app para abrir este archivo", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun saveToDownloads(context: android.content.Context, file: java.io.File, fileName: String, mimeType: String) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType)
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            )
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { os ->
                    file.inputStream().use { input -> input.copyTo(os) }
                }
                values.clear()
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(it, values, null, null)
            }
        } else {
            val dest = java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            file.copyTo(dest, overwrite = true)
        }
        android.widget.Toast.makeText(context, "Guardado en Descargas", android.widget.Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        android.widget.Toast.makeText(context, "Error al guardar el archivo", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun formatMs(ms: Int): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

private fun buildMessageFooter(message: UiMessage): String {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    if (!message.mine) return time
    val status = if (message.status == MESSAGE_STATUS_DELIVERED) "entregado" else "enviado"
    return "$time · $status"
}
