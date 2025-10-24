/*
 * File: ProjectRepository.kt
 * Description: Repository for project-related data combining local DB and remote API.
 * Author: SIMS Team
 */
package com.simsapp.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.simsapp.data.local.dao.ProjectDao
import com.simsapp.data.local.entity.ProjectEntity
import com.simsapp.data.remote.ApiService

import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DeleteResult
 * 
 * Represents the result of a delete operation.
 */
data class DeleteResult(
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

/**
 * ProjectRepository
 *
 * Acts as a single source of truth for project data.
 */
@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val api: ApiService,
    private val gson: Gson,
    @ApplicationContext private val appContext: Context,
    private val projectDetailDao: com.simsapp.data.local.dao.ProjectDetailDao,
    private val defectRepository: DefectRepository,
    private val eventRepository: EventRepository,
    private val projectDigitalAssetRepository: ProjectDigitalAssetRepository
) {
    /** 全局同步状态：用于跨页面/前后台保持“同步中”标识 */
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    /** Observe all projects. */
    fun getProjects(): Flow<List<ProjectEntity>> = projectDao.getAll()

    /** Insert or update a project locally. */
    suspend fun upsert(project: ProjectEntity): Long = projectDao.insert(project)
    
    /** Get a project by uid. */
    suspend fun getProjectByUid(uid: String): ProjectEntity? = projectDao.getByUid(uid)
    
    /** Get a project by id. */
    suspend fun getProjectById(id: Long): ProjectEntity? = projectDao.getById(id)
    
    /** Get project id by uid. */
    suspend fun getProjectIdByUid(uid: String): Long? = projectDao.getIdByUid(uid)

    /**
     * Helper: resolve projectId by exact project name.
     * Returns null when no match.
     */
    suspend fun resolveProjectIdByName(name: String): Long? = projectDao.getIdByExactName(name)

    /** Fetch remote status (placeholder) and return Boolean indicating healthy network. */
    suspend fun pingRemote(): Boolean = try {
        val resp = api.health()
        resp.isSuccessful
    } catch (e: Exception) {
        false
    }

    // -------------------- Risk Matrix Models & Fetch --------------------
    /** 文件级注释：以下数据类用于解析风险矩阵 JSON 配置。 */
    data class ConsequenceItem(
        val level: String,
        @SerializedName("severity_factor") val severityFactor: Double,
        val cost: String,
        val productionLoss: String,
        val safety: String,
        val other: String
    )

    data class LikelihoodItem(
        val level: String,
        val description: String?,
        @SerializedName("likelihood_factor") val likelihoodFactor: Double,
        val criteria: String? = null
    )

    data class PriorityItem(
        val priority: String,
        val criteria: String,
        val minValue: Double,
        val maxValue: Double,
        val minInclusive: String,
        val maxInclusive: String
    )

    data class RiskMatrixPayload(
        @SerializedName("consequenceData") val consequenceData: List<ConsequenceItem>,
        @SerializedName("likelihoodData") val likelihoodData: List<LikelihoodItem>,
        @SerializedName("priorityData") val priorityData: List<PriorityItem>,
        val timestamp: String? = null
    )

    /**
     * 函数：fetchRiskMatrix
     * 说明：
     * 1. 调用 resolveDownloadUrl() 接口解析真实下载链接（传入完整 endpoint 与文件ID数组）
     * 2. 使用返回结果中的 data[0].url（接口可能直接返回纯文本URL或 JSON）来下载风险矩阵 JSON
     * 3. 解析为 RiskMatrixPayload 返回
     *
     * @param endpoint 完整解析接口 URL，例如：/storage/download/url
     * @param fileIds 文件ID列表（仅使用首个）
     */
    suspend fun fetchRiskMatrix(endpoint: String, fileIds: List<String>): Result<RiskMatrixPayload> {
        // 使用块体函数以便在失败时进行早返回
        return try {
            // 第一步：解析下载 URL
            val resolveResp = api.resolveDownloadUrl(endpoint, fileIds)
            if (!resolveResp.isSuccessful) {
                return Result.failure(IllegalStateException("resolve url failed ${resolveResp.code()}"))
            }
            val raw = resolveResp.body()?.string().orEmpty()

            // 兼容两种返回：
            // 1) 直接为可访问的 URL 字符串
            // 2) JSON 对象/数组，包含 data 列表且第一个元素的 url 字段
            val downloadUrl = parseFirstUrlFromResponse(raw)
                ?: return Result.failure(IllegalStateException("No url found in resolve response"))

            // 第二步：下载 JSON
            val fileResp = api.downloadRiskMatrixByUrl(downloadUrl)
            if (!fileResp.isSuccessful) {
                return Result.failure(IllegalStateException("download failed ${fileResp.code()}"))
            }
            val json = fileResp.body()?.string().orEmpty()
            if (json.isBlank()) {
                return Result.failure(IllegalStateException("empty risk json"))
            }

            // 解析 JSON
            val payload = gson.fromJson(json, RiskMatrixPayload::class.java)
            Result.success(payload)
        } catch (e: Exception) {
            // 注意：不要在此处修改 _isSyncing，全局同步状态仅由顶层同步流程维护。
            return Result.failure(e)
        }
    }

    /**
     * 工具函数：从解析接口响应中提取第一个 URL。
     * 支持纯字符串或包含 data 数组的 JSON 对象。
     */
    private fun parseFirstUrlFromResponse(body: String): String? {
        val trimmed = body.trim()
        if (trimmed.startsWith("http")) return trimmed.replace("\"", "").trim()
        return try {
            val obj = gson.fromJson(trimmed, Map::class.java)
            // 期望结构：{"data":[{"url":"https://..."}]}
            val data = obj["data"] as? List<*>
            val first = data?.firstOrNull() as? Map<*, *>
            (first?.get("url") as? String)
        } catch (_: Exception) {
            null
        }
    }

    // 新增：项目详情接口固定端点（不含查询参数）
    private val projectDetailEndpoint: String =
        "app/project/project"

    /**
     * 函数：syncProjectsFromEndpoint
     * 说明：
     * - 调用动态 @Url 接口获取项目列表 JSON
     * - 兼容纯数组或带 data/items/list 包裹的对象结构
     * - 将远端字段健壮映射为本地 ProjectEntity
     * - 新增：基于 project_hash 的增量同步优化，避免重复获取详情
     * - 仅对 hash 值不同或本地不存在的项目进行详情拉取和缓存
     */
    suspend fun syncProjectsFromEndpoint(
        endpoint: String,
        username: String? = null,
        authorization: String? = null
    ): Result<Int> {
        _isSyncing.value = true
        return try {
            val resp = api.getProjects(endpoint, username, authorization)
            if (!resp.isSuccessful) {
                _isSyncing.value = false
                return Result.failure(IllegalStateException("http ${resp.code()}"))
            }
            val body = resp.body()?.string().orEmpty().trim()
            if (body.isBlank()) {
                return Result.failure(IllegalStateException("empty body"))
            }

            val records: List<Map<String, Any?>> = parseProjectsArray(body)
            val remoteEntities = records.map { map -> mapToEntity(map) }

            // 若解析结果为空，直接返回失败，首页不展示模拟数据
            if (remoteEntities.isEmpty()) {
                _isSyncing.value = false
                return Result.failure(IllegalStateException("parsed empty"))
            }

            // 获取本地所有项目的 uid-hash 映射
            val localUidHashMap: Map<String, String> = projectDao.getAllUidHashPairs().associate { pair -> 
                pair.project_uid to pair.project_hash 
            }
            
            // 分类处理：需要更新的项目和不需要更新的项目
            val projectsToUpdate = mutableListOf<ProjectEntity>()
            val projectsToSkip = mutableListOf<ProjectEntity>()
            
            for (remoteEntity in remoteEntities) {
                val uid = remoteEntity.projectUid
                val remoteHash = remoteEntity.projectHash
                val localHash = localUidHashMap[uid]
                
                if (localHash == null || localHash != remoteHash) {
                    // 本地不存在或 hash 值不同，需要更新
                    projectsToUpdate.add(remoteEntity)
                } else {
                    // hash 值相同，跳过详情拉取，但仍需更新基本信息（如计数器等）
                    projectsToSkip.add(remoteEntity)
                }
            }

            Log.i("SIMS-SYNC", "Projects analysis: total=${remoteEntities.size}, toUpdate=${projectsToUpdate.size}, toSkip=${projectsToSkip.size}")

            // 仅更新需要更新的项目的基本信息，保留hash值一致项目的本地缓存数据
            if (projectsToUpdate.isNotEmpty()) {
                // 记录更新前的events数量
                for (project in projectsToUpdate) {
                    val eventCount = eventRepository.getEventsByProjectUid(project.projectUid ?: "").first().size
                    Log.d("SIMS-SYNC", "Project ${project.projectUid} has $eventCount events before update")
                }
                
                projectDao.safeUpdateProjects(projectsToUpdate)
                Log.i("SIMS-SYNC", "Updated projects basic info: ${projectsToUpdate.size}")
                
                // 记录更新后的events数量
                for (project in projectsToUpdate) {
                    val eventCount = eventRepository.getEventsByProjectUid(project.projectUid ?: "").first().size
                    Log.d("SIMS-SYNC", "Project ${project.projectUid} has $eventCount events after update")
                }
            }
            
            // 对于hash值一致的项目，仅更新计数器等可能变化的字段，保留其他本地缓存数据
            for (entity in projectsToSkip) {
                val localProject = projectDao.getByUid(entity.projectUid)
                if (localProject != null) {
                    // 仅更新可能变化的计数器字段，保留其他本地数据
                    projectDao.updateCounters(localProject.projectId, entity.defectCount, entity.eventCount)
                }
            }
            Log.i("SIMS-SYNC", "Updated counters for ${projectsToSkip.size} unchanged projects")

            // 仅对需要更新的项目拉取详情
            var detailUpdateCount = 0
            try {
                for (entity in projectsToUpdate) {
                    val uid = entity.projectUid
                    if (uid.isBlank()) continue
                    
                    // 拉取详情
                    val detailResp = api.getProjectDetail(
                        endpoint = projectDetailEndpoint,
                        projectUid = uid,
                        username = username,
                        authorization = authorization
                    )
                    if (!detailResp.isSuccessful) {
                        Log.w("SIMS-SYNC", "Detail http ${detailResp.code()} for uid=$uid")
                        continue
                    }
                    val detailJson = detailResp.body()?.string().orEmpty()
                    if (detailJson.isBlank()) {
                        Log.w("SIMS-SYNC", "Empty detail for uid=$uid")
                        continue
                    }
                    
                    // 获取本地项目ID
                    val localProject = projectDao.getByUid(uid)
                    if (localProject == null) {
                        Log.w("SIMS-SYNC", "Local project not found for uid=$uid")
                        continue
                    }
                    
                    val detailEntity = mapDetailJsonToEntity(
                        json = detailJson,
                        projectId = localProject.projectId,
                        projectUid = uid
                    )
                    projectDetailDao.insert(detailEntity)
                    detailUpdateCount++
                    Log.d("SIMS-SYNC", "Cached detail for projectId=${localProject.projectId} uid=$uid")

                    // 在缓存详情后，解析历史缺陷并下载图片到本地
                    try {
                        cacheHistoryDefectImages(projectUid = uid, detailJson = detailJson)
                        // 缓存历史缺陷数据到defect表
                        cacheHistoryDefectsToDatabase(projectId = localProject.projectId, projectUid = uid, detailJson = detailJson)
                        
                        // 处理数字资产树，解析并下载所有file_id不为null的节点
                        try {
                            val (successCount, totalCount) = projectDigitalAssetRepository.processDigitalAssetTree(
                                projectId = localProject.projectId,
                                projectUid = uid,
                                projectDetailJson = detailJson
                            )
                            Log.d("SIMS-SYNC", "Digital asset processing completed for project uid=$uid: $successCount/$totalCount assets processed")
                        } catch (e: Exception) {
                            Log.e("SIMS-SYNC", "Digital asset processing error for project uid=$uid: ${e.message}", e)
                        }
                    } catch (e: Exception) {
                        Log.e("SIMS-SYNC", "cache images/assets error uid=$uid, ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("SIMS-SYNC", "Detail fetch error: ${e.message}", e)
            }

            Log.i("SIMS-SYNC", "Incremental sync completed: ${remoteEntities.size} projects, ${detailUpdateCount} details updated")
            _isSyncing.value = false
            Result.success(remoteEntities.size)
        } catch (e: Exception) {
            _isSyncing.value = false
            Result.failure(e)
        }
    }

    /**
     * 工具：解析 JSON，返回项目记录列表（Map）。
     * 支持：
     * - 纯数组：[{...}, {...}]
     * - 对象包裹：{"data":[...]}, {"items":[...]}, {"list":[...]}
     */
    private fun parseProjectsArray(json: String): List<Map<String, Any?>> {
        val trimmed = json.trim()
        return try {
            if (trimmed.startsWith("[")) {
                @Suppress("UNCHECKED_CAST")
                (gson.fromJson(trimmed, List::class.java) as List<Map<String, Any?>>)
            } else {
                @Suppress("UNCHECKED_CAST")
                val obj = gson.fromJson(trimmed, Map::class.java) as Map<String, Any?>
                // 顶层尝试 data/items/list 三种键
                val top = listOf("data", "items", "list").firstNotNullOfOrNull { key -> obj[key] }
                val arr: List<*>? = when (top) {
                    is List<*> -> top
                    is Map<*, *> -> {
                        // 兼容 { data: { list: [...] } } 或 { data: { items: [...] } }
                        val m = top as Map<String, Any?>
                        listOf("data", "items", "list").firstNotNullOfOrNull { k -> m[k] as? List<*> }
                    }
                    else -> null
                }
                @Suppress("UNCHECKED_CAST")
                (arr ?: emptyList<Any?>()).filterIsInstance<Map<String, Any?>>()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 工具：将远端 Map 映射为本地 ProjectEntity（健壮容错）。
     * 支持常见字段名：projectId/id/project_id、name/projectName/project_name、endDate/end_date/project_last_update_at、status/project_status、defectCount、eventCount、project_uid、project_hash。
     * 新增：解析 project_uid 用于后续详情拉取与缓存关联。
     * 新增：解析 project_hash 用于增量同步优化。
     */
    private fun mapToEntity(m: Map<String, Any?>): ProjectEntity {
        fun str(key: String): String? = (m[key] as? String)?.takeIf { it.isNotBlank() }
        fun num(key: String): Number? = m[key] as? Number
        fun anyStr(vararg keys: String): String? = keys.asSequence().mapNotNull { str(it) }.firstOrNull()
        fun anyNum(vararg keys: String): Number? = keys.asSequence().mapNotNull { num(it) }.firstOrNull()

        val id = anyNum("projectId", "id", "project_id")?.toLong() ?: 0L
        val name = anyStr("name", "projectName", "project_name") ?: "Unnamed Project"
        // 新增：project_uid，确保非空
        val projectUid = anyStr("project_uid", "projectUid", "uid", "projectUID") ?: ""
        // 新增：project_hash，用于增量同步比较
        val projectHash = anyStr("project_hash", "projectHash", "hash", "project_hash_value") ?: ""

        // 更新时间优先使用 project_last_update_at（及同义键），其次使用 endDate（及同义键）
        // 数字型与字符串型分别处理：数字视为 epochMillis；字符串支持数字或日期解析
        val lastUpdateKeysNum = arrayOf(
            "project_last_update_at", "projectLastUpdateAt",
            "last_update_at", "lastUpdateAt",
            "updated_at", "updatedAt"
        )
        val endDateKeysNum = arrayOf("endDate", "end_date")
        val lastUpdateKeysStr = lastUpdateKeysNum
        val endDateKeysStr = endDateKeysNum

        val endDateMillis: Long? = when {
            anyNum(*lastUpdateKeysNum) != null -> anyNum(*lastUpdateKeysNum)!!.toLong()
            anyStr(*lastUpdateKeysStr) != null -> {
                val s = anyStr(*lastUpdateKeysStr)!!
                s.toLongOrNull() ?: parseDateToMillis(s)
            }
            anyNum(*endDateKeysNum) != null -> anyNum(*endDateKeysNum)!!.toLong()
            anyStr(*endDateKeysStr) != null -> {
                val s = anyStr(*endDateKeysStr)!!
                s.toLongOrNull() ?: parseDateToMillis(s)
            }
            else -> null
        }

        val status = anyStr("status", "project_status") ?: "ACTIVE"
        val defectCount = (anyNum("defectCount", "defect_count")?.toInt()) ?: 0
        val eventCount = (anyNum("eventCount", "event_count")?.toInt()) ?: 0

        return ProjectEntity(
            projectId = id,
            name = name,
            projectUid = projectUid,
            projectHash = projectHash,
            endDate = endDateMillis,
            status = status,
            defectCount = defectCount,
            eventCount = eventCount
        )
    }

    /** 将日期字符串解析为毫秒，支持多种格式；解析失败返回 null */
    private fun parseDateToMillis(s: String): Long? {
        val text = s.trim()
        // 尝试纯数字字符串（时间戳）
        text.toLongOrNull()?.let { return it }
        // 常见日期时间格式优先匹配（含时间）
        val dtPatterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd HH:mm",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
        )
        for (p in dtPatterns) {
            try {
                val formatter = SimpleDateFormat(p.replace("XXX", "Z"), Locale.getDefault())
                val date = formatter.parse(text)
                if (date != null) return date.time
            } catch (_: Exception) { }
        }
        // 仅日期格式（按本地零点处理）
        val dPatterns = listOf("yyyy-MM-dd", "yyyy/MM/dd")
        for (p in dPatterns) {
            try {
                val normalized = if (p.contains('/')) text.replace('-', '/') else text.replace('/', '-')
                val formatter = SimpleDateFormat(p, Locale.getDefault())
                val date = formatter.parse(normalized)
                if (date != null) return date.time
            } catch (_: Exception) { }
        }
        // ISO8601 兜底 - 简化处理
        return try {
            if (text.endsWith("Z")) {
                val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                formatter.timeZone = TimeZone.getTimeZone("UTC")
                formatter.parse(text)?.time
            } else null
        } catch (_: Exception) { null }
    }

    /**
     * 获取当前项目总数。
     * @return 项目记录条数
     */
    suspend fun getProjectCount(): Int = projectDao.count()

    /**
     * 获取所有已完成状态的项目
     * @return 已完成状态的项目列表
     */
    fun getFinishedProjects(): Flow<List<ProjectEntity>> = projectDao.getFinishedProjects()

    /**
     * 批量删除项目及其相关数据
     * @param projectIds 要删除的项目ID列表
     * @return 删除操作的结果
     */
    suspend fun deleteProjectsAndRelatedData(projectIds: List<Long>): DeleteResult {
        return try {
            // 删除项目相关的所有数据
            for (projectId in projectIds) {
                // 删除项目下的缺陷
                defectRepository.deleteByProjectId(projectId)
                // 删除项目下的事件
                eventRepository.deleteByProjectId(projectId)
                // 删除项目详情
                projectDetailDao.deleteByProjectId(projectId)
            }
            // 最后删除项目本身
            projectDao.deleteByIds(projectIds)
            
            DeleteResult(isSuccess = true)
        } catch (e: Exception) {
            DeleteResult(isSuccess = false, errorMessage = e.message)
        }
    }

    /**
     * 标记项目为已删除状态（软删除）
     * @param projectIds 要标记为已删除的项目ID列表
     * @return 操作结果
     */
    suspend fun markProjectsAsDeleted(projectIds: List<Long>): DeleteResult {
        return try {
            projectDao.markAsDeleted(projectIds)
            DeleteResult(isSuccess = true)
        } catch (e: Exception) {
            DeleteResult(isSuccess = false, errorMessage = e.message)
        }
    }

    /**
     * 函数：updateProjectCounts
     * 说明：根据数据库中实际的defect和event数据更新项目的defect_count和event_count字段
     * - defect_count: 该项目下所有缺陷的数量（优先使用defect表，如果为空则从ProjectDetail解析）
     * - event_count: 该项目下的事件列表数量（直接统计event表记录数）
     * 
     * @param projectUid 项目唯一标识
     */
    suspend fun updateProjectCounts(projectUid: String) {
        try {
            // 获取项目实体
            val project = projectDao.getByUid(projectUid) ?: return
            
            // 统计该项目下的缺陷数量
            val defects = defectRepository.getDefectsByProjectUid(projectUid).first()
            var defectCount = defects.size
            
            // 如果defect表中没有数据，尝试从ProjectDetail表解析历史缺陷数量
            if (defectCount == 0) {
                val projectDetail = projectDetailDao.getByProjectUid(projectUid)
                if (projectDetail != null) {
                    defectCount = countHistoryDefectsFromJson(projectDetail.rawJson)
                    Log.d("ProjectRepository", "Using history defect count from ProjectDetail for $projectUid: $defectCount")
                }
            }
            
            // 统计该项目下的事件列表数量（直接统计event表记录数）
            val eventCount = eventRepository.getEventsByProjectUid(projectUid).first().size
            
            // 更新项目的计数字段
            projectDao.updateCounters(project.projectId, defectCount, eventCount)
            
            Log.d("ProjectRepository", "Updated project counts for $projectUid: defects=$defectCount, events=$eventCount")
        } catch (e: Exception) {
            Log.e("ProjectRepository", "Failed to update project counts for $projectUid: ${e.message}", e)
        }
    }

    /**
     * 函数：updateAllProjectCounts
     * 说明：更新所有项目的defect_count和event_count字段
     */
    suspend fun updateAllProjectCounts() {
        try {
            val projects = projectDao.getAll().first()
            projects.forEach { project ->
                project.projectUid?.let { uid ->
                    updateProjectCounts(uid)
                }
            }
            Log.d("ProjectRepository", "Updated counts for ${projects.size} projects")
        } catch (e: Exception) {
            Log.e("ProjectRepository", "Failed to update all project counts: ${e.message}", e)
        }
    }
// 移除过早的类结束符，使后续私有方法位于类内部
// }

/**
     * 工具：从 JSON 文本中取出首个对象。
     * 若为数组，取第一个对象；若为对象，返回对象；若含 data/item/project 包裹，则深入取其对象。
     * @param json 原始响应 JSON 文本
     * @return Map<String, Any?> 首个对象的键值映射；失败时返回空 Map
     */
    private fun parseFirstObject(json: String): Map<String, Any?> {
        val trimmed = json.trim()
        return try {
            if (trimmed.startsWith("{")) {
                @Suppress("UNCHECKED_CAST")
                val obj = gson.fromJson(trimmed, Map::class.java) as Map<String, Any?>
                // 尝试 data/item/project 包裹
                val nested = listOf("data", "item", "project").firstNotNullOfOrNull { key ->
                    (obj[key] as? Map<String, Any?>)
                }
                nested ?: obj
            } else if (trimmed.startsWith("[")) {
                @Suppress("UNCHECKED_CAST")
                val arr = gson.fromJson(trimmed, List::class.java) as List<Map<String, Any?>>
                arr.firstOrNull() ?: emptyMap()
            } else {
                emptyMap()
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * 工具：解析项目详情 JSON 并映射为 ProjectDetailEntity。
     * 兼容两类结构：
     * - 直接对象：{"name":..., "status":..., ...}
     * - 包裹对象：{"data": {...}} 或 {"item": {...}} 或 {"project": {...}}
     * 同时保留原始 JSON 到 raw_json 字段以便后续灵活使用。
     * @param json 原始响应 JSON 文本
     * @param projectId 本地项目 ID（用于一一关联）
     * @param projectUid 远端唯一标识（用于检索）
     */
    private fun mapDetailJsonToEntity(json: String, projectId: Long, projectUid: String): com.simsapp.data.local.entity.ProjectDetailEntity {
        val obj = parseFirstObject(json)
        fun s(key: String): String? = (obj[key] as? String)?.takeIf { it.isNotBlank() }
        fun n(key: String): Number? = obj[key] as? Number
        fun anyS(vararg keys: String): String? = keys.asSequence().mapNotNull { s(it) }.firstOrNull()
        fun anyN(vararg keys: String): Number? = keys.asSequence().mapNotNull { n(it) }.firstOrNull()

        val name = anyS("name", "projectName", "project_name")
        val status = anyS("status", "project_status")
        val start = anyS("startDate", "start_date", "project_start_at")
        val end = anyS("endDate", "end_date", "project_end_at", "project_last_update_at")
        val lastUpdate = anyS("project_last_update_at", "last_update_at", "updated_at")

        val startMillis = when {
            anyN("startDate", "start_date") != null -> anyN("startDate", "start_date")!!.toLong()
            start != null -> start.toLongOrNull() ?: parseDateToMillis(start)
            else -> null
        }
        val endMillis = when {
            anyN("endDate", "end_date", "project_end_at", "project_last_update_at") != null -> anyN("endDate", "end_date", "project_end_at", "project_last_update_at")!!.toLong()
            end != null -> end.toLongOrNull() ?: parseDateToMillis(end)
            else -> null
        }
        val lastUpdateMillis = when {
            anyN("project_last_update_at", "last_update_at", "updated_at") != null -> anyN("project_last_update_at", "last_update_at", "updated_at")!!.toLong()
            lastUpdate != null -> lastUpdate.toLongOrNull() ?: parseDateToMillis(lastUpdate)
            else -> null
        }

        return com.simsapp.data.local.entity.ProjectDetailEntity(
            projectId = projectId,
            projectUid = projectUid,
            name = name,
            status = status,
            startDate = startMillis,
            endDate = endMillis,
            lastUpdateAt = lastUpdateMillis,
            rawJson = json,
            lastFetchedAt = System.currentTimeMillis()
        )
    }

    // ... existing code ...
    // 新增：历史缺陷图片下载链接解析端点
    private val storageDownloadEndpoint: String =
        "storage/download/url"

    /**
     * 解析项目详情 JSON，遍历 history_defect_list：
     * - 若某条缺陷的 defect_pics 有值（字符串或字符串数组），调用存储解析接口换取下载链接
     * - 下载图片到本地缓存目录：files/history_defects/<projectUid>/<defectNo>/
     * - 命名保留远端文件名（若无法解析，回退为 index 序号并默认 .jpg 扩展名）
     * 线程：调用方位于 IO 调度器中
     */
    private suspend fun cacheHistoryDefectImages(projectUid: String, detailJson: String) {
        val obj = findHistoryDefectsObject(detailJson)
        val arr = obj.optJSONArray("history_defect_list") ?: return
        var touched = false
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val defectNo = item.optString("no").takeIf { it.isNotBlank() } ?: continue
            val pics = extractDefectPicIds(item)
            if (pics.isEmpty()) continue
            try {
                // 解析图片直链（可能返回一个或多个 URL）
                val resolveResp = api.resolveDownloadUrl(storageDownloadEndpoint, pics)
                if (!resolveResp.isSuccessful) {
                    Log.w("SIMS-SYNC", "resolve pics failed http=${resolveResp.code()} uid=$projectUid no=$defectNo")
                    continue
                }
                val body = resolveResp.body()?.string().orEmpty()
                val urls = parseUrlsFromResponse(body)
                if (urls.isEmpty()) continue

                // 目标目录：app/files/history_defects/<projectUid>/<defectNo>/
                val targetDir = File(appContext.filesDir, "history_defects/${projectUid}/${sanitize(defectNo)}")
                if (!targetDir.exists()) targetDir.mkdirs()

                // 下载每个 URL 文件
                urls.forEachIndexed { index, url ->
                    try {
                        val resp = api.downloadRiskMatrixByUrl(url) // 复用通用 @GET(@Url)
                        if (!resp.isSuccessful) {
                            Log.w("SIMS-SYNC", "download pic failed ${resp.code()} | $url")
                            return@forEachIndexed
                        }
                        val rb = resp.body() ?: return@forEachIndexed
                        writeResponseBodyToFile(url, rb, targetDir, index)
                        touched = true
                    } catch (e: Exception) {
                        Log.e("SIMS-SYNC", "download pic err ${e.message} | $url", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("SIMS-SYNC", "resolve pics err uid=$projectUid no=$defectNo: ${e.message}", e)
            }
        }
        // 若有图片落地或为确保 UI 可刷新，更新一次 ProjectDetail 的 lastFetchedAt 以触发 Room 观察者
        try {
            val detail = projectDetailDao.getByProjectUid(projectUid)
            if (detail != null) {
                projectDetailDao.update(detail.copy(lastFetchedAt = System.currentTimeMillis()))
            }
        } catch (e: Exception) {
            Log.w("SIMS-SYNC", "bump lastFetchedAt failed for uid=$projectUid: ${e.message}")
        }
    }

    /**
     * 从 JSON 中定位包含 history_defect_list 的对象（兼容 data/item/result 包裹）。
     */
    private fun findHistoryDefectsObject(raw: String): JSONObject {
        val trimmed = raw.trim()
        return try {
            if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                if (root.has("history_defect_list")) return root
                val wrappers = listOf("data", "item", "result")
                for (k in wrappers) {
                    val child = root.optJSONObject(k)
                    if (child != null && child.has("history_defect_list")) return child
                }
                root
            } else if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                arr.optJSONObject(0) ?: JSONObject()
            } else JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }
    }

    /**
     * 提取 defect_pics 字段为文件ID列表，兼容：
     * - 字符串 "id1,id2" 或单一 "id"
     * - 字符串数组 ["id1","id2"]
     * - 对象数组 [{"id":"..."}]（保底）
     */
    private fun extractDefectPicIds(item: JSONObject): List<String> {
        val v = item.opt("defect_pics") ?: return emptyList()
        return when (v) {
            is JSONArray -> {
                val out = mutableListOf<String>()
                for (i in 0 until v.length()) {
                    val elem = v.opt(i)
                    when (elem) {
                        is String -> out += elem
                        is JSONObject -> elem.optString("id").takeIf { it.isNotBlank() }?.let(out::add)
                    }
                }
                out.filter { it.isNotBlank() }.distinct()
            }
            is String -> v.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct()
            is JSONObject -> listOfNotNull(v.optString("id").takeIf { it.isNotBlank() })
            else -> emptyList()
        }
    }

    /**
     * 从解析接口响应中提取所有 URL：
     * - 若响应为直接 URL 字符串，返回单元素列表
     * - 若为 JSON，优先读取 data 数组中每个元素的 url 字段
     */
    private fun parseUrlsFromResponse(body: String): List<String> {
        val trimmed = body.trim()
        if (trimmed.startsWith("http")) return listOf(trimmed.replace("\"", "").trim())
        return try {
            val obj = gson.fromJson(trimmed, Map::class.java)
            val data = obj["data"] as? List<*>
            val urls = data?.mapNotNull { (it as? Map<*, *>)?.get("url") as? String } ?: emptyList()
            urls
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 将响应体写入本地文件。
     * 文件名优先取 URL 路径中的文件名；若无法解析则使用 index 占位并缺省为 .jpg。
     */
    private fun writeResponseBodyToFile(url: String, body: okhttp3.ResponseBody, dir: File, index: Int): File {
        val fileName = run {
            try {
                val path = java.net.URI(url).path ?: ""
                val name = path.substringAfterLast('/').ifBlank { "" }
                if (name.isNotBlank()) name else "img_${index}.jpg"
            } catch (_: Exception) {
                "img_${index}.jpg"
            }
        }
        val target = File(dir, sanitize(fileName))
        body.byteStream().use { input ->
            FileOutputStream(target).use { out ->
                val buf = ByteArray(8 * 1024)
                while (true) {
                    val r = input.read(buf)
                    if (r == -1) break
                    out.write(buf, 0, r)
                }
                out.flush()
            }
        }
        return target
    }

    /** 简单清洗文件名中的非法字符。 */
    private fun sanitize(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    /**
     * 统计原始 JSON 中 history_defect_list 的条数。
     * @param raw 详情 JSON 字符串
     * @return 列表长度；若解析失败或字段不存在则返回 0
     */
    private fun countHistoryDefectsFromJson(raw: String): Int {
        return try {
            val obj = findHistoryDefectsObject(raw)
            val arr = obj.optJSONArray("history_defect_list")
            arr?.length() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 缓存历史缺陷数据到defect表
     * 
     * @param projectId 项目在本地数据库中的ID
     * @param projectUid 项目唯一标识
     * @param detailJson 原始JSON数据
     */
    private suspend fun cacheHistoryDefectsToDatabase(projectId: Long, projectUid: String, detailJson: String) {
        if (detailJson.isBlank()) return
        try {
            val obj = findHistoryDefectsObject(detailJson)
            val arr = obj.optJSONArray("history_defect_list") ?: return
            val defects = mutableListOf<com.simsapp.data.local.entity.DefectEntity>()
            
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val no = item.optString("no").takeIf { it.isNotBlank() } ?: continue
                val risk = item.optString("risk_rating").takeIf { it.isNotBlank() } ?: ""
                val status = item.optString("status", "OPEN")
                
                // 从本地缓存目录读取图片缩略图路径
                val dir = File(appContext.filesDir, "history_defects/${projectUid}/${sanitize(no)}")
                val images = if (dir.exists()) {
                    dir.listFiles()
                        ?.sortedBy { it.name }
                        ?.map { it.absolutePath }
                        ?: emptyList()
                } else emptyList()
                
                // 检查是否已存在相同的defect记录
                val existingDefect = defectRepository.getDefectByProjectUidAndDefectNo(projectUid, no)
                
                // 如果存在则更新，不存在则创建新记录
                val defect = existingDefect?.copy(
                    riskRating = risk,
                    status = status,
                    images = images
                ) ?: com.simsapp.data.local.entity.DefectEntity(
                    projectId = projectId,
                    projectUid = projectUid,
                    defectNo = no,
                    riskRating = risk,
                    status = status,
                    images = images
                )
                
                defects.add(defect)
            }
            
            // 批量插入或更新defect记录
            if (defects.isNotEmpty()) {
                defectRepository.upsertAll(defects)
                Log.i("SIMS-SYNC", "Cached ${defects.size} defects for project uid=$projectUid")
            }
        } catch (e: Exception) {
            // 记录错误但不中断流程
            Log.e("SIMS-SYNC", "Cache defects error uid=$projectUid, ${e.message}", e)
        }
    }
}