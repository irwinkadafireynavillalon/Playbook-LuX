package com.example.util

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
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object NotificationChannels {
    const val CHANNEL_PLAYBACK = "playback_channel"
    const val CHANNEL_DOWNLOADS = "downloads_channel"
    const val CHANNEL_TRANSLATION = "translation_channel"
    const val CHANNEL_CONVERSION = "conversion_channel"

    const val NOTIF_ID_CONVERSION = 4101
    const val NOTIF_ID_DOWNLOAD_BASE = 4200
    const val NOTIF_ID_TRANSLATION = 4301

    fun createAll(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val playbackChannel = NotificationChannel(
            CHANNEL_PLAYBACK,
            "Reproducción de audiolibros",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Controles multimedia en barra de estado y pantalla de bloqueo"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }

        val downloadsChannel = NotificationChannel(
            CHANNEL_DOWNLOADS,
            "Descargas de libros",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Progreso de descarga de libros de dominio público"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }

        val translationChannel = NotificationChannel(
            CHANNEL_TRANSLATION,
            "Traducción en el dispositivo",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Estado de traducción offline de libros y textos"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }

        val conversionChannel = NotificationChannel(
            CHANNEL_CONVERSION,
            "Conversión de texto a audio",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Progreso de síntesis de voz y codificación Opus/OGG"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }

        manager.createNotificationChannels(
            listOf(playbackChannel, downloadsChannel, translationChannel, conversionChannel)
        )
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun buildOpenAppPendingIntent(context: Context, requestCode: Int = 10): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun postProgress(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        contentText: String,
        progressPercent: Int?, // null for completed/indeterminate-less
        ongoing: Boolean
    ) {
        if (!hasNotificationPermission(context)) return

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_playlux_logo)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(buildOpenAppPendingIntent(context, notificationId))
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)

        if (progressPercent != null) {
            builder.setProgress(100, progressPercent.coerceIn(0, 100), false)
        } else {
            builder.setProgress(0, 0, false)
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }

    fun cancel(context: Context, notificationId: Int) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }
}
