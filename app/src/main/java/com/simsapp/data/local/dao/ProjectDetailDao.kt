/*
 * File: ProjectDetailDao.kt
 * Description: Room DAO for ProjectDetailEntity providing CRUD operations and queries.
 * Author: SIMS Team
 */
package com.simsapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.simsapp.data.local.entity.ProjectDetailEntity
import kotlinx.coroutines.flow.Flow

/*
 * File: ProjectDetailDao.kt
 * Description: Room DAO for ProjectDetailEntity providing CRUD operations and queries.
 * Author: SIMS Team
 */

/**
 * ProjectDetailDao
 *
 * Defines CRUD and query operations for ProjectDetailEntity.
 */
@Dao
interface ProjectDetailDao {
    /** Insert or replace a project detail. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(detail: ProjectDetailEntity): Long

    /** Update an existing project detail. */
    @Update
    suspend fun update(detail: ProjectDetailEntity)

    /** Get detail by project id. */
    @Query("SELECT * FROM project_detail WHERE project_id = :projectId LIMIT 1")
    suspend fun getByProjectId(projectId: Long): ProjectDetailEntity?

    /** Get detail by project uid. */
    @Query("SELECT * FROM project_detail WHERE project_uid = :projectUid LIMIT 1")
    suspend fun getByProjectUid(projectUid: String): ProjectDetailEntity?

    // 新增：按 project_uid 订阅单条详情的变化，用于详情页自动刷新
    // 返回 Flow<ProjectDetailEntity?>，当该条记录更新或被替换时会自动通知订阅者
    @Query("SELECT * FROM project_detail WHERE project_uid = :projectUid LIMIT 1")
    fun observeByProjectUid(projectUid: String): Flow<ProjectDetailEntity?>

    // 新增：查询所有详情记录的 Flow，用于首页统计历史缺陷数量
    @Query("SELECT * FROM project_detail")
    fun getAll(): Flow<List<ProjectDetailEntity>>

    /** Delete all details (for full resync). */
    @Query("DELETE FROM project_detail")
    suspend fun clearAll()
}