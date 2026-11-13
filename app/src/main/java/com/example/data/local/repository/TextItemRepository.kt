package com.example.data.local.repository

import com.example.data.local.dao.TextItemDao
import com.example.data.local.entity.TextItem
import kotlinx.coroutines.flow.Flow

class TextItemRepository(
    private val textItemDao: TextItemDao
) {
    val allTextItems: Flow<List<TextItem>> = textItemDao.getAllTextItems()

    fun getTextItem(id: String): Flow<TextItem?> = textItemDao.getTextItemById(id)

    suspend fun getTextItemSync(id: String): TextItem? = textItemDao.getTextItemByIdSync(id)

    suspend fun insertTextItem(item: TextItem) {
        textItemDao.insertTextItem(item)
    }

    suspend fun updateTextItem(item: TextItem) {
        textItemDao.updateTextItem(item)
    }

    suspend fun updateStatus(id: String, status: String) {
        textItemDao.updateStatus(id, status)
    }

    suspend fun updateFolder(id: String, folderId: Long?) {
        textItemDao.updateFolder(id, folderId)
    }

    suspend fun deleteTextItem(id: String) {
        textItemDao.deleteTextItem(id)
    }
}
