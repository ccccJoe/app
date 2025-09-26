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
 */
data class DefectDetailUiState(
    val basic: DetailSection = DetailSection("Basic Info", emptyList()),
    val others: DetailSection = DetailSection("Others", emptyList()),
    val images: List<String> = emptyList()
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
    fun load(projectUid: String?, defectNo: String) {
        if (projectUid.isNullOrBlank() || defectNo.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val detail = runCatching { projectDetailDao.getByProjectUid(projectUid) }.getOrNull()
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
    private fun parseDefectDetail(projectUid: String, raw: String, defectNo: String): DefectDetailUiState {
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

            DefectDetailUiState(
                basic = DetailSection("Basic Info", basicItems),
                others = DetailSection("Others", otherItems),
                images = images
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