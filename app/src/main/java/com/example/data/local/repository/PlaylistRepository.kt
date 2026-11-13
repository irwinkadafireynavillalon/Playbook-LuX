package com.example.data.local.repository

import com.example.data.local.dao.PlaylistDao
import com.example.data.local.entity.Playlist
import com.example.data.local.entity.PlaylistItem
import kotlinx.coroutines.flow.Flow

class PlaylistRepository(
    private val playlistDao: PlaylistDao
) {
    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists()

    val allPlaylistItems: Flow<List<PlaylistItem>> = playlistDao.getAllPlaylistItems()

    fun getPlaylist(id: Long): Flow<Playlist?> = playlistDao.getPlaylistById(id)

    fun getPlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> =
        playlistDao.getItemsForPlaylist(playlistId)

    suspend fun getPlaylistItemsSync(playlistId: Long): List<PlaylistItem> =
        playlistDao.getItemsForPlaylistSync(playlistId)

    suspend fun createPlaylist(name: String, description: String = ""): Long {
        return playlistDao.insertPlaylist(Playlist(name = name.trim(), description = description.trim()))
    }

    suspend fun updatePlaylist(playlist: Playlist) {
        playlistDao.updatePlaylist(playlist)
    }

    suspend fun deletePlaylist(id: Long) {
        playlistDao.deletePlaylist(id)
    }

    suspend fun addItem(playlistId: Long, bookId: String, chapterId: Long? = null, position: Int = 0): Long {
        return playlistDao.insertPlaylistItem(
            PlaylistItem(
                playlistId = playlistId,
                bookId = bookId,
                chapterId = chapterId,
                position = position
            )
        )
    }

    suspend fun updateItemsOrder(items: List<PlaylistItem>) {
        val reindexed = items.mapIndexed { index, item -> item.copy(position = index) }
        playlistDao.updatePlaylistItems(reindexed)
    }

    suspend fun removeItem(id: Long) {
        playlistDao.deletePlaylistItem(id)
    }

    suspend fun clearItems(playlistId: Long) {
        playlistDao.clearPlaylistItems(playlistId)
    }
}
