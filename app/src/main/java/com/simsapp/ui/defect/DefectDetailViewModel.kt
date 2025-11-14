/*
 * File: DefectDetailViewModel.kt
 * Description: ViewModel for Defect Detail page. Parses cached ProjectDetail raw JSON to provide defect details and local image thumbnails.
 * Author: SIMS Team
 */
package com.simsapp.ui.defect

import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simsapp.data.local.dao.ProjectDetailDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File

/**
 * Data model: key-value pair for display.
 * @param label UI label (formatted)
 * @param value Display value (sanitized)
 */
data class KeyValueItem(val label: String, val value: String)

/**
 * Data model: a section with title and items.
 * @param title Section title
 * @param items List of key-value items
 */
data class DetailSection(val title: String, val items: List<KeyValueItem>)

/**
 * UI State for Defect Detail screen.
 * @param basic Basic Info section
 * @param others Others section
 * @param images Local image absolute paths (thumbnails), same logic as history defect list
 * @param assetImages 缺陷关联的数字资产中的图片本地路径列表
 * @param assetOthers 缺陷关联的非图片数字资产详情列表（用于列表展示与预览）
 */
data class DefectDetailUiState(
    val basic: DetailSection = DetailSection("Basic Info", emptyList()),
    val others: DetailSection = DetailSection("Others", emptyList()),
    val images: List<String> = emptyList(),
    val assetImages: List<String> = emptyList(),
    val assetOthers: List<com.example.sims_android.ui.event.DigitalAssetDetail> = emptyList()
)

/**
 * DefectDetailViewModel
 *
 * Responsibilities:
 * - Fetch cached ProjectDetail JSON by projectUid
 * - Parse history_defect_list and find item by defect no
 * - Expose UI state including Basic Info, Others and local image thumbnails
 *
 * Design Notes:
 * - Mirror parsing behaviors in ProjectDetailViewModel (wrappers, sanitize, local image dir)
 */
@HiltViewModel
class DefectDetailViewModel @Inject constructor(
    private val projectDetailDao: ProjectDetailDao,
    private val defectDataAssetDao: com.simsapp.data.local.dao.DefectDataAssetDao,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DefectDetailUiState())
    /** Public UI state to collect in Compose. */
    val uiState: StateFlow<DefectDetailUiState> = _uiState

    /**
     * Load defect detail by projectUid and defectNo.
     * @param projectUid The project unique id to locate cached detail and images directory.
     * @param defectNo The defect number used to match in history_defect_list.
     */
    /**
     * 加载缺陷详情与资产信息。
     *
     * 流程：
     * 1) 读取项目详情缓存原始 JSON；
     * 2) 解析目标缺陷基本信息与本地图片缩略图；
     * 3) 从 Room 表按缺陷 uid 查询数字资产，并拆分为图片与其他文件。
     *
     * @param projectUid 项目唯一标识，用于定位缓存与图片目录
     * @param defectNo 缺陷编号，用于在 JSON 中匹配目标项
     */
    fun load(projectUid: String?, defectNo: String) {
        if (projectUid.isNullOrBlank() || defectNo.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val detail = try { projectDetailDao.getByProjectUid(projectUid) } catch (_: Exception) { null }
            val raw = detail?.rawJson.orEmpty()
            val state = parseDefectDetail(projectUid, raw, defectNo)
            _uiState.value = state
        }
    }

    /**
     * Parse defect detail from raw JSON with wrappers support.
     * @param projectUid Used to resolve local image directory
     * @param raw Raw JSON string from ProjectDetailEntity
     * @param defectNo Target defect number to locate
     */
    /**
     * 解析缺陷详情与数字资产。
     *
     * - 支持常见外层包装（data/result/item/content）。
     * - 图片信息使用与历史缺陷列表一致的本地目录规则。
     * - 缺陷数字资产通过 `defect_uid` 或 `uid` 字段获取，再查询 Room 表。
     *
     * @param projectUid 项目 UID
     * @param raw 原始 JSON 字符串
     * @param defectNo 缺陷编号
     */
    private suspend fun parseDefectDetail(projectUid: String, raw: String, defectNo: String): DefectDetailUiState {
        if (raw.isBlank()) return DefectDetailUiState()
        return try {
            val root = parseRootObject(raw)
            val arr = root.optJSONArray("history_defect_list") ?: return DefectDetailUiState()
            var target: JSONObject? = null
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val no = obj.optString("no")
                if (no == defectNo) { target = obj; break }
            }
            val item = target ?: return DefectDetailUiState()

            // Basic Info fields
            val basicItems = buildList {
                add(KeyValueItem("Risk Rating", sanitizeDisplayString(item.optString("risk_rating"))))
                add(KeyValueItem("Defect No", sanitizeDisplayString(item.optString("no"))))
                add(KeyValueItem("Defect Type", sanitizeDisplayString(item.optString("type"))))
                add(KeyValueItem("Building Material", sanitizeDisplayString(item.optString("building_material"))))
                add(KeyValueItem("Defect Status", sanitizeDisplayString(item.optString("status"))))
                add(KeyValueItem("Inspection Date", sanitizeDisplayString(item.optString("inspection_date"))))
                add(KeyValueItem("Area", sanitizeDisplayString(item.optString("functional_area_name"))))
                add(KeyValueItem("Location", sanitizeDisplayString(item.optString("location"))))
                add(KeyValueItem("Asset", sanitizeDisplayString(item.optString("asset_name"))))
            }

            // Others fields
            val otherItems = buildList {
                add(KeyValueItem("Recommendations", sanitizeDisplayString(item.optString("recommendations"))))
                add(KeyValueItem("Engineering Required", sanitizeDisplayString(item.optString("engineering_required"))))
                add(KeyValueItem("Responsible Stakeholder", sanitizeDisplayString(item.optString("responsible_stakeholder"))))
                add(KeyValueItem("Shutdown Required", sanitizeDisplayString(item.optString("shutdown_required"))))
                add(KeyValueItem("Overdue", sanitizeDisplayString(item.optString("overdue"))))
                add(KeyValueItem("Dropped Object Risk", sanitizeDisplayString(item.optString("dropped_object"))))
            }

            // Local images (same directory logic as history defects)
            val dir = File(appContext.filesDir, "history_defects/${projectUid}/${sanitize(defectNo)}")
            val images = if (dir.exists()) {
                dir.listFiles()?.sortedBy { it.name }?.map { it.absolutePath } ?: emptyList()
            } else emptyList()

            // 解析缺陷 UID（兼容 uid / defect_uid）并查询数字资产
            val defectUid = item.optString("uid").ifBlank { item.optString("defect_uid") }
            val assetImages: List<String>
            val assetOthers: List<com.example.sims_android.ui.event.DigitalAssetDetail>
            if (defectUid.isNotBlank()) {
                val assets = try { defectDataAssetDao.getByDefectUid(defectUid) } catch (_: Exception) { emptyList() }
                val imageTypes = setOf("PIC", "IMAGE", "JPG", "JPEG", "PNG", "GIF", "BMP", "WEBP")
                assetImages = assets.filter { imageTypes.contains(it.type.uppercase()) }
                    .mapNotNull { it.localPath }
                assetOthers = assets.filter { !imageTypes.contains(it.type.uppercase()) }
                    .map { entity ->
                        com.example.sims_android.ui.event.DigitalAssetDetail(
                            fileId = entity.fileId,
                            fileName = entity.fileName ?: entity.fileId,
                            type = (entity.type ?: "").ifBlank { "UNKNOWN" },
                            localPath = entity.localPath
                        )
                    }
            } else {
                assetImages = emptyList()
                assetOthers = emptyList()
            }

            DefectDetailUiState(
                basic = DetailSection("Basic Info", basicItems),
                others = DetailSection("Others", otherItems),
                images = images,
                assetImages = assetImages,
                assetOthers = assetOthers
            )
        } catch (_: Exception) {
            DefectDetailUiState()
        }
    }

    /**
     * Parse root object with common wrappers (data/result/item/content).
     */
    private fun parseRootObject(raw: String): JSONObject {
        val json = JSONObject(raw)
        for (wrapperName in arrayOf("data", "result", "item", "content")) {
            val wrapper = json.optJSONObject(wrapperName)
            if (wrapper != null) return wrapper
        }
        return json
    }

    /**
     * Sanitize display string: trim, convert literal "null" to blank.
     */
    private fun sanitizeDisplayString(raw: String?): String {
        val s = raw?.trim() ?: return ""
        if (s.isEmpty()) return ""
        return if (s.equals("null", ignoreCase = true)) "" else s
    }

    /**
     * Make a safe file name from input (letters, digits, dot, underscore, hyphen).
     */
    private fun sanitize(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
}