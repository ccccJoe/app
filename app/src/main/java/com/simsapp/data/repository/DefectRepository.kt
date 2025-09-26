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

    /** Create or update a defect. */
    suspend fun upsert(defect: DefectEntity): Long = dao.insert(defect)
    
    /** Create or update multiple defects. */
    suspend fun upsertAll(defects: List<DefectEntity>): List<Long> = dao.insertAll(defects)

    /** Delete a defect by id. */
    suspend fun delete(id: Long) = dao.deleteById(id)
}