/*
 * File: EventFormViewModel.kt
 * Description: ViewModel for Event form screen. Provides risk matrix loader and handles UI-related data.
 * Author: SIMS Team
 */
package com.example.sims_android.ui.event

import android.content.Context
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.simsapp.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import com.simsapp.data.repository.DefectRepository
import com.simsapp.data.local.entity.DefectEntity
import com.simsapp.data.repository.EventRepository
import com.simsapp.data.local.dao.ProjectDao
import com.simsapp.data.local.entity.EventEntity
import android.util.Log

/**
 * EventFormViewModel
 *
 * 事件表单的 ViewModel，负责处理事件的创建、编辑、保存和删除等业务逻辑。
 * 支持风险评估、媒体文件管理、缺陷关联等功能。
 * 
 * 主要功能：
 * - 事件草稿的自动保存和加载
 * - 风险评估数据的处理
 * - 媒体文件（图片、音频）的管理
 * - 缺陷关联和event_count字段的更新
 * - 事件的本地和远程同步
 * 
 * 技术特点：
 * - 使用 MVVM 架构模式
 * - 支持协程异步操作
 * - 集成 Hilt 依赖注入
 * - Fallback to local assets/risk.json for offline usage and development
 */
@HiltViewModel
class EventFormViewModel @Inject constructor(
    private val repo: ProjectRepository,
    @ApplicationContext private val appContext: Context,
    private val gson: Gson,
    private val eventRepo: EventRepository,
    val projectDao: ProjectDao,
    private val defectRepository: DefectRepository
) : ViewModel() {

    /**
     * 函数：saveEventToRoom
     * 说明：保存事件到本地Room数据库
     * 策略：
     * - 通过项目名称解析 projectId（若失败则返回错误）
     * - 构造 EventEntity（isDraft=true，更新 lastEditTime）并调用 Repository.upsert
     * - 返回新建或更新后的 eventId
     *
     * @param projectName 项目名称（用于解析 projectId）
     * @param location 事件位置
     * @param description 事件描述
     * @param riskResult 风险评估结果
     * @param photoFiles 图片文件列表
     * @param audioFiles 音频文件列表
     * @param selectedDefects 关联的缺陷列表
     * @param isEditMode 是否为编辑模式
     * @param currentEventId 当前事件ID（编辑模式下使用）
     * @return Result<Long> 成功时返回事件ID，失败时返回异常
     */
    suspend fun saveEventToRoom(
        projectName: String,
        location: String?,
        description: String,
        riskResult: RiskAssessmentResult?,
        photoFiles: List<File>,
        audioFiles: List<File>,
        selectedDefects: List<DefectEntity>,
        isEditMode: Boolean = false,
        currentEventId: Long? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val pid = repo.resolveProjectIdByName(projectName)
                ?: projectDao.getIdByExactName(projectName)
                ?: return@withContext Result.failure(IllegalStateException("Project not found: $projectName"))
            val now = System.currentTimeMillis()
            // 获取项目的projectUid
            val project = projectDao.getById(pid)
            val projectUid = project?.projectUid ?: ""
            
            // 序列化风险评估答案
            val riskAnswersJson = riskResult?.answers?.let { answers ->
                gson.toJson(answers)
            }
            
            // 转换文件路径为字符串列表
            val photoFilePaths = photoFiles.map { it.absolutePath }
            val audioFilePaths = audioFiles.map { it.absolutePath }
            
            // 提取缺陷ID和编号
            val defectIds = selectedDefects.map { it.defectId }
            val defectNos = selectedDefects.map { it.defectNo }
            
            // 获取之前关联的缺陷列表（用于比较变化）
            val previousDefectIds = if (isEditMode && currentEventId != null && currentEventId > 0) {
                try {
                    eventRepo.getEventById(currentEventId)?.defectIds ?: emptyList()
                } catch (e: Exception) {
                    Log.w("EventFormVM", "Failed to get previous defects for event $currentEventId: ${e.message}")
                    emptyList()
                }
            } else {
                emptyList()
            }
            
            val entity = if (isEditMode && currentEventId != null && currentEventId > 0) {
                // 编辑模式：更新现有事件
                EventEntity(
                    eventId = currentEventId,
                    projectId = pid,
                    projectUid = projectUid,
                    defectIds = defectIds,
                    defectNos = defectNos,
                    location = location,
                    content = description,
                    lastEditTime = now,
                    assets = emptyList(),
                    riskLevel = riskResult?.level,
                    riskScore = riskResult?.score?.toDouble(),
                    riskAnswers = riskAnswersJson,
                    photoFiles = photoFilePaths,
                    audioFiles = audioFilePaths,
                    isDraft = true
                )
            } else {
                // 新建模式：创建新事件
                EventEntity(
                    eventId = 0L, // 让数据库自动生成ID
                    projectId = pid,
                    projectUid = projectUid,
                    defectIds = defectIds,
                    defectNos = defectNos,
                    location = location,
                    content = description,
                    lastEditTime = now,
                    assets = emptyList(),
                    riskLevel = riskResult?.level,
                    riskScore = riskResult?.score?.toDouble(),
                    riskAnswers = riskAnswersJson,
                    photoFiles = photoFilePaths,
                    audioFiles = audioFilePaths,
                    isDraft = true
                )
            }
            
            val id = eventRepo.upsert(entity)
            
            // 更新关联缺陷的event_count字段
            updateDefectEventCounts(
                previousDefectIds = previousDefectIds,
                newDefectIds = defectIds
            )
            
            // 更新项目的计数字段
            projectUid?.let { uid ->
                repo.updateProjectCounts(uid)
            }
            
            // 验收日志：打印生成/更新后的草稿事件 ID 与关键字段摘要
            Log.d(
                "EventFormVM",
                "saveEventToRoom -> pid=$pid, eventId=$id, location=${location ?: ""}, descLen=${description.length}, " +
                "riskLevel=${riskResult?.level ?: "null"}, photoCount=${photoFiles.size}, audioCount=${audioFiles.size}, " +
                "isEditMode=$isEditMode, existingId=${currentEventId ?: -1L}, defectCount=${selectedDefects.size}"
            )
            Result.success(id)
        } catch (e: Exception) {
            Log.e("EventFormVM", "saveEventToRoom failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 函数：getDefectsByProjectUid
     * 说明：根据项目UID获取该项目下的所有缺陷列表
     * 
     * @param projectUid 项目UID
     * @return Flow<List<DefectEntity>> 缺陷列表流
     */
    fun getDefectsByProjectUid(projectUid: String): Flow<List<DefectEntity>> {
        return defectRepository.getDefectsByProjectUid(projectUid)
    }

    /**
     * 函数：loadEventFromRoom
     * 说明：从数据库加载事件数据
     * 
     * @param eventId 事件ID
     * @return Result<EventEntity?> 成功时返回事件实体，失败时返回异常
     */
    suspend fun loadEventFromRoom(eventId: Long): Result<EventEntity?> = withContext(Dispatchers.IO) {
        try {
            val event = eventRepo.getEventById(eventId)
            Log.d("EventFormVM", "loadEventFromRoom -> eventId=$eventId, found=${event != null}")
            Result.success(event)
        } catch (e: Exception) {
            Log.e("EventFormVM", "loadEventFromRoom failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 函数：getRiskMatrix
     * 说明：获取风险评估矩阵数据
     * 策略：
     * - 优先从远程API获取最新数据
     * - 失败时回退到本地assets/risk.json
     * 
     * @return Result<RiskMatrix> 成功时返回风险矩阵，失败时返回异常
     */
    suspend fun getRiskMatrix(): Result<RiskMatrix> = withContext(Dispatchers.IO) {
        try {
            // 回退到本地assets，因为远程API返回的是不同的数据结构
            val inputStream = appContext.assets.open("risk.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val riskMatrix = gson.fromJson(jsonString, RiskMatrix::class.java)
            Result.success(riskMatrix)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 函数：getDefectById
     * 说明：根据缺陷ID获取缺陷实体
     * 
     * @param defectId 缺陷ID
     * @return DefectEntity? 缺陷实体或null（如果不存在）
     */
    suspend fun getDefectById(defectId: Long): DefectEntity? = withContext(Dispatchers.IO) {
        try {
            defectRepository.getDefectById(defectId)
        } catch (e: Exception) {
            Log.e("EventFormVM", "getDefectById failed: ${e.message}", e)
            null
        }
    }

    /**
     * 函数：getDefectByProjectUidAndDefectNo
     * 说明：根据项目UID和缺陷编号获取缺陷实体
     * 
     * @param projectUid 项目UID
     * @param defectNo 缺陷编号
     * @return DefectEntity? 缺陷实体或null（如果不存在）
     */
    suspend fun getDefectByProjectUidAndDefectNo(projectUid: String, defectNo: String): DefectEntity? = withContext(Dispatchers.IO) {
        try {
            defectRepository.getDefectByProjectUidAndDefectNo(projectUid, defectNo)
        } catch (e: Exception) {
            Log.e("EventFormVM", "getDefectByProjectUidAndDefectNo failed: ${e.message}", e)
            null
        }
    }

    /**
     * 函数：saveEventToLocal
     * 说明：将事件数据保存到本地文件系统
     * 
     * @param projectName 项目名称
     * @param location 事件位置
     * @param description 事件描述
     * @param riskResult 风险评估结果
     * @param photoFiles 图片文件列表
     * @param audioFiles 音频文件列表
     * @return Result<String> 成功时返回事件UID，失败时返回异常
     */
    suspend fun saveEventToLocal(
        projectName: String,
        location: String?,
        description: String,
        riskResult: RiskResult?,
        photoFiles: List<File>,
        audioFiles: List<File>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val uid = UUID.randomUUID().toString()
            val eventDir = File(appContext.filesDir, "events/$uid")
            eventDir.mkdirs()

            // Copy photos
            val savedPhotos = mutableListOf<String>()
            photoFiles.forEachIndexed { index, file ->
                val targetFile = File(eventDir, "photo_$index.jpg")
                FileInputStream(file).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                savedPhotos.add("photo_$index.jpg")
            }

            // Copy audios
            val savedAudios = mutableListOf<String>()
            audioFiles.forEachIndexed { index, file ->
                val targetFile = File(eventDir, "audio_$index.mp3")
                FileInputStream(file).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                savedAudios.add("audio_$index.mp3")
            }

            // Save creation timestamp
            val createdAtPersisted = System.currentTimeMillis()

            // Build meta content
            val meta = EventMeta(
                uid = uid,
                projectName = projectName,
                location = location,
                description = description,
                risk = riskResult?.let { RiskMeta(priority = it.level, score = it.score.toDouble(), answers = it.answers ?: emptyList()) },
                photos = savedPhotos.toList(),
                audios = savedAudios.toList(),
                createdAt = createdAtPersisted
            )
            val metaFile = File(eventDir, "meta.json")
            metaFile.writeText(gson.toJson(meta))

            Result.success(uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 函数：deleteEvent
     * 说明：删除事件（包括数据库记录和本地文件）
     * 
     * @param eventId 事件ID
     * @param uid 事件UID
     * @return Result<Unit> 成功时返回Unit，失败时返回异常
     */
    suspend fun deleteEvent(eventId: Long, uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. 获取要删除的事件信息，用于更新关联缺陷的event_count
            val eventToDelete = eventRepo.getEventById(eventId)
            val defectIdsToUpdate = eventToDelete?.defectIds ?: emptyList()
            val projectUid = eventToDelete?.projectUid
            
            // 2. 删除数据库中的事件记录
            eventRepo.delete(eventId)
            
            // 3. 更新关联缺陷的event_count字段（减少计数）
            defectIdsToUpdate.forEach { defectId ->
                defectRepository.decrementEventCount(defectId)
                Log.d("EventFormViewModel", "Decremented event_count for defect $defectId after deleting event $eventId")
            }
            
            // 4. 更新项目的计数字段
            projectUid?.let { uid ->
                repo.updateProjectCounts(uid)
            }
            
            // 5. 删除本地文件夹
            val deleteLocalResult = deleteEventLocal(uid)
            if (deleteLocalResult.isFailure) {
                Log.w("EventFormViewModel", "Failed to delete local files for event $uid: ${deleteLocalResult.exceptionOrNull()}")
                // 即使本地文件删除失败，也不影响数据库删除的成功
            }
            
            Log.d("EventFormViewModel", "Successfully deleted event $eventId with uid $uid, updated ${defectIdsToUpdate.size} defects")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("EventFormViewModel", "Failed to delete event $eventId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 函数：deleteEventLocal
     * 说明：删除事件的本地文件
     * 
     * @param uid 事件UID
     * @return Result<Unit> 成功时返回Unit，失败时返回异常
     */
    private suspend fun deleteEventLocal(uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val eventDir = File(appContext.filesDir, "events/$uid")
            if (eventDir.exists()) {
                val deleted = eventDir.deleteRecursively()
                if (deleted) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to delete event directory: $uid"))
                }
            } else {
                // 目录不存在，认为删除成功
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 函数：updateDefectEventCounts
     * 说明：更新缺陷的event_count字段
     * 策略：
     * - 对于不再关联的缺陷，减少其event_count
     * - 对于新关联的缺陷，增加其event_count
     * 
     * @param previousDefectIds 之前关联的缺陷ID列表
     * @param newDefectIds 新关联的缺陷ID列表
     */
    private suspend fun updateDefectEventCounts(
        previousDefectIds: List<Long>,
        newDefectIds: List<Long>
    ) = withContext(Dispatchers.IO) {
        try {
            // 找出需要减少计数的缺陷（之前关联但现在不关联的）
            val defectsToDecrement = previousDefectIds.filter { it !in newDefectIds }
            
            // 找出需要增加计数的缺陷（现在关联但之前不关联的）
            val defectsToIncrement = newDefectIds.filter { it !in previousDefectIds }
            
            // 减少计数
            defectsToDecrement.forEach { defectId ->
                defectRepository.decrementEventCount(defectId)
                Log.d("EventFormVM", "Decremented event_count for defect $defectId")
            }
            
            // 增加计数
            defectsToIncrement.forEach { defectId ->
                defectRepository.incrementEventCount(defectId)
                Log.d("EventFormVM", "Incremented event_count for defect $defectId")
            }
            
            Log.d("EventFormVM", "Updated event counts: decremented ${defectsToDecrement.size}, incremented ${defectsToIncrement.size}")
        } catch (e: Exception) {
            Log.e("EventFormVM", "Failed to update defect event counts: ${e.message}", e)
        }
    }

    /**
     * 函数：createRiskMatrixLoader
     * 说明：创建风险矩阵加载器，供EventFormScreen使用
     * 
     * @return RiskMatrixLoader 风险矩阵加载器函数
     */
    fun createRiskMatrixLoader(): RiskMatrixLoader {
        return buildRiskMatrixLoaderFromRepository(
            repo = repo,
            endpoint = "risk-matrix",
            fileIds = listOf("default")
        )
    }

    /**
     * 函数：uploadEvent
     * 说明：上传事件到云端
     * 
     * @param eventUid 事件UID
     * @return Pair<Boolean, String> 成功标志和消息
     */
    suspend fun uploadEvent(eventUid: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val (success, message) = eventRepo.uploadEventZip(appContext, eventUid)
            Log.d("EventFormVM", "uploadEvent -> eventUid=$eventUid, success=$success, message=$message")
            success to message
        } catch (e: Exception) {
            Log.e("EventFormVM", "uploadEvent failed: ${e.message}", e)
            false to "Upload failed: ${e.message}"
        }
    }
}

/**
 * 数据类：风险评估结果
 * 
 * @property level 风险等级
 * @property score 风险分数
 * @property answers 评估答案列表
 */
data class RiskResult(
    val level: String,
    val score: Int,
    val answers: List<String>? = null
)

/**
 * 数据类：风险评估矩阵
 * 
 * @property questions 评估问题列表
 * @property matrix 风险矩阵配置
 */
data class RiskMatrix(
    val questions: List<RiskQuestion>,
    val matrix: List<RiskLevel>
)

/**
 * 数据类：风险评估问题
 * 
 * @property id 问题ID
 * @property text 问题文本
 * @property options 选项列表
 */
data class RiskQuestion(
    val id: String,
    val text: String,
    val options: List<RiskOption>
)

/**
 * 数据类：风险评估选项
 * 
 * @property text 选项文本
 * @property score 选项分数
 */
data class RiskOption(
    val text: String,
    val score: Int
)

/**
 * 数据类：风险等级
 * 
 * @property level 等级名称
 * @property minScore 最小分数
 * @property maxScore 最大分数
 * @property color 等级颜色
 */
data class RiskLevel(
    val level: String,
    val minScore: Int,
    val maxScore: Int,
    val color: String
)

/**
 * 数据类：事件元数据
 * 
 * @property uid 事件UID
 * @property projectName 项目名称
 * @property location 事件位置
 * @property description 事件描述
 * @property risk 风险评估元数据
 * @property photos 图片文件列表
 * @property audios 音频文件列表
 * @property createdAt 创建时间戳
 */
data class EventMeta(
    val uid: String,
    val projectName: String,
    val location: String?,
    val description: String,
    val risk: RiskMeta?,
    val photos: List<String>,
    val audios: List<String>,
    val createdAt: Long
)

/**
 * 数据类：风险评估元数据
 * 
 * @property priority 风险优先级
 * @property score 风险分数
 * @property answers 评估答案列表
 */
data class RiskMeta(
    val priority: String,
    val score: Double,
    val answers: List<String>
)