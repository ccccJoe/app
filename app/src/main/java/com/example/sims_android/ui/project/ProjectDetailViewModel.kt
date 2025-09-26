package com.simsapp.ui.project

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simsapp.data.local.dao.ProjectDetailDao
import com.simsapp.data.local.dao.ProjectDao
import com.simsapp.data.local.entity.DefectEntity
import com.simsapp.data.repository.DefectRepository
import com.simsapp.data.repository.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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
    val images: List<String> = emptyList()
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
    @ApplicationContext private val appContext: Context
) : ViewModel() {

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

    /**
     * 主动加载一次：按 projectUid 拉取 Room 中的 ProjectDetail.rawJson 并解析，再读取本地图片目录。
     */
    fun loadDefectsByProjectUid(projectUid: String?) {
        if (projectUid.isNullOrBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val detail = projectDetailDao.getByProjectUid(projectUid)
                val raw = detail?.rawJson.orEmpty()
                parseHistoryDefects(projectUid, raw)
            }.getOrElse { emptyList() }
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
                // 订阅该项目的所有 event 记录
                eventRepository.getEventsByProjectUid(projectUid).collect { eventEntities ->
                    val eventItems = eventEntities.map { entity ->
                        EventItem(
                            id = entity.eventId.toString(),
                            title = if (entity.content.isNotBlank()) {
                                entity.content.take(50) + if (entity.content.length > 50) "..." else ""
                            } else "Event ${entity.eventId}",
                            location = entity.location ?: "",
                            defectNo = entity.defectNos.joinToString(", "),
                            date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(entity.lastEditTime))
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

    // 注意：重复的 observeDetailAndRefresh 已合并至文件底部的统一实现（同时刷新缺陷与分组）。

    /**
     * Parse history_defect_list from raw JSON.
     * Supports direct object, nested under common wrappers (data/item/result), or array root.
     * Returns a list of HistoryDefectItem with basic fields and local thumbnail paths.
     *
     * @param projectUid The project uid used to locate cached images directory.
     * @param raw The raw JSON string from ProjectDetailEntity.rawJson.
     */
    private fun parseHistoryDefects(projectUid: String, raw: String): List<HistoryDefectItem> {
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
                        ?.take(3)
                        ?: emptyList()
                } else emptyList()
                out += HistoryDefectItem(no = no, riskRating = risk, images = images)
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
     * Upload zip packages for selected events sequentially and return a merged textual report.
     *
     * Note: X-USERNAME and Authorization headers are injected by a global OkHttp interceptor by default.
     *       UI layer is not required to pass these headers. Parameters below exist only for explicit override.
     *
     * @param context Optional Android context used to locate local event directory. If null, use appContext.
     * @param eventUids The selected event identifiers (used as local directory names)
     * @param username The X-USERNAME header value; optional override for interceptor default
     * @param authorization Authorization header like "Bearer <token>"; optional override for interceptor default
     * @return A human-readable multi-line string showing each event's upload result
     */
    suspend fun uploadSelectedEvents(
        context: Context?,
        eventUids: List<String>,
        username: String = "test",
        authorization: String? = null
    ): String = withContext(Dispatchers.IO) {
        if (eventUids.isEmpty()) return@withContext "No events selected"
        val ctx = context ?: appContext
        val results = mutableListOf<String>()
        for (uid in eventUids) {
            val (ok, msg) = eventRepository.uploadEventZip(ctx, uid, username, authorization)
            results += "event=$uid | ${if (ok) "SUCCESS" else "FAIL"} | $msg"
        }
        results.joinToString(separator = "\n")
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
            val groups = runCatching {
                val detail = projectDetailDao.getByProjectUid(projectUid)
                val raw = detail?.rawJson.orEmpty()
                parseDetailGroups(raw)
            }.getOrElse { emptyList() }
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

            // 1) Project Info (basic)
            val basicInfo = mutableListOf<KeyValueItem>()
            add(basicInfo, "project_no", data.optString("project_no", ""))
            add(basicInfo, "project_name", data.optString("project_name", ""))
            add(basicInfo, "type_of_inspection", projectAttr?.optString("type_of_inspection", ""))
            add(basicInfo, "project_description", projectAttr?.optString("project_description", ""))
            out += DetailGroup("Project Info", basicInfo)

            // 2) Client
            val clientInfo = mutableListOf<KeyValueItem>()
            add(clientInfo, "client_code", master0?.optString("client_code", ""))
            add(clientInfo, "client_name", master0?.optString("client_name", ""))
            out += DetailGroup("Client", clientInfo)

            // 3) Facility
            val facilityInfo = mutableListOf<KeyValueItem>()
            add(facilityInfo, "facility_code", master0?.optString("facility_code", ""))
            add(facilityInfo, "facility_name", master0?.optString("facility_name", ""))
            out += DetailGroup("Facility", facilityInfo)

            // 4) Functional Area
            val functionalAreaInfo = mutableListOf<KeyValueItem>()
            add(functionalAreaInfo, "functional_area_code", master0?.optString("functional_area_code", ""))
            add(functionalAreaInfo, "functional_area_name", master0?.optString("functional_area_name", ""))
            out += DetailGroup("Functional Area", functionalAreaInfo)

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
            assetItems += KeyValueItem("Asset Code", assetCodes.joinToString(", "))
            assetItems += KeyValueItem("Asset Name", assetNames.joinToString(", "))
            out += DetailGroup("Asset", assetItems)

            // 7) Other Info (times + status)
            val otherInfo = mutableListOf<KeyValueItem>()
            val projectStart = optLongOrNull(projectAttr, "project_start_at")
            val projectEnd = optLongOrNull(projectAttr, "project_end_at")
            val inspStart = optLongOrNull(projectAttr, "inspection_start_at")
            val inspEnd = optLongOrNull(projectAttr, "inspection_end_at")
            val reportStart = optLongOrNull(projectAttr, "report_start_at")
            val reportEnd = optLongOrNull(projectAttr, "report_end_at")
            add(otherInfo, "project_start_at", formatEpoch(projectStart))
            add(otherInfo, "project_end_at", formatEpoch(projectEnd))
            add(otherInfo, "inspection_start_at", formatEpoch(inspStart))
            add(otherInfo, "inspection_end_at", formatEpoch(inspEnd))
            add(otherInfo, "report_start_at", formatEpoch(reportStart))
            add(otherInfo, "report_end_at", formatEpoch(reportEnd))
            add(otherInfo, "project_status", data.optString("project_status", ""))
            out += DetailGroup("Other Info", otherInfo)

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
     * 订阅指定 projectUid 的 ProjectDetail 记录变化，当 lastFetchedAt 更新或 rawJson 变化时，自动重新解析并刷新图片列表与详情分组。
     * 同时将historyDefectList缓存到本地defect数据库。
     */
    private fun observeDetailAndRefresh(projectUid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            projectDetailDao.observeByProjectUid(projectUid).collectLatest { detail ->
                if (detail == null) return@collectLatest
                val items = parseHistoryDefects(projectUid, detail.rawJson)
                _historyDefects.value = items
                // 新增：解析详情分组
                val groups = parseDetailGroups(detail.rawJson)
                _detailGroups.value = groups
                
                // 缓存historyDefectList到本地defect数据库
                cacheHistoryDefectsToLocalDb(projectUid, detail.projectId, detail.rawJson)
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
                ) ?: DefectEntity(
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
    private fun sanitizeDisplayString(raw: String?): String {
        val s = raw?.trim() ?: return ""
        if (s.isEmpty()) return ""
        return if (s.equals("null", ignoreCase = true)) "" else s
    }
}