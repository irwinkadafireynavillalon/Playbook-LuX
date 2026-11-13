package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.TextItem
import kotlinx.coroutines.flow.Flow

@Dao
interface TextItemDao {
    @Query("SELECT * FROM text_items ORDER BY createdAt DESC")
    fun getAllTextItems(): Flow<List<TextItem>>

    @Query("SELECT * FROM text_items WHERE id = :id")
    fun getTextItemById(id: String): Flow<TextItem?>

    @Query("SELECT * FROM text_items WHERE id = :id")
    suspend fun getTextItemByIdSync(id: String): TextItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTextItem(item: TextItem)

    @Update
    suspend fun updateTextItem(item: TextItem)

    @Query("UPDATE text_items SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE text_items SET folderId = :folderId WHERE id = :id")
    suspend fun updateFolder(id: String, folderId: Long?)

    @Query("DELETE FROM text_items WHERE id = :id")
    suspend fun deleteTextItem(id: String)
}
