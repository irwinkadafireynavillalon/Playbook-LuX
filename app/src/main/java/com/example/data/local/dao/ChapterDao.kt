package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.Chapter
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY `index` ASC")
    fun getChaptersForBook(bookId: String): Flow<List<Chapter>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY `index` ASC")
    suspend fun getChaptersForBookSync(bookId: String): List<Chapter>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<Chapter>)

    @Update
    suspend fun updateChapter(chapter: Chapter)

    @Query("UPDATE chapters SET title = :title WHERE id = :id")
    suspend fun updateChapterTitle(id: Long, title: String)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteChaptersForBook(bookId: String)
}
