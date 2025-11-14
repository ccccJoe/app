/*
 * File: DebugDbInspector.kt
 * Description: Debug utility to inspect Room database table state for diagnosing migration issues.
 * Author: SIMS Team
 */
package com.simsapp.utils

import android.util.Log
import com.simsapp.data.local.dao.ProjectDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DebugDbInspector
 *
 * Provides helper to log project table statistics, including total rows, rows containing nulls
 * in fields that are expected to be non-null, and a small sample of rows for manual inspection.
 */
object DebugDbInspector {

    private const val TAG = "DebugDbInspector"

    /**
     * Log project table state for debugging.
     *
     * Parameters:
     * - projectDao: DAO to query project table statistics and sample rows
     *
     * Returns:
     * - Unit. Outputs logs via Logcat (TAG=DebugDbInspector)
     */
    suspend fun logProjectTableState(projectDao: ProjectDao) = withContext(Dispatchers.IO) {
        try {
            val total = projectDao.count()
            val nullRows = projectDao.countRowsWithNulls()
            Log.d(TAG, "Project table total rows: $total, rows with nulls: $nullRows")

            // Log a small sample of rows
            val sample = projectDao.sample(5)
            sample.forEachIndexed { index, p ->
                Log.d(
                    TAG,
                    "Sample[$index] id=${p.projectId}, name='${p.name}', uid='${p.projectUid}', hash='${p.projectHash}', status='${p.status}', defects=${p.defectCount}, events=${p.eventCount}, deleted=${p.isDeleted}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inspect project table: ${e.message}", e)
        }
    }
}