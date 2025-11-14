/*
 * File: AppDatabase.kt
 * Description: Room database configuration with entities, version, and type converters.
 * Author: SIMS Team
 */
package com.simsapp.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.simsapp.data.local.dao.AssetDao
import com.simsapp.data.local.dao.DefectDao
import com.simsapp.data.local.dao.EventDao
import com.simsapp.data.local.dao.ProjectDao
import com.simsapp.data.local.dao.ProjectDetailDao
import com.simsapp.data.local.dao.ProjectDigitalAssetDao
import com.simsapp.data.local.dao.DefectDataAssetDao
import com.simsapp.data.local.entity.AssetEntity
import com.simsapp.data.local.entity.DefectEntity
import com.simsapp.data.local.entity.EventEntity
import com.simsapp.data.local.entity.ProjectDetailEntity
import com.simsapp.data.local.entity.ProjectDigitalAssetEntity
import com.simsapp.data.local.entity.DefectDataAssetEntity
import com.simsapp.data.local.entity.ProjectEntity

/**
 * AppDatabase
 *
 * Room database configuration for the SIMS application.
 * Includes all entities, DAOs, and type converters.
 */
@Database(
    entities = [
        ProjectEntity::class,
        DefectEntity::class,
        EventEntity::class,
        AssetEntity::class,
        ProjectDetailEntity::class,
        ProjectDigitalAssetEntity::class,
        DefectDataAssetEntity::class
    ],
    version = 25, // 升级到25以添加 project.ralation_time 字段用于首页项目列表缓存
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun defectDao(): DefectDao
    abstract fun eventDao(): EventDao
    abstract fun assetDao(): AssetDao
    abstract fun projectDetailDao(): ProjectDetailDao
    abstract fun projectDigitalAssetDao(): ProjectDigitalAssetDao
    abstract fun defectDataAssetDao(): DefectDataAssetDao
}