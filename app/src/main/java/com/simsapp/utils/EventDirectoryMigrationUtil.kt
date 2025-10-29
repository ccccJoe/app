/**
 * 事件目录迁移工具类
 * 
 * 用于为现有的事件创建缺失的文件系统目录结构，确保批量同步功能正常工作。
 * 
 * @author SIMS Team
 * @since 2025-01-27
 */
package com.simsapp.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.simsapp.data.local.dao.EventDao
import com.simsapp.data.local.entity.EventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 事件目录迁移工具类
 * 
 * 负责为现有事件创建缺失的目录结构，包括：
 * - 创建事件目录 /data/data/com.simsapp/files/events/{eventUid}/
 * - 生成meta.json文件
 * - 复制相关的媒体文件（如果存在）
 */
@Singleton
class EventDirectoryMigrationUtil @Inject constructor(
    private val eventDao: EventDao,
    private val gson: Gson
) {
    
    /**
     * 为所有现有事件创建缺失的目录结构
     * 
     * @param context Android上下文
     * @return 迁移结果统计信息
     */
    suspend fun migrateEventDirectories(context: Context): MigrationResult = withContext(Dispatchers.IO) {
        Log.d("EventMigration", "Starting event directory migration...")
        
        val result = MigrationResult()
        
        try {
            // 获取所有事件
            val allEvents = eventDao.getAllEvents()
            Log.d("EventMigration", "Found ${allEvents.size} events in database")
            
            val eventsDir = File(context.filesDir, "events")
            if (!eventsDir.exists()) {
                eventsDir.mkdirs()
                Log.d("EventMigration", "Created events root directory: ${eventsDir.absolutePath}")
            }
            
            allEvents.forEach { event ->
                try {
                    val migrated = migrateEventDirectory(context, event)
                    if (migrated) {
                        result.successCount++
                        Log.d("EventMigration", "Successfully migrated event: ${event.uid}")
                    } else {
                        result.skippedCount++
                        Log.d("EventMigration", "Skipped event (directory already exists): ${event.uid}")
                    }
                } catch (e: Exception) {
                    result.failureCount++
                    result.errors.add("Event ${event.uid}: ${e.message}")
                    Log.e("EventMigration", "Failed to migrate event ${event.uid}: ${e.message}", e)
                }
            }
            
            Log.d("EventMigration", "Migration completed: ${result.successCount} created, ${result.skippedCount} skipped, ${result.failureCount} failed")
            
        } catch (e: Exception) {
            Log.e("EventMigration", "Migration failed: ${e.message}", e)
            result.errors.add("Migration failed: ${e.message}")
        }
        
        result
    }
    
    /**
     * 为单个事件创建目录结构
     * 
     * @param context Android上下文
     * @param event 事件实体
     * @return true表示创建了新目录，false表示目录已存在
     */
    private suspend fun migrateEventDirectory(context: Context, event: EventEntity): Boolean {
        val eventDir = File(context.filesDir, "events/${event.uid}")
        
        // 如果目录已存在，跳过
        if (eventDir.exists()) {
            return false
        }
        
        // 创建事件目录
        eventDir.mkdirs()
        
        // 创建meta.json文件
        val riskData = mutableMapOf<String, Any>()
        riskData["level"] = event.riskLevel ?: ""
        riskData["score"] = event.riskScore ?: 0.0
        
        // 将risk_answers数据直接作为answers字段，不进行解析转换
            if (!event.riskAnswers.isNullOrEmpty()) {
                try {
                    // 直接将原始的risk_answers JSON字符串作为answers字段的值
                    val gson = com.google.gson.Gson()
                    val rawAnswersData = gson.fromJson(event.riskAnswers, Any::class.java)
                    riskData["answers"] = rawAnswersData
                } catch (e: Exception) {
                    Log.w("EventMigration", "Failed to parse risk answers JSON: ${e.message}")
                    riskData["answers"] = event.riskAnswers as Any
                }
            } else {
                riskData["answers"] = ""
            }
        
        // 将结构缺陷详情改为对象写入：解析为 JsonObject，失败时写入空对象
        val structuralJsonObj = try {
            val src = event.structuralDefectDetails
            if (!src.isNullOrBlank()) {
                com.google.gson.JsonParser.parseString(src).asJsonObject
            } else {
                com.google.gson.JsonObject()
            }
        } catch (e: Exception) {
            android.util.Log.w("EventMigration", "Failed to parse structuralDefectDetails to object: ${e.message}")
            com.google.gson.JsonObject()
        }

        val metaData = mapOf(
            "eventId" to event.eventId,
            "uid" to event.uid,
            "projectId" to event.projectId,
            "projectUid" to event.projectUid,
            "location" to (event.location ?: ""),
            "content" to (event.content ?: ""),
            "lastEditTime" to event.lastEditTime,
            "risk" to riskData,
            "photoFiles" to (event.photoFiles ?: emptyList()),
            "audioFiles" to (event.audioFiles ?: emptyList()),
            "defectIds" to (event.defectIds ?: emptyList()),
            "defectNos" to (event.defectNos ?: emptyList()),
            "isDraft" to event.isDraft,
            "structuralDefectDetails" to structuralJsonObj
        )
        
        val metaFile = File(eventDir, "meta.json")
        metaFile.writeText(gson.toJson(metaData))
        
        // 尝试复制媒体文件（如果原始文件存在）
        copyMediaFiles(eventDir, event)
        
        return true
    }
    
    /**
     * 复制媒体文件到事件目录
     * 
     * @param eventDir 事件目录
     * @param event 事件实体
     */
    private fun copyMediaFiles(eventDir: File, event: EventEntity) {
        // 复制图片文件
        event.photoFiles?.forEachIndexed { index, photoPath ->
            try {
                val sourceFile = File(photoPath)
                if (sourceFile.exists()) {
                    val targetFile = File(eventDir, "photo_$index.jpg")
                    sourceFile.copyTo(targetFile, overwrite = true)
                    Log.d("EventMigration", "Copied photo: ${sourceFile.name} -> ${targetFile.name}")
                } else {
                    Log.w("EventMigration", "Photo file not found: $photoPath")
                }
            } catch (e: Exception) {
                Log.w("EventMigration", "Failed to copy photo $photoPath: ${e.message}")
            }
        }
        
        // 复制音频文件
        event.audioFiles?.forEachIndexed { index, audioPath ->
            try {
                val sourceFile = File(audioPath)
                if (sourceFile.exists()) {
                    val targetFile = File(eventDir, "audio_$index.m4a")
                    sourceFile.copyTo(targetFile, overwrite = true)
                    Log.d("EventMigration", "Copied audio: ${sourceFile.name} -> ${targetFile.name}")
                } else {
                    Log.w("EventMigration", "Audio file not found: $audioPath")
                }
            } catch (e: Exception) {
                Log.w("EventMigration", "Failed to copy audio $audioPath: ${e.message}")
            }
        }
    }
    
    /**
     * 迁移结果数据类
     */
    data class MigrationResult(
        var successCount: Int = 0,
        var skippedCount: Int = 0,
        var failureCount: Int = 0,
        val errors: MutableList<String> = mutableListOf()
    ) {
        val totalProcessed: Int
            get() = successCount + skippedCount + failureCount
            
        val isSuccessful: Boolean
            get() = failureCount == 0
    }
}