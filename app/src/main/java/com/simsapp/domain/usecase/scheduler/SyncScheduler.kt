/*
 * File: SyncScheduler.kt
 * Description: Scheduler utility to enqueue one-off and periodic sync work with constraints.
 * Author: SIMS Team
 */
package com.simsapp.domain.usecase.scheduler

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import com.simsapp.data.worker.SyncWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * SyncScheduler
 *
 * Provides high-level APIs to schedule background synchronization jobs.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    /**
     * Enqueue a one-off sync task with network constraints.
     */
    fun enqueueOneOff(): String {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()
        WorkManager.getInstance(appContext)
            .enqueueUniqueWork(UNIQUE_ONE_OFF, ExistingWorkPolicy.REPLACE, request)
        return request.id.toString()
    }

    /**
     * Schedule periodic sync with given interval hours.
     */
    fun enqueuePeriodic(hours: Long = 6): String {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(hours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(appContext)
            .enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        return request.id.toString()
    }

    companion object {
        private const val UNIQUE_ONE_OFF = "sync_one_off"
        private const val UNIQUE_PERIODIC = "sync_periodic"
    }
}