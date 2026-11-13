package com.example.playback

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.PlayLuxApplication
import com.example.R
import java.io.File

class PlayLuxWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, PlayLuxWidgetProvider::class.java)
                )
                for (id in ids) {
                    updateWidget(context, manager, id)
                }
            }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val state = (context.applicationContext as? PlayLuxApplication)
                ?.container?.playbackConnection?.uiState?.value

            val views = RemoteViews(context.packageName, R.layout.widget_playlux)
            val book = state?.book
            val chapter = state?.chapters?.getOrNull(state.currentChapterIndex)
            val isPlaying = state?.isPlaying == true

            views.setTextViewText(
                R.id.widget_book_title,
                book?.title ?: context.getString(R.string.app_name)
            )
            views.setTextViewText(
                R.id.widget_chapter_title,
                chapter?.title ?: book?.author ?: "Toca para abrir tu bóveda"
            )
            views.setImageViewResource(
                R.id.widget_btn_play_pause,
                if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )

            val coverFile = book?.coverPath?.let { File(it) }
            if (coverFile != null && coverFile.exists() && coverFile.length() > 0) {
                val bmp = runCatching { BitmapFactory.decodeFile(coverFile.absolutePath) }.getOrNull()
                if (bmp != null) {
                    views.setImageViewBitmap(R.id.widget_cover, bmp)
                } else {
                    views.setImageViewResource(R.id.widget_cover, R.mipmap.ic_launcher_round)
                }
            } else {
                views.setImageViewResource(R.id.widget_cover, R.mipmap.ic_launcher_round)
            }

            // Root click opens MainActivity
            val openAppIntent = PendingIntent.getActivity(
                context,
                100,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent)

            // Playback controls route through PlaybackService when a book is loaded, or open app when idle
            views.setOnClickPendingIntent(
                R.id.widget_btn_play_pause,
                if (book != null) servicePendingIntent(context, PlaybackService.ACTION_WIDGET_TOGGLE_PLAY, 101) else openAppIntent
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_rewind,
                if (book != null) servicePendingIntent(context, PlaybackService.ACTION_WIDGET_REWIND, 102) else openAppIntent
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_forward,
                if (book != null) servicePendingIntent(context, PlaybackService.ACTION_WIDGET_FORWARD, 103) else openAppIntent
            )

            manager.updateAppWidget(appWidgetId, views)
        }

        private fun servicePendingIntent(context: Context, action: String, reqCode: Int): PendingIntent {
            val intent = Intent(context, PlaybackService::class.java).apply {
                this.action = action
            }
            return PendingIntent.getService(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
