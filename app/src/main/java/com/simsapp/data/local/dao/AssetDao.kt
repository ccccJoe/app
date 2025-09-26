/*
 * File: AssetDao.kt
 * Description: Room DAO for AssetEntity to manage digital assets.
 * Author: SIMS Team
 */
package com.simsapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.simsapp.data.local.entity.AssetEntity
import kotlinx.coroutines.flow.Flow

/**
 * AssetDao
 *
 * Provides CRUD and query operations for assets.
 */
@Dao
interface AssetDao {
    /** Insert or replace an asset. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: AssetEntity): Long

    /** Insert a list of assets. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(assets: List<AssetEntity>): List<Long>

    /** Update an asset. */
    @Update
    suspend fun update(asset: AssetEntity)

    /** Delete by id. */
    @Query("DELETE FROM asset WHERE asset_id = :id")
    suspend fun deleteById(id: Long)

    /** Get assets by event id. */
    @Query("SELECT * FROM asset WHERE event_id = :eventId ORDER BY created_at DESC")
    fun getByEvent(eventId: Long): Flow<List<AssetEntity>>

    /** Get assets by project id. */
    @Query("SELECT * FROM asset WHERE project_id = :projectId ORDER BY created_at DESC")
    fun getByProject(projectId: Long): Flow<List<AssetEntity>>
}