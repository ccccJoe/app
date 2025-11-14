/*
 * File: ProjectSyncProgress.kt
 * Description: UI progress model for dashboard project synchronization.
 * Author: SIMS Team
 */
package com.simsapp.ui.dashboard

/**
 * Class: ProjectSyncProgress
 * Responsibility: Represent project-level sync progress for the dashboard.
 * Design: Lightweight data class consumed by ViewModel and Compose UI.
 */
data class ProjectSyncProgress(
    /** Completed projects count in current sync batch */
    val completed: Int = 0,
    /** Total projects to update in current sync batch */
    val total: Int = 0,
    /** Whether sync is in progress for UI overlay control */
    val isLoading: Boolean = false,
    /** Optional message to show in overlay */
    val message: String? = null
)