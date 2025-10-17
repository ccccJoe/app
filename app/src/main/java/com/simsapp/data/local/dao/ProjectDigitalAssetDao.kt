/*
 * File: ProjectDigitalAssetDao.kt
 * Description: Room DAO for ProjectDigitalAssetEntity to manage project digital assets.
 * Author: SIMS Team
 */
package com.simsapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.simsapp.data.local.entity.ProjectDigitalAssetEntity
import kotlinx.coroutines.flow.Flow

/**
 * ProjectDigitalAssetDao
 *
 * Provides CRUD and query operations for project digital assets.
 */
@Dao
interface ProjectDigitalAssetDao {
    
    /** Insert or replace a project digital asset. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: ProjectDigitalAssetEntity): Long

    /** Insert a list of project digital assets. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(assets: List<ProjectDigitalAssetEntity>): List<Long>

    /** Upsert (insert or update) a project digital asset based on node_id. */
    suspend fun upsert(asset: ProjectDigitalAssetEntity): Long {
        val existing = getByNodeId(asset.nodeId)
        return if (existing != null) {
            // Update existing record with new data but keep the original id
            val updatedAsset = asset.copy(id = existing.id)
            update(updatedAsset)
            existing.id
        } else {
            // Insert new record
            insert(asset)
        }
    }

    /** Upsert (insert or update) a list of project digital assets based on node_id. */
    suspend fun upsertAll(assets: List<ProjectDigitalAssetEntity>): List<Long> {
        return assets.map { asset ->
            upsert(asset)
        }
    }

    /** Update a project digital asset. */
    @Update
    suspend fun update(asset: ProjectDigitalAssetEntity)

    /** Delete by asset id. */
    @Query("DELETE FROM project_digital_asset WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Delete all assets for a specific project UID. */
    @Query("DELETE FROM project_digital_asset WHERE project_uids LIKE '%\"' || :projectUid || '\"%'")
    suspend fun deleteByProjectUid(projectUid: String)

    /** Get failed downloads for a specific project UID. */
    @Query("SELECT * FROM project_digital_asset WHERE project_uids LIKE '%\"' || :projectUid || '\"%' AND download_status = 'FAILED'")
    suspend fun getFailedDownloadsByProjectUid(projectUid: String): List<ProjectDigitalAssetEntity>

    /** Get all failed downloads. */
    @Query("SELECT * FROM project_digital_asset WHERE download_status = 'FAILED'")
    suspend fun getAllFailedDownloads(): List<ProjectDigitalAssetEntity>

    /** Get all assets for a specific project UID. */
    @Query("SELECT * FROM project_digital_asset WHERE project_uids LIKE '%\"' || :projectUid || '\"%' ORDER BY created_at DESC")
    suspend fun getByProjectUid(projectUid: String): List<ProjectDigitalAssetEntity>

    /** Get asset by node ID. */
    @Query("SELECT * FROM project_digital_asset WHERE node_id = :nodeId LIMIT 1")
    suspend fun getByNodeId(nodeId: String): ProjectDigitalAssetEntity?

    /** Get assets by file ID. */
    @Query("SELECT * FROM project_digital_asset WHERE file_id = :fileId LIMIT 1")
    suspend fun getByFileId(fileId: String): ProjectDigitalAssetEntity?

    /** Get completed assets for a specific project UID. */
    @Query("SELECT * FROM project_digital_asset WHERE project_uids LIKE '%\"' || :projectUid || '\"%' AND download_status = 'COMPLETED' ORDER BY created_at DESC")
    suspend fun getCompletedByProjectUid(projectUid: String): List<ProjectDigitalAssetEntity>

    /** Get pending downloads. */
    @Query("SELECT * FROM project_digital_asset WHERE download_status = 'PENDING' ORDER BY created_at ASC")
    suspend fun getPendingDownloads(): List<ProjectDigitalAssetEntity>

    /** Get failed downloads. */
    @Query("SELECT * FROM project_digital_asset WHERE download_status = 'FAILED' ORDER BY created_at DESC")
    suspend fun getFailedDownloads(): List<ProjectDigitalAssetEntity>

    /** Update download status. */
    @Query("UPDATE project_digital_asset SET download_status = :status, updated_at = :updatedAt WHERE node_id = :nodeId")
    suspend fun updateDownloadStatus(nodeId: String, status: String, updatedAt: Long)

    /** Update local path. */
    @Query("UPDATE project_digital_asset SET local_path = :localPath, updated_at = :updatedAt WHERE node_id = :nodeId")
    suspend fun updateLocalPath(nodeId: String, localPath: String, updatedAt: Long)

    /** Update download URL. */
    @Query("UPDATE project_digital_asset SET download_url = :downloadUrl, updated_at = :updatedAt WHERE node_id = :nodeId")
    suspend fun updateDownloadUrl(nodeId: String, downloadUrl: String, updatedAt: Long)

    /** Update download URL by file ID. */
    @Query("UPDATE project_digital_asset SET download_url = :downloadUrl, updated_at = :updatedAt WHERE file_id = :fileId")
    suspend fun updateDownloadUrlByFileId(fileId: String, downloadUrl: String, updatedAt: Long)

    /** Update type field for digital asset. */
    @Query("UPDATE project_digital_asset SET type = :type, updated_at = :updatedAt WHERE node_id = :nodeId")
    suspend fun updateType(nodeId: String, type: String, updatedAt: Long)

    /** Update content field for storing JSON data. */
    @Query("UPDATE project_digital_asset SET content = :content, updated_at = :updatedAt WHERE node_id = :nodeId")
    suspend fun updateContent(nodeId: String, content: String, updatedAt: Long)

    /** Update download status, local path and content together. */
    @Query("UPDATE project_digital_asset SET download_status = :status, local_path = :localPath, content = :content, updated_at = :updatedAt WHERE node_id = :nodeId")
    suspend fun updateDownloadComplete(nodeId: String, status: String, localPath: String?, content: String?, updatedAt: Long)

    /** Check if asset exists by node ID. */
    @Query("SELECT COUNT(*) > 0 FROM project_digital_asset WHERE node_id = :nodeId")
    suspend fun existsByNodeId(nodeId: String): Boolean

    /** Check if asset exists by file ID. */
    @Query("SELECT COUNT(*) > 0 FROM project_digital_asset WHERE file_id = :fileId")
    suspend fun existsByFileId(fileId: String): Boolean

    /** Clear all project digital assets. */
    @Query("DELETE FROM project_digital_asset")
    suspend fun clearAll()
}