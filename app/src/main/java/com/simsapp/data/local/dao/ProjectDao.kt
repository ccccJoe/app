/*
 * File: ProjectDao.kt
 * Description: Room DAO for ProjectEntity to access and mutate project data.
 * Author: SIMS Team
 */
package com.simsapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.simsapp.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

/**
 * ProjectDao
 *
 * Defines CRUD and query operations for ProjectEntity.
 */
@Dao
interface ProjectDao {
    /** Insert a single project, replacing on conflict. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity): Long

    /** Insert multiple projects, replacing on conflict. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(projects: List<ProjectEntity>): List<Long>

    /** Update an existing project. */
    @Update
    suspend fun update(project: ProjectEntity)

    /** Delete a project by id. */
    @Query("DELETE FROM project WHERE project_id = :id")
    suspend fun deleteById(id: Long)

    /** Get all projects as a Flow ordered by name. */
    @Query("SELECT * FROM project ORDER BY name ASC")
    fun getAll(): Flow<List<ProjectEntity>>

    /** Get a single project by id. */
    @Query("SELECT * FROM project WHERE project_id = :id LIMIT 1")
    suspend fun getById(id: Long): ProjectEntity?
    
    /** Get a single project by uid. */
    @Query("SELECT * FROM project WHERE project_uid = :uid LIMIT 1")
    suspend fun getByUid(uid: String): ProjectEntity?
    
    /** Get project id by uid. */
    @Query("SELECT project_id FROM project WHERE project_uid = :uid LIMIT 1")
    suspend fun getIdByUid(uid: String): Long?

    /** Search projects by name keyword. */
    @Query("SELECT * FROM project WHERE name LIKE '%' || :keyword || '%' ORDER BY name ASC")
    fun searchByName(keyword: String): Flow<List<ProjectEntity>>

    /** 新增：按名称精确解析项目ID（用于实时保存草稿解析归属项目） */
    @Query("SELECT project_id FROM project WHERE name = :name LIMIT 1")
    suspend fun getIdByExactName(name: String): Long?
    
    /** 新增：按名称精确获取项目实体（用于缺陷选择对话框获取projectUid） */
    @Query("SELECT * FROM project WHERE name = :name LIMIT 1")
    suspend fun getByExactName(name: String): ProjectEntity?

    /** Update aggregated counters. */
    @Query("UPDATE project SET defect_count = :defectCount, event_count = :eventCount WHERE project_id = :projectId")
    suspend fun updateCounters(projectId: Long, defectCount: Int, eventCount: Int)

    /** 清空项目表，支持全量覆盖更新 */
    @Query("DELETE FROM project")
    suspend fun clearAll()

    /** 获取当前项目总数 */
    @Query("SELECT COUNT(*) FROM project")
    suspend fun count(): Int

    /** 根据project_uid获取project_hash值，用于增量同步比较 */
    @Query("SELECT project_hash FROM project WHERE project_uid = :uid LIMIT 1")
    suspend fun getHashByUid(uid: String): String?

    /** 获取所有项目的uid和hash映射，用于批量比较 */
    @Query("SELECT project_uid, project_hash FROM project")
    suspend fun getAllUidHashPairs(): List<UidHashPair>

    /** 批量更新项目（仅更新变化的项目） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(projects: List<ProjectEntity>): List<Long>

    /** 获取所有已完成状态的项目 */
    @Query("SELECT * FROM project WHERE UPPER(status) = 'FINISHED' OR status = '已完成' ORDER BY name ASC")
    fun getFinishedProjects(): Flow<List<ProjectEntity>>

    /** 批量删除项目（根据项目ID列表） */
    @Query("DELETE FROM project WHERE project_id IN (:projectIds)")
    suspend fun deleteByIds(projectIds: List<Long>)
}

/**
 * 数据类：用于存储project_uid和project_hash的映射关系
 */
data class UidHashPair(
    val project_uid: String,
    val project_hash: String
)