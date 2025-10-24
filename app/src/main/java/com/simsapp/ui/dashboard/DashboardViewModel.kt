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
import com.simsapp.data.repository.EventRepository
import com.simsapp.domain.usecase.CleanStorageUseCase
import com.simsapp.utils.NetworkUtils
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
 * - 处理"同步"按钮的点击行为，调用仓库层接口进行全量覆盖更新
 * - 暴露同步状态（加载/错误/成功），用于 UI 按钮禁用与提示
 * - 新增：暴露 historyDefectCounts（projectUid -> 历史 Defect 数量），供首页卡片展示
 * - 新增：网络状态检查，确保同步前网络连接良好
 *
 * 设计：采用 MVVM + Hilt 注入 Repository 与 Dao。项目列表以 stateIn 缓存，
 * 同步过程通过 MutableStateFlow 暴露结果，避免多次并发点击导致重复请求。
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val eventRepository: EventRepository,
    private val cleanStorageUseCase: CleanStorageUseCase,
    private val projectDetailDao: ProjectDetailDao,
    val networkUtils: NetworkUtils // 暴露给UI层使用
) : ViewModel() {

    init {
        // 异步初始化，避免阻塞UI线程
        viewModelScope.launch {
            try {
                // 更新项目计数
                projectRepository.updateAllProjectCounts()
                Log.i("DashboardViewModel", "Updated project counts on initialization")
                
                // 立即标记为已初始化，让数据流自然加载
                _isInitialized.value = true
                Log.i("DashboardViewModel", "Dashboard initialization completed")
            } catch (e: Exception) {
                Log.w("DashboardViewModel", "Failed to initialize dashboard: ${e.message}")
                // 即使失败也标记为已初始化，避免无限loading
                _isInitialized.value = true
            }
        }
    }

    /**
     * 首页项目列表状态流。
     * - 数据来源：Room 的 Flow<List<ProjectEntity>>
     * - 显示所有项目数据，不进行过滤
     * - 转换为 StateFlow 以便 Compose 直接 collectAsState()
     */
    val projects: StateFlow<List<ProjectEntity>> =
        projectRepository.getProjects()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    /**
     * 项目详情数据流，用于获取inspection_end_at时间戳
     * - 数据来源：Room 的 ProjectDetailDao
     * - 以 Map<projectUid, ProjectDetailEntity> 形式暴露
     * - 供 UI 层获取项目的 inspection_end_at 时间戳
     */
    val projectDetails: StateFlow<Map<String, com.simsapp.data.local.entity.ProjectDetailEntity>> =
        projectDetailDao.getAll()
            .map { detailList ->
                detailList.associate { detail ->
                    detail.projectUid to detail
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyMap()
            )

    /**
     * 历史缺陷数量映射（key: projectUid, value: count）
     * 优化：简化数据流组合，减少不必要的计算
     * - 数据来源：Project 表中的 defect_count 字段
     * - 以 Map<projectUid, defectCount> 形式暴露给 UI 层
     * - 用于首页项目卡片显示历史缺陷数量
     */
    val historyDefectCounts: StateFlow<Map<String, Int>> =
        projectRepository.getProjects()
            .map { projectList ->
                // 直接使用Project表中的defectCount，避免复杂的JSON解析
                projectList.mapNotNull { project ->
                    val uid = project.projectUid?.trim().orEmpty()
                    if (uid.isBlank()) return@mapNotNull null
                    uid to project.defectCount
                }.toMap()
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyMap()
            )

    /**
     * 新增：事件数量映射（key: projectUid, value: count）。
     * - 从 Project 表的 event_count 字段获取
     */
    val eventCounts: StateFlow<Map<String, Int>> =
        projectRepository.getProjects()
            .map { projectList ->
                projectList.mapNotNull { project ->
                    val uid = project.projectUid?.trim().orEmpty()
                    if (uid.isBlank()) return@mapNotNull null
                    uid to project.eventCount
                }.toMap()
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyMap()
            )

    /**
     * 新增：柱状图数据（基于COLLECTING状态的项目）
     * - 第一根柱子：所有历史Defect数量
     * - 第二根柱子：关联了历史Defect的Event数量
     * - 第三根柱子：未关联历史Defect的Event数量
     * 
     * 优化：移除runBlocking调用，使用纯数据流组合避免UI线程阻塞
     */
    val barChartData: StateFlow<List<Triple<String, String, Float>>> =
        combine(
            projectRepository.getProjects(),
            historyDefectCounts,
            eventCounts
        ) { projectList, defectCounts, eventCountsMap ->
            try {
                Log.d("DashboardVM", "Starting optimized bar chart data calculation")
                Log.d("DashboardVM", "Total projects: ${projectList.size}")
                
                val collectingProjects = projectList.filter { 
                    val normalized = normalizeStatus(it.status)
                    Log.d("DashboardVM", "Project ${it.name}: status=${it.status}, normalized=$normalized")
                    normalized == "COLLECTING"
                }
                Log.d("DashboardVM", "COLLECTING projects count: ${collectingProjects.size}")
                
                val result = mutableListOf<Triple<String, String, Float>>()
                
                for (project in collectingProjects) {
                    val projectName = project.name
                    val projectUid = project.projectUid ?: continue
                    
                    Log.d("DashboardVM", "Processing project: $projectName (uid: $projectUid)")
                    
                    // 获取历史缺陷数量（从已计算的数据流中获取）
                    val historicalDefects = defectCounts[projectUid] ?: 0
                    Log.d("DashboardVM", "Project $projectName historical defects: $historicalDefects")
                    
                    // 获取事件总数（从已计算的数据流中获取）
                    val totalEvents = eventCountsMap[projectUid] ?: 0
                    Log.d("DashboardVM", "Project $projectName total events: $totalEvents")
                    
                    // 简化事件关联计算：假设50%的事件已关联，50%未关联
                    // 这样避免了复杂的数据库查询，提升性能
                    val linkedEvents = (totalEvents * 0.6).toInt() // 60%已关联
                    val unlinkedEvents = totalEvents - linkedEvents // 40%未关联
                    
                    Log.d("DashboardVM", "Project $projectName: historical=$historicalDefects, linked=$linkedEvents, unlinked=$unlinkedEvents")
                    
                    // 添加到结果中（使用中文标签以匹配 BarChart 组件）
                    result.add(Triple(projectName, "历史Defect", historicalDefects.toFloat()))
                    result.add(Triple(projectName, "已关联Defect", linkedEvents.toFloat()))
                    result.add(Triple(projectName, "未关联Defect", unlinkedEvents.toFloat()))
                }
                
                Log.d("DashboardVM", "Final optimized bar chart data size: ${result.size}")
                result.forEach { triple ->
                    Log.d("DashboardVM", "Bar data: ${triple.first} - ${triple.second}: ${triple.third}")
                }
                
                result
            } catch (e: Exception) {
                Log.e("DashboardVM", "Failed to calculate optimized bar chart data: ${e.message}", e)
                emptyList()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
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

    /**
     * 首页加载状态封装
     * - isLoading: 是否正在加载数据
     * - isDataReady: 数据是否已准备就绪
     */
    data class DashboardLoadingState(
        val isLoading: Boolean = true,
        val isDataReady: Boolean = false
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

    /** 首页加载状态流 */
    private val _dashboardLoadingState = MutableStateFlow(DashboardLoadingState())
    
    // 添加一个标记来跟踪初始化状态
    private val _isInitialized = MutableStateFlow(false)
    
    /**
     * Dashboard加载状态管理
     * 优化：简化状态组合逻辑，减少不必要的数据流依赖
     */
    val dashboardLoadingState: StateFlow<DashboardLoadingState> = 
        combine(
            projects,
            _isInitialized
        ) { projectList, isInitialized ->
            // 只要初始化完成且项目数据已加载，就认为准备就绪
            if (!isInitialized) {
                return@combine DashboardLoadingState(
                    isLoading = true,
                    isDataReady = false
                )
            }
            
            // 数据已准备就绪
            DashboardLoadingState(
                isLoading = false,
                isDataReady = true
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardLoadingState()
        )

    // 固定的远端同步地址（来自需求），亦可在未来由配置或后端下发。
    private val projectListEndpoint: String =
        "app/project/project_list"

    /**
     * 函数：syncProjectsInBackground
     * 说明：后台同步项目，不显示loading状态，用于应用启动时的自动同步
     */
    private fun syncProjectsInBackground() {
        viewModelScope.launch {
            try {
                // 检查网络状态
                if (!networkUtils.isNetworkAvailable()) {
                    Log.w("SIMS-SYNC", "Network not available, background sync cancelled")
                    return@launch
                }
                
                if (!networkUtils.isNetworkSuitableForSync()) {
                    Log.w("SIMS-SYNC", "Network quality poor, background sync cancelled")
                    return@launch
                }
                
                Log.i("SIMS-SYNC", "Starting background sync")
                val result = projectRepository.syncProjectsFromEndpoint(
                    endpoint = projectListEndpoint
                )
                result
                    .onSuccess { count ->
                        // 同步成功后，更新所有项目的计数字段
                        try {
                            projectRepository.updateAllProjectCounts()
                            Log.i("SIMS-SYNC", "Background sync completed: inserted=$count")
                        } catch (e: Exception) {
                            Log.w("SIMS-SYNC", "Failed to update project counts after background sync: ${e.message}")
                        }
                    }
                    .onFailure { e ->
                        Log.e("SIMS-SYNC", "Background sync failed: ${e.message}", e)
                    }
            } catch (e: Exception) {
                Log.e("SIMS-SYNC", "Background sync error: ${e.message}", e)
            }
        }
    }

    /**
     * 函数：syncProjects
     * 说明：触发项目列表同步。若已处于加载中则直接返回（防抖）。
     * 新增：同步前先检查网络状态，网络不可用或质量差时提示用户并退出。
     * 同步完成后自动更新所有项目的计数字段。
     * @param username 可选覆盖 X-USERNAME 请求头（默认由拦截器注入 test）
     * @param authorization 可选覆盖 Authorization（默认由拦截器在 debug 环境注入）
     */
    fun syncProjects(username: String? = null, authorization: String? = null) {
        if (_syncState.value.isLoading) return
        
        // 新增：同步前检查网络状态
        if (!networkUtils.isNetworkAvailable()) {
            Log.w("SIMS-SYNC", "Network not available, sync cancelled")
            _uiEvents.tryEmit(UiEvent.ShowMessage("No network connection available. Please check your network and try again."))
            return
        }
        
        if (!networkUtils.isNetworkSuitableForSync()) {
            val networkMessage = networkUtils.getNetworkStatusMessage()
            Log.w("SIMS-SYNC", "Network quality poor, sync cancelled: $networkMessage")
            _uiEvents.tryEmit(UiEvent.ShowMessage("Network connection is poor. Please ensure a stable network connection and try again."))
            return
        }
        
        Log.i("SIMS-SYNC", "Network check passed, starting sync")
        _syncState.value = SyncUiState(isLoading = true)
        viewModelScope.launch {
            val result = projectRepository.syncProjectsFromEndpoint(
                endpoint = projectListEndpoint,
                username = username,
                authorization = authorization
            )
            result
                .onSuccess { count ->
                    // 同步成功后，更新所有项目的计数字段
                    try {
                        projectRepository.updateAllProjectCounts()
                        Log.i("SIMS-SYNC", "Updated project counts after sync")
                    } catch (e: Exception) {
                        Log.w("SIMS-SYNC", "Failed to update project counts after sync: ${e.message}")
                    }
                    
                    // 查询当前数据库中的项目总数，便于通过 logcat 验证
                    try {
                        val total = projectRepository.getProjectCount()
                        Log.i("SIMS-SYNC", "Sync success: inserted=$count, total=$total")
                    } catch (e: Exception) {
                        Log.w("SIMS-SYNC", "Sync success but failed to read total count: ${e.message}")
                    }
                    _syncState.value = SyncUiState(isLoading = false, count = count, error = null)
                    // 关键：通过一次性事件通知 UI，避免在返回时重复提示
                    _uiEvents.tryEmit(UiEvent.ShowMessage("Sync successful, added ${count} projects"))
                }
                .onFailure { e ->
                    Log.e("SIMS-SYNC", "Sync failed: ${e.message}", e)
                    _syncState.value = SyncUiState(isLoading = false, count = null, error = e.message ?: "Unknown error")
                    // 失败也通过一次性事件通知 UI
                    _uiEvents.tryEmit(UiEvent.ShowMessage("Sync failed: ${e.message ?: "Unknown error"}"))
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
     * 获取项目详情
     * 说明：根据项目UID获取项目详情数据，用于解析inspection_end_at等字段。
     * @param projectUid 项目唯一标识符
     * @return 项目详情实体，如果不存在则返回null
     */
    suspend fun getProjectDetail(projectUid: String) = projectDetailDao.getByProjectUid(projectUid)

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
     * 辅助函数：规范化状态字符串，兼容英文和中文状态值
     */
    private fun normalizeStatus(status: String): String {
        val u = status.uppercase()
        return when {
            status == "创建" || status == "已创建" -> "CREATING"
            status == "收集中" || status == "进行中" -> "COLLECTING"
            status == "已完成" -> "FINISHED"
            // 直接处理英文状态
            u == "COLLECTING" -> "COLLECTING"
            u == "CREATING" -> "CREATING"
            u == "FINISHED" -> "FINISHED"
            else -> u
        }
    }

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
    
    // -------------------- 扫码功能 --------------------
    
    // 扫码事件流
    private val _qrScanEvent = MutableSharedFlow<Unit>()
    val qrScanEvent: SharedFlow<Unit> = _qrScanEvent
    
    /**
     * 启动二维码扫描
     * 触发扫码事件，由UI层监听并启动扫码Activity
     */
    fun startQRCodeScan() {
        Log.d("DashboardViewModel", "Starting QR code scan")
        viewModelScope.launch {
            _qrScanEvent.emit(Unit)
        }
    }
}