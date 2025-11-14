/*
 * File: ProjectEntity.kt
 * Description: Room entity representing a project record in local database.
 * Author: SIMS Team
 */
package com.simsapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ProjectEntity
 *
 * Defines the schema for Project table.
 * This entity stores basic project attributes and counters.
 * Added field: project_uid for binding remote unique identifier and caching project detail.
 * Added field: project_hash for incremental sync optimization.
 * Added field: ralation_time to cache relation time from project_list API.
 */
@Entity(
    tableName = "project",
    indices = [
        Index(value = ["name"], unique = false),
        Index(value = ["project_uid"], unique = true)
    ]
)
data class ProjectEntity(
    /** Primary key of the project (auto-generated if not provided). */
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "project_id") val projectId: Long = 0,
    /** Display name of the project. */
    @ColumnInfo(name = "name") val name: String,
    /** Remote unique identifier of the project, used as the primary business key for all operations. */
    @ColumnInfo(name = "project_uid", defaultValue = "") val projectUid: String,
    /** Project hash value from remote API for incremental sync comparison. */
    @ColumnInfo(name = "project_hash", defaultValue = "") val projectHash: String = "",
    /** Project end date in epoch millis, null if not set. */
    @ColumnInfo(name = "end_date") val endDate: Long? = null,
    /** Cached relation time from project_list API (epoch millis if available). */
    @ColumnInfo(name = "ralation_time") val ralationTime: Long? = null,
    /** Current status string (e.g., ACTIVE, PAUSED, CLOSED). */
    @ColumnInfo(name = "status") val status: String = "ACTIVE",
    /** Count of related defects for quick dashboard access. */
    @ColumnInfo(name = "defect_count") val defectCount: Int = 0,
    /** Count of related events for quick dashboard access. */
    @ColumnInfo(name = "event_count") val eventCount: Int = 0,
    /** Flag indicating if the project has been marked as deleted/cleaned up. */
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false
)