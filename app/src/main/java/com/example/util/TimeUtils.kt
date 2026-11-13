package com.example.util

import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeUtils {
    fun formatMs(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun formatDurationHuman(ms: Long): String {
        if (ms <= 0L) return "0 min"
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return if (hours > 0) {
            if (minutes > 0) "${hours} h ${minutes} min" else "${hours} h"
        } else {
            "${minutes.coerceAtLeast(1)} min"
        }
    }
}
