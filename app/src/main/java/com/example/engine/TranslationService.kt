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
 * Foreground Service (foregroundServiceType="dataSync") that keeps ML Kit offline translation
 * running reliably in the background with a real-time percentage notification bar.
 */
class TranslationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null

    companion object {
        const val ACTION_START = "com.example.playlux.TRANSLATION_START"

        fun ensureRunning(context: Context) {
            val intent = Intent(context, TranslationService::class.java).apply {
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
        startAsForeground("Preparando traducción…", 0)
        observeTranslation()
        return START_NOT_STICKY
    }

    private fun startAsForeground(statusText: String, progressPct: Int) {
        val notification = NotificationCompat.Builder(this, NotificationChannels.CHANNEL_TRANSLATION)
            .setSmallIcon(R.drawable.ic_playlux_logo)
            .setContentTitle("Traduciendo libro (0%)")
            .setContentText(statusText)
            .setContentIntent(
                NotificationChannels.buildOpenAppPendingIntent(
                    this,
                    NotificationChannels.NOTIF_ID_TRANSLATION
                )
            )
            .setProgress(100, progressPct.coerceIn(0, 100), progressPct == 0)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationChannels.NOTIF_ID_TRANSLATION,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationChannels.NOTIF_ID_TRANSLATION, notification)
        }
    }

    private fun observeTranslation() {
        if (observerJob?.isActive == true) return
        val app = application as? PlayLuxApplication ?: return
        observerJob = serviceScope.launch {
            app.container.localTranslatorEngine.state.collectLatest { st ->
                if (st.isRunning) {
                    val pct = (st.progress * 100).toInt().coerceIn(0, 100)
                    val bookTitle = st.selectedTextItem?.title ?: "Libro"
                    NotificationChannels.postProgress(
                        context = this@TranslationService,
                        channelId = NotificationChannels.CHANNEL_TRANSLATION,
                        notificationId = NotificationChannels.NOTIF_ID_TRANSLATION,
                        title = "Traduciendo $bookTitle ($pct%)",
                        contentText = st.statusText,
                        progressPercent = pct,
                        ongoing = true
                    )
                } else {
                    if (st.completedTextItemId != null) {
                        val tone = runCatching {
                            app.container.settingsStore.getNotificationToneSync()
                        }.getOrDefault(com.example.data.datastore.NotificationTone.LUX_CHIME)

                        NotificationChannels.postCompletionAlert(
                            context = this@TranslationService,
                            tone = tone,
                            notificationId = NotificationChannels.NOTIF_ID_TRANSLATION + 1,
                            title = "Traducción completada",
                            contentText = "${st.selectedTextItem?.title ?: "Texto"} revisado y guardado en Bóveda → Textos"
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
        app?.container?.localTranslatorEngine?.pauseForServiceTimeout()
        NotificationChannels.postProgress(
            context = this,
            channelId = NotificationChannels.CHANNEL_TRANSLATION,
            notificationId = NotificationChannels.NOTIF_ID_TRANSLATION + 2,
            title = "Traducción en pausa (límite 6h)",
            contentText = "Abre Traductor y pulsa Reanudar para continuar.",
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
