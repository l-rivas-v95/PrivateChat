package com.lrv.privatechat.util

object AppVisibilityTracker {
    @Volatile
    var isInForeground: Boolean = false
        private set

    fun markForeground() {
        isInForeground = true
    }

    fun markBackground() {
        isInForeground = false
    }
}
