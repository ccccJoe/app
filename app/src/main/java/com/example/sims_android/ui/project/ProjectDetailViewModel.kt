package com.simsapp.ui.project

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simsapp.data.local.dao.ProjectDetailDao
import com.simsapp.data.local.dao.ProjectDao
import com.simsapp.data.local.dao.EventDao
import com.simsapp.data.local.entity.DefectEntity
import com.simsapp.data.repository.DefectRepository
import com.simsapp.data.repository.EventRepository
import com.simsapp.data.repository.EventUploadItem
import com.simsapp.utils.EventDirectoryMigrationUtil
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import android.util.Log

/**
 * 数据模型：历史缺陷列表项，包含缺陷编号与风险评级与本地图片缩略图路径。
 */
data class HistoryDefectItem(
    val no: String,
    val riskRating: String,
    val images: List<String> = emptyList(),
    val eventCount: Int = 0
)

/**
 * File: ProjectDetailViewModel.kt
 * Description: ViewModel for Project Detail page, orchestrating event sync/upload actions and providing historical defect list from cached project detail JSON.
 * Author: SIMS Team
 */

/**
 * ProjectDetailViewModel
 *
 * Responsibilities:
 * - Handle user actions from ProjectDetailScreen
 * - Aggregate upload results for multiple selected events
 * - Bridge UI (Compose) and Repository (EventRepository)
 * - Provide historical defect list parsed from ProjectDetailEntity.rawJson by project_uid
 *
 * Design Notes:
 * - Keep ViewModel thin; heavy IO work is done in repository/DAO
 * - Expose simple flows for UI layer to collect
 */
@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val projectDetailDao: ProjectDetailDao,
    private val projectDao: ProjectDao,
    private val defectRepository: DefectRepository,
    private val eventDao: EventDao,
    private val gson: Gson,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    // 记住当前页面的 projectUid，便于持久化排序时使用
    private var currentProjectUid: String? = null

    // ---------------- Historical Defects State ----------------
    /**
     * Backing state for historical defects, parsed from cached project detail JSON.
     * Each item contains "no", "risk_rating" and up to several local thumbnail paths.
     */
    private val _historyDefects = MutableStateFlow<List<HistoryDefectItem>>(emptyList())
    /** Public read-only flow for UI consumption. */
    val historyDefects: StateFlow<List<HistoryDefectItem>> = _historyDefects

    // ---------------- Events State ----------------
    /**
     * Backing state for events list, loaded from Room database by project_uid.
     * Each item contains event data for display in the event list.
     */
    private val _events = MutableStateFlow<List<EventItem>>(emptyList())
    /** Public read-only flow for UI consumption. */
    val events: StateFlow<List<EventItem>> = _events

    // ---------------- Project Description State ----------------
    /**
     * Backing state for project description, parsed from project_attr.project_description.
     */
    private val _projectDescription = MutableStateFlow<String>("")
    /** Public read-only flow for UI consumption. */
    val projectDescription: StateFlow<String> = _projectDescription

    /**
     * 数据类：批量事件同步进度。
     * 描述：用于 UI 展示“已完成/总共”的整体进度和运行状态。
     * - completed: 已成功上传到 OSS 的事件数量
     * - total: 参与同步的事件总数（有效打包后的数量）
     * - running: 是否处于同步进行中
     */
    data class SyncProgress(val completed: Int, val total: Int, val running: Boolean)

    /**
     * 事件同步进度状态流（只读暴露给 UI）。
     * 说明：uploadSelectedEvents 调用期间会实时更新该值。
     */
    private val _eventSyncProgress = MutableStateFlow(SyncProgress(0, 0, false))
    val eventSyncProgress: StateFlow<SyncProgress> = _eventSyncProgress

    /**
     * 主动加载一次：按 projectUid 拉取 Room 中的 ProjectDetail.rawJson 并解析，再读取本地图片目录。
     */
    fun loadDefectsByProjectUid(projectUid: String?) {
        if (projectUid.isNullOrBlank()) return
        currentProjectUid = projectUid
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                val detail = projectDetailDao.getByProjectUid(projectUid)
                val raw = detail?.rawJson.orEmpty()
                // Parse project description
                val description = parseProjectDescription(raw)
                _projectDescription.value = description
                // 先解析历史缺陷
                val parsed = parseHistoryDefects(projectUid, raw)
                // 为避免页面初次进入时的排序闪烁，按数据库的 sort_order 进行初始排序
                val defects = defectRepository.getDefectsByProjectUid(projectUid).first()
                val position = defects.mapIndexed { index, d -> d.defectNo to index }.toMap()
                parsed.sortedBy { position[it.no] ?: Int.MAX_VALUE }
            } catch (e: Exception) {
                Log.e("ProjectDetailViewModel", "Failed to load defects for project $projectUid: ${e.message}", e)
                emptyList()
            }
            _historyDefects.value = result
        }
        // 订阅这条 detail 的变化（例如：同步完成后图片下载落地 -> 仓库更新 lastFetchedAt）
        observeDetailAndRefresh(projectUid)
    }

    /**
     * 主动加载一次：按 projectUid 从 Room 数据库中加载 event 列表。
     * 
     * @param projectUid 项目唯一标识，用于筛选对应的 event 记录
     */
    fun loadEventsByProjectUid(projectUid: String?) {
        if (projectUid.isNullOrBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 订阅该项目的未同步 event 记录
                eventRepository.getUnsyncedEventsByProjectUid(projectUid).collect { eventEntities ->
                    val eventItems = eventEntities.map { entity ->
                        EventItem(
                            id = entity.eventId.toString(), // 使用eventId作为主键标识，用于数据库查询
                            uid = entity.uid, // 添加uid字段，用于文件系统目录定位和删除操作
                            title = if (entity.content.isNotBlank()) {
                                entity.content.take(50) + if (entity.content.length > 50) "..." else ""
                            } else "Event ${entity.eventId}",
                            location = entity.location ?: "",
                            defectNo = entity.defectNos.joinToString(", "),
                            date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(entity.lastEditTime))
                            ,
                            riskRating = entity.riskLevel
                        )
                    }
                    _events.value = eventItems
                }
            } catch (e: Exception) {
                Log.e("ProjectDetailVM", "Failed to load events for projectUid=$projectUid: ${e.message}", e)
                _events.value = emptyList()
            }
        }
    }

    /**
     * 函数：获取非完成状态项目列表 Flow
     * 说明：用于在项目详情页事件列表下的同步入口弹出项目选择底部弹窗。
     * 返回：Flow<List<ProjectEntity>>
     */
    fun getNotFinishedProjects(): Flow<List<com.simsapp.data.local.entity.ProjectEntity>> =
        projectDao.getNotFinishedProjects()

    // 注意：重复的 observeDetailAndRefresh 已合并至文件底部的统一实现（同时刷新缺陷与分组）。

    /**
     * Parse history_defect_list from raw JSON.
     * Supports direct object, nested under common wrappers (data/item/result), or array root.
     * Returns a list of HistoryDefectItem with basic fields and local thumbnail paths.
     *
     * @param projectUid The project uid used to locate cached images directory.
     * @param raw The raw JSON string from ProjectDetailEntity.rawJson.
     */
    private suspend fun parseHistoryDefects(projectUid: String, raw: String): List<HistoryDefectItem> {
        if (raw.isBlank()) return emptyList()
        return try {
            val obj = findDefectsObject(raw)
            val arr = obj.optJSONArray("history_defect_list") ?: return emptyList()
            val out = mutableListOf<HistoryDefectItem>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val no = item.optString("no").takeIf { it.isNotBlank() } ?: continue
                val risk = item.optString("risk_rating").takeIf { it.isNotBlank() } ?: ""
                // 从本地缓存目录读取图片缩略图路径：files/history_defects/<projectUid>/<defectNo>/
                val dir = File(appContext.filesDir, "history_defects/${projectUid}/${sanitize(no)}")
                val images = if (dir.exists()) {
                    dir.listFiles()
                        ?.sortedBy { it.name }
                        ?.map { it.absolutePath }
                        // 移除 .take(3) 限制，支持显示所有图片并左右滑动查看
                        ?: emptyList()
                } else emptyList()
                
                // 从数据库获取该缺陷的event_count
                val defectEntity = defectRepository.getDefectByProjectUidAndDefectNo(projectUid, no)
                val eventCount = defectEntity?.eventCount ?: 0
                
                out += HistoryDefectItem(no = no, riskRating = risk, images = images, eventCount = eventCount)
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Locate the JSON object containing history_defect_list.
     * Tries the root, then common wrappers, and finally the first element if root is an array.
     */
    private fun findDefectsObject(raw: String): JSONObject {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                val wrappers = listOf("data", "item", "result")
                for (key in wrappers) {
                    val child = root.optJSONObject(key)
                    if (child != null && child.has("history_defect_list")) return child
                }
                root
            }
            trimmed.startsWith("[") -> {
                val arr = JSONArray(trimmed)
                arr.optJSONObject(0) ?: JSONObject()
            }
            else -> JSONObject()
        }
    }

    /**
     * 简单清洗文件名中的非法字符，保证与缓存目录匹配。
     */
    private fun sanitize(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    // ---------------- Event Upload (existing) ----------------
    /**
     * 函数：uploadSelectedEvents
     * 说明：批量上传选中的事件到云端
     * 实现完整的同步流程：打包 -> 生成hash -> 调用接口 -> 上传到OSS -> 轮询状态
     * 
     * @param context Android上下文
     * @param eventUids 事件UID列表
     * @param projectUid 当前项目的UID
     * @return Pair<Boolean, String> 成功标志和消息
     */
    suspend fun uploadSelectedEvents(context: Context, eventUids: List<String>, projectUid: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        // 初始化：根据选择的数量设置总数（后续以有效打包数为准），标记为进行中
        _eventSyncProgress.value = SyncProgress(completed = 0, total = eventUids.size, running = true)
        // 首先执行事件目录迁移，确保所有事件都有对应的目录结构
        val migrationUtil = EventDirectoryMigrationUtil(eventDao, gson)
        val migrationResult = migrationUtil.migrateEventDirectories(context)
        
        android.util.Log.d("ProjectDetailViewModel", "Migration completed: ${migrationResult.successCount} created, ${migrationResult.skippedCount} skipped, ${migrationResult.failureCount} failed")
        
        if (!migrationResult.isSuccessful) {
            android.util.Log.w("ProjectDetailViewModel", "Migration had failures: ${migrationResult.errors}")
        }
        val results = mutableListOf<String>()
        
        android.util.Log.d("ProjectDetailViewModel", "Starting batch event upload")
        android.util.Log.d("ProjectDetailViewModel", "Event UIDs: $eventUids")
        android.util.Log.d("ProjectDetailViewModel", "Project UID: $projectUid")
        
        if (eventUids.isEmpty()) {
            android.util.Log.w("ProjectDetailViewModel", "No events selected for sync")
            return@withContext false to "No events selected for sync"
        }
        
        try {
            // Step 1: 为所有事件创建压缩包并收集hash信息
            val eventPackages = mutableListOf<EventUploadItem>()
            android.util.Log.d("ProjectDetailViewModel", "Step 1: Creating zip packages for ${eventUids.size} events")
            
            for (uid in eventUids) {
                android.util.Log.d("ProjectDetailViewModel", "Processing event UID: $uid")
                
                // 检查事件目录是否存在
                val eventDir = File(context.filesDir, "events/$uid")
                android.util.Log.d("ProjectDetailViewModel", "Checking event directory: ${eventDir.absolutePath}")
                android.util.Log.d("ProjectDetailViewModel", "Directory exists: ${eventDir.exists()}")
                
                if (eventDir.exists()) {
                    val files = eventDir.listFiles()
                    android.util.Log.d("ProjectDetailViewModel", "Directory contains ${files?.size ?: 0} files:")
                    files?.forEach { file ->
                        android.util.Log.d("ProjectDetailViewModel", "  - ${file.name} (${if (file.isDirectory) "DIR" else "FILE"}, ${file.length()} bytes)")
                    }
                } else {
                    android.util.Log.e("ProjectDetailViewModel", "Event directory does not exist: ${eventDir.absolutePath}")
                    results += "event=$uid | FAIL | Directory not found: ${eventDir.absolutePath}"
                    continue
                }

                // 在打包前覆盖 meta.json 的目标项目UID，并写入数据库风险与核心字段
                run {
                    val (okProj, msgProj) = eventRepository.overrideMetaProjectUid(appContext, uid, projectUid)
                    android.util.Log.d("ProjectDetailViewModel", "overrideMetaProjectUid($uid) -> $okProj | $msgProj")

                    val (okRisk, msgRisk) = eventRepository.updateMetaRiskFromDb(appContext, uid)
                    android.util.Log.d("ProjectDetailViewModel", "updateMetaRiskFromDb($uid) -> $okRisk | $msgRisk")

                    val (okCore, msgCore) = eventRepository.updateMetaCoreFromDb(appContext, uid)
                    android.util.Log.d("ProjectDetailViewModel", "updateMetaCoreFromDb($uid) -> $okCore | $msgCore")
                }
                
                val zipResult = eventRepository.createEventZip(context, uid)
                if (!zipResult.first) {
                    android.util.Log.e("ProjectDetailViewModel", "Failed to create zip for event $uid: ${zipResult.second}")
                    results += "event=$uid | FAIL | Create zip failed: ${zipResult.second}"
                    continue
                }
                
                val packageHash = zipResult.second
                android.util.Log.d("ProjectDetailViewModel", "Created zip for event $uid with hash: $packageHash")
                eventPackages.add(
                    EventUploadItem(
                        eventUid = uid,
                        eventPackageHash = packageHash,
                        eventPackageName = "${uid}.zip"
                    )
                )
                results += "event=$uid | SUCCESS | Zip created with hash: $packageHash"
            }
            
            if (eventPackages.isEmpty()) {
                android.util.Log.e("ProjectDetailViewModel", "No valid event packages created")
                // 无可用包，结束并清空进度
                _eventSyncProgress.value = SyncProgress(completed = 0, total = 0, running = false)
                return@withContext false to "No valid event packages created"
            }
            // 更新总数为有效打包后的数量，保持进行中
            _eventSyncProgress.value = SyncProgress(completed = 0, total = eventPackages.size, running = true)
            
            android.util.Log.d("ProjectDetailViewModel", "Step 2: Calling create_event_upload API with ${eventPackages.size} packages")
            
            // 详细打印每个事件包的信息
            android.util.Log.d("ProjectDetailViewModel", "Event packages details:")
            eventPackages.forEachIndexed { index, pkg ->
                android.util.Log.d("ProjectDetailViewModel", "Package $index: eventUid=${pkg.eventUid}, hash=${pkg.eventPackageHash}, name=${pkg.eventPackageName}")
            }
            
            // Step 2: 调用 create_event_upload 接口
            val taskUid = "batch_task_${System.currentTimeMillis()}"
            android.util.Log.d("ProjectDetailViewModel", "Generated task UID: $taskUid")
            android.util.Log.d("ProjectDetailViewModel", "About to call createEventUpload with ${eventPackages.size} packages")
            
            val createResult = eventRepository.createEventUpload(taskUid, projectUid, eventPackages)
            
            android.util.Log.d("ProjectDetailViewModel", "Create event upload API result: success=${createResult.first}")
            
            if (!createResult.first || createResult.second == null) {
                android.util.Log.e("ProjectDetailViewModel", "Create event upload failed")
                return@withContext false to "Create event upload failed"
            }
            
            val uploadResponse = createResult.second!!
            android.util.Log.d("ProjectDetailViewModel", "Upload response: success=${uploadResponse.success}, message=${uploadResponse.message}")
            android.util.Log.d("ProjectDetailViewModel", "Upload response data count: ${uploadResponse.data?.size ?: 0}")
            
            if (!uploadResponse.success || uploadResponse.data.isNullOrEmpty()) {
                android.util.Log.e("ProjectDetailViewModel", "Create event upload response invalid: ${uploadResponse.message}")
                return@withContext false to "Create event upload response invalid: ${uploadResponse.message}"
            }
            
            results += "API | SUCCESS | Got ${uploadResponse.data.size} upload tickets"
            
            android.util.Log.d("ProjectDetailViewModel", "Step 3: Uploading ${uploadResponse.data.size} packages to OSS")
            
            // Step 4: 根据返回的data列表，匹配hash并上传对应的压缩包
            var uploadSuccessCount = 0
            val successfulEventUids = mutableListOf<String>()
            
            android.util.Log.d("ProjectDetailViewModel", "Hash matching verification:")
            android.util.Log.d("ProjectDetailViewModel", "Local packages count: ${eventPackages.size}")
            for (pkg in eventPackages) {
                android.util.Log.d("ProjectDetailViewModel", "Local package - Event: ${pkg.eventUid}, Hash: ${pkg.eventPackageHash}")
            }
            
            android.util.Log.d("ProjectDetailViewModel", "Response items count: ${uploadResponse.data.size}")
            for (item in uploadResponse.data) {
                android.util.Log.d("ProjectDetailViewModel", "Response item - Event: ${item.eventUid}, Hash: ${item.eventPackageHash}")
            }
            
            for (responseItem in uploadResponse.data) {
                android.util.Log.d("ProjectDetailViewModel", "Processing response item with hash: ${responseItem.eventPackageHash}")
                
                val matchingPackage = eventPackages.find { it.eventPackageHash == responseItem.eventPackageHash }
                if (matchingPackage != null) {
                    android.util.Log.d("ProjectDetailViewModel", "Found matching package for event: ${matchingPackage.eventUid}")
                    android.util.Log.d("ProjectDetailViewModel", "Hash match confirmed: ${matchingPackage.eventPackageHash} == ${responseItem.eventPackageHash}")
                    
                    // 将票据数据转换为Map格式
                    val ticketData = mapOf(
                        "host" to responseItem.ticket.host,
                        "dir" to responseItem.ticket.dir,
                        "file_id" to responseItem.ticket.fileId,
                        "policy" to responseItem.ticket.policy,
                        "signature" to responseItem.ticket.signature,
                        "accessid" to responseItem.ticket.accessId
                    )
                    
                    android.util.Log.d("ProjectDetailViewModel", "Uploading to OSS with ticket data: $ticketData")
                    
                    // 使用票据信息上传压缩包
                    val uploadResult = eventRepository.uploadEventZipWithTicket(
                        context, 
                        matchingPackage.eventUid, 
                        matchingPackage.eventPackageHash, 
                        ticketData
                    )
                    
                    android.util.Log.d("ProjectDetailViewModel", "OSS upload result for ${matchingPackage.eventUid}: success=${uploadResult.first}")
                    
                    if (uploadResult.first) {
                        uploadSuccessCount++
                        successfulEventUids.add(matchingPackage.eventUid)
                        results += "event=${matchingPackage.eventUid} | SUCCESS | Uploaded to OSS"
                        // 上传成功后更新“已完成”数量
                        _eventSyncProgress.value = _eventSyncProgress.value.copy(completed = uploadSuccessCount)
                    } else {
                        android.util.Log.e("ProjectDetailViewModel", "OSS upload failed for ${matchingPackage.eventUid}: ${uploadResult.second}")
                        results += "event=${matchingPackage.eventUid} | FAIL | Upload to OSS failed: ${uploadResult.second}"
                    }
                } else {
                    android.util.Log.w("ProjectDetailViewModel", "No matching package found for hash: ${responseItem.eventPackageHash}")
                    android.util.Log.w("ProjectDetailViewModel", "Available local hashes: ${eventPackages.map { it.eventPackageHash }}")
                    results += "WARN | No matching package found for hash: ${responseItem.eventPackageHash}"
                }
            }
            
            android.util.Log.d("ProjectDetailViewModel", "Upload completed: $uploadSuccessCount/${uploadResponse.data.size} successful")
            
            // Step 5: 轮询查询上传状态
            if (uploadSuccessCount > 0) {
                android.util.Log.d("ProjectDetailViewModel", "Starting status polling...")
                results += "POLLING | Starting status check..."
                var pollCount = 0
                val maxPollCount = 15 // 最多轮询15次（批量同步时间可能较长）
                val pollInterval = 3000L // 每3秒轮询一次
                
                while (pollCount < maxPollCount) {
                    kotlinx.coroutines.delay(pollInterval)
                    val statusResult = eventRepository.noticeEventUploadSuccess(taskUid)
                    
                    if (statusResult.first) { // 请求成功
                        if (statusResult.second) { // 任务完成
                            results += "POLLING | SUCCESS | All events synced to cloud"
                            // 标记进度完成
                            _eventSyncProgress.value = _eventSyncProgress.value.copy(running = false)
                            // 批量成功后将所有成功上传的事件标记为已同步
                            if (successfulEventUids.isNotEmpty()) {
                                try {
                                    eventRepository.markEventsSynced(successfulEventUids)
                                } catch (markErr: Exception) {
                                    android.util.Log.w("ProjectDetailViewModel", "Failed to mark events synced: ${markErr.message}")
                                }
                            }
                            return@withContext true to results.joinToString(separator = "\n")
                        } else {
                            results += "POLLING | PROGRESS | Poll attempt ${pollCount + 1}/$maxPollCount"
                        }
                    } else {
                        results += "POLLING | WARN | Poll request failed, continuing..."
                    }
                    
                    pollCount++
                }
                
                if (pollCount >= maxPollCount) {
                    results += "POLLING | TIMEOUT | Please check sync status later"
                    _eventSyncProgress.value = _eventSyncProgress.value.copy(running = false)
                    return@withContext false to results.joinToString(separator = "\n")
                }
            }
            // 若没有成功上传的项，也结束进度
            _eventSyncProgress.value = _eventSyncProgress.value.copy(running = false)
            
        } catch (e: Exception) {
            results += "ERROR | Batch sync failed: ${e.message}"
            _eventSyncProgress.value = _eventSyncProgress.value.copy(running = false)
            return@withContext false to results.joinToString(separator = "\n")
        }
        
        return@withContext true to results.joinToString(separator = "\n")
    }

    // ---------------- 新增：项目详情分组展示所需的数据模型与状态流 ----------------
    /**
     * ProjectInfo 分组数据模型
     * 用于在 ProjectInfoScreen 中展示键值对信息。
     */
    data class KeyValueItem(val key: String, val value: String)

    /** 分组：标题 + 多个键值对 */
    data class DetailGroup(val title: String, val items: List<KeyValueItem>)

    /** 详情分组状态（只读暴露给 UI） */
    private val _detailGroups = MutableStateFlow<List<DetailGroup>>(emptyList())
    val detailGroups: StateFlow<List<DetailGroup>> = _detailGroups

    /** 顶部头部键值（Inspector、Project No、Type Of Inspection、Project Description） */
    private val _headerItems = MutableStateFlow<List<KeyValueItem>>(emptyList())
    val headerItems: StateFlow<List<KeyValueItem>> = _headerItems

    /**
     * 项目状态（用于右侧状态标签显示）
     * 暴露为驼峰形式的文案，例如："Finished"、"Collecting"。
     */
    private val _projectStatus = MutableStateFlow("")
    val projectStatus: StateFlow<String> = _projectStatus

    /**
     * 项目状态对应的标签背景色（十六进制字符串，如 "#2E5EA3"）。
     * UI 层将解析为 Compose Color 用于渲染。
     */
    private val _projectStatusColorHex = MutableStateFlow("#2E5EA3")
    val projectStatusColorHex: StateFlow<String> = _projectStatusColorHex

    /**
     * 函数：根据 projectUid 主动加载一次详情分组信息。
     * - 从 Room 的 ProjectDetailDao 取 rawJson
     * - 解析为分组结构并更新状态流
     * - 同时开始订阅该条记录的变化，以便同步后 UI 自动刷新
     * @param projectUid 远端唯一标识
     */
    fun loadProjectInfoByProjectUid(projectUid: String?) {
        if (projectUid.isNullOrBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val groups = try {
                val detail = projectDetailDao.getByProjectUid(projectUid)
                val raw = detail?.rawJson.orEmpty()
                // 解析顶部头部信息与项目状态（将状态转为驼峰并映射颜色）
                val (header, statusRaw) = parseHeaderItems(raw)
                _headerItems.value = header
                _projectStatus.value = toCamelCaseStatus(statusRaw)
                _projectStatusColorHex.value = mapStatusColorHex(statusRaw)
                parseDetailGroups(raw)
            } catch (e: Exception) {
                Log.e("ProjectDetailViewModel", "Failed to load project info groups for $projectUid: ${e.message}", e)
                emptyList()
            }
            _detailGroups.value = groups
        }
        observeDetailAndRefresh(projectUid)
    }

    /**
     * 解析详情为分组键值列表，兼容常见字段命名与包裹结构。
     * @param raw JSON 字符串
     * @return 分组详情列表，每个分组含标题与键值对列表
     */
    private fun parseDetailGroups(raw: String): List<DetailGroup> {
        if (raw.isBlank()) return emptyList()
        return try {
            val data = parseRootObject(raw)
            val out = mutableListOf<DetailGroup>()
            val projectAttr = data.optJSONObject("project_attr")
            val relations = data.optJSONObject("project_ralations")
            val masterDatas = relations?.optJSONArray("master_datas")
            val master0 = masterDatas?.optJSONObject(0)

            // local helper: add formatted key with blank when null
            fun add(items: MutableList<KeyValueItem>, rawKey: String, rawValue: String?) {
                val label = formatKeyLabel(rawKey)
                // ... existing code ...
                items += KeyValueItem(label, sanitizeDisplayString(rawValue))
             }

            // 1) 顶部信息由 Header 渲染，不作为分组

            // 2) Client Info（标签统一为 Name / Code）
            val clientInfo = mutableListOf<KeyValueItem>()
            clientInfo += KeyValueItem("Name", sanitizeDisplayString(master0?.optString("client_name", "")))
            clientInfo += KeyValueItem("Code", sanitizeDisplayString(master0?.optString("client_code", "")))
            out += DetailGroup("Client Info", clientInfo)

            // 3) Facility Info（标签统一为 Name / Code）
            val facilityInfo = mutableListOf<KeyValueItem>()
            facilityInfo += KeyValueItem("Name", sanitizeDisplayString(master0?.optString("facility_name", "")))
            facilityInfo += KeyValueItem("Code", sanitizeDisplayString(master0?.optString("facility_code", "")))
            out += DetailGroup("Facility Info", facilityInfo)

            // 4) Function Area Info（标签统一为 Name / Code）
            val functionalAreaInfo = mutableListOf<KeyValueItem>()
            functionalAreaInfo += KeyValueItem("Name", sanitizeDisplayString(master0?.optString("functional_area_name", "")))
            functionalAreaInfo += KeyValueItem("Code", sanitizeDisplayString(master0?.optString("functional_area_code", "")))
            out += DetailGroup("Function Area Info", functionalAreaInfo)

            // 5) Purchase Orders - removed per requirement (skip adding)

            // 6) Asset (aggregate values; order: Code first, then Name; comma separated)
            // 6) Asset (aggregate values; order: Code first, then Name; comma separated)
            val assetCodes = mutableListOf<String>()
            val assetNames = mutableListOf<String>()
            if (masterDatas != null) {
                for (i in 0 until masterDatas.length()) {
                    val ms = masterDatas.optJSONObject(i) ?: continue
                    ms.optString("asset_code").let { s ->
                        if (!s.isNullOrBlank() && !s.equals("null", ignoreCase = true)) assetCodes += s
                    }
                    ms.optString("asset_name").let { s ->
                        if (!s.isNullOrBlank() && !s.equals("null", ignoreCase = true)) assetNames += s
                    }
                }
            }
            val assetItems = mutableListOf<KeyValueItem>()
            assetItems += KeyValueItem("Name", assetNames.joinToString(", "))
            assetItems += KeyValueItem("Code", assetCodes.joinToString(", "))
            out += DetailGroup("Asset Info", assetItems)

            // 7) Timeline（组装为区间字符串；移除 project_status）
            val timelineInfo = mutableListOf<KeyValueItem>()
            val projectStart = optLongOrNull(projectAttr, "project_start_at")
            val projectEnd = optLongOrNull(projectAttr, "project_end_at")
            val inspStart = optLongOrNull(projectAttr, "inspection_start_at")
            val inspEnd = optLongOrNull(projectAttr, "inspection_end_at")
            val reportStart = optLongOrNull(projectAttr, "report_start_at")
            val reportEnd = optLongOrNull(projectAttr, "report_end_at")
            fun rangeLabel(start: Long?, end: Long?): String {
                val s = formatEpoch(start)
                val e = formatEpoch(end)
                return when {
                    s.isNotEmpty() && e.isNotEmpty() -> "$s to $e"
                    s.isNotEmpty() -> s
                    e.isNotEmpty() -> e
                    else -> ""
                }
            }
            timelineInfo += KeyValueItem("Project Date", rangeLabel(projectStart, projectEnd))
            timelineInfo += KeyValueItem("Inspection Date", rangeLabel(inspStart, inspEnd))
            timelineInfo += KeyValueItem("Report Date", rangeLabel(reportStart, reportEnd))
            out += DetailGroup("Timeline", timelineInfo)

            // 8) Report Info
            val reportInfo = mutableListOf<KeyValueItem>()
            add(reportInfo, "report_name", projectAttr?.optString("report_name", ""))
            add(reportInfo, "report_revision", projectAttr?.optString("report_revision", ""))
            add(reportInfo, "report_review_by", projectAttr?.optString("report_review_by", ""))
            val reviewAt = optLongOrNull(projectAttr, "report_review_at")
            add(reportInfo, "report_review_at", formatEpoch(reviewAt))
            add(reportInfo, "report_approved_by", projectAttr?.optString("report_approved_by", ""))
            val approvedAt = optLongOrNull(projectAttr, "report_approved_at")
            add(reportInfo, "report_approved_at", formatEpoch(approvedAt))
            out += DetailGroup("Report Info", reportInfo)

            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Parse project description from raw JSON.
     * @param raw JSON string from ProjectDetailEntity.rawJson
     * @return Project description string, empty if not found
     */
    private fun parseProjectDescription(raw: String): String {
        if (raw.isBlank()) return ""
        return try {
            val data = parseRootObject(raw)
            val projectAttr = data.optJSONObject("project_attr")
            projectAttr?.optString("project_description", "") ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 解析顶部头部信息（Inspector、Project No、Type Of Inspection、Project Description）与项目状态
     * @param raw 原始 JSON 字符串
     * @return Pair<List<KeyValueItem>, String> -> 头部键值列表与项目状态
     */
    private fun parseHeaderItems(raw: String): Pair<List<KeyValueItem>, String> {
        if (raw.isBlank()) return emptyList<KeyValueItem>() to ""
        return try {
            val data = parseRootObject(raw)
            val projectAttr = data.optJSONObject("project_attr")
            val items = mutableListOf<KeyValueItem>()
            fun addItem(k: String, v: String?) {
                items += KeyValueItem(formatKeyLabel(k), sanitizeDisplayString(v))
            }
            addItem("inspector", projectAttr?.optString("reviewer", ""))
            addItem("project_no", data.optString("project_no", ""))
            addItem("type_of_inspection", projectAttr?.optString("type_of_inspection", ""))
            addItem("project_description", projectAttr?.optString("project_description", ""))
            val status = sanitizeDisplayString(data.optString("project_status", ""))
            items to status
        } catch (_: Exception) {
            emptyList<KeyValueItem>() to ""
        }
    }

    /**
     * 函数：toCamelCaseStatus
     * 说明：将原始状态字符串转换为驼峰形式（单词首字母大写，去除分隔符）。
     * 示例："FINISHED" -> "Finished"；"collecting_status" -> "CollectingStatus"。
     * @param status 原始状态文本
     * @return 驼峰形式文本
     */
    private fun toCamelCaseStatus(status: String): String {
        if (status.isBlank()) return ""
        val tokens = status
            .lowercase(java.util.Locale.getDefault())
            .split(Regex("""[\s_-]+"""))
            .filter { it.isNotBlank() }
        return tokens.joinToString(separator = "") { token ->
            token.replaceFirstChar { c -> c.titlecase(java.util.Locale.getDefault()) }
        }
    }

    /**
     * 函数：mapStatusColorHex
     * 说明：根据状态映射胶囊标签的背景色（十六进制字符串）。
     * - Collecting / Active -> 蓝色
     * - Finished / Completed / Done -> 绿色
     * - Paused / Suspended -> 橙色
     * - Inactive / Idle -> 灰色
     * - Delayed / Overdue / Failed -> 红色
     * 未匹配时返回默认蓝色。
     * @param status 原始状态文本
     * @return 背景色十六进制字符串，如 "#2E5EA3"
     */
    private fun mapStatusColorHex(status: String): String {
        val s = status.trim().uppercase(java.util.Locale.getDefault())
        return when (s) {
            "COLLECTING", "ACTIVE", "RUNNING" -> "#2E5EA3" // Primary Blue
            "FINISHED", "COMPLETED", "DONE" -> "#2E8F3A"   // Success Green
            "PAUSED", "SUSPENDED" -> "#E39A1A"               // Warning Orange
            "INACTIVE", "IDLE" -> "#9AA0A6"                  // Neutral Gray
            "DELAYED", "OVERDUE", "FAILED" -> "#D64545"     // Danger Red
            else -> "#2E5EA3"
        }
    }

    /**
     * 订阅指定 projectUid 的 ProjectDetail 记录变化，当 lastFetchedAt 更新或 rawJson 变化时，自动重新解析并刷新图片列表与详情分组。
     * 同时将historyDefectList缓存到本地defect数据库。
     * 同时订阅DefectEntity表的变化，确保event_count更新时UI能及时刷新。
     */
    private fun observeDetailAndRefresh(projectUid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // 订阅ProjectDetail表的变化
            projectDetailDao.observeByProjectUid(projectUid).collectLatest { detail ->
                if (detail == null) return@collectLatest
                val items = parseHistoryDefects(projectUid, detail.rawJson)
                // 同步数据库排序到 UI 展示顺序
                val defects = defectRepository.getDefectsByProjectUid(projectUid).first()
                val position = defects.mapIndexed { index, d -> d.defectNo to index }.toMap()
                _historyDefects.value = items.sortedBy { position[it.no] ?: Int.MAX_VALUE }
                // 新增：解析详情分组
                val groups = parseDetailGroups(detail.rawJson)
                _detailGroups.value = groups
                // 解析头部键值与项目状态
                val (header, statusRaw) = parseHeaderItems(detail.rawJson)
                _headerItems.value = header
                _projectStatus.value = toCamelCaseStatus(statusRaw)
                _projectStatusColorHex.value = mapStatusColorHex(statusRaw)
                // 新增：解析项目描述
                val description = parseProjectDescription(detail.rawJson)
                _projectDescription.value = description
                
                // 缓存historyDefectList到本地defect数据库
                cacheHistoryDefectsToLocalDb(projectUid, detail.projectId, detail.rawJson)
            }
        }
        
        // 同时订阅DefectEntity表的变化，确保event_count更新时UI能及时刷新
        viewModelScope.launch(Dispatchers.IO) {
            defectRepository.getDefectsByProjectUid(projectUid).collectLatest { defects ->
                // 当DefectEntity表有变化时，重新解析historyDefects以获取最新的event_count
                val detail = projectDetailDao.getByProjectUid(projectUid)
                if (detail != null) {
                    val items = parseHistoryDefects(projectUid, detail.rawJson)
                    val position = defects.mapIndexed { index, d -> d.defectNo to index }.toMap()
                    _historyDefects.value = items.sortedBy { position[it.no] ?: Int.MAX_VALUE }
                }
            }
        }
    }
    
    /**
     * 将historyDefectList缓存到本地defect数据库
     * 
     * @param projectUid 项目唯一标识
     * @param projectId 项目在本地数据库中的ID
     * @param rawJson 原始JSON数据
     */
    private suspend fun cacheHistoryDefectsToLocalDb(projectUid: String, projectId: Long, rawJson: String) {
        if (rawJson.isBlank()) return
        try {
            val obj = findDefectsObject(rawJson)
            val arr = obj.optJSONArray("history_defect_list") ?: return
            val defects = mutableListOf<DefectEntity>()
            
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val no = item.optString("no").takeIf { it.isNotBlank() } ?: continue
                val risk = item.optString("risk_rating").takeIf { it.isNotBlank() } ?: ""
                val status = item.optString("status", "OPEN")
                val type = item.optString("type", "")
                
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
                    type = type,
                    images = images
                ) ?: DefectEntity(
                    projectId = projectId,
                    projectUid = projectUid,
                    defectNo = no,
                    riskRating = risk,
                    status = status,
                    type = type,
                    images = images
                )
                
                defects.add(defect)
            }
            
            // 批量插入或更新defect记录
            if (defects.isNotEmpty()) {
                defectRepository.upsertAll(defects)
            }
        } catch (e: Exception) {
            // 记录错误但不中断流程
            e.printStackTrace()
        }
    }

    /**
     * 通用解析根对象，兼容不同包裹结构。
     * @param raw JSON 字符串
     * @return 根 JSONObject
     */
    private fun parseRootObject(raw: String): JSONObject {
        val json = JSONObject(raw)
        
        // 尝试常见包裹字段
        for (wrapperName in arrayOf("data", "result", "item", "content")) {
            val wrapper = json.optJSONObject(wrapperName)
            if (wrapper != null) return wrapper
        }
        
        // 没有包裹，直接返回根对象
        return json
    }

    /**
     * 获取第一个非空数组，按给定字段名顺序查找。
     * @param root 根 JSONObject
     * @param names 候选字段名列表
     * @return 第一个找到的非空数组，或 null
     */
    private fun firstJSONArray(root: JSONObject, vararg names: String): JSONArray? {
        for (name in names) {
            val arr = root.optJSONArray(name)
            if (arr != null && arr.length() > 0) return arr
        }
        return null
    }

    // 新增：安全获取 Long（处理 null）
    private fun optLongOrNull(obj: JSONObject?, key: String): Long? {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
        return obj.optLong(key)
    }
    
    // 新增：字段名格式化（去下划线并首字母大写）
    private fun formatKeyLabel(raw: String): String {
        if (raw.isBlank()) return ""
        return raw.split('_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(java.util.Locale.getDefault()) else c.toString() }
            }
    }
    
    // 新增：时间戳格式化（毫秒），null 或非法返回空字符串
    private fun formatEpoch(epochMs: Long?): String {
        if (epochMs == null || epochMs <= 0) return ""
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            sdf.format(java.util.Date(epochMs))
        } catch (_: Exception) { "" }
    }

    // 新增：通用显示字符串清洗，后端若返回字符串 "null" 也按空白处理
    /**
     * 更新历史缺陷的排序顺序
     * 用于Sort按钮功能，更新本地缓存的缺陷顺序
     */
    fun updateDefectOrder(reorderedDefects: List<HistoryDefectItem>) {
        viewModelScope.launch {
            try {
                // 更新StateFlow中的缺陷顺序
                _historyDefects.value = reorderedDefects
                // 持久化到本地数据库的 sort_order 列（按当前顺序写入）
                val projectUid = currentProjectUid
                if (!projectUid.isNullOrBlank()) {
                    val orderedNos = reorderedDefects.map { it.no }
                    defectRepository.updateSortOrders(projectUid, orderedNos)
                }
                
                Log.d("ProjectDetailViewModel", "Defect order updated successfully. New order: ${reorderedDefects.map { it.no }}")
            } catch (e: Exception) {
                Log.e("ProjectDetailViewModel", "Failed to update defect order", e)
            }
        }
    }

    /**
     * 函数：uploadSingleEvent
     * 说明：单个事件同步上传功能
     * 参考EventFormViewModel的uploadEventWithSync方法实现
     * 1. 检查事件是否存在于本地
     * 2. 创建事件压缩包并获取 SHA-256 哈希值
     * 3. 调用 create_event_upload 接口获取票据信息
     * 4. 根据返回的票据信息上传对应的压缩包
     * 5. 轮询 notice_event_upload_success 获取同步状态
     * 
     * @param eventUid 事件UID
     * @param projectUid 项目UID
     * @return Pair<Boolean, String> 成功标志和消息
     */
    suspend fun uploadSingleEvent(eventUid: String, projectUid: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            Log.d("ProjectDetailViewModel", "Starting single event upload for eventUid: $eventUid, projectUid: $projectUid")
            
            // Step 1: 检查事件目录是否存在
            val eventDir = File(appContext.filesDir, "events/$eventUid")
            if (!eventDir.exists()) {
                Log.e("ProjectDetailViewModel", "Event directory not found: ${eventDir.absolutePath}")
                return@withContext false to "Event not found locally"
            }
            
            Log.d("ProjectDetailViewModel", "Event directory exists: ${eventDir.absolutePath}")

            // 在打包前覆盖 meta.json 的目标项目UID，并写入数据库风险与核心字段
            run {
                val (okProj, msgProj) = eventRepository.overrideMetaProjectUid(appContext, eventUid, projectUid)
                Log.d("ProjectDetailViewModel", "overrideMetaProjectUid($eventUid) -> $okProj | $msgProj")

                val (okRisk, msgRisk) = eventRepository.updateMetaRiskFromDb(appContext, eventUid)
                Log.d("ProjectDetailViewModel", "updateMetaRiskFromDb($eventUid) -> $okRisk | $msgRisk")

                val (okCore, msgCore) = eventRepository.updateMetaCoreFromDb(appContext, eventUid)
                Log.d("ProjectDetailViewModel", "updateMetaCoreFromDb($eventUid) -> $okCore | $msgCore")
            }

            // Step 2: 创建事件压缩包并获取 SHA-256 哈希值
            Log.d("ProjectDetailViewModel", "Creating event zip package...")
            val zipResult = eventRepository.createEventZip(appContext, eventUid)
            if (!zipResult.first) {
                Log.e("ProjectDetailViewModel", "Create zip failed: ${zipResult.second}")
                return@withContext false to "Create zip failed: ${zipResult.second}"
            }
            
            val packageHash = zipResult.second // 获取 SHA-256 哈希值
            Log.d("ProjectDetailViewModel", "Event zip created successfully, SHA-256: $packageHash")
            
            // Step 3: 调用 create_event_upload 接口获取票据信息
            val taskUid = "single_task_${System.currentTimeMillis()}" // 生成任务UID
            
            val uploadList = listOf(
                EventUploadItem(
                    eventUid = eventUid,
                    eventPackageHash = packageHash, // 使用 SHA-256 哈希值
                    eventPackageName = "${eventUid}.zip"
                )
            )
            
            Log.d("ProjectDetailViewModel", "Calling create_event_upload API...")
            val createResult = eventRepository.createEventUpload(taskUid, projectUid, uploadList)
            if (!createResult.first || createResult.second == null) {
                Log.e("ProjectDetailViewModel", "Create event upload failed")
                return@withContext false to "Create event upload failed"
            }
            
            val uploadResponse = createResult.second!!
            if (!uploadResponse.success || uploadResponse.data.isNullOrEmpty()) {
                Log.e("ProjectDetailViewModel", "Create event upload response invalid: ${uploadResponse.message}")
                return@withContext false to "Create event upload response invalid: ${uploadResponse.message}"
            }
            
            Log.d("ProjectDetailViewModel", "Create event upload API called successfully, got ${uploadResponse.data.size} items")
            
            // Step 4: 遍历返回的data列表，根据event_package_hash匹配并上传对应的压缩包
            for (responseItem in uploadResponse.data) {
                if (responseItem.eventPackageHash == packageHash) {
                    Log.d("ProjectDetailViewModel", "Found matching item for hash: $packageHash, uploading...")
                    
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
                    val uploadResult = eventRepository.uploadEventZipWithTicket(
                        appContext, 
                        eventUid, 
                        packageHash, 
                        ticketData
                    )
                    
                    if (!uploadResult.first) {
                        Log.e("ProjectDetailViewModel", "Upload zip failed: ${uploadResult.second}")
                        return@withContext false to "Upload zip failed: ${uploadResult.second}"
                    }
                    
                    Log.d("ProjectDetailViewModel", "Zip uploaded successfully for event: $eventUid")
                    break
                }
            }
            
            // Step 5: 轮询查询上传状态
            Log.d("ProjectDetailViewModel", "Starting polling for upload status...")
            var pollCount = 0
            val maxPollCount = 30 // 最多轮询30次
            val pollInterval = 2000L // 每2秒轮询一次
            
            while (pollCount < maxPollCount) {
                kotlinx.coroutines.delay(pollInterval)
                val statusResult = eventRepository.noticeEventUploadSuccess(taskUid)
                
                Log.d("ProjectDetailViewModel", "Poll attempt ${pollCount + 1}/$maxPollCount, result: ${statusResult.first}, completed: ${statusResult.second}")
                
                if (statusResult.first) { // 请求成功
                    if (statusResult.second) { // 任务完成
                        Log.d("ProjectDetailViewModel", "Event sync completed successfully")
                        // 成功后标记事件为已同步（is_synced = 1）
                        try {
                            eventRepository.markEventSynced(eventUid)
                        } catch (markErr: Exception) {
                            Log.w("ProjectDetailViewModel", "Failed to mark event synced: ${markErr.message}")
                        }
                        return@withContext true to "Event synced to cloud successfully"
                    }
                } else {
                    Log.w("ProjectDetailViewModel", "Poll request failed, continuing...")
                }
                
                pollCount++
            }
            
            // 轮询超时，返回失败
            Log.w("ProjectDetailViewModel", "Polling timeout after $maxPollCount attempts")
            false to "Sync timeout - please check sync status later"
        } catch (e: Exception) {
            Log.e("ProjectDetailViewModel", "uploadSingleEvent failed: ${e.message}", e)
            false to "Sync failed: ${e.message}"
        }
    }

    /**
     * Function: uploadSingleEventWithRetry
     * Description: Adds automatic retry for single event sync upload.
     * Behavior:
     * - Retry up to `maxRetries` times when `uploadSingleEvent` fails
     * - Wait `delayMs` milliseconds between attempts
     * - Return immediately on first success
     *
     * @param eventUid Event UID
     * @param projectUid Project UID
     * @param maxRetries Maximum retry attempts (default 5)
     * @param delayMs Delay between attempts in milliseconds (default 3000)
     * @return Pair<Boolean, String> success flag and message
     */
    suspend fun uploadSingleEventWithRetry(
        eventUid: String,
        projectUid: String,
        maxRetries: Int = 5,
        delayMs: Long = 3000L
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        var lastMessage = ""
        for (attempt in 1..maxRetries) {
            val (ok, msg) = try {
                uploadSingleEvent(eventUid, projectUid)
            } catch (e: Exception) {
                false to ("Exception: ${e.message}")
            }
            if (ok) {
                Log.d("ProjectDetailViewModel", "uploadSingleEventWithRetry success on attempt $attempt")
                return@withContext true to "Sync succeeded on attempt $attempt"
            }
            lastMessage = msg
            Log.w("ProjectDetailViewModel", "uploadSingleEvent attempt $attempt failed: $msg")
            if (attempt < maxRetries) {
                kotlinx.coroutines.delay(delayMs)
            }
        }
        false to "Sync failed after $maxRetries attempts: $lastMessage"
    }

    /**
     * Function: uploadSelectedEventsWithRetry
     * Description: Adds automatic retry around batch sync upload for Events.
     * Behavior:
     * - Invokes `uploadSelectedEvents` and retries on failure
     * - Keeps existing progress updates; progress resets per attempt
     * - Waits `delayMs` milliseconds between attempts
     *
     * @param context Android context
     * @param eventUids List of event UIDs to upload
     * @param projectUid Project UID
     * @param maxRetries Maximum retry attempts (default 5)
     * @param delayMs Delay between attempts in milliseconds (default 3000)
     * @return Pair<Boolean, String> success flag and message
     */
    suspend fun uploadSelectedEventsWithRetry(
        context: Context,
        eventUids: List<String>,
        projectUid: String,
        maxRetries: Int = 5,
        delayMs: Long = 3000L
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        var lastMessage = ""
        for (attempt in 1..maxRetries) {
            // Reset progress each attempt to reflect actual re-run
            _eventSyncProgress.value = _eventSyncProgress.value.copy(running = true, total = eventUids.size, completed = 0)
            val (ok, msg) = try {
                uploadSelectedEvents(context, eventUids, projectUid)
            } catch (e: Exception) {
                false to ("Exception: ${e.message}")
            }
            if (ok) {
                Log.d("ProjectDetailViewModel", "uploadSelectedEventsWithRetry success on attempt $attempt")
                return@withContext true to "Batch sync succeeded on attempt $attempt"
            }
            lastMessage = msg
            Log.w("ProjectDetailViewModel", "uploadSelectedEvents attempt $attempt failed: $msg")
            if (attempt < maxRetries) {
                kotlinx.coroutines.delay(delayMs)
            }
        }
        false to "Batch sync failed after $maxRetries attempts: $lastMessage"
    }

    private fun sanitizeDisplayString(raw: String?): String {
        val s = raw?.trim() ?: return ""
        if (s.isEmpty()) return ""
        return if (s.equals("null", ignoreCase = true)) "" else s
    }
}