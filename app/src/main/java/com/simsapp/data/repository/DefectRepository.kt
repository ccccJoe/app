/*
 * File: DefectRepository.kt
 * Description: Repository for defect-related data.
 * Author: SIMS Team
 */
package com.simsapp.data.repository

import com.simsapp.data.local.dao.DefectDao
import com.simsapp.data.local.entity.DefectEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * DefectRepository
 *
 * Provides a unified API for defect data operations.
 */
@Singleton
class DefectRepository @Inject constructor(
    private val dao: DefectDao
) {
    /** Observe defects by project_id. */
    fun getDefectsByProject(projectId: Long): Flow<List<DefectEntity>> = dao.getByProject(projectId)
    
    /** Observe defects by project_uid. */
    fun getDefectsByProjectUid(projectUid: String): Flow<List<DefectEntity>> = dao.getByProjectUid(projectUid)
    
    /** Get defect by project_uid and defect_no. */
    suspend fun getDefectByProjectUidAndDefectNo(projectUid: String, defectNo: String): DefectEntity? = 
        dao.getByProjectUidAndDefectNo(projectUid, defectNo)
    
    /** Get defect by id. */
    suspend fun getDefectById(defectId: Long): DefectEntity? = dao.getById(defectId)

    /** Create or update a defect. */
    suspend fun upsert(defect: DefectEntity): Long = dao.insert(defect)
    
    /** Create or update multiple defects. */
    suspend fun upsertAll(defects: List<DefectEntity>): List<Long> = dao.insertAll(defects)

    /** Delete a defect by id. */
    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Delete all defects for a project by project_id. */
    suspend fun deleteByProjectId(projectId: Long) = dao.deleteByProjectId(projectId)

    /** Update event_count for a defect by defect_id. */
    suspend fun updateEventCount(defectId: Long, eventCount: Int) = dao.updateEventCount(defectId, eventCount)

    /** Increment event_count for a defect by defect_id. */
    suspend fun incrementEventCount(defectId: Long) = dao.incrementEventCount(defectId)

    /** Decrement event_count for a defect by defect_id. */
    suspend fun decrementEventCount(defectId: Long) = dao.decrementEventCount(defectId)
}