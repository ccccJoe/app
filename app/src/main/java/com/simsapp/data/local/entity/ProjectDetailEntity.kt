/*
 * File: ProjectDetailEntity.kt
 * Description: Room entity for caching detailed project information fetched from remote by project_uid.
 * Author: SIMS Team
 */
package com.simsapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ProjectDetailEntity
 *
 * Stores detailed attributes of a project, fetched by project_uid from remote API.
 * One-to-one mapping to ProjectEntity via project_id (unique).
 * Raw JSON is kept for flexible access of non-modeled fields.
 */
@Entity(
    tableName = "project_detail",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["project_id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["project_id"], unique = true),
        Index(value = ["project_uid"], unique = true)
    ]
)
data class ProjectDetailEntity(
    /** Primary key (auto-generated). */
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "detail_id") val detailId: Long = 0,
    /** Foreign key to ProjectEntity (unique to ensure one detail per project). */
    @ColumnInfo(name = "project_id") val projectId: Long,
    /** Remote unique identifier used to fetch detail. */
    @ColumnInfo(name = "project_uid", defaultValue = "") val projectUid: String,
    /** Project name (duplicate for quick access). */
    @ColumnInfo(name = "name") val name: String?,
    /** Project status from detail if provided. */
    @ColumnInfo(name = "status") val status: String?,
    /** Start date of the project in epoch millis (nullable). */
    @ColumnInfo(name = "start_date") val startDate: Long?,
    /** End date of the project in epoch millis (nullable). */
    @ColumnInfo(name = "end_date") val endDate: Long?,
    /** Last update time provided by server (epoch millis, nullable). */
    @ColumnInfo(name = "last_update_at") val lastUpdateAt: Long?,
    /** Raw JSON payload for flexible access to extra fields. */
    @ColumnInfo(name = "raw_json") val rawJson: String,
    /** When the detail was fetched (epoch millis). */
    @ColumnInfo(name = "last_fetched_at") val lastFetchedAt: Long = System.currentTimeMillis()
)