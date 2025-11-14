/*
 * File: ProjectDao.kt
 * Description: Room DAO for ProjectEntity to access and mutate project data.
 * Author: SIMS Team
 */
package com.simsapp.data.local.dao

import android.util.Log
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

    /**
     * 函数：updateStatusByUid
     * 说明：根据 `project_uid` 更新项目的 `status` 字段，仅变更状态不影响其他字段。
     * 参数：
     * - uid: 目标项目的唯一业务标识
     * - status: 远端最新状态字符串（大小写不敏感，不做转换直接入库）
     * 返回：无
     */
    @Query("UPDATE project SET status = :status WHERE project_uid = :uid")
    suspend fun updateStatusByUid(uid: String, status: String)

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
    
    /** 
     * 安全更新项目信息，避免级联删除关联的events和defects
     * 对于已存在的项目，使用UPDATE而不是REPLACE，保护外键关联数据
     */
    suspend fun safeUpdateProjects(projects: List<ProjectEntity>) {
        for (project in projects) {
            val existingProject = getByUid(project.projectUid)
            if (existingProject != null) {
                // 项目已存在，使用UPDATE更新字段，保留project_id不变
                val updatedProject = project.copy(projectId = existingProject.projectId)
                Log.d("ProjectDao", "Updating existing project: ${project.projectUid}, preserving project_id: ${existingProject.projectId}")
                update(updatedProject)
            } else {
                // 新项目，直接插入
                Log.d("ProjectDao", "Inserting new project: ${project.projectUid}")
                insert(project)
            }
        }
    }

    /** 获取所有已完成状态的项目（排除已删除的项目） */
    @Query("SELECT * FROM project WHERE (UPPER(status) = 'FINISHED' OR status = '已完成') AND (is_deleted = 0 OR is_deleted IS NULL) ORDER BY name ASC")
    fun getFinishedProjects(): Flow<List<ProjectEntity>>

    /**
     * 获取所有非已完成状态的项目（排除已删除的项目）
     * 说明：用于上传前的目标项目选择弹窗，仅展示可收集或激活中的项目。
     * 兼容中英文状态值，过滤 FINISHED/已完成。
     */
    @Query("SELECT * FROM project WHERE (UPPER(status) <> 'FINISHED' AND status <> '已完成') AND (is_deleted = 0 OR is_deleted IS NULL) ORDER BY name ASC")
    fun getNotFinishedProjects(): Flow<List<ProjectEntity>>

    /** 批量删除项目（根据项目ID列表） */
    @Query("DELETE FROM project WHERE project_id IN (:projectIds)")
    suspend fun deleteByIds(projectIds: List<Long>)
    
    /** Mark projects as deleted by IDs (batch soft deletion). */
    @Query("UPDATE project SET is_deleted = 1 WHERE project_id IN (:projectIds)")
    suspend fun markAsDeleted(projectIds: List<Long>)

    /**
     * 函数：updateRalationTimeByUid
     * 说明：根据 `project_uid` 更新项目的 `ralation_time` 字段，用于首页 project_list 接口缓存。
     * 参数：
     * - uid: 目标项目的唯一业务标识
     * - time: 关系时间（毫秒），可为空；空时写入 NULL
     */
    @Query("UPDATE project SET ralation_time = :time WHERE project_uid = :uid")
    suspend fun updateRalationTimeByUid(uid: String, time: Long?)

    // --- Diagnostics helpers for migration verification ---
    /** Count rows with any nullable critical fields that should be non-null (post-migration). */
    @Query("SELECT COUNT(*) FROM project WHERE name IS NULL OR project_uid IS NULL OR project_hash IS NULL OR status IS NULL OR defect_count IS NULL OR event_count IS NULL OR is_deleted IS NULL")
    suspend fun countRowsWithNulls(): Int

    /** Sample a few rows for logging to inspect field values post-migration. */
    @Query("SELECT * FROM project LIMIT :limit")
    suspend fun sample(limit: Int): List<ProjectEntity>
}

/**
 * 数据类：用于存储project_uid和project_hash的映射关系
 */
data class UidHashPair(
    val project_uid: String,
    val project_hash: String
)