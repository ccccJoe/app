/*
 * File: DefectDataAssetDao.kt
 * Description: Room DAO，用于管理缺陷数字资产 DefectDataAssetEntity。
 * Author: SIMS Team
 */
package com.simsapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.simsapp.data.local.entity.DefectDataAssetEntity

/**
 * 类：DefectDataAssetDao
 * 职责：提供缺陷数字资产的增删改查，以及常用查询方法。
 */
@Dao
interface DefectDataAssetDao {
    /** 插入或替换一条资产记录（以 file_id 唯一约束）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: DefectDataAssetEntity): Long

    /** 批量插入或替换资产记录。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(assets: List<DefectDataAssetEntity>): List<Long>

    /** 更新资产记录。 */
    @Update
    suspend fun update(asset: DefectDataAssetEntity)

    /** 根据 file_id 获取资产。 */
    @Query("SELECT * FROM defect_data_asset WHERE file_id = :fileId LIMIT 1")
    suspend fun getByFileId(fileId: String): DefectDataAssetEntity?

    /** 根据 defect_uid 获取资产列表。 */
    @Query("SELECT * FROM defect_data_asset WHERE defect_uid = :defectUid ORDER BY created_at DESC")
    suspend fun getByDefectUid(defectUid: String): List<DefectDataAssetEntity>

    /** 根据项目UID与类型获取资产列表。 */
    @Query("SELECT * FROM defect_data_asset WHERE project_uid = :projectUid AND type = :type ORDER BY created_at DESC")
    suspend fun getByProjectUidAndType(projectUid: String, type: String): List<DefectDataAssetEntity>

    /** 更新下载状态。 */
    @Query("UPDATE defect_data_asset SET download_status = :status, updated_at = :updatedAt WHERE file_id = :fileId")
    suspend fun updateDownloadStatus(fileId: String, status: String, updatedAt: Long)

    /** 更新本地路径。 */
    @Query("UPDATE defect_data_asset SET local_path = :localPath, updated_at = :updatedAt WHERE file_id = :fileId")
    suspend fun updateLocalPath(fileId: String, localPath: String?, updatedAt: Long)

    /** 更新下载直链。 */
    @Query("UPDATE defect_data_asset SET download_url = :downloadUrl, updated_at = :updatedAt WHERE file_id = :fileId")
    suspend fun updateDownloadUrl(fileId: String, downloadUrl: String?, updatedAt: Long)

    /** 更新类型（按 file_id）。 */
    @Query("UPDATE defect_data_asset SET type = :type, updated_at = :updatedAt WHERE file_id = :fileId")
    suspend fun updateType(fileId: String, type: String, updatedAt: Long)

    /** 更新原始文件名（按 file_id）。 */
    @Query("UPDATE defect_data_asset SET file_name = :fileName, updated_at = :updatedAt WHERE file_id = :fileId")
    suspend fun updateFileName(fileId: String, fileName: String?, updatedAt: Long)
}