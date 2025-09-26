/*
 * File: SyncWorker.kt
 * Description: WorkManager Hilt-enabled worker performing data synchronization with retry policy.
 * Author: SIMS Team
 */
package com.simsapp.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.simsapp.domain.usecase.SyncUseCase

/**
 * SyncWorker
 *
 * Executes the SyncUseCase in background with DI-enabled dependencies.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncUseCase: SyncUseCase
) : CoroutineWorker(appContext, params) {

    /**
     * Do background sync work with progress hooks.
     */
    override suspend fun doWork(): Result {
        return try {
            syncUseCase.execute { progress, message ->
                setProgressAsync(androidx.work.Data.Builder()
                    .putFloat(KEY_PROGRESS, progress)
                    .putString(KEY_MESSAGE, message)
                    .build())
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_PROGRESS = "progress"
        const val KEY_MESSAGE = "message"
    }
}