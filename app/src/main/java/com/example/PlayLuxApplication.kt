package com.example

import android.app.Application
import com.example.di.AppContainer
import com.example.util.NotificationChannels
import com.example.work.RecycleCleanupWorker

class PlayLuxApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        container = AppContainer(this)
        RecycleCleanupWorker.scheduleDailyCleanup(this)
    }
}
