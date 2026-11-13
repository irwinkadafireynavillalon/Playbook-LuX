package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.Part
import kotlinx.coroutines.flow.Flow

@Dao
interface PartDao {
    @Query("SELECT * FROM parts WHERE bookId = :bookId ORDER BY `index` ASC")
    fun getPartsForBook(bookId: String): Flow<List<Part>>

    @Query("SELECT * FROM parts WHERE bookId = :bookId ORDER BY `index` ASC")
    suspend fun getPartsForBookSync(bookId: String): List<Part>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParts(parts: List<Part>)

    @Query("DELETE FROM parts WHERE bookId = :bookId")
    suspend fun deletePartsForBook(bookId: String)
}
