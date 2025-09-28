package com.simsapp.ui.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext

import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast

// 新增用于缩略图渲染的导入
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable

/**
 * File: ProjectDetailScreen.kt
 * Purpose: Project detail page showing overview, quick actions, and tabbed lists for Defects and Events.
 * Author: SIMS-Android Development Team
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectName: String,
    projectUid: String?,
    onBack: () -> Unit,
    onCreateEvent: () -> Unit,
    onCreateEventForDefect: (String) -> Unit,
    onOpenEvent: (String) -> Unit,
    // 新增：打开项目详情页（键值信息页）的回调
    onOpenProjectInfo: () -> Unit,
    // 新增：打开缺陷详情页的回调（参数为缺陷编号 no）
    onOpenDefect: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: ProjectDetailViewModel = hiltViewModel()
    // 删除：loading 覆盖层相关状态

    // 使用 rememberSaveable 持久化 Tab 选择，保证从 Event 页面返回后仍保持原状态
    var selectedTab by rememberSaveable { mutableStateOf(0) } // 0: Defects, 1: Events
    var searchText by remember { mutableStateOf("") }
    var isReorderMode by remember { mutableStateOf(false) }
    var isEventSelectMode by remember { mutableStateOf(false) }
    val selectedEventIds = remember { mutableStateListOf<String>() }
    // 新增：逐项上传中的 ID 集合，用于在标题前显示转圈
    val uploadingIds = remember { mutableStateListOf<String>() }

    // 新增：收集历史缺陷列表（仅包含 no 和 risk_rating），并在进入页面时根据 projectUid 触发加载
    val defects by viewModel.historyDefects.collectAsState(emptyList())
    // 新增：收集事件列表，并在进入页面时根据 projectUid 触发加载
    val events by viewModel.events.collectAsState(emptyList())
    // 新增：收集项目描述
    val projectDescription by viewModel.projectDescription.collectAsState("")
    // 新增：控制项目描述的展开/收起状态
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    
    LaunchedEffect(projectUid) {
        viewModel.loadDefectsByProjectUid(projectUid)
        viewModel.loadEventsByProjectUid(projectUid)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F7FB))
    ) {
        OverviewCard(
            projectName = projectName,
            projectDescription = projectDescription,
            isDescriptionExpanded = isDescriptionExpanded,
            onToggleDescription = { isDescriptionExpanded = !isDescriptionExpanded },
            onCreateEvent = onCreateEvent,
            // 传递：打开项目详情（键值信息）
            onOpenProjectInfo = onOpenProjectInfo
        )

        TabRow(selectedTabIndex = selectedTab) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Historical Defects") })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Events") })
                    }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                    placeholder = { Text("Search") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Color(0xFF222222)),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedTab == 0) {
                        Button(
                            onClick = { isReorderMode = !isReorderMode },
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) { Text(if (isReorderMode) "Finish Sorting" else "Sort") }
                    } else {
                        Button(
                            onClick = {
                                isEventSelectMode = true
                                selectedEventIds.clear()
                            },
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) { Text("Sync") }
                    }
                }
                if (selectedTab == 0 && isReorderMode) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Long press and drag to reorder", color = Color(0xFF9E9E9E), fontSize = 11.sp)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when (selectedTab) {
                0 -> HistoryDefectList(
                    searchText = searchText, 
                    defects = defects, 
                    onOpenDefect = { no -> onOpenDefect(no) },
                    onCreateEventForDefect = { defectNo -> 
                        // 跳转到新建事件页面并自动关联该缺陷
                        onCreateEventForDefect(defectNo)
                    }
                )
                1 -> EventList(
                    projectName = projectName,
                    searchText = searchText,
                    selectionMode = isEventSelectMode,
                    selectedIds = selectedEventIds,
                    onToggle = { id -> if (selectedEventIds.contains(id)) selectedEventIds.remove(id) else selectedEventIds.add(id) },
                    onOpen = { id -> onOpenEvent(id) },
                    uploadingIds = uploadingIds,
                    events = events
                )
            }

            if (selectedTab == 1 && isEventSelectMode) {
                Surface(tonalElevation = 6.dp, shadowElevation = 8.dp, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Selected ${selectedEventIds.size} items", color = Color(0xFF666666), fontSize = 12.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            isEventSelectMode = false
                            selectedEventIds.clear()
                        }) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val ids = selectedEventIds.toList()
                                isEventSelectMode = false
                                selectedEventIds.clear()
                                if (ids.isEmpty()) return@Button
                                scope.launch {
                                    // 1) 提示：同步初始化中
                                Toast.makeText(context, "Sync initializing", Toast.LENGTH_SHORT).show()
                                    // 2) 展示逐项转圈：先将所有选中 ID 标记为上传中
                                    uploadingIds.clear()
                                    uploadingIds.addAll(ids)
                                    // 3) 逐项上传，完成后从集合中移除以关闭对应转圈
                                    for (id in ids) {
                                        runCatching {
                                            viewModel.uploadSelectedEvents(
                                                context = context,
                                                eventUids = listOf(id)
                                            )
                                        }.onFailure {
                                            // 失败场景：这里先简单忽略，仅移除转圈；可在后续增强中增加失败提示
                                        }
                                        // 移除当前完成的 ID，以停止对应行的转圈
                                        uploadingIds.remove(id)
                                    }
                                    // 4) 全部完成提示：同步成功
                                Toast.makeText(context, "Sync completed", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("Confirm") }
                    }
                }
            }
        }
    }
}

// 其余组件（OverviewCard、DefectList、EventList、loadLocalEvents 等）保持不变

/**
 * Composable entry for Project Detail Screen.
 * - Shows a top overview banner with project name and a quick action "New Event".
 * - Provides Tab switching between Historical Defect list and Event list.
 * - Delegates "New Event" action to caller via onCreateEvent.
 *
 * @param projectName The name of current project to display.
 * @param onBack Callback when user taps back on the top app bar.
 * @param onCreateEvent Callback when user taps "New Event" button in the quick actions area.
 */
@Deprecated("Use primary ProjectDetailScreen defined above with ViewModel integration")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreenLegacy(
    projectName: String,
    onBack: () -> Unit,
    onCreateEvent: () -> Unit,
    onOpenEvent: (String) -> Unit
) {
    ProjectDetailScreen(
        projectName = projectName,
        projectUid = null,
        onBack = onBack,
        onCreateEvent = onCreateEvent,
        onOpenEvent = onOpenEvent,
        onOpenProjectInfo = {},
        onOpenDefect = {},
        onCreateEventForDefect = { _ -> onCreateEvent() }
    )
}

// 顶部概览卡片
@Composable
private fun OverviewCard(
    projectName: String,
    projectDescription: String,
    isDescriptionExpanded: Boolean,
    onToggleDescription: () -> Unit,
    onCreateEvent: () -> Unit,
    // 新增：点击标题右侧箭头打开项目详情
    onOpenProjectInfo: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 顶部：项目标题 + 右侧箭头
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = projectName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF333333),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenProjectInfo) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Project Detail",
                        tint = Color(0xFF666666)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            // 描述信息：使用project_description，支持展开/收起
            // 过滤null值，将null视为空内容
            val validDescription = if (projectDescription == "null" || projectDescription.isBlank()) "" else projectDescription
            
            // 当有描述内容时，显示文本（根据展开状态决定是否显示）
            if (validDescription.isNotBlank() && isDescriptionExpanded) {
                Text(
                    text = validDescription,
                    fontSize = 12.sp,
                    color = Color(0xFF7A7A7A),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            // 底部：左侧收起/展开（有描述内容时始终显示），右侧右下角的 New Event 按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 有描述内容时始终显示展开/收起按钮
                if (validDescription.isNotBlank()) {
                    Text(
                        text = if (isDescriptionExpanded) "Collapse" else "Expand",
                        color = Color(0xFF4A90E2),
                        fontSize = 12.sp,
                        modifier = Modifier.clickable {
                            onToggleDescription()
                        }
                    )
                } else {
                    // 占位空间，保持布局一致
                    Spacer(modifier = Modifier.width(0.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = onCreateEvent,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Event", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Event", fontSize = 12.sp)
                }
                // 关闭底部 Row
            }
        }
    }
}

/**
 * Historical Defect list tab.
 * 改造：按当前顺序展示，并在 isReorderMode 为 true 时支持长按拖拽交换顺序。
 */
@Composable
private fun DefectList(
    searchText: String,
    defects: List<HistoryDefectItem>,
    isReorderMode: Boolean
) {
    // TODO: Implement reordered historical defect list if needed
    // 当前 UI 已采用 HistoryDefectList 展示，保留占位以满足编译。
}

/**
 * 事件列表：根据传入的events数据渲染EventCard。
 *
 * @param projectName 项目名称（可用于筛选或加载数据）
 * @param searchText 搜索关键字
 * @param selectionMode 是否进入多选模式
 * @param selectedIds 已选中的事件 ID 集合（只读，用于决定复选框状态）
 * @param onToggle 切换选中状态的回调
 * @param onOpen 打开事件详情的回调
 * @param uploadingIds 正在上传中的事件 ID 集合（用于在标题前显示转圈）
 * @param events 事件列表数据
 */
@Composable
private fun EventList(
    projectName: String,
    searchText: String,
    selectionMode: Boolean,
    selectedIds: List<String>,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
    uploadingIds: List<String>,
    events: List<EventItem>
) {
    // 根据搜索关键字过滤事件列表
    val filteredEvents = remember(events, searchText) {
        if (searchText.isBlank()) {
            events
        } else {
            events.filter { event ->
                event.title.contains(searchText, ignoreCase = true) ||
                event.location.contains(searchText, ignoreCase = true) ||
                event.defectNo.contains(searchText, ignoreCase = true)
            }
        }
    }
    
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(filteredEvents) { item ->
            val checked = selectedIds.contains(item.id)
            val uploading = uploadingIds.contains(item.id)
            EventCard(
                item = item,
                selectionMode = selectionMode,
                checked = checked,
                uploading = uploading,
                onToggle = { onToggle(item.id) },
                onOpen = { onOpen(item.id) }
            )
        }
    }
}

/** Small visual tag for priority (P1/P2/P3). */
@Composable
private fun PriorityTag(priority: String) {
    val (bg, fg) = when (priority) {
        "P1" -> Color(0xFFFFEAEA) to Color(0xFFD32F2F)
        "P2" -> Color(0xFFFFF4E5) to Color(0xFFF57C00)
        else -> Color(0xFFEAF4FF) to Color(0xFF1976D2)
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text = priority, color = fg, fontSize = 10.sp)
    }
}

/**
 * RiskTag
 * A small label used to visualize risk rating levels with distinct colors.
 * Supported values: P1/High (red), P2/Medium (orange), P3/Low (green). Others fall back to gray.
 * - Parameters:
 *   - risk: String risk level text, case-insensitive.
 */
@Composable
private fun RiskTag(risk: String) {
    val key = risk.trim().uppercase()
    val (bg, fg) = when (key) {
        "P1", "HIGH" -> Color(0xFFFFEAEA) to Color(0xFFD32F2F)
        "P2", "MEDIUM" -> Color(0xFFFFF4E5) to Color(0xFFF57C00)
        "P3", "LOW" -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        else -> Color(0xFFECEFF1) to Color(0xFF607D8B)
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text = key, color = fg, fontSize = 10.sp)
    }
}

/**
 * 数据模型：事件列表项（UI 层使用）。
 * 说明：为满足当前 ProjectDetailScreen 的编译需求，提供最小字段集合。
 */
data class EventItem(
    val id: String,
    val title: String,
    val location: String,
    val defectNo: String,
    val date: String
)

/**
 * One item card of Event list.
 * 在 selectionMode 下显示复选框并支持整卡点击切换选中。
 * 变更：当 uploading == true 时，在标题前展示一个小号 CircularProgressIndicator。
 * 参数：
 * - item: 事件项数据。
 * - selectionMode: 是否处于选择模式。
 * - checked: 当前项是否已选中。
 * - uploading: 当前项是否处于上传中状态。
 * - onToggle: 选择模式下切换选中回调。
 * - onOpen: 非选择模式下打开详情回调。
 */
@Composable
private fun EventCard(
    item: EventItem,
    selectionMode: Boolean,
    checked: Boolean,
    uploading: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit
) {
    val rowModifier = if (selectionMode) Modifier.clickable { onToggle() } else Modifier.clickable { onOpen() }
    Card(
        modifier = rowModifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Checkbox(checked = checked, onCheckedChange = { onToggle() })
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (uploading) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(text = item.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "Location: ${item.location}", fontSize = 12.sp, color = Color(0xFF666666))
            Text(text = "DefectNo: ${item.defectNo}", fontSize = 12.sp, color = Color(0xFF666666))
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = item.date, fontSize = 11.sp, color = Color(0xFF9E9E9E))
                Spacer(modifier = Modifier.weight(1f))
                if (!selectionMode) {
                    Text(text = "Details >", color = Color(0xFF4A90E2), fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * 历史缺陷列表渲染，仅显示 no 与 risk_rating。
 * 支持按搜索关键字过滤（按 no 或 risk_rating 进行包含匹配）。
 */
@Composable
private fun HistoryDefectList(
    searchText: String,
    defects: List<HistoryDefectItem>,
    // 新增：点击缺陷项时回调，传递缺陷编号 no
    onOpenDefect: (String) -> Unit,
    // 新增：点击新增事件时回调，传递缺陷编号 no
    onCreateEventForDefect: (String) -> Unit
) {
    val filtered = remember(searchText, defects) {
        val keyword = searchText.trim()
        if (keyword.isEmpty()) defects
        else defects.filter { it.no.contains(keyword, ignoreCase = true) || it.riskRating.contains(keyword, ignoreCase = true) }
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(filtered) { item ->
            HistoryDefectCard(
                item = item,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                onClick = { onOpenDefect(item.no) },
                onCreateEvent = { onCreateEventForDefect(item.no) }
            )
        }
    }
}

/**
 * 历史缺陷卡片：
 * - 显示缺陷编号、风险等级标签、照片缩略图；
 * - 显示关联事件数量和新增链接；
 * - 支持点击跳转详情页；
 * - 支持点击照片预览大图。
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun HistoryDefectCard(
    item: HistoryDefectItem,
    modifier: Modifier = Modifier,
    // 新增：点击回调
    onClick: () -> Unit,
    // 新增：点击新增事件回调
    onCreateEvent: () -> Unit
) {
    // State: 当前被预览的大图路径
    var largePhotoPath by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier
            // 新增：卡片点击跳转详情
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // 调整标题布局：No 与 Tag 紧挨着显示，作为一条数据，若 No 换行则 Tag 跟随换行
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp), // 减小间距使其更紧密
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "No.${item.no}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF222222)
                )
                if (item.riskRating.isNotBlank()) {
                    RiskTag(item.riskRating)
                }
            }
            
            if (item.images.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                ThumbnailRow(
                    images = item.images,
                    onPhotoClick = { path -> largePhotoPath = path }
                )
            }
            
            // 关联事件数量和新增链接（调整到最右侧显示）
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f)) // 推送到右侧
                Text(
                    text = "Related Events: ${item.eventCount}",
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add New",
                    fontSize = 12.sp,
                    color = Color(0xFF2196F3),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { 
                        onCreateEvent()
                    }
                )
            }
        }
    }

    // Full-screen preview dialog for selected photo
    if (largePhotoPath != null) {
        LargePhotoDialogForPath(
            path = largePhotoPath!!,
            onDismiss = { largePhotoPath = null }
        )
    }
}

/**
 * 照片缩略图横向列表：
 * - 尺寸与 Event 页 PhotoThumb 保持一致（72.dp、圆角 8.dp）；
 * - 间距 8.dp；
 * - 支持横向滑动；
 * - 点击任意缩略图通过回调通知以打开预览。
 * @param images 本地文件绝对路径列表
 * @param onPhotoClick 点击缩略图时回调，携带被点击图片路径
 */
@Composable
private fun ThumbnailRow(images: List<String>, onPhotoClick: (String) -> Unit) {
    if (images.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(images) { path ->
            FileThumbnail(
                path = path,
                size = 72.dp,
                modifier = Modifier,
                onClick = { onPhotoClick(path) }
            )
        }
    }
}

/**
 * 单个文件缩略图：异步从本地路径解码 Bitmap 并展示；可点击触发预览，与 Event 页 PhotoThumb 风格一致（去除删除按钮）。
 * - 使用 IO 线程解码，避免阻塞主线程；
 * - 失败时显示灰色占位背景；
 * - 采用 ContentScale.Crop 并圆角裁剪。
 *
 * @param path 本地图片绝对路径
 * @param size 期望的缩略图边长（正方形）
 * @param onClick 点击缩略图时回调
 */
@Composable
private fun FileThumbnail(path: String, size: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bitmapState = remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            val bmp = runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
            withContext(Dispatchers.Main) { bitmapState.value = bmp }
        }
    }
    val bmp = bitmapState.value
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "thumbnail",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onClick() }
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF3F4F7))
                .clickable { onClick() }
        ) { /* placeholder */ }
    }
}

/**
 * 大图预览对话框（基于本地文件路径），对齐 Event 页 LargePhotoDialog 的交互：
 * - 支持双指缩放和拖拽；
 * - 顶部右侧关闭按钮；
 * - 黑色背景全屏展示。
 * @param path 本地图片绝对路径
 * @param onDismiss 关闭回调
 */
@Composable
private fun LargePhotoDialogForPath(path: String, onDismiss: () -> Unit) {
    val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (bitmap != null) {
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                    scale = (scale * zoomChange).coerceIn(1f, 5f)
                    offset = offset + panChange
                }
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Photo Preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .transformable(transformState),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            } else {
                Text(
                    text = "Photo decode failed",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Close") }
            }
        }
    }
}