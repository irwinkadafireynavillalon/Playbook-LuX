package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.Book
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE id NOT IN (SELECT bookId FROM recycle_entries) ORDER BY createdAt DESC")
    fun getAllActiveBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun getBookById(id: String): Flow<Book?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookByIdSync(id: String): Book?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book)

    @Update
    suspend fun updateBook(book: Book)

    @Query("UPDATE books SET lastChapter = :chapterIndex, lastPosMs = :posMs, finished = :finished WHERE id = :bookId")
    suspend fun updateProgress(bookId: String, chapterIndex: Int, posMs: Long, finished: Boolean)

    @Query("UPDATE books SET speed = :speed WHERE id = :bookId")
    suspend fun updateSpeed(bookId: String, speed: Float)

    @Query("UPDATE books SET favorite = :favorite WHERE id = :bookId")
    suspend fun updateFavorite(bookId: String, favorite: Boolean)

    @Query("UPDATE books SET folderId = :folderId WHERE id = :bookId")
    suspend fun updateFolder(bookId: String, folderId: Long?)

    @Query("UPDATE books SET title = :title, author = :author, coverPath = :coverPath WHERE id = :bookId")
    suspend fun updateBookInfo(bookId: String, title: String, author: String, coverPath: String?)

    @Query("UPDATE books SET isCorrupted = :isCorrupted WHERE id = :bookId")
    suspend fun setCorrupted(bookId: String, isCorrupted: Boolean)

    @Query("UPDATE books SET lastPlayedAt = :timestamp WHERE id = :bookId")
    suspend fun updateLastPlayedAt(bookId: String, timestamp: Long)

    @Query("SELECT * FROM books WHERE id NOT IN (SELECT bookId FROM recycle_entries) AND lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun getRecentlyPlayedBooks(limit: Int = 15): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id NOT IN (SELECT bookId FROM recycle_entries) ORDER BY lastPlayedAt DESC, createdAt DESC")
    suspend fun getAllActiveBooksSync(): List<Book>

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: String)
}
