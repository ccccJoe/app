/*
 * File: DatabaseModule.kt
 * Description: Hilt module providing Room database and DAOs.
 * Author: SIMS Team
 */
package com.simsapp.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.simsapp.data.local.AppDatabase
import com.simsapp.data.local.dao.AssetDao
import com.simsapp.data.local.dao.DefectDao
import com.simsapp.data.local.dao.EventDao
import com.simsapp.data.local.dao.ProjectDao
import com.simsapp.data.local.dao.ProjectDetailDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DatabaseModule
 *
 * Provides Room database and DAOs singleton instances.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** Migration: v2 -> v3, add is_draft column with default 1 (true). */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Room 默认不支持新增非空列到已有表，这里新增可空列并在后续写入时保证非空。
            // SQLite: add column is_draft INTEGER DEFAULT 1
            database.execSQL("ALTER TABLE event ADD COLUMN is_draft INTEGER DEFAULT 1")
        }
    }
    
    /** Migration: v3 -> v4, add project_uid to defect and event tables, modify project_uid constraints. */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 1. 修改ProjectEntity的project_uid为非空且唯一
            database.execSQL("CREATE TABLE IF NOT EXISTS project_new (" +
                    "project_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "project_uid TEXT NOT NULL DEFAULT '', " +
                    "end_date INTEGER, " +
                    "status TEXT NOT NULL, " +
                    "defect_count INTEGER NOT NULL, " +
                    "event_count INTEGER NOT NULL)")
            
            // 复制数据，确保project_uid不为null
            database.execSQL("INSERT INTO project_new (project_id, name, project_uid, end_date, status, defect_count, event_count) " +
                    "SELECT project_id, name, IFNULL(project_uid, ''), end_date, status, defect_count, event_count FROM project")
            
            // 删除旧表并重命名新表
            database.execSQL("DROP TABLE project")
            database.execSQL("ALTER TABLE project_new RENAME TO project")
            
            // 重建索引
            database.execSQL("CREATE INDEX IF NOT EXISTS index_project_name ON project (name)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_project_project_uid ON project (project_uid)")
            
            // 2. 修改ProjectDetailEntity的project_uid为非空且唯一
            database.execSQL("CREATE TABLE IF NOT EXISTS project_detail_new (" +
                    "detail_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "project_id INTEGER NOT NULL, " +
                    "project_uid TEXT NOT NULL DEFAULT '', " +
                    "name TEXT, " +
                    "status TEXT, " +
                    "start_date INTEGER, " +
                    "end_date INTEGER, " +
                    "last_update_at INTEGER, " +
                    "raw_json TEXT NOT NULL, " +
                    "last_fetched_at INTEGER NOT NULL, " +
                    "FOREIGN KEY(project_id) REFERENCES project(project_id) ON DELETE CASCADE)")
            
            // 复制数据，确保project_uid不为null
            database.execSQL("INSERT INTO project_detail_new (detail_id, project_id, project_uid, name, status, start_date, end_date, last_update_at, raw_json, last_fetched_at) " +
                    "SELECT detail_id, project_id, IFNULL(project_uid, ''), name, status, start_date, end_date, last_update_at, raw_json, last_fetched_at FROM project_detail")
            
            // 删除旧表并重命名新表
            database.execSQL("DROP TABLE project_detail")
            database.execSQL("ALTER TABLE project_detail_new RENAME TO project_detail")
            
            // 重建索引
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_project_detail_project_id ON project_detail (project_id)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_project_detail_project_uid ON project_detail (project_uid)")
            
            // 3. 添加project_uid到DefectEntity
            database.execSQL("ALTER TABLE defect ADD COLUMN project_uid TEXT NOT NULL DEFAULT ''")
            
            // 更新defect表中的project_uid
            database.execSQL("UPDATE defect SET project_uid = (SELECT project_uid FROM project WHERE project.project_id = defect.project_id)")
            
            // 创建复合唯一索引
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_defect_project_uid_defect_no ON defect (project_uid, defect_no)")
            
            // 4. 添加project_uid和defect_nos到EventEntity
            database.execSQL("ALTER TABLE event ADD COLUMN project_uid TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE event ADD COLUMN defect_nos TEXT NOT NULL DEFAULT '[]'")
            
            // 更新event表中的project_uid
            database.execSQL("UPDATE event SET project_uid = (SELECT project_uid FROM project WHERE project.project_id = event.project_id)")
            
            // 创建索引
            database.execSQL("CREATE INDEX IF NOT EXISTS index_event_project_uid ON event (project_uid)")
        }
    }

    /**
     * Database migration from version 4 to 5.
     * Adds project_hash field to ProjectEntity for incremental sync optimization.
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 添加project_hash字段到project表
            database.execSQL("ALTER TABLE project ADD COLUMN project_hash TEXT NOT NULL DEFAULT ''")
        }
    }

    /**
     * Database migration from version 5 to 6.
     * Adds risk assessment and media file fields to EventEntity for complete data caching.
     */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 添加风险评估相关字段到event表
            database.execSQL("ALTER TABLE event ADD COLUMN risk_level TEXT")
            database.execSQL("ALTER TABLE event ADD COLUMN risk_score REAL")
            database.execSQL("ALTER TABLE event ADD COLUMN risk_answers TEXT")
            // 添加媒体文件路径字段到event表
            database.execSQL("ALTER TABLE event ADD COLUMN photo_files TEXT NOT NULL DEFAULT '[]'")
            database.execSQL("ALTER TABLE event ADD COLUMN audio_files TEXT NOT NULL DEFAULT '[]'")
        }
    }

    /**
     * Provide the Room database instance.
     *
     * - 在 DEBUG 构建下启用 fallbackToDestructiveMigration 以避免历史版本迁移缺失导致的启动崩溃。
     * - 在 RELEASE 构建下严格执行迁移，确保数据安全与兼容。
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "sims.db")
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            // 仅调试构建启用破坏性迁移，避免老设备上的历史 DB 版本导致崩溃
            .apply {
                if (com.simsapp.BuildConfig.DEBUG) fallbackToDestructiveMigration()
            }
            .build()

    /** Provide ProjectDao. */
    @Provides
    fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()

    /** Provide DefectDao. */
    @Provides
    fun provideDefectDao(db: AppDatabase): DefectDao = db.defectDao()

    /** Provide EventDao. */
    @Provides
    fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()

    /** Provide AssetDao. */
    @Provides
    fun provideAssetDao(db: AppDatabase): AssetDao = db.assetDao()

    /** Provide ProjectDetailDao. */
    @Provides
    fun provideProjectDetailDao(db: AppDatabase): ProjectDetailDao = db.projectDetailDao()
}