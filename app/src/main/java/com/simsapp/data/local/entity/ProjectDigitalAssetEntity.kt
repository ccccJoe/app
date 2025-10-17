/*
 * File: ProjectDigitalAssetEntity.kt
 * Description: Room entity for caching project digital assets from project_digital_asset_tree.
 * Author: SIMS Team
 */
package com.simsapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.simsapp.data.local.entity.ProjectEntity

/**
 * ProjectDigitalAssetEntity
 *
 * Stores digital assets from project_digital_asset_tree for offline access.
 * These assets are used in risk matrix and digital asset selection in event creation.
 */
@Entity(
    tableName = "project_digital_asset",
    indices = [
        Index(value = ["project_uids"]),
        Index(value = ["node_id"], unique = true),
        Index(value = ["parent_id"]),
        Index(value = ["file_id"], unique = true) // file_id改为唯一索引，避免重复下载
    ]
)
data class ProjectDigitalAssetEntity(
    /** Primary key (auto-generated). */
    @PrimaryKey(autoGenerate = true) 
    @ColumnInfo(name = "id") 
    val id: Long = 0,
    
    /** Project UIDs array stored as JSON string. */
    @ColumnInfo(name = "project_uids") 
    val projectUids: String, // JSON数组: ["uid1", "uid2"]
    
    /** Node ID from project_digital_asset_tree. */
    @ColumnInfo(name = "node_id") 
    val nodeId: String,
    
    /** Parent node ID (null for root nodes). */
    @ColumnInfo(name = "parent_id") 
    val parentId: String?,
    
    /** Node name/title from the tree structure. */
    @ColumnInfo(name = "name") 
    val name: String,
    
    /** Node type (folder, file, etc.). */
    @ColumnInfo(name = "type") 
    val type: String,
    
    /** File ID from project_digital_asset_tree node (null for folders). */
    @ColumnInfo(name = "file_id") 
    val fileId: String?,
    
    /** Local file absolute path where the downloaded file is stored. */
    @ColumnInfo(name = "local_path") 
    val localPath: String?,
    
    /** Download status: PENDING, DOWNLOADING, COMPLETED, FAILED. */
    @ColumnInfo(name = "download_status") 
    val downloadStatus: String,
    
    /** Download URL resolved from the server. */
    @ColumnInfo(name = "download_url") 
    val downloadUrl: String?,
    
    /** File size in bytes. */
    @ColumnInfo(name = "file_size") 
    val fileSize: Long?,
    
    /** When the asset was created. */
    @ColumnInfo(name = "created_at") 
    val createdAt: Long,
    
    /** When the asset was last updated. */
    @ColumnInfo(name = "updated_at") 
    val updatedAt: Long,
    
    /** JSON content for risk matrix and other data files. */
    @ColumnInfo(name = "content") 
    val content: String? = null
)