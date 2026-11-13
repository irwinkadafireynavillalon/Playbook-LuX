package com.example.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.local.repository.RecycleRepository
import java.util.concurrent.TimeUnit

class RecycleCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val database = AppDatabase.getInstance(applicationContext)
            val recycleRepository = RecycleRepository(
                recycleDao = database.recycleDao(),
                bookDao = database.bookDao(),
                chapterDao = database.chapterDao(),
                partDao = database.partDao(),
                bookmarkDao = database.bookmarkDao()
            )
            recycleRepository.cleanupExpired(applicationContext, maxAgeDays = 30)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "PlayLuxRecycleCleanupWork"

        fun scheduleDailyCleanup(context: Context) {
            try {
                val request = PeriodicWorkRequestBuilder<RecycleCleanupWorker>(1, TimeUnit.DAYS)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } catch (e: Throwable) {
                // Safe guard for test environments where WorkManager is mocked/uninitialized
            }
        }
    }
}
