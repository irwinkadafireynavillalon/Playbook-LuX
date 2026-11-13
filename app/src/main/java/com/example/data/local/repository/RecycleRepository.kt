package com.example.data.local.repository

import android.content.Context
import com.example.data.local.dao.BookDao
import com.example.data.local.dao.BookmarkDao
import com.example.data.local.dao.ChapterDao
import com.example.data.local.dao.PartDao
import com.example.data.local.dao.RecycleDao
import com.example.data.local.entity.Book
import com.example.data.local.entity.RecycleEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class RecycleRepository(
    private val recycleDao: RecycleDao,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val partDao: PartDao,
    private val bookmarkDao: BookmarkDao
) {
    val recycledBooks: Flow<List<Book>> = recycleDao.getRecycledBooks()

    suspend fun moveToRecycle(bookId: String) {
        recycleDao.insertRecycleEntry(RecycleEntry(bookId = bookId, deletedAt = System.currentTimeMillis()))
    }

    suspend fun restoreFromRecycle(bookId: String) {
        recycleDao.deleteRecycleEntry(bookId)
    }

    suspend fun deletePermanently(context: Context, bookId: String) = withContext(Dispatchers.IO) {
        // Delete audio files in filesDir/books/<bookId>
        val bookDir = File(context.filesDir, "books/$bookId")
        if (bookDir.exists()) {
            bookDir.deleteRecursively()
        }
        bookmarkDao.deleteBookmarksForBook(bookId)
        chapterDao.deleteChaptersForBook(bookId)
        partDao.deletePartsForBook(bookId)
        recycleDao.deleteRecycleEntry(bookId)
        bookDao.deleteBookById(bookId)
    }

    suspend fun emptyRecycle(context: Context) = withContext(Dispatchers.IO) {
        val expired = recycleDao.getExpiredEntries(Long.MAX_VALUE)
        for (entry in expired) {
            val bookDir = File(context.filesDir, "books/${entry.bookId}")
            if (bookDir.exists()) {
                bookDir.deleteRecursively()
            }
            bookmarkDao.deleteBookmarksForBook(entry.bookId)
            chapterDao.deleteChaptersForBook(entry.bookId)
            partDao.deletePartsForBook(entry.bookId)
            bookDao.deleteBookById(entry.bookId)
        }
        recycleDao.clearAllRecycleEntries()
    }

    suspend fun cleanupExpired(context: Context, maxAgeDays: Int = 30) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - (maxAgeDays.toLong() * 24 * 60 * 60 * 1000)
        val expired = recycleDao.getExpiredEntries(cutoff)
        for (entry in expired) {
            val bookDir = File(context.filesDir, "books/${entry.bookId}")
            if (bookDir.exists()) {
                bookDir.deleteRecursively()
            }
            bookmarkDao.deleteBookmarksForBook(entry.bookId)
            chapterDao.deleteChaptersForBook(entry.bookId)
            partDao.deletePartsForBook(entry.bookId)
            recycleDao.deleteRecycleEntry(entry.bookId)
            bookDao.deleteBookById(entry.bookId)
        }
    }
}
