/*
 * File: EventDao.kt
 * Description: Room DAO for EventEntity to manage event records.
 * Author: SIMS Team
 */
package com.simsapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.simsapp.data.local.entity.EventEntity
import kotlinx.coroutines.flow.Flow

/**
 * EventDao
 *
 * Provides CRUD and query operations for events.
 */
@Dao
interface EventDao {
    /** Insert or replace an event. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity): Long

    /** Insert multiple events. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventEntity>): List<Long>

    /** Update event. */
    @Update
    suspend fun update(event: EventEntity)

    /** Delete by id. */
    @Query("DELETE FROM event WHERE event_id = :id")
    suspend fun deleteById(id: Long)

    /** Delete all events for a project by project_id. */
    @Query("DELETE FROM event WHERE project_id = :projectId")
    suspend fun deleteByProjectId(projectId: Long)

    /** Get all events for a project by project_id. */
    @Query("SELECT * FROM event WHERE project_id = :projectId ORDER BY last_edit_time DESC")
    fun getByProject(projectId: Long): Flow<List<EventEntity>>
    
    /** Get all events for a project by project_uid. */
    @Query("SELECT * FROM event WHERE project_uid = :projectUid ORDER BY last_edit_time DESC")
    fun getByProjectUid(projectUid: String): Flow<List<EventEntity>>

    /** Get unsynced events for a project by project_uid. */
    @Query("SELECT * FROM event WHERE project_uid = :projectUid AND is_synced = 0 ORDER BY last_edit_time DESC")
    fun getUnsyncedByProjectUid(projectUid: String): Flow<List<EventEntity>>
    
    /** Get events that reference a specific defect by defect_id. */
    @Query("SELECT * FROM event WHERE defect_ids LIKE '%' || :defectId || '%' ORDER BY last_edit_time DESC")
    fun getByDefectId(defectId: Long): Flow<List<EventEntity>>
    
    /** Get events that reference a specific defect by defect_no and project_uid. */
    @Query("SELECT * FROM event WHERE project_uid = :projectUid AND defect_nos LIKE '%' || :defectNo || '%' ORDER BY last_edit_time DESC")
    fun getByDefectNoAndProjectUid(projectUid: String, defectNo: String): Flow<List<EventEntity>>

    /** Get events that reference a specific defect by defect_uid. */
    @Query("SELECT * FROM event WHERE defect_uids LIKE '%' || :defectUid || '%' ORDER BY last_edit_time DESC")
    fun getByDefectUid(defectUid: String): Flow<List<EventEntity>>

    /** Get events that reference a specific defect by defect_uid and project_uid. */
    @Query("SELECT * FROM event WHERE project_uid = :projectUid AND defect_uids LIKE '%' || :defectUid || '%' ORDER BY last_edit_time DESC")
    fun getByDefectUidAndProjectUid(projectUid: String, defectUid: String): Flow<List<EventEntity>>

    /** Get event by id. */
    @Query("SELECT * FROM event WHERE event_id = :id LIMIT 1")
    suspend fun getById(id: Long): EventEntity?

    /** Get event by UID (using actual uid field). */
    @Query("SELECT * FROM event WHERE uid = :uid LIMIT 1")
    suspend fun getByUid(uid: String): EventEntity?

    /** Mark single event as synced by UID. */
    @Query("UPDATE event SET is_synced = 1 WHERE uid = :uid")
    suspend fun markSyncedByUid(uid: String)

    /** Mark multiple events as synced by UIDs. */
    @Query("UPDATE event SET is_synced = 1 WHERE uid IN (:uids)")
    suspend fun markSyncedByUids(uids: List<String>)
    
    /** Get all events (for migration purposes). */
    @Query("SELECT * FROM event ORDER BY last_edit_time DESC")
    suspend fun getAllEvents(): List<EventEntity>

    /**
     * 统计未关联任何缺陷的事件数量。
     * 条件：defect_uids 为 NULL 或 JSON 空数组 '[]'。
     * 返回响应式 Flow 便于 UI 观察。
     */
    @Query("SELECT COUNT(*) FROM event WHERE defect_uids IS NULL OR defect_uids = '[]'")
    fun countUnlinkedEvents(): Flow<Int>
}