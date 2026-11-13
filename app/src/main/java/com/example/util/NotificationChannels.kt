package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.datastore.NotificationTone

object NotificationChannels {
    const val CHANNEL_PLAYBACK = "playback_channel"
    const val CHANNEL_DOWNLOADS = "downloads_channel"
    const val CHANNEL_TRANSLATION = "translation_channel"
    const val CHANNEL_CONVERSION = "conversion_channel"

    const val NOTIF_ID_CONVERSION = 4101
    const val NOTIF_ID_DOWNLOAD_BASE = 4200
    const val NOTIF_ID_TRANSLATION = 4301

    private var activePreviewPlayer: MediaPlayer? = null

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
            "Descargas de libros (progreso)",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Progreso de descarga de libros de dominio público"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }

        val translationChannel = NotificationChannel(
            CHANNEL_TRANSLATION,
            "Traducción en el dispositivo (progreso)",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Porcentaje de traducción offline en segundo plano"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }

        val conversionChannel = NotificationChannel(
            CHANNEL_CONVERSION,
            "Conversión de texto a audio (progreso)",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Porcentaje de síntesis de voz y codificación Opus/OGG"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val toneChannels = NotificationTone.entries.map { tone ->
            val importance = if (tone == NotificationTone.SILENT) {
                NotificationManager.IMPORTANCE_LOW
            } else {
                NotificationManager.IMPORTANCE_DEFAULT
            }
            NotificationChannel(
                tone.channelId,
                "Avisos completados (${tone.displayName})",
                importance
            ).apply {
                description = "Notificación al completar descargas, traducciones o conversiones"
                val uri = resolveToneUri(context, tone)
                setSound(uri, if (uri != null) audioAttrs else null)
                enableVibration(tone != NotificationTone.SILENT)
            }
        }

        manager.createNotificationChannels(
            listOf(playbackChannel, downloadsChannel, translationChannel, conversionChannel) + toneChannels
        )
    }

    fun resolveToneUri(context: Context, tone: NotificationTone): Uri? {
        return when (tone) {
            NotificationTone.LUX_CHIME -> rawUri(context, R.raw.tone_lux_chime)
            NotificationTone.AMBER_PULSE -> rawUri(context, R.raw.tone_amber_pulse)
            NotificationTone.CRYSTAL_BELL -> rawUri(context, R.raw.tone_crystal_bell)
            NotificationTone.SYSTEM_DEFAULT -> Settings.System.DEFAULT_NOTIFICATION_URI
            NotificationTone.SILENT -> null
        }
    }

    private fun rawUri(context: Context, rawResId: Int): Uri {
        return Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/$rawResId")
    }

    fun previewTone(context: Context, tone: NotificationTone) {
        runCatching {
            activePreviewPlayer?.stop()
            activePreviewPlayer?.release()
            activePreviewPlayer = null
        }
        val uri = resolveToneUri(context, tone) ?: return
        runCatching {
            if (tone == NotificationTone.SYSTEM_DEFAULT) {
                RingtoneManager.getRingtone(context.applicationContext, uri)?.play()
            } else {
                activePreviewPlayer = MediaPlayer().apply {
                    setDataSource(context.applicationContext, uri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setOnCompletionListener { mp ->
                        runCatching { mp.release() }
                        if (activePreviewPlayer === mp) activePreviewPlayer = null
                    }
                    prepare()
                    start()
                }
            }
        }
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
        progressPercent: Int?,
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

    fun postCompletionAlert(
        context: Context,
        tone: NotificationTone,
        notificationId: Int,
        title: String,
        contentText: String
    ) {
        if (!hasNotificationPermission(context)) return
        createAll(context)
        val uri = resolveToneUri(context, tone)

        val builder = NotificationCompat.Builder(context, tone.channelId)
            .setSmallIcon(R.drawable.ic_playlux_logo)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(buildOpenAppPendingIntent(context, notificationId))
            .setAutoCancel(true)
            .setOngoing(false)

        if (tone == NotificationTone.SILENT) {
            builder.setSilent(true)
        } else if (uri != null) {
            builder.setSound(uri)
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
