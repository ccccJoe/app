/*
 * File: EventEntity.kt
 * Description: Room entity representing an event record, can link project and multiple defects.
 * Author: SIMS Team
 */
package com.simsapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * EventEntity
 *
 * Defines the schema for Event table.
 * Stores event content, location, and asset references.
 */
@Entity(
    tableName = "event",
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
        Index(value = ["project_uid"])
    ]
)
data class EventEntity(
    /** Primary key of the event (auto-generated). */
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "event_id") val eventId: Long = 0,
    /** Project foreign key. */
    @ColumnInfo(name = "project_id") val projectId: Long,
    /** Project unique identifier for direct association without joins. */
    @ColumnInfo(name = "project_uid", defaultValue = "") val projectUid: String,
    /** Related defect ids (JSON list via TypeConverters). */
    @ColumnInfo(name = "defect_ids") val defectIds: List<Long> = emptyList(),
    /** Related defect numbers (JSON list via TypeConverters). */
    @ColumnInfo(name = "defect_nos") val defectNos: List<String> = emptyList(),
    /** Optional location string (e.g., GPS or description). */
    @ColumnInfo(name = "location") val location: String? = null,
    /** Event textual content/description. */
    @ColumnInfo(name = "content") val content: String = "",
    /** Last edit timestamp in epoch millis. */
    @ColumnInfo(name = "last_edit_time") val lastEditTime: Long = System.currentTimeMillis(),
    /** Digital assets with file ID and name as JSON list via TypeConverters. */
    @ColumnInfo(name = "assets") val assets: List<DigitalAssetItem> = emptyList(),
    /** Risk assessment level (e.g., "High", "Medium", "Low"). */
    @ColumnInfo(name = "risk_level") val riskLevel: String? = null,
    /** Risk assessment score. */
    @ColumnInfo(name = "risk_score") val riskScore: Double? = null,
    /** Risk assessment detailed answers as JSON string. */
    @ColumnInfo(name = "risk_answers") val riskAnswers: String? = null,
    /** Photo file paths as JSON list via TypeConverters. */
    @ColumnInfo(name = "photo_files") val photoFiles: List<String> = emptyList(),
    /** Audio file paths as JSON list via TypeConverters. */
    @ColumnInfo(name = "audio_files") val audioFiles: List<String> = emptyList(),
    /** Draft flag to indicate the event is not finalized and created via real-time autosave. */
    @ColumnInfo(name = "is_draft") val isDraft: Boolean = true
)