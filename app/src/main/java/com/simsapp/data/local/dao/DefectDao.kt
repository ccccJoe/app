/*
 * File: DefectDao.kt
 * Description: Room DAO for DefectEntity to manage defect records.
 * Author: SIMS Team
 */
package com.simsapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.simsapp.data.local.entity.DefectEntity
import kotlinx.coroutines.flow.Flow

/**
 * DefectDao
 *
 * Provides CRUD and query operations for defects.
 */
@Dao
interface DefectDao {
    /** Insert or replace a defect. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(defect: DefectEntity): Long

    /** Insert multiple defects. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(defects: List<DefectEntity>): List<Long>

    /** Update defect. */
    @Update
    suspend fun update(defect: DefectEntity)

    /** Delete by id. */
    @Query("DELETE FROM defect WHERE defect_id = :id")
    suspend fun deleteById(id: Long)

    /** Delete all defects for a project by project_id. */
    @Query("DELETE FROM defect WHERE project_id = :projectId")
    suspend fun deleteByProjectId(projectId: Long)

    /** Get all defects for a project by project_id, ordered by sort_order then defect_no. */
    @Query("SELECT * FROM defect WHERE project_id = :projectId ORDER BY sort_order ASC, defect_no ASC")
    fun getByProject(projectId: Long): Flow<List<DefectEntity>>
    
    /** Get all defects for a project by project_uid, ordered by sort_order then defect_no. */
    @Query("SELECT * FROM defect WHERE project_uid = :projectUid ORDER BY sort_order ASC, defect_no ASC")
    fun getByProjectUid(projectUid: String): Flow<List<DefectEntity>>
    
    /** Get defect by project_uid and defect_no. */
    @Query("SELECT * FROM defect WHERE project_uid = :projectUid AND defect_no = :defectNo LIMIT 1")
    suspend fun getByProjectUidAndDefectNo(projectUid: String, defectNo: String): DefectEntity?

    /** Get defect by id. */
    @Query("SELECT * FROM defect WHERE defect_id = :id LIMIT 1")
    suspend fun getById(id: Long): DefectEntity?

    /** Get defect by remote uid. */
    @Query("SELECT * FROM defect WHERE uid = :uid LIMIT 1")
    suspend fun getByUid(uid: String): DefectEntity?

    /** Get defect by project_uid and remote uid. */
    @Query("SELECT * FROM defect WHERE project_uid = :projectUid AND uid = :uid LIMIT 1")
    suspend fun getByProjectUidAndUid(projectUid: String, uid: String): DefectEntity?

    /** Update event_count for a defect by defect_id. */
    @Query("UPDATE defect SET event_count = :eventCount WHERE defect_id = :defectId")
    suspend fun updateEventCount(defectId: Long, eventCount: Int)

    /** Increment event_count for a defect by defect_id. */
    @Query("UPDATE defect SET event_count = event_count + 1 WHERE defect_id = :defectId")
    suspend fun incrementEventCount(defectId: Long)

    /** Decrement event_count for a defect by defect_id. */
    @Query("UPDATE defect SET event_count = event_count - 1 WHERE defect_id = :defectId AND event_count > 0")
    suspend fun decrementEventCount(defectId: Long)

    /** Update sort_order for a defect within a project. */
    @Query("UPDATE defect SET sort_order = :sortOrder WHERE project_uid = :projectUid AND defect_no = :defectNo")
    suspend fun updateSortOrder(projectUid: String, defectNo: String, sortOrder: Int)

    /**
     * 统计指定项目 UID 列表下的缺陷总数（Historical Defects）。
     * 返回：Flow<Int>，用于响应式更新。
     */
    @Query("SELECT COUNT(*) FROM defect WHERE project_uid IN (:projectUids)")
    fun countByProjectUids(projectUids: List<String>): Flow<Int>

    /**
     * 统计指定项目 UID 列表下 event_count > 0 的缺陷数量（Linked Events）。
     * 返回：Flow<Int>。
     */
    @Query("SELECT COUNT(*) FROM defect WHERE project_uid IN (:projectUids) AND event_count > 0")
    fun countLinkedByProjectUids(projectUids: List<String>): Flow<Int>
}