package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.BookDao
import com.example.data.local.dao.BookmarkDao
import com.example.data.local.dao.ChapterDao
import com.example.data.local.dao.FolderDao
import com.example.data.local.dao.ListeningStatsDao
import com.example.data.local.dao.PartDao
import com.example.data.local.dao.PlaylistDao
import com.example.data.local.dao.RecycleDao
import com.example.data.local.dao.TextItemDao
import com.example.data.local.entity.Book
import com.example.data.local.entity.Bookmark
import com.example.data.local.entity.Chapter
import com.example.data.local.entity.Folder
import com.example.data.local.entity.ListeningStat
import com.example.data.local.entity.Part
import com.example.data.local.entity.Playlist
import com.example.data.local.entity.PlaylistItem
import com.example.data.local.entity.RecycleEntry
import com.example.data.local.entity.TextItem

@Database(
    entities = [
        Book::class,
        Part::class,
        Chapter::class,
        Bookmark::class,
        Folder::class,
        RecycleEntry::class,
        TextItem::class,
        Playlist::class,
        PlaylistItem::class,
        ListeningStat::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun partDao(): PartDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun folderDao(): FolderDao
    abstract fun recycleDao(): RecycleDao
    abstract fun textItemDao(): TextItemDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun listeningStatsDao(): ListeningStatsDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN sourceTextId TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN lastPlayedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `listening_stats` (`dateKey` TEXT NOT NULL, `totalSeconds` INTEGER NOT NULL, `sessionsCount` INTEGER NOT NULL, PRIMARY KEY(`dateKey`))"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "playlux.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
