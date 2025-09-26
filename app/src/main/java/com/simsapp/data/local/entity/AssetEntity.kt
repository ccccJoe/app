/*
 * File: AssetEntity.kt
 * Description: Room entity representing a digital asset (image/audio/pdf) associated with Project/Event.
 * Author: SIMS Team
 */
package com.simsapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AssetEntity
 *
 * Defines the schema for Asset table to store local asset metadata.
 */
@Entity(
    tableName = "asset",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["project_id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["event_id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["project_id"]), Index(value = ["event_id"])]
)
data class AssetEntity(
    /** Primary key of the asset (auto-generated). */
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "asset_id") val assetId: Long = 0,
    /** Asset type string: PIC/REC/PDF/MP3, etc. */
    @ColumnInfo(name = "type") val type: String,
    /** Local file absolute path or content URI string. */
    @ColumnInfo(name = "url") val url: String,
    /** Optional related event id. */
    @ColumnInfo(name = "event_id") val eventId: Long? = null,
    /** Optional related project id. */
    @ColumnInfo(name = "project_id") val projectId: Long? = null,
    /** Created timestamp in epoch millis. */
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)