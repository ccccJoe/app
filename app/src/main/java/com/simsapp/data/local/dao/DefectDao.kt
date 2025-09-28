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

    /** Get all defects for a project by project_id. */
    @Query("SELECT * FROM defect WHERE project_id = :projectId ORDER BY defect_no ASC")
    fun getByProject(projectId: Long): Flow<List<DefectEntity>>
    
    /** Get all defects for a project by project_uid. */
    @Query("SELECT * FROM defect WHERE project_uid = :projectUid ORDER BY defect_no ASC")
    fun getByProjectUid(projectUid: String): Flow<List<DefectEntity>>
    
    /** Get defect by project_uid and defect_no. */
    @Query("SELECT * FROM defect WHERE project_uid = :projectUid AND defect_no = :defectNo LIMIT 1")
    suspend fun getByProjectUidAndDefectNo(projectUid: String, defectNo: String): DefectEntity?

    /** Get defect by id. */
    @Query("SELECT * FROM defect WHERE defect_id = :id LIMIT 1")
    suspend fun getById(id: Long): DefectEntity?

    /** Update event_count for a defect by defect_id. */
    @Query("UPDATE defect SET event_count = :eventCount WHERE defect_id = :defectId")
    suspend fun updateEventCount(defectId: Long, eventCount: Int)

    /** Increment event_count for a defect by defect_id. */
    @Query("UPDATE defect SET event_count = event_count + 1 WHERE defect_id = :defectId")
    suspend fun incrementEventCount(defectId: Long)

    /** Decrement event_count for a defect by defect_id. */
    @Query("UPDATE defect SET event_count = event_count - 1 WHERE defect_id = :defectId AND event_count > 0")
    suspend fun decrementEventCount(defectId: Long)
}