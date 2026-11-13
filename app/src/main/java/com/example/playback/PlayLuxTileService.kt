package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.MainActivity
import com.example.PlayLuxApplication
import com.example.R

/**
 * Android Quick Settings Tile ("Play LuX") to toggle audiobook playback cleanly
 * from the notification shade or open the library when idle.
 */
class PlayLuxTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val state = (applicationContext as? PlayLuxApplication)
            ?.container?.playbackConnection?.uiState?.value

        if (state?.book != null) {
            val intent = Intent(this, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_WIDGET_TOGGLE_PLAY
            }
            runCatching { startService(intent) }
        } else {
            val openIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pending = android.app.PendingIntent.getActivity(
                    this,
                    201,
                    openIntent,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(openIntent)
            }
        }
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val state = (applicationContext as? PlayLuxApplication)
            ?.container?.playbackConnection?.uiState?.value

        val isPlaying = state?.isPlaying == true
        val bookTitle = state?.book?.title

        tile.state = when {
            isPlaying -> Tile.STATE_ACTIVE
            bookTitle != null -> Tile.STATE_INACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = bookTitle ?: getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                isPlaying -> "Reproduciendo"
                bookTitle != null -> "En pausa"
                else -> "Abrir bóveda"
            }
        }
        tile.updateTile()
    }

    companion object {
        fun requestRefresh(context: Context) {
            runCatching {
                requestListeningState(
                    context,
                    ComponentName(context, PlayLuxTileService::class.java)
                )
            }
        }
    }
}
