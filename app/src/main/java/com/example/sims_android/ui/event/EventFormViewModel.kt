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
import com.simsapp.data.repository.RiskMatrixRepository
import com.simsapp.data.repository.EventRepository
import com.simsapp.data.local.dao.ProjectDao
import com.simsapp.data.local.entity.DigitalAssetItem
import com.simsapp.data.local.entity.EventEntity
import com.example.sims_android.ui.event.buildRiskMatrixLoaderFromRepository
import com.example.sims_android.ui.event.buildRiskMatrixLoaderFromLocalCache
import android.util.Log
import com.simsapp.data.repository.EventUploadItem

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
    private val defectRepository: DefectRepository,
    private val riskMatrixRepository: RiskMatrixRepository,
    val projectDigitalAssetDao: com.simsapp.data.local.dao.ProjectDigitalAssetDao
) : ViewModel() {

    /**
     * 根据file_id列表获取对应的文件名列表
     * 
     * @param fileIds file_id列表
     * @return 文件名列表，如果找不到对应的文件名则使用file_id作为fallback
     */
    suspend fun getFileNamesByIds(fileIds: List<String>): List<String> = withContext(Dispatchers.IO) {
        try {
            val fileNames = mutableListOf<String>()
            for (fileId in fileIds) {
                val asset = projectDigitalAssetDao.getByFileId(fileId)
                val fileName = asset?.name ?: fileId // 如果找不到文件名，使用file_id作为fallback
                fileNames.add(fileName)
            }
            fileNames
        } catch (e: Exception) {
            Log.e("EventFormViewModel", "Error getting file names for file_ids: $fileIds", e)
            // 如果出错，返回file_id列表作为fallback
            fileIds
        }
    }

    /**
     * 函数：getEventUidById
     * 说明：根据 Room 主键 ID 查询事件的唯一 UID。
     * 用途：
     * - 首次自动保存后，通过返回的 eventId 反查 UID，持久化到界面状态，
     *   以便后续通过 UID 反查主键并执行更新而非插入，避免重复新建事件。
     *
     * @param eventId 事件的 Room 主键ID
     * @return String? 事件UID；查询失败或不存在返回null
     */
    suspend fun getEventUidById(eventId: Long): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            eventRepo.getEventById(eventId)?.uid
        } catch (e: Exception) {
            Log.w("EventFormVM", "getEventUidById query failed for id=$eventId: ${e.message}")
            null
        }
    }

    /**
     * 根据单个file_id获取对应的文件名
     * 
     * @param fileId file_id
     * @return 文件名，如果找不到则返回null
     */
    suspend fun getFileNameById(fileId: String): String? = withContext(Dispatchers.IO) {
        try {
            val asset = projectDigitalAssetDao.getByFileId(fileId)
            asset?.name
        } catch (e: Exception) {
            Log.e("EventFormViewModel", "Error getting file name for file_id: $fileId", e)
            null
        }
    }

    /**
     * 根据file_id列表获取数字资产的详细信息（包含type字段）
     * 
     * @param fileIds file_id列表
     * @return 数字资产详细信息列表
     */
    suspend fun getDigitalAssetDetailsByIds(fileIds: List<String>): List<DigitalAssetDetail> = withContext(Dispatchers.IO) {
        try {
            val assetDetails = mutableListOf<DigitalAssetDetail>()
            for (fileId in fileIds) {
                val asset = projectDigitalAssetDao.getByFileId(fileId)
                if (asset != null) {
                    assetDetails.add(
                        DigitalAssetDetail(
                            fileId = asset.fileId ?: fileId,
                            fileName = asset.name ?: fileId,
                            type = asset.type ?: "file",
                            localPath = asset.localPath
                        )
                    )
                } else {
                    // 如果找不到资产信息，创建一个默认的详情对象
                    assetDetails.add(
                        DigitalAssetDetail(
                            fileId = fileId,
                            fileName = fileId,
                            type = "file",
                            localPath = null
                        )
                    )
                }
            }
            assetDetails
        } catch (e: Exception) {
            Log.e("EventFormViewModel", "Error getting digital asset details for file_ids: $fileIds", e)
            // 如果出错，返回基本信息作为fallback
            fileIds.map { fileId ->
                DigitalAssetDetail(
                    fileId = fileId,
                    fileName = fileId,
                    type = "file",
                    localPath = null
                )
            }
        }
    }

    /**
     * 根据节点ID列表映射为文件ID列表
     *
     * 用途：页面回显与上传前将 `nodeId` 映射为 `fileId`，优先使用数据库中已解析的关系。
     *
     * @param nodeIds 节点ID列表
     * @return 文件ID列表（过滤掉未能映射的项）；发生异常时返回空列表
     */
    suspend fun getFileIdsByNodeIds(nodeIds: List<String>): List<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val fileIds = mutableListOf<String>()
            for (nodeId in nodeIds) {
                val asset = projectDigitalAssetDao.getByNodeId(nodeId)
                val fileId = asset?.fileId
                if (!fileId.isNullOrBlank()) {
                    fileIds.add(fileId)
                }
            }
            fileIds
        } catch (e: Exception) {
            Log.e("EventFormViewModel", "Error mapping nodeIds to fileIds: $nodeIds", e)
            emptyList()
        }
    }

    /**
     * 根据节点ID列表获取数字资产详情（包含类型与本地路径）
     *
     * 用途：当无法映射 `nodeId -> fileId` 时，回退以 `nodeId` 构造基础详情用于界面展示。
     *
     * @param nodeIds 节点ID列表
     * @return 数字资产详细信息列表；发生异常时返回以 nodeId 构造的基础详情
     */
    suspend fun getDigitalAssetDetailsByNodeIds(nodeIds: List<String>): List<DigitalAssetDetail> = withContext(Dispatchers.IO) {
        return@withContext try {
            val details = mutableListOf<DigitalAssetDetail>()
            for (nodeId in nodeIds) {
                val asset = projectDigitalAssetDao.getByNodeId(nodeId)
                if (asset != null) {
                    details.add(
                        DigitalAssetDetail(
                            fileId = asset.fileId ?: nodeId,
                            fileName = asset.name ?: nodeId,
                            type = asset.type ?: "file",
                            localPath = asset.localPath
                        )
                    )
                } else {
                    details.add(
                        DigitalAssetDetail(
                            fileId = nodeId,
                            fileName = nodeId,
                            type = "file",
                            localPath = null
                        )
                    )
                }
            }
            details
        } catch (e: Exception) {
            Log.e("EventFormViewModel", "Error getting digital asset details by nodeIds: $nodeIds", e)
            nodeIds.map { nodeId ->
                DigitalAssetDetail(
                    fileId = nodeId,
                    fileName = nodeId,
                    type = "file",
                    localPath = null
                )
            }
        }
    }

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
    /**
     * 函数：saveEventToRoom（更新：新增 forceClearAssets 参数）
     * 说明：保存事件到本地Room数据库；当发生数字资产删除并且删除后为空时，需要明确清空数据库中的资产列表。
     * 新增参数：
     * - forceClearAssets：当为 true 时，无论现有资产如何，强制将 assets 置为空列表，实现“彻底删除”。
     */
    suspend fun saveEventToRoom(
        projectName: String,
        location: String?,
        description: String,
        riskResult: RiskAssessmentResult?,
        photoFiles: List<File>,
        audioFiles: List<File>,
        selectedDefects: List<DefectEntity>,
        digitalAssetFileIds: List<String> = emptyList(),
        isEditMode: Boolean = false,
        currentEventId: Long? = null,
        structuralDefectDetails: String? = null,
        forceClearAssets: Boolean = false
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val pid = repo.resolveProjectIdByName(projectName)
                ?: projectDao.getIdByExactName(projectName)
                ?: return@withContext Result.failure(IllegalStateException("Project not found: $projectName"))
            val now = System.currentTimeMillis()
            // 获取项目的projectUid
            val project = projectDao.getById(pid)
            val projectUid = project?.projectUid ?: ""
            
            // 序列化风险评估数据（新的对象格式）
            val riskAnswersJson = riskResult?.assessmentData?.let { assessmentData ->
                gson.toJson(assessmentData)
            } ?: riskResult?.answers?.let { answers ->
                // 兼容旧格式：如果没有新格式数据，则使用旧格式
                gson.toJson(answers)
            }
            
            // 转换文件路径为字符串列表
            val photoFilePaths = photoFiles.map { it.absolutePath }
            val audioFilePaths = audioFiles.map { it.absolutePath }
            
            // 构建数字资产对象列表
            // 修复：即使查询不到文件名也不要跳过资产，使用 fileId 作为回退名称，确保回显
            Log.d("EventFormVM", "Processing digital assets: ${digitalAssetFileIds.size} file IDs provided")
            // 新增：在更新模式且未传入新资产ID时，保留数据库中的既有资产，避免误清空
            val existingAssets: List<DigitalAssetItem> = if (currentEventId != null && currentEventId > 0) {
                try {
                    eventRepo.getEventById(currentEventId)?.assets ?: emptyList()
                } catch (e: Exception) {
                    Log.w("EventFormVM", "Failed to fetch existing assets for event $currentEventId: ${e.message}")
                    emptyList()
                }
            } else {
                emptyList()
            }

            val digitalAssets = when {
                // 关键修复：当明确要求清空资产时，忽略数据库中已有资产，直接置为空
                forceClearAssets -> {
                    Log.d("EventFormVM", "forceClearAssets=true; clearing digital assets list")
                    emptyList()
                }
                digitalAssetFileIds.isEmpty() -> {
                    if (existingAssets.isNotEmpty()) {
                        Log.d("EventFormVM", "No new asset IDs provided; preserving ${existingAssets.size} existing assets from DB")
                        existingAssets
                    } else {
                        Log.d("EventFormVM", "No new asset IDs and no existing assets; saving empty asset list")
                        emptyList()
                    }
                }
                else -> {
                    digitalAssetFileIds.map { fileId ->
                        val fileName = getFileNameById(fileId) ?: fileId
                        // 查询并缓存节点ID，确保详情页与选择器按 nodeId 精确回显
                        // 若查询失败，nodeId 置为 null 以兼容旧数据
                        val nodeId: String? = try {
                            projectDigitalAssetDao.getByFileId(fileId)?.nodeId
                        } catch (_: Exception) { null }
                        Log.d("EventFormVM", "Digital asset resolved: fileId=$fileId, fileName=$fileName")
                        DigitalAssetItem(fileId = fileId, fileName = fileName, nodeId = nodeId)
                    }
                }
            }
            Log.d("EventFormVM", "Digital assets after processing: ${digitalAssets.size} items ready for save")
            
            // 提取缺陷ID和编号
            val defectIds = selectedDefects.map { it.defectId }
            val defectNos = selectedDefects.map { it.defectNo }
            // 提取缺陷远程UID
            val defectUids = selectedDefects.map { it.uid }.filter { it.isNotBlank() }
            
            // 获取之前关联的缺陷列表（用于比较变化）
            // 修复：只要传入了有效的 currentEventId，就按更新模式处理，无需依赖 isEditMode 标记
            val previousDefectIds = if (currentEventId != null && currentEventId > 0) {
                try {
                    eventRepo.getEventById(currentEventId)?.defectIds ?: emptyList()
                } catch (e: Exception) {
                    Log.w("EventFormVM", "Failed to get previous defects for event $currentEventId: ${e.message}")
                    emptyList()
                }
            } else {
                emptyList()
            }

            // 函数级注释（新增）：
            // - 关键修复：编辑模式下保留已同步状态 isSynced，避免页面退出自动保存将已同步事件误置为未同步。
            // - 策略：读取数据库中 existingEvent?.isSynced 作为有效值；新建模式始终为 false。
            val entity = if (currentEventId != null && currentEventId > 0) {
                // 编辑模式：更新现有事件，保持原有的uid
                val existingEvent = eventRepo.getEventById(currentEventId)
                // 新增：风险字段保留逻辑——若UI未提供riskResult，则沿用数据库中已有的riskLevel/riskScore/riskAnswers
                val effectiveRiskLevel = riskResult?.level ?: existingEvent?.riskLevel
                val effectiveRiskScore = riskResult?.score?.toDouble() ?: existingEvent?.riskScore
                val effectiveRiskAnswers = riskAnswersJson ?: existingEvent?.riskAnswers
                // 新增：保留同步标记，防止自动保存导致 is_synced 回退为 0
                val effectiveIsSynced = existingEvent?.isSynced ?: false

                EventEntity(
                    eventId = currentEventId,
                    uid = existingEvent?.uid ?: java.util.UUID.randomUUID().toString(),
                    projectId = pid,
                    projectUid = projectUid,
                    defectIds = defectIds,
                    defectUids = defectUids,
                    defectNos = defectNos,
                    location = location,
                    content = description,
                    lastEditTime = now,
                    assets = digitalAssets,
                    riskLevel = effectiveRiskLevel,
                    riskScore = effectiveRiskScore,
                    riskAnswers = effectiveRiskAnswers,
                    photoFiles = photoFilePaths,
                    audioFiles = audioFilePaths,
                    isDraft = true,
                    isSynced = effectiveIsSynced,
                    structuralDefectDetails = structuralDefectDetails
                )
            } else {
                // 新建模式：创建新事件，生成新的UUID
                EventEntity(
                    eventId = 0L, // 让数据库自动生成ID
                    uid = java.util.UUID.randomUUID().toString(),
                    projectId = pid,
                    projectUid = projectUid,
                    defectIds = defectIds,
                    defectUids = defectUids,
                    defectNos = defectNos,
                    location = location,
                    content = description,
                    lastEditTime = now,
                    assets = digitalAssets,
                    riskLevel = riskResult?.level,
                    riskScore = riskResult?.score?.toDouble(),
                    riskAnswers = riskAnswersJson,
                    photoFiles = photoFilePaths,
                    audioFiles = audioFilePaths,
                    isDraft = true,
                    isSynced = false,
                    structuralDefectDetails = structuralDefectDetails
                )
            }
            
            val id = eventRepo.upsert(entity)
            
            // 确保事件目录存在（新增逻辑）
            val eventUid = entity.uid
            val eventDir = File(appContext.filesDir, "events/$eventUid")
            if (!eventDir.exists()) {
                Log.d("EventFormVM", "Creating event directory: ${eventDir.absolutePath}")
                eventDir.mkdirs()
                
                // 创建meta.json文件
                val riskData = mutableMapOf<String, Any>()
                riskData["level"] = riskResult?.level ?: ""
                riskData["score"] = riskResult?.score ?: 0.0
                
                // 将risk_answers数据直接作为answers字段，不进行解析转换
                if (!riskAnswersJson.isNullOrEmpty()) {
                    try {
                        // 直接将原始的risk_answers JSON字符串作为answers字段的值
                        val gson = com.google.gson.Gson()
                        val rawAnswersData = gson.fromJson(riskAnswersJson, Any::class.java)
                        riskData["answers"] = rawAnswersData
                    } catch (e: Exception) {
                        Log.w("EventFormVM", "Failed to parse risk answers JSON: ${e.message}")
                        riskData["answers"] = riskAnswersJson as Any
                    }
                } else {
                    riskData["answers"] = ""
                }
                
                // 将结构缺陷详情写入为对象：解析为 JsonObject，失败时写入空对象
                val structuralJsonObj = try {
                    if (!structuralDefectDetails.isNullOrBlank()) {
                        com.google.gson.JsonParser.parseString(structuralDefectDetails).asJsonObject
                    } else {
                        com.google.gson.JsonObject()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("EventFormVM", "Failed to parse structuralDefectDetails to object: ${e.message}")
                    com.google.gson.JsonObject()
                }

                // 为媒体文件生成带时间戳的规范文件名，保持索引 + 时间戳结构
                val photoNames = mutableListOf<String>()
                photoFiles.forEachIndexed { index, file ->
                    val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                        .format(java.util.Date(file.lastModified().takeIf { it > 0 } ?: now))
                    val ext = "jpg" // 保持照片扩展名统一为 .jpg
                    photoNames.add("photo_${index}_${ts}.${ext}")
                }

                val audioNames = mutableListOf<String>()
                audioFiles.forEachIndexed { index, file ->
                    val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                        .format(java.util.Date(file.lastModified().takeIf { it > 0 } ?: now))
                    val ext = "m4a" // 根据既有共识，音频统一写入 .m4a 扩展名
                    audioNames.add("audio_${index}_${ts}.${ext}")
                }

                val metaData = mapOf(
                    "eventId" to id,
                    "uid" to eventUid,
                    "projectId" to pid,
                    "projectUid" to projectUid,
                    "location" to (location ?: ""),
                    "content" to description,
                    "lastEditTime" to now,
                    "risk" to riskData,
                    "photoFiles" to photoNames,
                    "audioFiles" to audioNames,
                    // 新增：写入数字资产对象数组（来自本地事件表的 assets 字段）
                    // 结构：[{"fileId": "...", "fileName": "..."}, ...]
                    "assets" to digitalAssets,
                    // 兼容旧版读取：同时写入 digitalAssets（仅 fileId 列表）
                    "digitalAssets" to digitalAssets.map { it.fileId },
                    "defectIds" to defectIds,
                    "defectUids" to defectUids,
                    "defectNos" to defectNos,
                    "isDraft" to true,
                    "structuralDefectDetails" to structuralJsonObj
                )
                
                val metaFile = File(eventDir, "meta.json")
                try {
                    metaFile.writeText(gson.toJson(metaData))
                    Log.d("EventFormVM", "Created meta.json for event: $eventUid")
                } catch (e: Exception) {
                    Log.w("EventFormVM", "Failed to create meta.json for event $eventUid: ${e.message}")
                }
                
                // 复制图片文件到事件目录（使用带时间戳的文件名）
                photoFiles.forEachIndexed { index, file ->
                    try {
                        val targetFile = File(eventDir, photoNames[index])
                        file.copyTo(targetFile, overwrite = true)
                        Log.d("EventFormVM", "Copied photo file: ${file.name} -> ${targetFile.name}")
                    } catch (e: Exception) {
                        Log.w("EventFormVM", "Failed to copy photo file ${file.name}: ${e.message}")
                    }
                }
                
                // 复制音频文件到事件目录（使用带时间戳的文件名）
                audioFiles.forEachIndexed { index, file ->
                    try {
                        val targetFile = File(eventDir, audioNames[index])
                        file.copyTo(targetFile, overwrite = true)
                        Log.d("EventFormVM", "Copied audio file: ${file.name} -> ${targetFile.name}")
                    } catch (e: Exception) {
                        Log.w("EventFormVM", "Failed to copy audio file ${file.name}: ${e.message}")
                    }
                }
                
                Log.d("EventFormVM", "Event directory created successfully: ${eventDir.absolutePath}")
            } else {
                Log.d("EventFormVM", "Event directory already exists: ${eventDir.absolutePath}")
            }
            
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
                "existingId=${currentEventId ?: -1L}, defectCount=${selectedDefects.size}, " +
                "digitalAssetsCount=${digitalAssets.size}, digitalAssetsSaved=${entity.assets.size}"
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
     * 函数：获取非完成状态项目列表 Flow
     * 说明：供UI层底部弹窗收集，展示可选择的目标项目。
     * 返回：Flow<List<ProjectEntity>>
     */
    fun getNotFinishedProjects(): Flow<List<com.simsapp.data.local.entity.ProjectEntity>> =
        repo.getNotFinishedProjects()

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
     * 函数：getDefectByUid
     * 说明：根据缺陷远端UID获取缺陷实体
     *
     * @param uid 缺陷远端UID
     * @return DefectEntity? 缺陷实体或null（如果不存在）
     */
    suspend fun getDefectByUid(uid: String): DefectEntity? = withContext(Dispatchers.IO) {
        try {
            defectRepository.getDefectByUid(uid)
        } catch (e: Exception) {
            Log.e("EventFormVM", "getDefectByUid failed: ${e.message}", e)
            null
        }
    }

    /**
     * 函数：getDefectByProjectUidAndUid
     * 说明：根据项目UID和缺陷远端UID获取缺陷实体
     *
     * @param projectUid 项目UID
     * @param uid 缺陷远端UID
     * @return DefectEntity? 缺陷实体或null（如果不存在）
     */
    suspend fun getDefectByProjectUidAndUid(projectUid: String, uid: String): DefectEntity? = withContext(Dispatchers.IO) {
        try {
            defectRepository.getDefectByProjectUidAndUid(projectUid, uid)
        } catch (e: Exception) {
            Log.e("EventFormVM", "getDefectByProjectUidAndUid failed: ${e.message}", e)
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
     * @param digitalAssetFileIds 数字资产文件ID列表
     * @return Result<String> 成功时返回事件UID，失败时返回异常
     */
    suspend fun saveEventToLocal(
        projectName: String,
        location: String?,
        description: String,
        riskResult: RiskAssessmentResult?,
        photoFiles: List<File>,
        audioFiles: List<File>,
        digitalAssetFileIds: List<String> = emptyList(),
        structuralDefectDetails: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val uid = UUID.randomUUID().toString()
            val eventDir = File(appContext.filesDir, "events/$uid")
            eventDir.mkdirs()

            // 使用索引 + 时间戳命名并复制媒体到事件目录
            val savedPhotos = mutableListOf<String>()
            photoFiles.forEachIndexed { index, file ->
                val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                    .format(java.util.Date(file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()))
                val name = "photo_${index}_${ts}.jpg" // 保持照片统一为 .jpg
                val targetFile = File(eventDir, name)
                FileInputStream(file).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                savedPhotos.add(name)
            }

            val savedAudios = mutableListOf<String>()
            audioFiles.forEachIndexed { index, file ->
                val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                    .format(java.util.Date(file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()))
                val name = "audio_${index}_${ts}.m4a" // 根据既有共识，音频统一写入 .m4a
                val targetFile = File(eventDir, name)
                FileInputStream(file).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                savedAudios.add(name)
            }

            // Save creation timestamp
            val createdAtPersisted = System.currentTimeMillis()

            // Build meta content with original risk_answers data
            val riskData = mutableMapOf<String, Any>()
            riskData["level"] = riskResult?.level ?: ""
            riskData["score"] = riskResult?.score ?: 0.0
            
            // 获取原始的risk_answers数据
            val savedEvent = eventRepo.getEventByUid(uid)
            val originalRiskAnswers = savedEvent?.riskAnswers
            
            if (!originalRiskAnswers.isNullOrEmpty()) {
                try {
                    // 直接将原始的risk_answers JSON字符串作为answers字段的值
                    val rawAnswersData = gson.fromJson(originalRiskAnswers, Any::class.java)
                    riskData["answers"] = rawAnswersData
                } catch (e: Exception) {
                    Log.w("EventFormVM", "Failed to parse risk answers JSON: ${e.message}")
                    riskData["answers"] = originalRiskAnswers as Any
                }
            } else {
                riskData["answers"] = ""
            }
            
            val metaData = mapOf(
                 "eventId" to (savedEvent?.eventId ?: 0),
                 "uid" to uid,
                 "projectId" to (savedEvent?.projectId ?: 0),
                 "projectUid" to (savedEvent?.projectUid ?: ""),
                 "location" to location,
                 "content" to description,
                 "lastEditTime" to createdAtPersisted,
                 "risk" to riskData,
                 "photoFiles" to savedPhotos.toList(),
                 "audioFiles" to savedAudios.toList(),
                // 新增：写入数字资产对象数组，并尝试补充 nodeId（用于后续根据节点ID精确回显与选中）
                "assets" to (try {
                    digitalAssetFileIds.mapNotNull { fileId ->
                        val fileName = getFileNameById(fileId)
                        val nodeId: String? = try { projectDigitalAssetDao.getByFileId(fileId)?.nodeId } catch (_: Exception) { null }
                        if (fileName != null) com.simsapp.data.local.entity.DigitalAssetItem(fileId = fileId, fileName = fileName, nodeId = nodeId) else null
                    }
                } catch (_: Exception) { emptyList<com.simsapp.data.local.entity.DigitalAssetItem>() }),
                 // 兼容旧版读取：同时写入 digitalAssets（仅 fileId 列表）
                 "digitalAssets" to digitalAssetFileIds,
                 "defectIds" to (savedEvent?.defectIds ?: emptyList<Long>()),
                 "defectUids" to (savedEvent?.defectUids ?: emptyList<String>()),
                 "defectNos" to (savedEvent?.defectNos ?: emptyList<String>()),
                 "isDraft" to (savedEvent?.isDraft ?: false),
                 "structuralDefectDetails" to structuralDefectDetails
             )
            
            val metaFile = File(eventDir, "meta.json")
            metaFile.writeText(gson.toJson(metaData))

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
            for (defectId in defectIdsToUpdate) {
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
            for (defectId in defectsToDecrement) {
                defectRepository.decrementEventCount(defectId)
                Log.d("EventFormVM", "Decremented event_count for defect $defectId")
            }
            
            // 增加计数
            for (defectId in defectsToIncrement) {
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
     * 优先从本地数字资产缓存加载，提升性能和离线可用性
     * 
     * @param projectUid 项目UID，直接用于定位对应的缓存文件
     * @return RiskMatrixLoader 风险矩阵加载器函数
     */
    fun createRiskMatrixLoader(projectUid: String): RiskMatrixLoader {
        return suspend {
            try {
                if (projectUid.isBlank()) {
                    Log.w("EventFormVM", "Project UID is blank, falling back to remote")
                    // 如果项目UID为空，回退到远程加载
                    buildRiskMatrixLoaderFromRepository(
                        repo = repo,
                        endpoint = "risk-matrix",
                        fileIds = listOf("default")
                    ).invoke()
                } else {
                    // 优先使用本地缓存加载器
                    buildRiskMatrixLoaderFromLocalCache(
                        projectDigitalAssetDao = projectDigitalAssetDao,
                        projectUid = projectUid
                    ).invoke()
                }
            } catch (e: Exception) {
                Log.e("EventFormVM", "Failed to create risk matrix loader for project UID $projectUid: ${e.message}", e)
                // 出现异常时回退到远程加载
                buildRiskMatrixLoaderFromRepository(
                    repo = repo,
                    endpoint = "risk-matrix",
                    fileIds = listOf("default")
                ).invoke()
            }
        }
    }

    /**
     * 函数：createRiskMatrixLoaderByName (兼容性方法)
     * 说明：通过项目名称创建风险矩阵加载器的兼容性方法
     * 优先从本地数字资产缓存加载，提升性能和离线可用性
     * 
     * @param projectName 项目名称，用于获取项目UID并定位对应的缓存文件
     * @return RiskMatrixLoader 风险矩阵加载器函数
     * @deprecated 请使用直接传递projectUid的版本以提升性能
     */
    @Deprecated("Use createRiskMatrixLoader(projectUid: String) instead")
    fun createRiskMatrixLoaderByName(projectName: String): RiskMatrixLoader {
        return suspend {
            try {
                // 通过项目名称获取项目UID
                val project = projectDao.getByExactName(projectName)
                val projectUid = project?.projectUid
                
                if (projectUid.isNullOrBlank()) {
                    Log.w("EventFormVM", "Project UID not found for project: $projectName, falling back to remote")
                    // 如果无法获取项目UID，回退到远程加载
                    buildRiskMatrixLoaderFromRepository(
                        repo = repo,
                        endpoint = "risk-matrix",
                        fileIds = listOf("default")
                    ).invoke()
                } else {
                    // 优先使用本地缓存加载器
                    buildRiskMatrixLoaderFromLocalCache(
                        projectDigitalAssetDao = projectDigitalAssetDao,
                        projectUid = projectUid
                    ).invoke()
                }
            } catch (e: Exception) {
                Log.e("EventFormVM", "Failed to create risk matrix loader for project $projectName: ${e.message}", e)
                // 出现异常时回退到远程加载
                buildRiskMatrixLoaderFromRepository(
                    repo = repo,
                    endpoint = "risk-matrix",
                    fileIds = listOf("default")
                ).invoke()
            }
        }
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
            // 使用新的createEventZip方法创建压缩包并获取哈希值
            val zipResult = eventRepo.createEventZip(appContext, eventUid)
            Log.d("EventFormVM", "uploadEvent -> eventUid=$eventUid, success=${zipResult.first}, hash=${zipResult.second}")
            zipResult.first to if (zipResult.first) "Zip created with hash: ${zipResult.second}" else zipResult.second
        } catch (e: Exception) {
            Log.e("EventFormVM", "uploadEvent failed: ${e.message}", e)
            false to "Upload failed: ${e.message}"
        }
    }

    /**
     * 函数：uploadEventWithSync
     * 说明：增强的事件同步上传功能
     * 1. 如果事件未保存到本地缓存，则先自动保存
     * 2. 创建事件压缩包并获取 SHA-256 哈希值
     * 3. 调用 create_event_upload 接口获取票据信息
     * 4. 根据返回的票据信息上传对应的压缩包
     * 5. 轮询 notice_event_upload_success 获取同步状态
     * 
     * @param eventUid 事件UID
     * @param projectName 项目名称（用于自动保存）
     * @param location 事件位置
     * @param description 事件描述
     * @param riskResult 风险评估结果
     * @param photoFiles 图片文件列表
     * @param audioFiles 音频文件列表
     * @param selectedDefects 关联的缺陷列表
     * @param digitalAssetFileIds 数字资产文件ID列表
     * @return Pair<Boolean, String> 成功标志和消息
     */
    suspend fun uploadEventWithSync(
        eventUid: String,
        projectName: String,
        location: String?,
        description: String,
        riskResult: RiskAssessmentResult?,
        photoFiles: List<File>,
        audioFiles: List<File>,
        selectedDefects: List<DefectEntity>,
        digitalAssetFileIds: List<String> = emptyList(),
        structuralDefectDetails: String? = null,
        /** 可选：用户在上传前选择的目标项目UID，若为空则按projectName解析 */
        overrideTargetProjectUid: String? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // Step 1: 确保事件已保存到本地数据库和文件系统
            val event = eventRepo.getEventByUid(eventUid)
            if (event == null) {
                Log.d("EventFormVM", "Event not found in local cache, auto-saving first...")
                
                // 1.1 保存到数据库
                val saveResult = saveEventToRoom(
                    projectName = projectName,
                    location = location,
                    description = description,
                    riskResult = riskResult,
                    photoFiles = photoFiles,
                    audioFiles = audioFiles,
                    selectedDefects = selectedDefects,
                    digitalAssetFileIds = digitalAssetFileIds,
                    isEditMode = false,
                    currentEventId = null,
                    structuralDefectDetails = structuralDefectDetails
                )
                
                if (saveResult.isFailure) {
                    return@withContext false to "Auto-save to database failed: ${saveResult.exceptionOrNull()?.message}"
                }
                
                Log.d("EventFormVM", "Auto-save to database completed successfully")
            }
            
            // Step 1.2: 确保事件目录和文件存在（无论事件是否已在数据库中）
            val eventDir = File(appContext.filesDir, "events/$eventUid")
            if (!eventDir.exists()) {
                Log.d("EventFormVM", "Creating event directory: ${eventDir.absolutePath}")
                
                // 使用现有的 saveEventToLocal 方法来创建完整的事件目录结构
                val localSaveResult = saveEventToLocal(
                    projectName = projectName,
                    location = location,
                    description = description,
                    riskResult = riskResult,
                    photoFiles = photoFiles,
                    audioFiles = audioFiles,
                    digitalAssetFileIds = digitalAssetFileIds,
                    // 将事件的结构缺陷详情一并写入 meta.json（包含向导生成的summary）
                    structuralDefectDetails = structuralDefectDetails ?: event?.structuralDefectDetails
                )
                
                if (localSaveResult.isFailure) {
                    return@withContext false to "Auto-save to local files failed: ${localSaveResult.exceptionOrNull()?.message}"
                }
                
                // saveEventToLocal 会生成新的 UID，我们需要将文件移动到正确的 eventUid 目录
                val generatedUid = localSaveResult.getOrNull()
                if (generatedUid != null && generatedUid != eventUid) {
                    val generatedDir = File(appContext.filesDir, "events/$generatedUid")
                    if (generatedDir.exists()) {
                        // 将生成的目录重命名为正确的 eventUid
                        val success = generatedDir.renameTo(eventDir)
                        if (success) {
                            Log.d("EventFormVM", "Renamed event directory from $generatedUid to $eventUid")
                        } else {
                            Log.w("EventFormVM", "Failed to rename event directory, copying files instead...")
                            // 如果重命名失败，则复制文件
                            eventDir.mkdirs()
                            generatedDir.copyRecursively(eventDir, overwrite = true)
                            generatedDir.deleteRecursively()
                        }
                    }
                }
                
                Log.d("EventFormVM", "Auto-save to local files completed successfully")
            } else {
                Log.d("EventFormVM", "Event directory already exists: ${eventDir.absolutePath}")
            }

            // Step 1.3: 解析目标项目UID（允许UI覆盖）并写入/覆盖到 meta.json
            val resolvedPid = repo.resolveProjectIdByName(projectName)
                ?: projectDao.getIdByExactName(projectName)
            val resolvedProject = resolvedPid?.let { projectDao.getById(it) }
            val targetProjectUidFinal = overrideTargetProjectUid
                ?: (resolvedProject?.projectUid ?: "")
            if (targetProjectUidFinal.isNotBlank()) {
                val (okMeta, msgMeta) = eventRepo.overrideMetaProjectUid(appContext, eventUid, targetProjectUidFinal)
                Log.d("EventFormVM", "overrideMetaProjectUid -> $okMeta | $msgMeta")
            } else {
                Log.w("EventFormVM", "targetProjectUidFinal is blank; skip meta override")
            }

            // Step 1.4: 将数据库中的最新风险评估写入 meta.json，避免上传时答案缺失
            run {
                val (okRisk, msgRisk) = eventRepo.updateMetaRiskFromDb(appContext, eventUid)
                Log.d("EventFormVM", "updateMetaRiskFromDb -> $okRisk | $msgRisk")
            }

            // Step 2: 创建事件压缩包并获取 SHA-256 哈希值
            Log.d("EventFormVM", "Creating event zip package...")
            val zipResult = eventRepo.createEventZip(appContext, eventUid)
            if (!zipResult.first) {
                return@withContext false to "Create zip failed: ${zipResult.second}"
            }
            
            val packageHash = zipResult.second // 获取 SHA-256 哈希值
            Log.d("EventFormVM", "Event zip created successfully, SHA-256: $packageHash")
            
            // Step 3: 调用 create_event_upload 接口获取票据信息
            val taskUid = "task_${System.currentTimeMillis()}" // 生成任务UID
            val targetProjectUid = targetProjectUidFinal.takeIf { it.isNotBlank() }
                ?: run {
                    val pid = repo.resolveProjectIdByName(projectName)
                        ?: projectDao.getIdByExactName(projectName)
                        ?: return@withContext false to "Project not found: $projectName"
                    val proj = projectDao.getById(pid)
                    proj?.projectUid ?: return@withContext false to "Project UID not found"
                }
            
            val uploadList = listOf(
                EventUploadItem(
                    eventUid = eventUid,
                    eventPackageHash = packageHash, // 使用 SHA-256 哈希值
                    eventPackageName = "${eventUid}.zip"
                )
            )
            
            Log.d("EventFormVM", "Calling create_event_upload API...")
            val createResult = eventRepo.createEventUpload(taskUid, targetProjectUid, uploadList)
            if (!createResult.first || createResult.second == null) {
                return@withContext false to "Create event upload failed"
            }
            
            val uploadResponse = createResult.second!!
            if (!uploadResponse.success || uploadResponse.data.isNullOrEmpty()) {
                return@withContext false to "Create event upload response invalid: ${uploadResponse.message}"
            }
            
            Log.d("EventFormVM", "Create event upload API called successfully, got ${uploadResponse.data.size} items")
            
            // Step 4: 遍历返回的data列表，根据event_package_hash匹配并上传对应的压缩包
            for (responseItem in uploadResponse.data) {
                if (responseItem.eventPackageHash == packageHash) {
                    Log.d("EventFormVM", "Found matching item for hash: $packageHash, uploading...")
                    
                    // 将票据数据转换为Map格式
                    val ticketData = mapOf(
                        "host" to responseItem.ticket.host,
                        "dir" to responseItem.ticket.dir,
                        "file_id" to responseItem.ticket.fileId,
                        "policy" to responseItem.ticket.policy,
                        "signature" to responseItem.ticket.signature,
                        "accessid" to responseItem.ticket.accessId
                    )
                    
                    // 使用票据信息上传压缩包
                    val uploadResult = eventRepo.uploadEventZipWithTicket(
                        appContext, 
                        eventUid, 
                        packageHash, 
                        ticketData
                    )
                    
                    if (!uploadResult.first) {
                        return@withContext false to "Upload zip failed: ${uploadResult.second}"
                    }
                    
                    Log.d("EventFormVM", "Zip uploaded successfully for event: $eventUid")
                    break
                }
            }
            
            // Step 5: 轮询查询上传状态
            Log.d("EventFormVM", "Starting polling for upload status...")
            var pollCount = 0
            val maxPollCount = 30 // 最多轮询30次
            val pollInterval = 2000L // 每2秒轮询一次
            
            while (pollCount < maxPollCount) {
                kotlinx.coroutines.delay(pollInterval)
                val statusResult = eventRepo.noticeEventUploadSuccess(taskUid)
                
                Log.d("EventFormVM", "Poll attempt ${pollCount + 1}/$maxPollCount, result: ${statusResult.first}, completed: ${statusResult.second}")
                
                if (statusResult.first) { // 请求成功
                    if (statusResult.second) { // 任务完成
                        Log.d("EventFormVM", "Event sync completed successfully")
                        // 成功后标记事件为已同步（is_synced = 1）
                        try {
                            eventRepo.markEventSynced(eventUid)
                        } catch (markErr: Exception) {
                            Log.w("EventFormVM", "Failed to mark event synced: ${markErr.message}")
                        }
                        return@withContext true to "Event synced to cloud successfully"
                    }
                } else {
                    Log.w("EventFormVM", "Poll request failed, continuing...")
                }
                
                pollCount++
            }
            
            // 轮询超时，返回失败
            Log.w("EventFormVM", "Polling timeout after $maxPollCount attempts")
            false to "Sync timeout - please check sync status later"
        } catch (e: Exception) {
            Log.e("EventFormVM", "uploadEventWithSync failed: ${e.message}", e)
            false to "Sync failed: ${e.message}"
        }
    }

    /**
     * Function: uploadEventWithSyncRetry
     * Description: Wrapper adding automatic retry for New Event sync upload.
     * Policy:
     * - Retry up to `maxRetries` times when `uploadEventWithSync` fails
     * - Wait `delayMs` milliseconds between attempts
     * - Return immediately on first success
     *
     * @param eventUid Event UID
     * @param projectName Project name
     * @param location Event location
     * @param description Event description
     * @param riskResult Risk assessment result
     * @param photoFiles Photo files
     * @param audioFiles Audio files
     * @param selectedDefects Associated defects
     * @param digitalAssetFileIds Digital asset file IDs
     * @param structuralDefectDetails Structural defect details (optional)
     * @param maxRetries Maximum retry attempts (default 5)
     * @param delayMs Delay between attempts in milliseconds (default 3000)
     * @return Pair<Boolean, String> success flag and message
     */
    suspend fun uploadEventWithSyncRetry(
        eventUid: String,
        projectName: String,
        location: String?,
        description: String,
        riskResult: RiskAssessmentResult?,
        photoFiles: List<File>,
        audioFiles: List<File>,
        selectedDefects: List<DefectEntity>,
        digitalAssetFileIds: List<String> = emptyList(),
        structuralDefectDetails: String? = null,
        /** 可选：上传前用户选择的目标项目UID，若为空则按projectName解析 */
        overrideTargetProjectUid: String? = null,
        maxRetries: Int = 5,
        delayMs: Long = 3000L
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        var lastMessage = ""
        // 兼容保护：若传入的是数值主键字符串，自动映射为真实UID
        val normalizedUid: String = if (eventUid.all { it.isDigit() }) {
            try {
                val id = eventUid.toLong()
                getEventUidById(id) ?: eventUid
            } catch (_: Exception) {
                eventUid
            }
        } else eventUid
        for (attempt in 1..maxRetries) {
            val (ok, msg) = try {
                uploadEventWithSync(
                    eventUid = normalizedUid,
                    projectName = projectName,
                    location = location,
                    description = description,
                    riskResult = riskResult,
                    photoFiles = photoFiles,
                    audioFiles = audioFiles,
                    selectedDefects = selectedDefects,
                    digitalAssetFileIds = digitalAssetFileIds,
                    structuralDefectDetails = structuralDefectDetails,
                    overrideTargetProjectUid = overrideTargetProjectUid
                )
            } catch (e: Exception) { false to ("Exception: ${e.message}") }
            if (ok) {
                Log.d("EventFormVM", "uploadEventWithSyncRetry success on attempt $attempt")
                return@withContext true to "Event sync succeeded on attempt $attempt"
            }
            lastMessage = msg
            Log.w("EventFormVM", "uploadEventWithSync attempt $attempt failed: $msg")
            if (attempt < maxRetries) {
                kotlinx.coroutines.delay(delayMs)
            }
        }
        false to "Event sync failed after $maxRetries attempts: $lastMessage"
    }

    /**
     * Function: uploadEventWithSyncRetryById
     * Description: Convenience wrapper that accepts Room numeric ID, resolves UID, then delegates to
     *              `uploadEventWithSyncRetry`. This shields call sites from ID/UID混用问题。
     *
     * @param eventId Room 主键ID
     * @param projectName 项目名称
     * @param location 事件地点
     * @param description 事件描述
     * @param riskResult 风险评估结果
     * @param photoFiles 照片文件列表
     * @param audioFiles 音频文件列表
     * @param selectedDefects 关联缺陷
     * @param digitalAssetFileIds 数字资产 file_id 列表
     * @param structuralDefectDetails 结构性缺陷详情（JSON字符串，可选）
     * @param maxRetries 最大重试次数
     * @param delayMs 重试间隔毫秒
     * @return Pair<Boolean, String> 成功与消息
     */
    suspend fun uploadEventWithSyncRetryById(
        eventId: Long,
        projectName: String,
        location: String?,
        description: String,
        riskResult: RiskAssessmentResult?,
        photoFiles: List<File>,
        audioFiles: List<File>,
        selectedDefects: List<DefectEntity>,
        digitalAssetFileIds: List<String> = emptyList(),
        structuralDefectDetails: String? = null,
        /** 可选：上传前用户选择的目标项目UID，若为空则按projectName解析 */
        overrideTargetProjectUid: String? = null,
        maxRetries: Int = 5,
        delayMs: Long = 3000L
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val uid = getEventUidById(eventId)
            ?: return@withContext false to "Event UID not found for id=$eventId. Save locally then retry."
        uploadEventWithSyncRetry(
            eventUid = uid,
            projectName = projectName,
            location = location,
            description = description,
            riskResult = riskResult,
            photoFiles = photoFiles,
            audioFiles = audioFiles,
            selectedDefects = selectedDefects,
            digitalAssetFileIds = digitalAssetFileIds,
            structuralDefectDetails = structuralDefectDetails,
            overrideTargetProjectUid = overrideTargetProjectUid,
            maxRetries = maxRetries,
            delayMs = delayMs
        )
    }

    /**
     * 函数级注释：通过事件 UID 查询 Room 主键 ID
     *
     * 用途：
     * - 屏幕在自动保存或页面退出时，根据 UID 判断该事件是否已存在，
     *   以决定进行插入还是更新，避免并发情况下的重复插入。
     *
     * 参数：
     * - uid: 事件的唯一标识（目录名）
     * 返回：
     * - Long? 已存在事件的 Room 主键 ID；未找到返回 null
     */
    suspend fun getEventRoomIdByUid(uid: String): Long? = withContext(Dispatchers.IO) {
        return@withContext try {
            eventRepo.getEventByUid(uid)?.eventId
        } catch (e: Exception) {
            Log.w("EventFormVM", "getEventRoomIdByUid query failed for uid=$uid: ${e.message}")
            null
        }
    }
}

/**
 * 数据类：数字资产详细信息
 * 
 * @property fileId 文件ID
 * @property fileName 文件名
 * @property type 文件类型（如 PDF、PIC、MP3、REC、RISK_MATRIX 等）
 * @property localPath 本地文件路径
 */
data class DigitalAssetDetail(
    val fileId: String,
    val fileName: String,
    val type: String,
    val localPath: String?
)

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
 * @property digitalAssets 数字资产文件ID列表
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
    val digitalAssets: List<String>,
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