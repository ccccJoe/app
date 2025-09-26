/*
 * File: DashboardViewModel.kt
 * Description: 首页仪表盘 ViewModel，负责项目列表订阅与同步按钮行为，封装 UI 状态（加载/错误/成功）。
 * Author: SIMS Team
 */
package com.simsapp.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simsapp.data.local.entity.ProjectEntity
import com.simsapp.data.repository.ProjectRepository
import com.simsapp.domain.usecase.CleanStorageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
// 新增：一次性事件所需的 Flow 类型
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import android.util.Log
import com.simsapp.data.local.dao.ProjectDetailDao
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import kotlinx.coroutines.flow.combine

/**
 * DashboardViewModel
 *
 * 负责：
 * - 订阅本地 Room 的项目列表并以 StateFlow 暴露给 Compose
 * - 处理“同步”按钮的点击行为，调用仓库层接口进行全量覆盖更新
 * - 暴露同步状态（加载/错误/成功），用于 UI 按钮禁用与提示
 * - 新增：暴露 historyDefectCounts（projectUid -> 历史 Defect 数量），供首页卡片展示
 *
 * 设计：采用 MVVM + Hilt 注入 Repository 与 Dao。项目列表以 stateIn 缓存，
 * 同步过程通过 MutableStateFlow 暴露结果，避免多次并发点击导致重复请求。
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val cleanStorageUseCase: CleanStorageUseCase,
    private val projectDetailDao: ProjectDetailDao
) : ViewModel() {

    /**
     * 首页项目列表状态流（过滤示例数据）。
     * - 数据来源：Room 的 Flow<List<ProjectEntity>>
     * - 通过 map 过滤掉调试示例项目（projectUid = "demo-uid"），避免首页显示模拟数据
     * - 转换为 StateFlow 以便 Compose 直接 collectAsState()
     */
    val projects: StateFlow<List<ProjectEntity>> =
        projectRepository.getProjects()
            .map { list ->
                list.filterNot { it.projectUid?.trim() == "demo-uid" }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    /**
     * 新增：历史 Defect 数量映射（key: projectUid, value: count）。
     * - 订阅 ProjectDetail 表变化，解析 raw_json 中的 history_defect_list 长度。
     */
    val historyDefectCounts: StateFlow<Map<String, Int>> =
        projectDetailDao.getAll()
            .map { list ->
                list.mapNotNull { detail ->
                    val uid = detail.projectUid?.trim().orEmpty()
                    if (uid.isBlank()) return@mapNotNull null
                    uid to countHistoryDefects(detail.rawJson)
                }.toMap()
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyMap()
            )

    /**
     * 同步 UI 状态封装（文件内私有类型）。
     * - isLoading: 是否加载中
     * - count: 成功插入的条目数量（成功时有效）
     * - error: 错误消息（失败时有效）
     */
    data class SyncUiState(
        val isLoading: Boolean = false,
        val count: Int? = null,
        val error: String? = null
    )

    /**
     * 清理 UI 状态封装（文件内私有类型）。
     * - isLoading: 是否清理中
     * - count: 成功删除的文件数量
     * - error: 错误消息
     */
    data class CleanUiState(
        val isLoading: Boolean = false,
        val count: Int? = null,
        val error: String? = null
    )

    /** 对外暴露的同步状态流 */
    private val _syncState = MutableStateFlow(SyncUiState())
    val syncState: StateFlow<SyncUiState> =
        combine(_syncState, projectRepository.isSyncing) { ui, syncing ->
            // 用 Repository 的全局同步状态覆盖 isLoading，保证跨页面/前后台一致
            ui.copy(isLoading = syncing)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SyncUiState()
        )

    /** 对外暴露的清理状态流 */
    private val _cleanState = MutableStateFlow(CleanUiState())
    val cleanState: StateFlow<CleanUiState> = _cleanState

    // 固定的远端同步地址（来自需求），亦可在未来由配置或后端下发。
    private val projectListEndpoint: String =
        "https://sims.ink-stone.win/zuul/sims-master/app/project/project_list"

    /**
     * 函数：syncProjects
     * 说明：触发项目列表同步。若已处于加载中则直接返回（防抖）。
     * @param username 可选覆盖 X-USERNAME 请求头（默认由拦截器注入 test）
     * @param authorization 可选覆盖 Authorization（默认由拦截器在 debug 环境注入）
     */
    fun syncProjects(username: String? = null, authorization: String? = null) {
        if (_syncState.value.isLoading) return
        _syncState.value = SyncUiState(isLoading = true)
        viewModelScope.launch {
            val result = projectRepository.syncProjectsFromEndpoint(
                endpoint = projectListEndpoint,
                username = username,
                authorization = authorization
            )
            result
                .onSuccess { count ->
                    // 查询当前数据库中的项目总数，便于通过 logcat 验证
                    try {
                        val total = projectRepository.getProjectCount()
                        Log.i("SIMS-SYNC", "Sync success: inserted=$count, total=$total")
                    } catch (e: Exception) {
                        Log.w("SIMS-SYNC", "Sync success but failed to read total count: ${e.message}")
                    }
                    _syncState.value = SyncUiState(isLoading = false, count = count, error = null)
                    // 关键：通过一次性事件通知 UI，避免在返回时重复提示
                    _uiEvents.tryEmit(UiEvent.ShowMessage("同步成功，新增 ${count} 条项目"))
                }
                .onFailure { e ->
                    Log.e("SIMS-SYNC", "Sync failed: ${e.message}", e)
                    _syncState.value = SyncUiState(isLoading = false, count = null, error = e.message ?: "Unknown error")
                    // 失败也通过一次性事件通知 UI
                    _uiEvents.tryEmit(UiEvent.ShowMessage("同步失败：${e.message ?: "Unknown error"}"))
                }
        }
    }

    /**
     * 函数：cleanStorage
     * 说明：触发本地缓存与临时文件清理。若已处于清理中则返回（防抖）。
     * @return 无（通过 StateFlow 暴露结果）
     */
    fun cleanStorage() {
        if (_cleanState.value.isLoading) return
        _cleanState.value = CleanUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val deletedCount = cleanStorageUseCase.execute()
                _cleanState.value = CleanUiState(isLoading = false, count = deletedCount, error = null)
            } catch (e: Exception) {
                _cleanState.value = CleanUiState(isLoading = false, count = null, error = e.message ?: "Unknown error")
            }
        }
    }

    /**
     * 一次性 UI 事件（单次消费）
     * 说明：用于触发 Snackbar/Toast 等不应因重组或返回页面而重复展示的提示。
     */
    sealed class UiEvent {
        /** 展示简单文本消息 */
        data class ShowMessage(val message: String) : UiEvent()
    }

    // 事件通道（共享流），具备缓冲以避免 UI 未及时收集导致丢事件
    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    /** 对外暴露的一次性事件流 */
    val uiEvents: SharedFlow<UiEvent> = _uiEvents

    // -------------------- 工具方法：解析历史 Defect 数量 --------------------
    /**
     * 统计原始 JSON 中 history_defect_list 的条数。
     * @param raw 详情 JSON 字符串
     * @return 列表长度；若解析失败或字段不存在则返回 0
     */
    private fun countHistoryDefects(raw: String): Int {
        return try {
            val obj = findDefectsObject(raw)
            val arr = obj.optJSONArray("history_defect_list")
            arr?.length() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 在 JSON 中定位包含 history_defect_list 的对象。
     * - 优先在根查找；若无，则在常见子节点中浅层查找一层。
     */
    private fun findDefectsObject(raw: String): JSONObject {
        val root = JSONObject(raw)
        if (root.has("history_defect_list")) return root
        // 常见容器字段：data/content/result/detail
        val keys = listOf("data", "content", "result", "detail")
        for (k in keys) {
            val child = root.optJSONObject(k)
            if (child != null && child.has("history_defect_list")) return child
        }
        return root
    }
}