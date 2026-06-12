package com.lrv.privatechat.model

import androidx.compose.ui.graphics.Color

const val UNKNOWN_CONTACT_NAME = "Usuario desconocido"
const val MESSAGE_STATUS_SENT = "SENT"
const val MESSAGE_STATUS_DELIVERED = "DELIVERED"
const val MESSAGE_STATUS_RECEIVED = "RECEIVED"

enum class AppColor(
    val label: String,
    val main: Color,
    val light: Color
) {
    GREEN("Verde", Color(0xFF075E54), Color(0xFFD9FDD3)),
    BLUE("Azul", Color(0xFF0B5CAD), Color(0xFFD8EAFE)),
    PURPLE("Morado", Color(0xFF6750A4), Color(0xFFEADDFF))
}
