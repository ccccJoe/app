/*
 * File: DefectDataAssetEntity.kt
 * Description: Room 实体，用于缓存历史缺陷的数字资产（图片/音频/PDF等）。
 * Author: SIMS Team
 */
package com.simsapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 类：DefectDataAssetEntity
 * 职责：
 * - 记录与某个缺陷（通过远端 `defect_uid` 关联）相关的数字资产下载与本地缓存信息。
 * - 支持按照 `file_id` 唯一约束，避免重复插入与重复下载。
 * 设计思路：
 * - 与项目级 `ProjectDigitalAssetEntity` 类似，但聚焦到缺陷维度，并存储 `defect_uid`。
 * - 保存 `type`（文件类型）以支持页面按类型展示。
 */
@Entity(
    tableName = "defect_data_asset",
    indices = [
        Index(value = ["defect_uid"]),
        Index(value = ["project_uid"]),
        Index(value = ["file_id"], unique = true)
    ]
)
data class DefectDataAssetEntity(
    /** 主键ID（自增）。 */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** 关联的项目UID（便于查询与隔离）。 */
    @ColumnInfo(name = "project_uid")
    val projectUid: String,

    /** 关联的缺陷远端UID（与 defect 表的 `uid` 对应）。 */
    @ColumnInfo(name = "defect_uid")
    val defectUid: String,

    /** 远端文件ID（唯一约束）。 */
    @ColumnInfo(name = "file_id")
    val fileId: String,

    /** 文件类型（例如：PIC/REC/PDF/MP3/UNKNOWN）。 */
    @ColumnInfo(name = "type")
    val type: String,

    /** 远端返回的原始文件名（例如：微信图片_20250820...jpg）。 */
    @ColumnInfo(name = "file_name")
    val fileName: String? = null,

    /** 解析出的下载直链（可选）。 */
    @ColumnInfo(name = "download_url")
    val downloadUrl: String? = null,

    /** 本地缓存绝对路径（下载完成后填充）。 */
    @ColumnInfo(name = "local_path")
    val localPath: String? = null,

    /** 下载状态：PENDING/COMPLETED/FAILED。 */
    @ColumnInfo(name = "download_status")
    val downloadStatus: String = "PENDING",

    /** 创建时间戳（毫秒）。 */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /** 更新时间戳（毫秒）。 */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)