/*
 * File: AssetRepository.kt
 * Description: Repository for managing local assets and coordinating uploads.
 * Author: SIMS Team
 */
package com.simsapp.data.repository

import com.simsapp.data.local.dao.AssetDao
import com.simsapp.data.local.entity.AssetEntity
import com.simsapp.data.remote.ApiService
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AssetRepository
 *
 * Acts as a single source of truth for asset data, exposing query and mutation APIs.
 */
@Singleton
class AssetRepository @Inject constructor(
    private val assetDao: AssetDao,
    private val apiService: ApiService
) {
    /** Observe assets by event id. */
    fun observeEventAssets(eventId: Long): Flow<List<AssetEntity>> = assetDao.getByEvent(eventId)

    /** Observe assets by project id. */
    fun observeProjectAssets(projectId: Long): Flow<List<AssetEntity>> = assetDao.getByProject(projectId)

    /** Create or update an asset locally. */
    suspend fun upsert(asset: AssetEntity): Long = assetDao.insert(asset)

    /** Bulk upsert assets locally. */
    suspend fun upsertAll(assets: List<AssetEntity>): List<Long> = assetDao.insertAll(assets)

    /** Remove asset by id. */
    suspend fun remove(id: Long) = assetDao.deleteById(id)

    /**
     * Upload asset file to server.
     * Note: Implementation should conform to ApiService once backend endpoints are defined.
     */
    suspend fun uploadAsset(/* file: File, meta: Map<String, String> */): Boolean {
        // TODO: Integrate with ApiService.upload when backend contract is ready.
        return true
    }
}