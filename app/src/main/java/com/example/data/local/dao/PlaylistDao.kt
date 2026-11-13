package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.Playlist
import com.example.data.local.entity.PlaylistItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun getPlaylistById(id: Long): Flow<Playlist?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlist_items ORDER BY position ASC")
    fun getAllPlaylistItems(): Flow<List<PlaylistItem>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getItemsForPlaylist(playlistId: Long): Flow<List<PlaylistItem>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getItemsForPlaylistSync(playlistId: Long): List<PlaylistItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(item: PlaylistItem): Long

    @Update
    suspend fun updatePlaylistItems(items: List<PlaylistItem>)

    @Query("DELETE FROM playlist_items WHERE id = :id")
    suspend fun deletePlaylistItem(id: Long)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearPlaylistItems(playlistId: Long)
}
