/*
 * File: DefectEntity.kt
 * Description: Room entity representing a defect record associated with a project.
 * Author: SIMS Team
 */
package com.simsapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * DefectEntity
 *
 * Defines the schema for Defect table.
 * Stores defect metadata including risk rating and attachments.
 */
@Entity(
    tableName = "defect",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["project_id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["project_id"]),
        Index(value = ["uid"]),
        Index(value = ["project_uid", "defect_no"], unique = true)
    ],
)
data class DefectEntity(
    /** Primary key of the defect (auto-generated). */
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "defect_id") val defectId: Long = 0,
    /** Remote unique identifier (UID) for defect, used for cross-device linking. */
    @ColumnInfo(name = "uid", defaultValue = "") val uid: String = "",
    /** Project foreign key. */
    @ColumnInfo(name = "project_id") val projectId: Long,
    /** Project unique identifier for direct association without joins. */
    @ColumnInfo(name = "project_uid", defaultValue = "") val projectUid: String,
    /** Human-readable defect number or code. */
    @ColumnInfo(name = "defect_no") val defectNo: String,
    /** Risk rating string (e.g., LOW, MEDIUM, HIGH, CRITICAL). */
    @ColumnInfo(name = "risk_rating") val riskRating: String,
    /** Current status (e.g., OPEN, IN_PROGRESS, CLOSED). */
    @ColumnInfo(name = "status") val status: String = "OPEN",
    /** Defect type (e.g., LOCALLY_AFFECT_STRUCTURAL_PERFORMANCE, GLOBAL_AFFECT_STRUCTURAL_PERFORMANCE). */
    @ColumnInfo(name = "type", defaultValue = "") val type: String = "",
    /** Image file paths as JSON string list via TypeConverters. */
    @ColumnInfo(name = "images") val images: List<String> = emptyList(),
    /** Count of related events for quick listing. */
    @ColumnInfo(name = "event_count") val eventCount: Int = 0,
    /** Sort order within the project; 0 by default, smaller comes first. */
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0
)