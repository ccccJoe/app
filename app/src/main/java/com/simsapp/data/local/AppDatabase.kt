/*
 * File: AppDatabase.kt
 * Description: Room database definition including entities, DAOs, and type converters.
 * Author: SIMS Team
 */
package com.simsapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.simsapp.data.local.dao.DefectDao
import com.simsapp.data.local.dao.EventDao
import com.simsapp.data.local.dao.ProjectDao
import com.simsapp.data.local.dao.AssetDao
import com.simsapp.data.local.dao.ProjectDetailDao
import com.simsapp.data.local.dao.ProjectDigitalAssetDao
import com.simsapp.data.local.entity.AssetEntity
import com.simsapp.data.local.entity.DefectEntity
import com.simsapp.data.local.entity.EventEntity
import com.simsapp.data.local.entity.ProjectEntity
import com.simsapp.data.local.entity.ProjectDetailEntity
import com.simsapp.data.local.entity.ProjectDigitalAssetEntity

/**
 * AppDatabase
 *
 * Central Room database for the SIMS app.
 */
@Database(
    entities = [ProjectEntity::class, DefectEntity::class, EventEntity::class, AssetEntity::class, ProjectDetailEntity::class, ProjectDigitalAssetEntity::class],
    version = 9,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    /** Provides DAO for project operations. */
    abstract fun projectDao(): ProjectDao

    /** Provides DAO for defect operations. */
    abstract fun defectDao(): DefectDao

    /** Provides DAO for event operations. */
    abstract fun eventDao(): EventDao

    /** Provides DAO for asset operations. */
    abstract fun assetDao(): AssetDao

    /** Provides DAO for project detail operations. */
    abstract fun projectDetailDao(): ProjectDetailDao

    /** Provides DAO for project digital asset operations. */
    abstract fun projectDigitalAssetDao(): ProjectDigitalAssetDao
}