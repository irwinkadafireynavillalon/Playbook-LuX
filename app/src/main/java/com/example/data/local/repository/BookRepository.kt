package com.example.data.local.repository

import com.example.data.local.dao.BookDao
import com.example.data.local.dao.BookmarkDao
import com.example.data.local.dao.ChapterDao
import com.example.data.local.dao.ListeningStatsDao
import com.example.data.local.dao.PartDao
import com.example.data.local.entity.Book
import com.example.data.local.entity.Bookmark
import com.example.data.local.entity.Chapter
import com.example.data.local.entity.ListeningStat
import com.example.data.local.entity.Part
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class BookRepository(
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val partDao: PartDao,
    private val bookmarkDao: BookmarkDao,
    private val listeningStatsDao: ListeningStatsDao? = null
) {
    val activeBooks: Flow<List<Book>> = bookDao.getAllActiveBooks()

    val recentlyPlayedBooks: Flow<List<Book>> = bookDao.getRecentlyPlayedBooks(15)

    val totalListenedSeconds: Flow<Long>? = listeningStatsDao?.getTotalListenedSeconds()

    fun getRecentListeningStats(days: Int = 14): Flow<List<ListeningStat>>? =
        listeningStatsDao?.getRecentStats(days)

    fun getBook(id: String): Flow<Book?> = bookDao.getBookById(id)

    suspend fun getBookSync(id: String): Book? = bookDao.getBookByIdSync(id)

    suspend fun getAllActiveBooksSync(): List<Book> = bookDao.getAllActiveBooksSync()

    fun getChapters(bookId: String): Flow<List<Chapter>> = chapterDao.getChaptersForBook(bookId)

    suspend fun getChaptersSync(bookId: String): List<Chapter> = chapterDao.getChaptersForBookSync(bookId)

    fun getParts(bookId: String): Flow<List<Part>> = partDao.getPartsForBook(bookId)

    fun getBookmarks(bookId: String): Flow<List<Bookmark>> = bookmarkDao.getBookmarksForBook(bookId)

    suspend fun insertBookWithChapters(book: Book, parts: List<Part>, chapters: List<Chapter>) {
        bookDao.insertBook(book)
        if (parts.isNotEmpty()) {
            partDao.insertParts(parts)
        }
        chapterDao.insertChapters(chapters)
    }

    suspend fun updateProgress(bookId: String, chapterIndex: Int, posMs: Long, finished: Boolean) {
        bookDao.updateProgress(bookId, chapterIndex, posMs, finished)
    }

    suspend fun markBookPlayed(bookId: String) {
        bookDao.updateLastPlayedAt(bookId, System.currentTimeMillis())
    }

    suspend fun recordListeningSeconds(seconds: Long, isNewSession: Boolean = false) {
        val dao = listeningStatsDao ?: return
        if (seconds <= 0L && !isNewSession) return
        val todayKey = LocalDate.now().toString()
        val existing = dao.getStatForDay(todayKey)
        val updated = if (existing == null) {
            ListeningStat(
                dateKey = todayKey,
                totalSeconds = seconds.coerceAtLeast(0L),
                sessionsCount = if (isNewSession) 1 else 0
            )
        } else {
            existing.copy(
                totalSeconds = existing.totalSeconds + seconds.coerceAtLeast(0L),
                sessionsCount = existing.sessionsCount + (if (isNewSession) 1 else 0)
            )
        }
        dao.upsertStat(updated)
    }

    suspend fun updateSpeed(bookId: String, speed: Float) {
        bookDao.updateSpeed(bookId, speed)
    }

    suspend fun toggleFavorite(bookId: String, current: Boolean) {
        bookDao.updateFavorite(bookId, !current)
    }

    suspend fun setFolder(bookId: String, folderId: Long?) {
        bookDao.updateFolder(bookId, folderId)
    }

    suspend fun updateBookInfo(bookId: String, title: String, author: String, coverPath: String?) {
        bookDao.updateBookInfo(bookId, title, author, coverPath)
    }

    suspend fun updateChapterTitle(chapterId: Long, title: String) {
        chapterDao.updateChapterTitle(chapterId, title)
    }

    suspend fun setCorrupted(bookId: String, isCorrupted: Boolean) {
        bookDao.setCorrupted(bookId, isCorrupted)
    }

    suspend fun addBookmark(bookId: String, chapterId: Long, posMs: Long, note: String): Long {
        return bookmarkDao.insertBookmark(
            Bookmark(
                bookId = bookId,
                chapterId = chapterId,
                posMs = posMs,
                note = note.trim()
            )
        )
    }

    suspend fun deleteBookmark(id: Long) {
        bookmarkDao.deleteBookmarkById(id)
    }
}
