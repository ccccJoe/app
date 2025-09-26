/*
 * File: EventFormViewModel.kt
 * Description: ViewModel for Event form screen. Provides risk matrix loader and handles UI-related data.
 * Author: SIMS Team
 */
package com.simsapp.ui.event

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
import com.simsapp.data.repository.EventRepository
import com.simsapp.data.local.dao.ProjectDao
import com.simsapp.data.local.entity.EventEntity
import android.util.Log

/**
 * EventFormViewModel
 *
 * Responsibilities:
 * - Builds RiskMatrixLoader for RiskAssessmentWizardDialog
 * - Orchestrates data sources (remote or offline assets) for risk configuration
 * - Persist event data locally (assets + meta.json), supports create and update by uid
 * - Delete local event folder when needed
 *
 * Design:
 * - Prefer remote loading via ProjectRepository when endpoint and fileIds are configured
 * - Fallback to local assets/risk.json for offline usage and development
 */
@HiltViewModel
class EventFormViewModel @Inject constructor(
    private val repo: ProjectRepository,
    @ApplicationContext private val appContext: Context,
    private val gson: Gson,
    private val eventRepo: EventRepository,
    private val projectDao: ProjectDao
) : ViewModel() {

    /**
     * 函数：autosaveDraftToRoom
     * 说明：将当前输入实时保存为 Room 草稿事件。
     * 策略：
     * - 通过项目名称解析 projectId（若失败则返回错误）
     * - 构造 EventEntity（isDraft=true，更新 lastEditTime）并调用 Repository.upsert
     * - 返回新建或更新后的 eventId
     *
     * @param projectName 项目名称（用于解析 projectId）
     * @param location 位置输入
     * @param description 内容输入
     * @param currentEventId 当前事件ID（用于更新草稿；为空或0则新建）
     */
    /**
     * 函数：saveEventToRoom
     * 说明：保存事件数据到 Room 数据库，区分新建和编辑模式。
     * 策略：
     * - 通过项目名称解析 projectId（若失败则返回错误）
     * - 根据是否有 currentEventId 判断是新建还是编辑
     * - 构造 EventEntity（isDraft=true，更新 lastEditTime）并调用 Repository.upsert
     * - 返回新建或更新后的 eventId
     *
     * @param projectName 项目名称（用于解析 projectId）
     * @param location 位置输入
     * @param description 内容输入
     * @param currentEventId 当前事件ID（用于更新草稿；为空或0则新建）
     * @param isEditMode 是否为编辑模式（true=编辑现有event，false=新建event）
     */
    suspend fun saveEventToRoom(
        projectName: String,
        location: String?,
        description: String,
        currentEventId: Long?,
        isEditMode: Boolean = false,
        riskResult: RiskAssessmentResult? = null,
        photoFiles: List<File> = emptyList(),
        audioFiles: List<File> = emptyList()
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // 优先使用仓库辅助解析，回退到 DAO
            val pid = runCatching { repo.resolveProjectIdByName(projectName) }.getOrNull()
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
            
            val entity = if (isEditMode && currentEventId != null && currentEventId > 0) {
                // 编辑模式：更新现有事件
                EventEntity(
                    eventId = currentEventId,
                    projectId = pid,
                    projectUid = projectUid,
                    defectIds = emptyList(),
                    defectNos = emptyList(),
                    location = location,
                    content = description,
                    lastEditTime = now,
                    assets = emptyList(),
                    riskLevel = riskResult?.level,
                    riskScore = riskResult?.score,
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
                    defectIds = emptyList(),
                    defectNos = emptyList(),
                    location = location,
                    content = description,
                    lastEditTime = now,
                    assets = emptyList(),
                    riskLevel = riskResult?.level,
                    riskScore = riskResult?.score,
                    riskAnswers = riskAnswersJson,
                    photoFiles = photoFilePaths,
                    audioFiles = audioFilePaths,
                    isDraft = true
                )
            }
            
            val id = eventRepo.upsert(entity)
            // 验收日志：打印生成/更新后的草稿事件 ID 与关键字段摘要
            Log.d(
                "EventFormVM",
                "saveEventToRoom -> pid=$pid, eventId=$id, location=${location ?: ""}, descLen=${description.length}, " +
                "riskLevel=${riskResult?.level ?: "null"}, photoCount=${photoFiles.size}, audioCount=${audioFiles.size}, " +
                "isEditMode=$isEditMode, existingId=${currentEventId ?: -1L}"
            )
            Result.success(id)
        } catch (e: Exception) {
            Log.e("EventFormVM", "saveEventToRoom failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 函数：autosaveDraftToRoom（保持向后兼容）
     * 说明：将当前输入实时保存为 Room 草稿事件。
     * 策略：
     * - 通过项目名称解析 projectId（若失败则返回错误）
     * - 构造 EventEntity（isDraft=true，更新 lastEditTime）并调用 Repository.upsert
     * - 返回新建或更新后的 eventId
     *
     * @param projectName 项目名称（用于解析 projectId）
     * @param location 位置输入
     * @param description 内容输入
     * @param currentEventId 当前事件ID（用于更新草稿；为空或0则新建）
     */
    suspend fun autosaveDraftToRoom(
        projectName: String,
        location: String?,
        description: String,
        currentEventId: Long?
    ): Result<Long> = saveEventToRoom(projectName, location, description, currentEventId, false)

    /**
     * 函数：loadEventFromRoom
     * 说明：从 Room 数据库中加载指定的事件数据。
     * 
     * @param eventId 事件ID
     * @return Result<EventEntity?> 事件实体或null（如果不存在）
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
      * Bridge: 压缩并上传本地事件目录（基于 UID）。
     */
    suspend fun uploadEvent(uid: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        // 验收日志：上传开始与结束（便于观测仅上传到云的执行路径）
        Log.i("EventFormVM", "uploadEvent: begin uid=" + uid)
        val res = eventRepo.uploadEventZip(appContext, uid)
        Log.i("EventFormVM", "uploadEvent: end ok=" + res.first + ", msgLen=" + res.second.length)
        res
    }

    /**
     * Create a RiskMatrixLoader for RiskAssessmentWizardDialog.
     * Strategy:
     * 1) If endpoint and fileIds are configured, use repository-based online loader
     * 2) Otherwise, fallback to local assets/risk.json
     *
     * @return a function that loads and maps risk matrix config into UI model
     */
    fun createRiskMatrixLoader(): RiskMatrixLoader {
        // TODO: fill in with real endpoint and file id list when backend is ready
        val endpoint = "" // e.g., https://sims.ink-stone.win/zuul/sims-master/storage/download/url
        val fileIds: List<String> = emptyList() // e.g., listOf("base64-id")

        return if (endpoint.isNotBlank() && fileIds.isNotEmpty()) {
            // Use online loader mapping helper
            buildRiskMatrixLoaderFromRepository(repo, endpoint, fileIds)
        } else {
            // Offline fallback: load from assets/risk.json
            suspend {
                try {
                    val json = appContext.assets.open("risk.json").bufferedReader().use { it.readText() }
                    val payload = gson.fromJson(json, ProjectRepository.RiskMatrixPayload::class.java)
                    val c = payload.consequenceData.map {
                        ConsequenceItemUI(
                            severityFactor = it.severityFactor,
                            cost = it.cost,
                            productionLoss = it.productionLoss,
                            safety = it.safety,
                            other = it.other
                        )
                    }
                    val l = payload.likelihoodData.map {
                        LikelihoodItemUI(
                            likelihoodFactor = it.likelihoodFactor,
                            criteria = it.criteria
                        )
                    }
                    val p = payload.priorityData.map {
                        PriorityItemUI(
                            priority = it.priority,
                            minValue = it.minValue,
                            maxValue = it.maxValue,
                            minInclusive = it.minInclusive,
                            maxInclusive = it.maxInclusive
                        )
                    }
                    Result.success(RiskMatrixUI(c, l, p))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Persist an Event to local app storage.
     * When existingUid is blank, create a new event folder and generate a new uid.
     * When existingUid is provided, update the existing event folder and reuse its uid and createdAt.
     *
     * Steps:
     * 1) Determine target uid and event directory
     * 2) Move/copy provided photo/audio files from cache to app filesDir/events/{uid} if needed
     *    - Files already within eventDir will be kept in place
     *    - New files will be named with incremental indices (photo_#, audio_#)
     * 3) Create/overwrite meta.json containing description, location, risk result, and asset file names
     *
     * @param projectName Optional project name for display-only purpose
     * @param location User input location string
     * @param description User input description/content
     * @param photoFiles Photo files, may include temp files (from cache) and/or existing files under eventDir
     * @param audioFiles Audio files, may include temp files (from cache) and/or existing files under eventDir
     * @param riskResult RiskAssessmentResult containing priority code (P1~P4), score (Double), and optional answers list
     * @param existingUid If not blank, update the event with this uid; otherwise create a new event
     * @return Result<String> the uid on success
     */
    suspend fun saveEventToLocal(
        projectName: String,
        location: String,
        description: String,
        photoFiles: List<File>,
        audioFiles: List<File>,
        riskResult: RiskAssessmentResult?,
        existingUid: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val uid = existingUid?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            val baseDir = File(appContext.filesDir, "events")
            if (!baseDir.exists()) baseDir.mkdirs()
            val eventDir = File(baseDir, uid)
            if (!eventDir.exists()) eventDir.mkdirs()

            // Read previous createdAt when updating
            val createdAtPersisted: Long = try {
                if (!existingUid.isNullOrBlank()) {
                    val meta = File(eventDir, "meta.json")
                    if (meta.exists()) {
                        val obj = org.json.JSONObject(meta.readText())
                        obj.optLong("createdAt", System.currentTimeMillis())
                    } else System.currentTimeMillis()
                } else System.currentTimeMillis()
            } catch (_: Exception) { System.currentTimeMillis() }

            // Helpers: compute next index for naming new assets
            fun nextIndexFor(prefix: String, dir: File): Int {
                val regex = Regex("^${prefix}_(\\d+)\\..+")
                val max = dir.listFiles()?.mapNotNull { f ->
                    regex.find(f.name)?.groupValues?.getOrNull(1)?.toIntOrNull()
                }?.maxOrNull() ?: 0
                return max
            }
            var nextPhotoIndex = nextIndexFor("photo", eventDir)
            var nextAudioIndex = nextIndexFor("audio", eventDir)

            val savedPhotos = mutableListOf<String>()
            val savedAudios = mutableListOf<String>()

            // Move/copy photos if needed
            photoFiles.forEach { f ->
                val inPlace = try { f.parentFile?.canonicalPath == eventDir.canonicalPath } catch (_: Exception) { false }
                if (inPlace) {
                    savedPhotos.add(f.name)
                } else {
                    val ext = f.extension.ifBlank { "jpg" }
                    nextPhotoIndex += 1
                    val target = File(eventDir, "photo_${nextPhotoIndex}.${ext}")
                    moveOrCopyFile(f, target)
                    savedPhotos.add(target.name)
                }
            }
            // Move/copy audios if needed
            audioFiles.forEach { f ->
                val inPlace = try { f.parentFile?.canonicalPath == eventDir.canonicalPath } catch (_: Exception) { false }
                if (inPlace) {
                    savedAudios.add(f.name)
                } else {
                    val ext = f.extension.ifBlank { "m4a" }
                    nextAudioIndex += 1
                    val target = File(eventDir, "audio_${nextAudioIndex}.${ext}")
                    moveOrCopyFile(f, target)
                    savedAudios.add(target.name)
                }
            }

            // Build meta content
            val meta = EventMeta(
                uid = uid,
                projectName = projectName,
                location = location,
                description = description,
                risk = riskResult?.let { RiskMeta(priority = it.level, score = it.score, answers = it.answers) },
                photos = savedPhotos,
                audios = savedAudios,
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
     * 说明：删除指定的事件，包括数据库记录和本地文件
     * 参数：eventId - 事件ID，uid - 事件唯一标识符
     * 返回：Result<Unit> - 删除结果
     */
    suspend fun deleteEvent(eventId: Long, uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. 删除数据库中的事件记录
            eventRepo.delete(eventId)
            
            // 2. 删除本地文件夹
            val deleteLocalResult = deleteEventLocal(uid)
            if (deleteLocalResult.isFailure) {
                Log.w("EventFormViewModel", "Failed to delete local files for event $uid: ${deleteLocalResult.exceptionOrNull()}")
                // 即使本地文件删除失败，也不影响数据库删除的成功
            }
            
            Log.d("EventFormViewModel", "Successfully deleted event $eventId with uid $uid")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("EventFormViewModel", "Failed to delete event $eventId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete an existing local event folder by uid.
     * @param uid The event identifier
     * @return Result<Unit> success when deleted or folder does not exist
     */
    suspend fun deleteEventLocal(uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Ensure delete target path matches saveEventToLocal directory structure: filesDir/events/{uid}
            val baseDir = File(appContext.filesDir, "events")
            val dir = File(baseDir, uid)
            if (dir.exists()) deleteRecursively(dir)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Move a file by rename, fallback to copy+delete when needed.
     */
    private fun moveOrCopyFile(src: File, dest: File) {
        // Ensure parent exists
        dest.parentFile?.mkdirs()
        if (!src.renameTo(dest)) {
            FileInputStream(src).channel.use { inCh ->
                FileOutputStream(dest).channel.use { outCh ->
                    inCh.transferTo(0, inCh.size(), outCh)
                }
            }
            try { src.delete() } catch (_: Exception) {}
        }
    }

    /**
     * Recursively delete a file or directory.
     */
    private fun deleteRecursively(target: File) {
        if (target.isDirectory) {
            target.listFiles()?.forEach { child ->
                deleteRecursively(child)
            }
        }
        try { target.delete() } catch (_: Exception) {}
    }

    /**
     * Metadata structure persisted to meta.json for each event.
     */
    data class EventMeta(
        val uid: String,
        val projectName: String?,
        val location: String?,
        val description: String,
        val risk: RiskMeta?,
        val photos: List<String>,
        val audios: List<String>,
        val createdAt: Long
    )

    /**
     * Risk meta saved inside meta.json
     * - priority: P1..P4 level code
     * - score: numeric score computed from wizard
     * - answers: optional detailed selections for auditing and backend upload
     */
    data class RiskMeta(
        val priority: String,
        val score: Double,
        val answers: List<RiskAnswer>? = null
    )
}