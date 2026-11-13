package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey
    val id: String,
    val title: String,
    val author: String,
    val lang: String = "es",
    val coverPath: String? = null,
    val durationMs: Long = 0L,
    val folderId: Long? = null,
    val favorite: Boolean = false,
    val finished: Boolean = false,
    val lastChapter: Int = 0,
    val lastPosMs: Long = 0L,
    val speed: Float = 1.0f,
    val isCorrupted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val sourceTextId: String? = null,
    val lastPlayedAt: Long = 0L
)

@Entity(
    tableName = "parts",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class Part(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: String,
    val index: Int,
    val title: String
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId"), Index("partId")]
)
data class Chapter(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: String,
    val partId: Long? = null,
    val index: Int,
    val title: String,
    val filePath: String,
    val durationMs: Long
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId"), Index("chapterId")]
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: String,
    val chapterId: Long,
    val posMs: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recycle_entries")
data class RecycleEntry(
    @PrimaryKey
    val bookId: String,
    val deletedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "text_items")
data class TextItem(
    @PrimaryKey
    val id: String,
    val title: String,
    val author: String,
    val format: String, // "EPUB", "TXT", "PDF", "DOCX", "HTML", "MD"
    val filePath: String,
    val sizeBytes: Long = 0L,
    val status: String = "UNPREPARED", // "UNPREPARED", "PREPARED", "CONVERTED"
    val detectedLang: String = "es",
    val wordCount: Int = 0,
    val folderId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    val bookId: String,
    val chapterId: Long? = null, // null = libro completo
    val position: Int = 0
)

@Entity(tableName = "listening_stats")
data class ListeningStat(
    @PrimaryKey
    val dateKey: String, // yyyy-MM-dd
    val totalSeconds: Long = 0L,
    val sessionsCount: Int = 0
)

