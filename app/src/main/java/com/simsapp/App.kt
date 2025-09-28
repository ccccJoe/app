/*
 * File: App.kt
 * Description: Application entry annotated with Hilt and WorkManager configuration for DI-enabled workers.
 * Author: SIMS Team
 */
package com.simsapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * App
 *
 * Application class that bootstraps Hilt DI graph and provides WorkManager configuration.
 * Keeping global initialization lightweight to optimize app startup.
 */
@HiltAndroidApp
class App : Application(), Configuration.Provider {

    // Hilt-provided WorkerFactory for DI-enabled WorkManager workers
    @Inject lateinit var workerFactory: HiltWorkerFactory



    /**
     * onCreate
     *
     * Perform minimal startup initialization. Avoid heavy work here.
     */
    override fun onCreate() {
        super.onCreate()
        // Initialize global lightweight components if needed
    }

    /**
     * WorkManager configuration provider to inject HiltWorkerFactory.
     * Returning minimal logging level by default to reduce noise in production.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .setWorkerFactory(workerFactory)
            .build()

}