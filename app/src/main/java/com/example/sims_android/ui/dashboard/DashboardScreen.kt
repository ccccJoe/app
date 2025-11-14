/**
 * 文件：DashboardScreen.kt
 * 说明：首页仪表盘界面，展示项目统计、图表和项目列表
 * 作者：SIMS-Android 开发团队
 */
package com.example.sims_android.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import com.example.sims_android.ui.common.PieChart
import com.example.sims_android.ui.common.BarChart
import com.example.sims_android.ui.common.PublicKeyDialog
import java.text.SimpleDateFormat
import java.util.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.simsapp.data.local.entity.ProjectEntity
import com.simsapp.ui.dashboard.DashboardViewModel
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * 首页仪表盘主界面
 * @param modifier 修饰符，用于布局和样式调整
 * @param onProjectClick 点击项目卡片
 * @param onEventCreate 创建事件
 * @param onSyncClick 兼容旧参数（已由 ViewModel 内部处理，可忽略传入）
 */
/**
 * 函数：DashboardScreen
 * 说明：首页仪表盘 Compose 界面。负责订阅 ViewModel 状态流，渲染图表与项目列表，并将缓存详情中的
 *       history_defect_list 条数映射到项目卡片的历史缺陷数量展示。
 * 参数：
 * - modifier 布局与样式修饰符
 * - onProjectClick 点击项目卡片的回调，携带 ProjectCardData
 * - onEventCreate 创建事件的回调（预留）
 * - onSyncClick 触发同步的回调（兼容旧接口，当前由 ViewModel 内部处理）
 * - viewModel 仪表盘 ViewModel（Hilt 注入）
 * 返回：无（Compose UI）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onProjectClick: (ProjectCardData) -> Unit = {},
    onEventCreate: () -> Unit = {},
    onSyncClick: () -> Unit = {},
    onCleanClick: () -> Unit = {},
    onQRScanClick: () -> Unit = {},
    onKeyClick: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {

    // 订阅 ViewModel 的项目列表与同步状态
    val projects by viewModel.projects.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val cleanState by viewModel.cleanState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 设置状态栏颜色与图标样式，统一为深蓝色主题
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            window.statusBarColor = Color(0xFF0B2E66).toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    // 新增：订阅历史缺陷数量（projectUid -> count）
    val historyCounts by viewModel.historyDefectCounts.collectAsState()
    
    // 新增：订阅事件数量（projectUid -> count）
    val eventCounts by viewModel.eventCounts.collectAsState()

    // 移除首页内部的loading状态订阅，因为已经在MainActivity中处理
    // val dashboardLoadingState by viewModel.dashboardLoadingState.collectAsState()

    // 消费一次性事件：只在事件到达时展示提示，不会在返回页面时重复触发
    LaunchedEffect(Unit) {
        viewModel.uiEvents.collectLatest { event ->
            when (event) {
                is DashboardViewModel.UiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    // 收集项目详情数据，用于获取inspection_end_at时间戳
    val projectDetails by viewModel.projectDetails.collectAsState(emptyMap())
    
    // 将实体映射为 UI 所需数据结构（包含历史缺陷数量和事件数量）
    val projectList = remember(projects, historyCounts, eventCounts, projectDetails) {
        projects.map { entity ->
            val card = entity.toCard()
            val defectCount = historyCounts[card.projectUid ?: ""] ?: 0
            val eventCount = eventCounts[card.projectUid ?: ""] ?: 0
            
            // 从缓存的项目详情中获取inspection_end_at时间戳
            val inspectionEndAt = projectDetails[card.projectUid ?: ""]?.let { detail ->
                parseInspectionEndAt(detail.rawJson)
            }
            
            card.copy(
                defectCount = defectCount, 
                eventCount = eventCount,
                inspectionEndAt = inspectionEndAt
            )
        }.sortedWith(compareBy<ProjectCardData> {
            when (normalizeStatus(it.status)) {
                "CREATING", "COLLECTING" -> 0
                "FINISHED" -> 1
                else -> 2
            }
        }.thenBy { it.endDate })
    }

    // 新版信息模块统计指标订阅（来自 ViewModel）
    val completed90 by viewModel.completedCount.collectAsState()
    val historicalTotal by viewModel.historicalDefectsTotal.collectAsState()
    val linkedTotal by viewModel.linkedEventsTotal.collectAsState()
    val unlinkedTotal by viewModel.unlinkedEventsTotal.collectAsState()
    val incompleteTotal by viewModel.incompleteTotal.collectAsState()

    // 新增：同步确认弹窗的显隐状态
    var showSyncConfirm by remember { mutableStateOf(false) }
    
    // 新增：公钥弹窗的显隐状态
    var showPublicKeyDialog by remember { mutableStateOf(false) }

    // 页面背景色
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
            // 移除滑动控制，因为loading已经在MainActivity中处理
            // userScrollEnabled = dashboardLoadingState.isDataReady
        ) {
            // 顶部 Projects 操作栏悬停
            stickyHeader {
                DashboardTopBar(
                    onCleanClick = onCleanClick,
                    onQRScanClick = onQRScanClick,
                    onKeyClick = { showPublicKeyDialog = true }
                )
            }
            // 顶部统计信息模块（V2）
            item {
                DashboardHeaderSectionV2(
                    completed = completed90,
                    incomplete = incompleteTotal,
                    historical = historicalTotal,
                    linked = linkedTotal,
                    unlinked = unlinkedTotal,
                    onSyncClick = { showSyncConfirm = true },
                    isSyncing = syncState.isLoading,
                    onCleanClick = onCleanClick,
                    onQRScanClick = onQRScanClick,
                    onKeyClick = { }
                )
            }
            
            // 已移除底部按钮区，Sync 按钮已内置于顶部信息模块
            
            // 项目列表
            items(projectList) { project ->
                ProjectCard(project = project, onClick = { onProjectClick(project) })
            }
            
            // 底部间距
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
            // 新增：同步过程全屏遮罩与 Loading，阻断页面交互 + 进度
        if (syncState.isLoading) {
            // 收集项目级同步进度
            val progressState = viewModel.projectSyncProgress.collectAsState().value
            val completed = progressState.completed
            val total = progressState.total
            val fraction = if (total > 0) completed / total.toFloat() else 0f
            val progressText = if (total > 0) {
                "Syncing: $completed / $total projects"
            } else {
                "Preparing project list…"
            }
            FullscreenLoadingOverlay(
                message = progressState.message ?: "Sync in progress, please wait…",
                progressText = progressText,
                progressFraction = fraction
            )
        }
        
        // 移除首页数据加载中的Loading覆盖层，因为已经在MainActivity中处理
        // if (dashboardLoadingState.isLoading) {
        //     FullscreenLoadingOverlay(message = "Loading dashboard data…")
        // }
        
        // 新增：同步确认弹窗
        if (showSyncConfirm) {
            SyncConfirmDialog(
                onDismiss = { showSyncConfirm = false },
                onConfirm = {
                    showSyncConfirm = false
                    // 先触发 ViewModel 同步，再保留兼容回调
                    viewModel.syncProjects()
                    onSyncClick()
                },
                viewModel = viewModel
            )
        }
        
        // 公钥显示弹窗
        if (showPublicKeyDialog) {
            PublicKeyDialog(
                onDismiss = { showPublicKeyDialog = false }
            )
        }
        // Snackbar反馈：仅保留清理状态；同步成功/失败通过一次性事件处理
        LaunchedEffect(cleanState) {
            if (!cleanState.isLoading) {
                cleanState.count?.let { count ->
                    snackbarHostState.showSnackbar("清理完成，删除 ${count} 个文件")
                }
                cleanState.error?.let { msg ->
                    snackbarHostState.showSnackbar("清理失败：${msg}")
                }
            }
        }
    }
}

/**
 * 函数：DashboardTopBar
 * 说明：顶部固定标题与操作栏（删除、钥匙、扫码）。通过 LazyColumn 的 stickyHeader 悬停在顶部，
 *       在项目列表滚动时始终可见，提升操作可达性。
 * 参数：
 * - onCleanClick 顶部清理按钮点击回调
 * - onQRScanClick 顶部扫码按钮点击回调
 * - onKeyClick 顶部公钥查看按钮点击回调
 * 返回：无（Compose UI）
 */
@Composable
private fun DashboardTopBar(
    onCleanClick: () -> Unit = {},
    onQRScanClick: () -> Unit = {},
    onKeyClick: () -> Unit = {}
) {
    Surface(
        color = Color(0xFF0B2E66),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Projects",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCleanClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clean",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onKeyClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Public Key",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onQRScanClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "QR Code Scan",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * 组件：DashboardHeaderSectionV2
 * 说明：替代旧的图表卡片为信息模块，展示 90 天统计指标，并内置 Sync 按钮。
 * 参数：
 * - completed：过去 90 天完成项目数量
 * - incomplete：未完成数量（historical - linked）
 * - historical：历史缺陷总数（采集中项目）
 * - linked：已关联事件总数
 * - unlinked：未关联事件总数
 * - onSyncClick：点击同步回调
 * - isSyncing：是否处于同步中，控制按钮状态与进度
 * - onCleanClick：点击清理回调（图标按钮）
 * - onQRScanClick：点击扫码回调（图标按钮）
 * - onKeyClick：点击查看公钥回调（图标按钮）
 */
@Composable
private fun DashboardHeaderSectionV2(
    completed: Int,
    incomplete: Int,
    historical: Int,
    linked: Int,
    unlinked: Int,
    onSyncClick: () -> Unit = {},
    isSyncing: Boolean = false,
    onCleanClick: () -> Unit = {},
    onQRScanClick: () -> Unit = {},
    onKeyClick: () -> Unit = {}
) {
    // 头部信息模块：深蓝色外层 + 白色内容卡片 + 灰色统计条 + 三角指示
    Column(
        modifier = Modifier.fillMaxWidth().padding(0.dp)
    ) {
        // 深蓝色背景容器（内部留 8dp 以形成蓝色边框，不在模块外部留白）
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            // 深蓝背景与顶部栏保持一致
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B2E66))
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
                // 白色内容卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Completed (Past 90 days)",
                                    color = Color(0xFF475467),
                                    fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    softWrap = false,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$completed",
                                    color = Color(0xFF3DDC84),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Incomplete",
                                    color = Color(0xFF475467),
                                    fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    softWrap = false,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$incomplete",
                                    color = Color(0xFF101828),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // 灰色统计条 + 顶部三角（先绘制三角，再下移灰卡，避免被裁剪或重叠遮挡）
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // 顶部三角（在白色区域内），位于第二列中心
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Canvas(
                                        modifier = Modifier
                                            .size(width = 24.dp, height = 12.dp) // 缩窄宽度，保持高度
                                            .zIndex(2f)
                                    ) {
                                        // 使三角“尖头”朝上，指向 Incomplete 数字
                                        val path = Path().apply {
                                            moveTo(size.width / 2f, 0f)
                                            lineTo(0f, size.height)
                                            lineTo(size.width, size.height)
                                            close()
                                        }
                                        drawPath(path = path, color = Color(0xFFF2F4F7))
                                    }
                                }
                            }

                            // 统计卡片整体向下偏移与“三角高度”一致，使三角底边与灰卡顶部严丝合缝
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F4F7))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "Historical Defects",
                                            color = Color(0xFF667085),
                                            fontSize = 11.sp,
                                            softWrap = false,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "$historical",
                                            color = Color(0xFF101828),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "Linked Events",
                                            color = Color(0xFF667085),
                                            fontSize = 11.sp,
                                            softWrap = false,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "$linked",
                                            color = Color(0xFF101828),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "Unlinked Events",
                                            color = Color(0xFF667085),
                                            fontSize = 11.sp,
                                            softWrap = false,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "$unlinked",
                                            color = Color(0xFF101828),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onSyncClick,
                            enabled = !isSyncing,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF153E75))
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Syncing…", color = Color.White, fontSize = 14.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Sync", color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 文件用途：Dashboard 屏幕（项目信息与列表）。
// 设计说明：MVVM + Jetpack Compose，状态由 ViewModel 提供。
// 旧版 DashboardHeaderSection 已废弃并移除，避免与新版 V2 冲突。
// 统计图表区域（Legacy，占位空实现）
@Composable
private fun StatisticsSection(
    pieChartData: List<Pair<String, Float>>,
    barChartData: List<Triple<String, String, Float>>
) {
    // Deprecated: 已由 DashboardHeaderSectionV2 替代，保留空实现以避免引用报错。
}

/**
 * 操作按钮区域
 */
@Composable
private fun ActionButtonsSection(
    onSyncClick: () -> Unit = {},
    isSyncing: Boolean = false,
    onCleanClick: () -> Unit = {},
    isCleaning: Boolean = false
) {
    // Deprecated: 顶部信息模块内已包含 Sync 按钮，此处保留空实现。
}

/**
 * 类：ProjectCardData
 * 说明：用于首页项目卡片展示的 UI 数据模型，包含项目名称、历史缺陷数量、事件数量、更新时间、状态与唯一 UID。
 * 字段：
 * - name 项目名称
 * - defectCount 历史缺陷数量（来源于缓存详情的 history_defect_list 条数）
 * - eventCount 本次添加事件数量（预留，当前为 0）
 * - endDate 项目更新时间（格式化后的字符串）
 * - status 项目状态（CREATING/COLLECTING/FINISHED）
 * - projectUid 项目唯一标识（用于查询 ProjectDetail）
 * - inspectionEndAt 检查结束时间戳（毫秒，用于背景颜色计算）
 */
data class ProjectCardData(
    val name: String,
    val defectCount: Int,
    val eventCount: Int,
    val endDate: String,
    val status: String,
    val projectUid: String?,
    val inspectionEndAt: Long? = null,
    val isDeleted: Boolean = false
)

/**
 * 扩展函数：将 ProjectEntity 转成 ProjectCardData
 * - 标题：使用 project_name（仓库已映射到 name）
 * - 时间：使用 project_last_update_at（仓库已映射到 endDate）并格式化为 yyyy-MM-dd HH:mm
 * - 状态：使用 project_status（仓库已映射到 status）原样展示（不做中文转换）
 * - 计数：defect/event 默认显示 0
 */
private fun ProjectEntity.toCard(): ProjectCardData = ProjectCardData(
    name = this.name,
    defectCount = 0,
    eventCount = 0,
    endDate = this.endDate?.let { formatEpochMillisToDateString(it) } ?: "",
    status = this.status,
    projectUid = this.projectUid, // 新增映射
    isDeleted = this.isDeleted
)

/**
 * 函数：parseInspectionEndAt
 * 说明：从项目详情的rawJson中解析inspection_end_at时间戳
 * 参数：rawJson - 项目详情的原始JSON字符串
 * 返回：inspection_end_at的时间戳（毫秒），如果不存在则返回null
 */
private fun parseInspectionEndAt(rawJson: String): Long? {
    return try {
        val jsonObject = JSONObject(rawJson)
        
        // 尝试从不同的可能位置获取inspection_end_at
        val inspectionEndAt = when {
            // 直接在根级别
            jsonObject.has("inspection_end_at") -> jsonObject.optLong("inspection_end_at", 0)
            // 在project_attr中
            jsonObject.has("project_attr") -> {
                val projectAttr = jsonObject.getJSONObject("project_attr")
                projectAttr.optLong("inspection_end_at", 0)
            }
            // 在data.project_attr中
            jsonObject.has("data") -> {
                val data = jsonObject.getJSONObject("data")
                if (data.has("project_attr")) {
                    val projectAttr = data.getJSONObject("project_attr")
                    projectAttr.optLong("inspection_end_at", 0)
                } else {
                    0L
                }
            }
            else -> 0L
        }
        
        if (inspectionEndAt > 0) inspectionEndAt else null
    } catch (e: Exception) {
        android.util.Log.w("DashboardScreen", "Failed to parse inspection_end_at from rawJson: ${e.message}")
        null
    }
}

/**
 * 工具方法：将 epoch 毫秒时间戳格式化为字符串
 * @param epochMillis Long 类型的时间戳（毫秒）
 * @return 按本地时区格式化后的时间字符串，格式为 yyyy-MM-dd HH:mm
 */
private fun formatEpochMillisToDateString(epochMillis: Long): String {
    val date = Date(epochMillis)
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(date)
}

/**
 * 获取项目状态颜色
 * @param status 项目状态
 * @param endDate 结束日期
 * @return 状态对应的颜色
 */
private fun getStatusColor(status: String, endDate: String): Color {
    return when (normalizeStatus(status)) {
        "FINISHED" -> Color(0xFF2E7D32) // 深绿
        "COLLECTING" -> Color(0xFF4A90E2) // 蓝色
        "CREATING" -> Color(0xFFF5A623) // 橙黄
        else -> {
            try {
                val endDateParsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(endDate)
                val today = Date()
                val daysUntilEnd = if (endDateParsed != null) {
                    ((endDateParsed.time - today.time) / (1000 * 60 * 60 * 24)).toInt()
                } else 0
                when {
                    daysUntilEnd > 5 -> Color(0xFF4A90E2)
                    daysUntilEnd in 3..5 -> Color(0xFFF5A623)
                    daysUntilEnd <= 2 -> Color(0xFFFF4444)
                    else -> Color(0xFFCCCCCC)
                }
            } catch (e: Exception) {
                Color(0xFFCCCCCC)
            }
        }
    }
}

/**
 * 函数：getCardBackgroundColor
 * 说明：根据项目状态和 inspection_end_at 时间戳返回卡片背景颜色，采用更易分辨的低饱和色块。
 * 逻辑（加深版本）：
 * 1. FINISHED：淡绿色（约 22% 透明度）
 * 2. 根据 inspection_end_at 与当前时间的差值：
 *    - >5天：淡蓝（约 8% 透明度）
 *    - 2-5天：淡黄（约 12% 透明度）
 *    - <2天未过期：淡红（约 16% 透明度）
 *    - 已过期：略明显淡红（约 24% 透明度）
 * 3. 默认：白色
 */
private fun getCardBackgroundColor(project: ProjectCardData): Color {
    // 1. 如果项目状态是 FINISHED，返回更明显的淡绿色（加深）
    if (normalizeStatus(project.status) == "FINISHED") {
        return Color(0x38E8F5E8) // 淡绿色，加深到约 22% 透明度
    }
    
    // 2. 根据inspection_end_at与当前时间的差值计算颜色
    val inspectionEndAt = project.inspectionEndAt
    if (inspectionEndAt != null) {
        val currentTime = System.currentTimeMillis()
        val daysDiff = (inspectionEndAt - currentTime) / (24 * 60 * 60 * 1000) // 转换为天数
        
        return when {
            daysDiff > 5 -> Color(0x14E3F2FD) // 淡蓝 - >5天，约 8% 透明度
            daysDiff >= 2 -> Color(0x20FFF8E1) // 淡黄 - 2-5天，约 12% 透明度
            daysDiff >= 0 -> Color(0x28FFEBEE) // 淡红 - <2天但未过期，约 16% 透明度
            else -> Color(0x3DFFCDD2) // 略明显淡红 - 已过期，约 24% 透明度
        }
    }
    
    // 3. 默认白色
    return Color.White
}

/**
 * 返回状态Tag的背景色与文字色
 */
private fun getStatusTagStyle(status: String): Pair<Color, Color> {
    return when (normalizeStatus(status)) {
        "COLLECTING" -> Color(0xFFE6F0FA) to Color(0xFF4A90E2)
        "CREATING" -> Color(0xFFFFF4E5) to Color(0xFFF5A623)
        "FINISHED" -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        else -> Color(0xFFEDEDED) to Color(0xFF666666)
    }
}

/**
 * 状态标签
 * @param status 状态文本
 */
@Composable
private fun StatusTag(status: String) {
    val (bg, fg) = getStatusTagStyle(status)
    // 需求变更：状态文本原样展示，不进行中文映射
    val display = status
    Row(
        modifier = Modifier
            .background(bg, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(getStatusColor(status, ""), shape = RoundedCornerShape(3.dp))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = display, fontSize = 11.sp, color = fg)
    }
}

// 规范化状态字符串：兼容英文 FINISHED/COLLECTING/CREATING 与中文旧值
private fun normalizeStatus(status: String): String {
    val u = status.uppercase()
    return when {
        status == "创建" || status == "已创建" -> "CREATING"
        status == "收集中" || status == "进行中" -> "COLLECTING"
        status == "已完成" -> "FINISHED"
        else -> u
    }
}
/**
 * 函数：ProjectCard
 * 说明：项目卡片 Compose 组件。展示项目状态标签、名称、历史缺陷数量、事件数量与更新时间，并提供点击跳转。
 * 参数：
 * - project ProjectCardData UI 数据
 * - onClick 点击回调
 * 返回：无（Compose UI）
 */
@Composable
private fun ProjectCard(project: ProjectCardData, onClick: () -> Unit = {}) {
    // 函数级注释：项目卡片 UI，展示状态Tag + 名称 + 统计与更新时间
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = getCardBackgroundColor(project)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatusTag(status = project.status)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = project.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (project.isDeleted) Color(0xFF999999) else Color(0xFF333333),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 为已清除项目添加标识
                    if (project.isDeleted) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "[DELETED]",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB00020)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                // 展示历史缺陷数量（来源于缓存详情的 history_defect_list 条数）
                Text(
                    text = "Historical Defects: ${project.defectCount}",
                    fontSize = 12.sp,
                    color = if (project.isDeleted) Color(0xFFCCCCCC) else Color(0xFF666666)
                )
                Text(
                    text = "Events Quantity: ${project.eventCount}",
                    fontSize = 12.sp,
                    color = if (project.isDeleted) Color(0xFFCCCCCC) else Color(0xFF666666)
                )
                Text(
                    text = "Updated at: ${project.endDate}",
                    fontSize = 12.sp,
                    color = if (project.isDeleted) Color(0xFFCCCCCC) else Color(0xFF999999)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Go",
                tint = if (project.isDeleted) Color(0xFFEEEEEE) else Color(0xFFCCCCCC)
            )
        }
    }
}

/**
 * 函数：FullscreenLoadingOverlay
 * 说明：在同步进行时显示的全屏 Loading 组件，包含半透明遮罩与居中转圈提示；通过不可见的点击层拦截所有触摸，保证页面不可操作。
 * 参数：
 * - message 显示的提示文案，默认“同步中”。
 * 返回：无（Compose UI）
 */
/**
 * 函数：FullscreenLoadingOverlay
 * 说明：全屏 Loading 组件，支持项目级同步进度展示。
 * 参数：
 * - message：顶部提示文案
 * - progressText：可选进度文案（X/Y）
 * - progressFraction：可选进度比例 [0f, 1f]
 */
@Composable
private fun FullscreenLoadingOverlay(
    message: String = "Syncing…",
    progressText: String? = null,
    progressFraction: Float? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            // 通过可点击但无反馈的层拦截所有触摸事件，阻止底部页面交互
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { },
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Color(0xFF4A90E2))
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = message, fontSize = 14.sp, color = Color(0xFF333333))
                if (progressText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = progressText, fontSize = 13.sp, color = Color(0xFF666666))
                }
                if (progressFraction != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = progressFraction.coerceIn(0f, 1f),
                        color = Color(0xFF4A90E2)
                    )
                }
            }
        }
    }
}

/**
 * 同步确认弹窗组件
 * 包含网络状态检查和用户确认功能
 */
@Composable
private fun SyncConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    viewModel: DashboardViewModel
) {
    // 获取网络状态信息
    val networkUtils = viewModel.networkUtils
    val isNetworkAvailable = remember { networkUtils.isNetworkAvailable() }
    val networkQuality = remember { networkUtils.getNetworkQuality() }
    val networkMessage = remember { networkUtils.getNetworkStatusMessage() }
    val isNetworkSuitable = remember { networkUtils.isNetworkSuitableForSync() }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(text = "Confirm Sync") 
        },
        text = { 
            Column {
                Text(text = "The synchronization time is relatively long, please ensure good network connection.")
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 网络状态显示
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 网络状态指示器
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = when {
                                    !isNetworkAvailable -> Color.Red
                                    !isNetworkSuitable -> Color(0xFFFF9800) // Orange
                                    else -> Color(0xFF4CAF50)
                                },
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = networkMessage,
                        fontSize = 12.sp,
                        color = when {
                            !isNetworkAvailable -> Color.Red
                            !isNetworkSuitable -> Color(0xFFFF9800) // Orange
                            else -> Color(0xFF4CAF50)
                        }
                    )
                }
                
                // 网络不佳时的警告提示
                if (!isNetworkAvailable || !isNetworkSuitable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (!isNetworkAvailable) {
                            "⚠️ No network connection detected. Sync will fail without network."
                        } else {
                            "⚠️ Poor network connection may cause sync to fail or take longer."
                        },
                        fontSize = 11.sp,
                        color = Color.Red,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = isNetworkAvailable // 只有在有网络连接时才允许确认
            ) {
                Text(
                    text = "Confirm",
                    color = if (isNetworkAvailable) Color(0xFF4A90E2) else Color.Gray
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}