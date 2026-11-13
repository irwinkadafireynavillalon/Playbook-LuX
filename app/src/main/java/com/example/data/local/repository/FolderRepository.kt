package com.example.data.local.repository

import com.example.data.local.dao.BookDao
import com.example.data.local.dao.FolderDao
import com.example.data.local.entity.Folder
import kotlinx.coroutines.flow.Flow

class FolderRepository(
    private val folderDao: FolderDao,
    private val bookDao: BookDao
) {
    val allFolders: Flow<List<Folder>> = folderDao.getAllFolders()

    suspend fun createFolder(name: String): Long {
        return folderDao.insertFolder(Folder(name = name.trim()))
    }

    suspend fun renameFolder(id: Long, name: String) {
        folderDao.updateFolder(Folder(id = id, name = name.trim()))
    }

    suspend fun deleteFolder(id: Long) {
        folderDao.deleteFolder(id)
    }
}
