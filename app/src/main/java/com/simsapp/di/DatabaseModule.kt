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
import com.simsapp.data.local.dao.ProjectDigitalAssetDao
import com.simsapp.data.local.dao.DefectDataAssetDao
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
    
    /** Migration: v13 -> v14, add is_deleted column to project table for soft delete functionality. */
    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add is_deleted column to project table with default value false (0)
            database.execSQL("ALTER TABLE project ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")
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
     * Migration from version 14 to 15: Add uid field to EventEntity
     * 
     * 为EventEntity表添加uid字段，用于文件系统目录命名
     */
    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 1. 为event表添加uid字段
            database.execSQL("ALTER TABLE event ADD COLUMN uid TEXT NOT NULL DEFAULT ''")
            
            // 2. 为现有事件生成UUID
            database.execSQL("""
                UPDATE event 
                SET uid = 'event-' || event_id || '-' || CAST((RANDOM() * 1000000) AS INTEGER)
                WHERE uid = ''
            """.trimIndent())
            
            // 3. 创建uid字段的唯一索引
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_event_uid ON event (uid)")
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

    /** Migration: v6 -> v7, add asset table for digital assets. */
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add asset table for digital assets
            database.execSQL("CREATE TABLE IF NOT EXISTS asset (asset_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, url TEXT NOT NULL, event_id INTEGER, project_id INTEGER, FOREIGN KEY(event_id) REFERENCES event(event_id) ON DELETE CASCADE, FOREIGN KEY(project_id) REFERENCES project(project_id) ON DELETE CASCADE)")
        }
    }

    /** Migration: v7 -> v8, add project_digital_asset table for digital asset tree structure. */
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add project_digital_asset table for digital asset tree structure
            database.execSQL("CREATE TABLE IF NOT EXISTS project_digital_asset (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "project_uid TEXT NOT NULL, " +
                    "node_id TEXT NOT NULL, " +
                    "parent_id TEXT, " +
                    "name TEXT NOT NULL, " +
                    "type TEXT NOT NULL, " +
                    "file_id TEXT, " +
                    "local_path TEXT, " +
                    "download_status TEXT NOT NULL, " +
                    "download_url TEXT, " +
                    "file_size INTEGER, " +
                    "created_at INTEGER NOT NULL, " +
                    "updated_at INTEGER NOT NULL)")
            
            // Create indexes for better query performance
            database.execSQL("CREATE INDEX IF NOT EXISTS index_project_digital_asset_project_uid ON project_digital_asset (project_uid)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_project_digital_asset_node_id ON project_digital_asset (node_id)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_project_digital_asset_parent_id ON project_digital_asset (parent_id)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_project_digital_asset_file_id ON project_digital_asset (file_id)")
        }
    }

    /** Migration: v8 -> v9, add content field to project_digital_asset table for storing JSON data. */
    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add content column to project_digital_asset table
            database.execSQL("ALTER TABLE project_digital_asset ADD COLUMN content TEXT")
        }
    }

    /**
     * Migration from version 9 to 10.
     * 
     * Adds digital_asset_file_ids field to event table for storing selected digital asset file IDs.
     */
    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add digital_asset_file_ids column to event table
            database.execSQL("ALTER TABLE event ADD COLUMN digital_asset_file_ids TEXT NOT NULL DEFAULT '[]'")
        }
    }

    /**
     * Migration from version 10 to 11.
     * 
     * Removes digital_asset_file_ids field and modifies assets field to store DigitalAssetItem objects.
     */
    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create new event table with updated schema
            database.execSQL("""
                CREATE TABLE event_new (
                    event_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    project_id INTEGER NOT NULL,
                    project_uid TEXT NOT NULL DEFAULT '',
                    defect_ids TEXT NOT NULL DEFAULT '[]',
                    defect_nos TEXT NOT NULL DEFAULT '[]',
                    location TEXT,
                    content TEXT NOT NULL DEFAULT '',
                    last_edit_time INTEGER NOT NULL DEFAULT 0,
                    assets TEXT NOT NULL DEFAULT '[]',
                    risk_level TEXT,
                    risk_score REAL,
                    risk_answers TEXT,
                    photo_files TEXT NOT NULL DEFAULT '[]',
                    audio_files TEXT NOT NULL DEFAULT '[]',
                    is_draft INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY(project_id) REFERENCES project(project_id) ON DELETE CASCADE
                )
            """)
            
            // Copy data from old table to new table, converting digital_asset_file_ids to assets format
            database.execSQL("""
                INSERT INTO event_new (
                    event_id, project_id, project_uid, defect_ids, defect_nos, location, content,
                    last_edit_time, assets, risk_level, risk_score, risk_answers, photo_files, audio_files, is_draft
                )
                SELECT 
                    event_id, project_id, project_uid, defect_ids, defect_nos, location, content,
                    last_edit_time, 
                    CASE 
                        WHEN digital_asset_file_ids IS NOT NULL AND digital_asset_file_ids != '[]' AND digital_asset_file_ids != '' 
                        THEN digital_asset_file_ids 
                        ELSE '[]' 
                    END as assets, 
                    risk_level, risk_score, risk_answers, photo_files, audio_files, is_draft
                FROM event
            """)
            
            // Drop old table and rename new table
            database.execSQL("DROP TABLE event")
            database.execSQL("ALTER TABLE event_new RENAME TO event")
            
            // Recreate indices
            database.execSQL("CREATE INDEX index_event_project_id ON event(project_id)")
            database.execSQL("CREATE INDEX index_event_project_uid ON event(project_uid)")
        }
    }

    /** Migration: v11 -> v12, add digital asset file_id field. */
    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add file_id column to project_digital_asset table
            database.execSQL("ALTER TABLE project_digital_asset ADD COLUMN file_id TEXT")
            
            // Create index for file_id
            database.execSQL("CREATE INDEX IF NOT EXISTS index_project_digital_asset_file_id ON project_digital_asset (file_id)")
        }
    }
    
    /** Migration: v12 -> v13, adjust project_digital_asset table structure for multi-project support. */
    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 1. 创建新的project_digital_asset表，将project_uid改为project_uids
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS project_digital_asset_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    project_uids TEXT NOT NULL,
                    node_id TEXT NOT NULL,
                    parent_id TEXT,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    file_id TEXT,
                    file_size INTEGER,
                    download_url TEXT,
                    local_path TEXT,
                    download_status TEXT NOT NULL DEFAULT 'PENDING',
                    content TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())
            
            // 2. 迁移数据：将project_uid转换为project_uids JSON数组
            database.execSQL("""
                INSERT INTO project_digital_asset_new (
                    id, project_uids, node_id, parent_id, name, type, file_id, file_size,
                    download_url, local_path, download_status, content, created_at, updated_at
                )
                SELECT 
                    id, 
                    '["' || project_uid || '"]' as project_uids,
                    node_id, parent_id, name, type, file_id, file_size,
                    download_url, local_path, download_status, content, created_at, updated_at
                FROM project_digital_asset
            """.trimIndent())
            
            // 3. 删除旧表并重命名新表
            database.execSQL("DROP TABLE project_digital_asset")
            database.execSQL("ALTER TABLE project_digital_asset_new RENAME TO project_digital_asset")
            
            // 4. 重建索引
            database.execSQL("CREATE INDEX IF NOT EXISTS index_project_digital_asset_project_uids ON project_digital_asset (project_uids)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_project_digital_asset_node_id ON project_digital_asset (node_id)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_project_digital_asset_parent_id ON project_digital_asset (parent_id)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_project_digital_asset_file_id ON project_digital_asset (file_id)")
        }
    }

    /** Migration: v15 -> v16, add resource_id column to project_digital_asset table. */
    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add resource_id column to project_digital_asset table
            database.execSQL("ALTER TABLE project_digital_asset ADD COLUMN resource_id TEXT")
        }
    }

    /** Migration: v16 -> v17, add structural_defect_details field to event table. */
    private val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 为 event 表添加 structural_defect_details 字段，用于存储 SDD 表单数据
            database.execSQL("ALTER TABLE event ADD COLUMN structural_defect_details TEXT")
        }
    }

    /** Migration: v17 -> v18, reserved for future use. */
    private val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 预留迁移，暂无操作
        }
    }

    /** Migration: v18 -> v19, add defect.uid and event.defect_uids */
    private val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 为 defect 表添加 uid 字段（非唯一索引，用于按UID查询）
            database.execSQL("ALTER TABLE defect ADD COLUMN uid TEXT NOT NULL DEFAULT ''")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_defect_uid ON defect (uid)")

            // 为 event 表添加 defect_uids 字段（List<String> 的 JSON 存储）
            database.execSQL("ALTER TABLE event ADD COLUMN defect_uids TEXT NOT NULL DEFAULT '[]'")
        }
    }

    /** Migration: v19 -> v20, add sort_order column to defect for persistent ordering. */
    private val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 新增排序列，默认为0；在应用层根据拖拽结果写入顺序
            database.execSQL("ALTER TABLE defect ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** Migration: v20 -> v21, create defect_data_asset table for historical defects' assets. */
    private val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 创建用于缓存历史缺陷数字资产的表
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS defect_data_asset (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    project_uid TEXT NOT NULL,
                    defect_uid TEXT NOT NULL,
                    file_id TEXT NOT NULL,
                    type TEXT NOT NULL,
                    file_name TEXT,
                    download_url TEXT,
                    local_path TEXT,
                    download_status TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_defect_data_asset_defect_uid ON defect_data_asset(defect_uid)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_defect_data_asset_project_uid ON defect_data_asset(project_uid)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_defect_data_asset_file_id ON defect_data_asset(file_id)")
        }
    }

    /** Migration: v21 -> v22, add file_name column to defect_data_asset table (idempotent). */
    private val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add column only if it does not already exist to avoid duplicate column error
            val cursor = database.query("PRAGMA table_info(defect_data_asset)")
            var hasFileName = false
            try {
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    val col = if (nameIndex != -1) cursor.getString(nameIndex) else null
                    if (col == "file_name") {
                        hasFileName = true
                        break
                    }
                }
            } finally {
                cursor.close()
            }
            if (!hasFileName) {
                database.execSQL("ALTER TABLE defect_data_asset ADD COLUMN file_name TEXT")
            }
        }
    }

    /**
     * Migration: v22 -> v23
     * 目的：修复历史数据库中 project.project_uid 可能为 NULL 导致 Kotlin 非空映射崩溃的问题。
     * 方法：重建 project 表，确保以下字段的非空与默认值：
     * - name TEXT NOT NULL
     * - project_uid TEXT NOT NULL DEFAULT ''
     * - project_hash TEXT NOT NULL DEFAULT ''
     * - status TEXT NOT NULL DEFAULT 'ACTIVE'
     * - defect_count INTEGER NOT NULL DEFAULT 0
     * - event_count INTEGER NOT NULL DEFAULT 0
     * - is_deleted INTEGER NOT NULL DEFAULT 0
     * 并通过 COALESCE 将旧表中的空值安全回填为默认值。
     */
    private val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 1. 创建新表，约束非空与默认值
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS project_new (
                    project_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    project_uid TEXT NOT NULL DEFAULT '',
                    project_hash TEXT NOT NULL DEFAULT '',
                    end_date INTEGER,
                    status TEXT NOT NULL DEFAULT 'ACTIVE',
                    defect_count INTEGER NOT NULL DEFAULT 0,
                    event_count INTEGER NOT NULL DEFAULT 0,
                    is_deleted INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )

            // 2. 拷贝旧数据，使用 COALESCE 将 NULL 转换为安全默认值
            database.execSQL(
                """
                INSERT INTO project_new (
                    project_id, name, project_uid, project_hash, end_date, status, defect_count, event_count, is_deleted
                )
                SELECT 
                    project_id,
                    COALESCE(name, ''),
                    COALESCE(project_uid, ''),
                    COALESCE(project_hash, ''),
                    end_date,
                    COALESCE(status, 'ACTIVE'),
                    COALESCE(defect_count, 0),
                    COALESCE(event_count, 0),
                    COALESCE(is_deleted, 0)
                FROM project
                """.trimIndent()
            )

            // 3. 替换旧表
            database.execSQL("DROP TABLE project")
            database.execSQL("ALTER TABLE project_new RENAME TO project")

            // 4. 重建索引
            database.execSQL("CREATE INDEX IF NOT EXISTS index_project_name ON project (name)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_project_project_uid ON project (project_uid)")
        }
    }

    /**
     * Migration: v23 -> v24
     * 目的：为 event 表新增同步标记字段 is_synced，便于区分未同步与已同步事件。
     * 逻辑：
     * - 添加新列 is_synced，类型为 INTEGER，非空，默认值为 0（未同步）。
     * - 创建复合索引 (project_uid, is_synced)，优化按项目筛选未同步事件的查询。
     */
    private val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 为 event 表添加 is_synced 列，默认 0 表示未同步
            database.execSQL("ALTER TABLE event ADD COLUMN is_synced INTEGER NOT NULL DEFAULT 0")
            // 创建复合索引以优化查询
            database.execSQL("CREATE INDEX IF NOT EXISTS index_event_project_uid_is_synced ON event (project_uid, is_synced)")
        }
    }

    /**
     * Migration: v24 -> v25
     * 目的：为 project 表新增缓存字段 ralation_time（INTEGER，可空），用于首页 project_list 接口的关系时间缓存。
     * 逻辑：添加列，不设置 NOT NULL，以兼容历史数据为空。
     */
    private val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE project ADD COLUMN ralation_time INTEGER")
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
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25)
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

    /** Provide ProjectDigitalAssetDao. */
    @Provides
    fun provideProjectDigitalAssetDao(db: AppDatabase): ProjectDigitalAssetDao = db.projectDigitalAssetDao()

    /** Provide DefectDataAssetDao. */
    @Provides
    fun provideDefectDataAssetDao(db: AppDatabase): DefectDataAssetDao = db.defectDataAssetDao()
}