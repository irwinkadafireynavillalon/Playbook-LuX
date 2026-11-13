package com.example.engine

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.PlayLuxApplication
import com.example.R
import com.example.util.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground Service (foregroundServiceType="dataSync") keeping long TTS + Opus conversions alive
 * when the screen locks or the user switches apps, with ongoing progress notification.
 */
class ConversionService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null

    companion object {
        const val ACTION_START = "com.example.playlux.CONVERSION_START"

        fun ensureRunning(context: Context) {
            val intent = Intent(context, ConversionService::class.java).apply {
                action = ACTION_START
            }
            runCatching {
                context.startForegroundService(intent)
            }.onFailure {
                runCatching { context.startService(intent) }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground("Iniciando conversión…", 0)
        observeQueue()
        return START_NOT_STICKY
    }

    private fun startAsForeground(statusText: String, progressPct: Int) {
        val notification = NotificationCompat.Builder(this, NotificationChannels.CHANNEL_CONVERSION)
            .setSmallIcon(R.drawable.ic_playlux_logo)
            .setContentTitle("Convirtiendo audiolibro (Opus)")
            .setContentText(statusText)
            .setContentIntent(NotificationChannels.buildOpenAppPendingIntent(this, NotificationChannels.NOTIF_ID_CONVERSION))
            .setProgress(100, progressPct.coerceIn(0, 100), progressPct == 0)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationChannels.NOTIF_ID_CONVERSION,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationChannels.NOTIF_ID_CONVERSION, notification)
        }
    }

    private fun observeQueue() {
        if (observerJob?.isActive == true) return
        val app = application as? PlayLuxApplication ?: return
        observerJob = serviceScope.launch {
            app.container.ttsConverterEngine.state.collectLatest { st ->
                val activeJob = st.queue.firstOrNull { it.isActive }
                if (activeJob != null) {
                    val pct = (activeJob.progress * 100).toInt().coerceIn(0, 100)
                    val title = "${activeJob.textItem.title} ($pct%)"
                    NotificationChannels.postProgress(
                        context = this@ConversionService,
                        channelId = NotificationChannels.CHANNEL_CONVERSION,
                        notificationId = NotificationChannels.NOTIF_ID_CONVERSION,
                        title = title,
                        contentText = "${activeJob.statusText} • ${activeJob.timeRemainingText}",
                        progressPercent = pct,
                        ongoing = true
                    )
                } else if (st.queue.none { !it.isCompleted && !it.isPaused && it.error == null }) {
                    val hasPausedTimeoutJob = st.queue.any { it.isPaused }
                    if (!hasPausedTimeoutJob && st.lastCompletedBookId != null) {
                        val tone = runCatching {
                            app.container.settingsStore.getNotificationToneSync()
                        }.getOrDefault(com.example.data.datastore.NotificationTone.LUX_CHIME)

                        NotificationChannels.postCompletionAlert(
                            context = this@ConversionService,
                            tone = tone,
                            notificationId = NotificationChannels.NOTIF_ID_CONVERSION + 1,
                            title = "Audiolibro convertido",
                            contentText = st.lastMessage ?: "Listo en tu Bóveda (formato Opus)."
                        )
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        val app = application as? PlayLuxApplication
        app?.container?.ttsConverterEngine?.pauseForServiceTimeout()
        NotificationChannels.postProgress(
            context = this,
            channelId = NotificationChannels.CHANNEL_CONVERSION,
            notificationId = NotificationChannels.NOTIF_ID_CONVERSION + 2,
            title = "Conversión pausada (límite 6h)",
            contentText = "Abre el Convertidor y pulsa Reanudar para seguir desde el último capítulo.",
            progressPercent = null,
            ongoing = false
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        observerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
