package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.Book
import com.example.data.local.entity.RecycleEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface RecycleDao {
    @Query("SELECT books.* FROM books INNER JOIN recycle_entries ON books.id = recycle_entries.bookId ORDER BY recycle_entries.deletedAt DESC")
    fun getRecycledBooks(): Flow<List<Book>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecycleEntry(entry: RecycleEntry)

    @Query("DELETE FROM recycle_entries WHERE bookId = :bookId")
    suspend fun deleteRecycleEntry(bookId: String)

    @Query("SELECT * FROM recycle_entries WHERE deletedAt <= :cutoffTimestamp")
    suspend fun getExpiredEntries(cutoffTimestamp: Long): List<RecycleEntry>

    @Query("DELETE FROM recycle_entries")
    suspend fun clearAllRecycleEntries()
}
