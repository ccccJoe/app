package com.simsapp.ui.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.itemsIndexed
// Removed ExperimentalFoundationApi and animateItemPlacement to avoid unresolved reference on older Compose versions
// 移除重复的导入语句
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween

import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast
import android.util.Log

// 新增用于缩略图渲染的导入
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.zIndex
import com.simsapp.data.local.entity.DefectEntity
import com.simsapp.data.local.entity.EventEntity
import com.simsapp.data.local.entity.ProjectEntity
import com.simsapp.ui.common.RiskTagColors
import com.simsapp.ui.common.ProjectPickerBottomSheet

/**
 * 风险等级标签组件
 * 
 * 统一的风险等级标签UI组件，具有圆角背景、内边距和颜色配置。
 * 用于在各个界面中显示风险等级，保持一致的视觉效果。
 * 
 * @param riskLevel 风险等级字符串（如P1、P2、P3、P4等）
 * @param modifier 修饰符
 */
@Composable
private fun RiskLevelTag(
    riskLevel: String,
    modifier: Modifier = Modifier
) {
    val colorPair = RiskTagColors.getColorPair(riskLevel)
    
    Box(
        modifier = modifier
            .background(
                color = colorPair.backgroundColor,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = riskLevel.trim().uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = colorPair.textColor
        )
    }
}

/**
 * File: ProjectDetailScreen.kt
 * Purpose: Project detail page showing overview, quick actions, and tabbed lists for Defects and Events.
 * Author: SIMS-Android Development Team
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    onOpenDefect: (String) -> Unit,
    // 新增：打开缺陷排序页面的回调
    onOpenDefectSort: (List<HistoryDefectItem>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: ProjectDetailViewModel = hiltViewModel()
    // 删除：loading 覆盖层相关状态

    // 使用 rememberSaveable 持久化 Tab 选择，保证从 Event 页面返回后仍保持原状态
    var selectedTab by rememberSaveable { mutableStateOf(0) } // 0: Defects, 1: Events
    // 分别为两个tab维护独立的搜索状态
    var defectSearchText by remember { mutableStateOf("") }
    var eventSearchText by remember { mutableStateOf("") }
    var isReorderMode by remember { mutableStateOf(false) }
    var isEventSelectMode by remember { mutableStateOf(false) }
    val selectedEventIds = remember { mutableStateListOf<String>() }
    // 新增：逐项上传中的 ID 集合，用于在标题前显示转圈
    val uploadingIds = remember { mutableStateListOf<String>() }
    // 新增：在切换 Tab 前记录是否处于吸顶，以及希望保持的偏移量
    var forceStickOnTabChange by remember { mutableStateOf(false) }
    var stickOffsetOnChange by remember { mutableStateOf(0) }

    // 新增：非完成项目列表（用于项目选择弹窗）
    val notFinishedProjects by viewModel.getNotFinishedProjects().collectAsState(initial = emptyList())
    // 新增：项目选择底部弹窗显隐控制
    var showProjectPicker by remember { mutableStateOf(false) }
    // 新增：待同步的单个事件ID（弹窗确认后执行）
    var pendingSingleSyncEventId by remember { mutableStateOf<String?>(null) }
    // 新增：待同步的批量事件ID列表（弹窗确认后执行）
    val pendingBatchEventIds = remember { mutableStateListOf<String>() }
    // 新增：批量同步进度收集（用于底部显示 Synced x/y）
    val syncProgress by viewModel.eventSyncProgress.collectAsState(
        ProjectDetailViewModel.SyncProgress(0, 0, false)
    )

    // 新增：收集历史缺陷列表（仅包含 no 和 risk_rating），并在进入页面时根据 projectUid 触发加载
    val defects by viewModel.historyDefects.collectAsState(emptyList())
    // 新增：收集事件列表，并在进入页面时根据 projectUid 触发加载
    val events by viewModel.events.collectAsState(emptyList())
    // 新增：收集项目描述
    val projectDescription by viewModel.projectDescription.collectAsState("")
    // 新增：控制项目描述的展开/收起状态
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    
    // 新增：控制排序对话框的显示状态
    var showSortDialog by remember { mutableStateOf(false) }
    // 新增：排序对话框中的缺陷列表状态（可拖拽重排序）
    var sortableDefects by remember { mutableStateOf<List<HistoryDefectItem>>(emptyList()) }
    
    LaunchedEffect(projectUid) {
        viewModel.loadDefectsByProjectUid(projectUid)
        viewModel.loadEventsByProjectUid(projectUid)
    }

    // 控制是否已滚动至概览模块之外，用于动态切换顶部标题为项目名
    var isListScrolled by remember { mutableStateOf(false) }
    val pageListState = rememberLazyListState()
    // 监听整页滚动：当第一个可见项不是概览（index > 0）时认为项目内容不可见
    LaunchedEffect(pageListState) {
        snapshotFlow { pageListState.firstVisibleItemIndex > 0 }
            .distinctUntilChanged()
            .collect { isListScrolled = it }
    }

    // 修复：当 tabs 已吸顶时，切换另一个 tab 仍应保持吸顶。
    // 使用 LaunchedEffect(selectedTab) 在切换后（完成重组与测量）将父级 LazyColumn
    // 定位到 stickyHeader 的 index=1，并保留当前滚动偏移，避免跳回顶部显示概览模块。
    LaunchedEffect(selectedTab) {
        if (forceStickOnTabChange) {
            // 切换后强制保持吸顶：滚到 stickyHeader，使用记录的偏移（默认 0）
            pageListState.scrollToItem(1, stickOffsetOnChange)
            forceStickOnTabChange = false
        }
    }

    Scaffold(
        topBar = {
            com.simsapp.ui.common.AppTopBar(
                title = if (isListScrolled) projectName.ifBlank { "Project" } else "Project",
                onBack = onBack,
                containerColor = Color(0xFF0B2E66),
                titleColor = Color.White,
                navigationIconColor = Color.White,
                actions = {
                    // 顶部导航栏最右侧：New 文本胶囊按钮，触发创建事件
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = "New",
                            color = Color(0xFF0B2E66),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clickable { onCreateEvent() }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            )
        }
    ) { scaffoldPadding ->
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF6F7FB)).padding(scaffoldPadding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = pageListState
            ) {
                // 1) 顶部概览（不固定，随页面滚动）
                item {
                    OverviewCard(
                        projectName = projectName,
                        projectDescription = projectDescription,
                        isDescriptionExpanded = isDescriptionExpanded,
                        onToggleDescription = { isDescriptionExpanded = !isDescriptionExpanded },
                        onCreateEvent = onCreateEvent,
                        onOpenProjectInfo = onOpenProjectInfo
                    )
                }
                // 2) Tabs + 搜索：stickyHeader 固定到顶部
                stickyHeader {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .zIndex(1f)
                    ) {
                        // 自定义无涟漪 Tab 行：完全去除点击背景动态效果
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .padding(horizontal = 0.dp, vertical = 0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val interactionA = remember { MutableInteractionSource() }
                            val interactionB = remember { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clickable(
                                        indication = null,
                                        interactionSource = interactionA
                                    ) {
                                        // 记录当前是否已吸顶（index>=1 或存在偏移），供切换后保持位置
                                        val isSticky = pageListState.firstVisibleItemIndex >= 1 || pageListState.firstVisibleItemScrollOffset > 0
                                        forceStickOnTabChange = isSticky
                                        // 吸顶时将偏移重置为 0，保持 tabs 紧贴顶部
                                        stickOffsetOnChange = 0
                                        selectedTab = 0
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Historical Defects",
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 0) Color(0xFF2E5EA3) else Color(0xFF666666)
                                )
                                if (selectedTab == 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(horizontal = 32.dp)
                                            .height(2.dp)
                                            .fillMaxWidth()
                                            .background(Color(0xFF2E5EA3))
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clickable(
                                        indication = null,
                                        interactionSource = interactionB
                                    ) {
                                        val isSticky = pageListState.firstVisibleItemIndex >= 1 || pageListState.firstVisibleItemScrollOffset > 0
                                        forceStickOnTabChange = isSticky
                                        stickOffsetOnChange = 0
                                        selectedTab = 1
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Events",
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 1) Color(0xFF2E5EA3) else Color(0xFF666666)
                                )
                                if (selectedTab == 1) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(horizontal = 32.dp)
                                            .height(2.dp)
                                            .fillMaxWidth()
                                            .background(Color(0xFF2E5EA3))
                                    )
                                }
                            }
                        }
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .background(Color(0xFFEFF3F8), RoundedCornerShape(18.dp))
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = "Search",
                                        tint = Color(0xFF6F8BAF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        BasicTextField(
                                            value = if (selectedTab == 0) defectSearchText else eventSearchText,
                                            onValueChange = { text ->
                                                if (selectedTab == 0) defectSearchText = text else eventSearchText = text
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            textStyle = LocalTextStyle.current.copy(
                                                fontSize = 13.sp,
                                                color = Color(0xFF333333)
                                            ),
                                            cursorBrush = SolidColor(Color(0xFF1976D2))
                                        )
                                        if ((selectedTab == 0 && defectSearchText.isEmpty()) || (selectedTab == 1 && eventSearchText.isEmpty())) {
                                            Text(
                                                text = "Search",
                                                fontSize = 13.sp,
                                                color = Color(0xFF9AA8B9)
                                            )
                                        }
                                    }
                                }
                                if (selectedTab == 0) {
                                    Surface(color = Color(0xFFEFF3F8), shape = RoundedCornerShape(18.dp)) {
                                        IconButton(onClick = { showSortDialog = true }, modifier = Modifier.size(38.dp)) {
                                            Icon(imageVector = Icons.Filled.SwapVert, contentDescription = "Sort", tint = Color(0xFF6F8BAF))
                                        }
                                    }
                                } else {
                                    if (isEventSelectMode) {
                                        val visibleIds = remember(events, eventSearchText) {
                                            events.filter { e ->
                                                val q = eventSearchText.trim()
                                                if (q.isEmpty()) true else {
                                                    val s = q.lowercase()
                                                    e.title.lowercase().contains(s) ||
                                                    e.location.lowercase().contains(s) ||
                                                    e.defectNo.lowercase().contains(s)
                                                }
                                            }.map { it.id }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Invert",
                                                color = Color(0xFF1976D2),
                                                fontSize = 12.sp,
                                                modifier = Modifier.clickable {
                                                    visibleIds.forEach { id ->
                                                        if (selectedEventIds.contains(id)) selectedEventIds.remove(id) else selectedEventIds.add(id)
                                                    }
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "Select All",
                                                color = Color(0xFF1976D2),
                                                fontSize = 12.sp,
                                                modifier = Modifier.clickable {
                                                    val toAdd = visibleIds.filterNot { selectedEventIds.contains(it) }
                                                    selectedEventIds.addAll(toAdd)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3) 列表内容：与父 LazyColumn 合并，避免嵌套滚动
                if (selectedTab == 0) {
                    val filteredDefects = if (defectSearchText.isBlank()) defects else defects.filter {
                        it.no.contains(defectSearchText, ignoreCase = true) || it.riskRating.contains(defectSearchText, ignoreCase = true)
                    }
                    items(filteredDefects) { item ->
                        HistoryDefectCard(
                            item = item,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            onClick = { onOpenDefect(item.no) },
                            onCreateEvent = { onCreateEventForDefect(item.no) }
                        )
                    }
                    // 移除历史 Defects 列表的空数据英文提示：当列表为空时不插入额外占位项
                } else {
                    val filteredEvents = if (eventSearchText.isBlank()) events else events.filter { event ->
                        event.title.contains(eventSearchText, ignoreCase = true) ||
                        event.location.contains(eventSearchText, ignoreCase = true) ||
                        event.defectNo.contains(eventSearchText, ignoreCase = true)
                    }
                    items(filteredEvents) { item ->
                        val checked = selectedEventIds.contains(item.id)
                        val uploading = uploadingIds.contains(item.id)
                        EventCard(
                            item = item,
                            selectionMode = isEventSelectMode,
                            checked = checked,
                            uploading = uploading,
                            onToggle = {
                                if (selectedEventIds.contains(item.id)) selectedEventIds.remove(item.id) else selectedEventIds.add(item.id)
                            },
                            onOpen = { onOpenEvent(item.id) },
                            onSync = {
                                if (notFinishedProjects.isEmpty()) {
                                    scope.launch {
                                        Toast.makeText(context, "Sync initializing", Toast.LENGTH_SHORT).show()
                                        uploadingIds.clear()
                                        uploadingIds.add(item.id)
                                        runCatching {
                                            val eventItem = events.find { it.id == item.id }
                                            if (eventItem == null) {
                                                throw IllegalArgumentException("Event not found with id: ${item.id}")
                                            }
                                            val result = viewModel.uploadSingleEventWithRetry(
                                                eventUid = eventItem.uid,
                                                projectUid = projectUid ?: "",
                                                maxRetries = 5,
                                                delayMs = 3000L
                                            )
                                            uploadingIds.clear()
                                            Toast.makeText(context, "Sync completed", Toast.LENGTH_SHORT).show()
                                        }.onFailure { exception ->
                                            uploadingIds.clear()
                                            Toast.makeText(context, "Sync failed: ${exception.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    pendingSingleSyncEventId = item.id
                                    showProjectPicker = true
                                }
                            }
                        )
                    }
                    // 移除 Events 列表的空数据英文提示：当列表为空时不插入额外占位项
                    // 当底部 Bulk Sync 底栏显示时，为避免遮挡最后一条事件，增加底部留白
                    item {
                        // 列表底部留白：非选择模式的固定按钮与选择模式的工具栏均可能遮挡最后一条事件
                        val spacerHeight = when {
                            isEventSelectMode -> 88.dp
                            !isEventSelectMode && !syncProgress.running -> 80.dp
                            else -> 0.dp
                        }
                        Spacer(modifier = Modifier.height(spacerHeight))
                    }
                }
            }

            // 底部覆盖层：当进入选择模式时，展示选择数量与“Bulk Sync”确认
            if (selectedTab == 1 && isEventSelectMode) {
                Surface(tonalElevation = 6.dp, shadowElevation = 8.dp, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Selected ${selectedEventIds.size} items", color = Color(0xFF666666), fontSize = 12.sp)
                        }
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
                                if (notFinishedProjects.isEmpty()) {
                                    scope.launch {
                                        Toast.makeText(context, "Sync initializing", Toast.LENGTH_SHORT).show()
                                        uploadingIds.clear()
                                        uploadingIds.addAll(ids)
                                        runCatching {
                                            val result = viewModel.uploadSelectedEventsWithRetry(
                                                context = context,
                                                eventUids = ids.mapNotNull { id ->
                                                    events.find { it.id == id }?.uid
                                                },
                                                projectUid = projectUid ?: "",
                                                maxRetries = 5,
                                                delayMs = 3000L
                                            )
                                            uploadingIds.clear()
                                            if (result.first) {
                                                Toast.makeText(context, "Sync completed", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Sync failed: ${result.second}", Toast.LENGTH_LONG).show()
                                            }
                                        }.onFailure { exception ->
                                            uploadingIds.clear()
                                            Toast.makeText(context, "Sync failed: ${exception.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    pendingBatchEventIds.clear()
                                    pendingBatchEventIds.addAll(ids)
                                    showProjectPicker = true
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B2E66)),
                            modifier = Modifier.height(36.dp).width(110.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text("Confirm", color = Color.White, fontSize = 12.sp) }
                    }
                }
            }

            // 底部覆盖层：非选择模式时固定显示“Bulk Sync”，点击进入选择模式
            if (selectedTab == 1 && !isEventSelectMode && !syncProgress.running) {
                Surface(
                    color = Color.White,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            isEventSelectMode = true
                            selectedEventIds.clear()
                        },
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0B2E66),
                            disabledContainerColor = Color(0xFFB0BEC5)
                        ),
                        enabled = events.isNotEmpty()
                    ) { Text(text = "Bulk Sync", color = Color.White) }
                }
            }

            if (selectedTab == 1 && !isEventSelectMode && syncProgress.running) {
                Surface(tonalElevation = 6.dp, shadowElevation = 8.dp, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = "Synced ${syncProgress.completed} / ${syncProgress.total}", color = Color(0xFF666666), fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = "Sync in progress", color = Color(0xFF1976D2), fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // 统一的项目选择底部弹窗（支持单个与批量事件同步前选择目标项目）
    if (showProjectPicker) {
        ProjectPickerBottomSheet(
            projects = notFinishedProjects,
            defaultProjectUid = projectUid,
            onConfirm = { targetProject ->
                showProjectPicker = false
                val chosenUid = targetProject.projectUid
                scope.launch {
                    // 单个事件待同步
                    val singleId = pendingSingleSyncEventId
                    if (singleId != null) {
                        pendingSingleSyncEventId = null
                        Toast.makeText(context, "Sync initializing", Toast.LENGTH_SHORT).show()
                        uploadingIds.clear()
                        uploadingIds.add(singleId)
                        runCatching {
                            val eventItem = events.find { it.id == singleId }
                            if (eventItem == null) {
                                throw IllegalArgumentException("Event not found with id: $singleId")
                            }
                            val result = viewModel.uploadSingleEventWithRetry(
                                eventUid = eventItem.uid,
                                projectUid = chosenUid,
                                maxRetries = 5,
                                delayMs = 3000L
                            )
                            uploadingIds.clear()
                            Toast.makeText(context, "Sync completed", Toast.LENGTH_SHORT).show()
                        }.onFailure { exception ->
                            uploadingIds.clear()
                            Toast.makeText(context, "Sync failed: ${exception.message}", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }

                    // 批量事件待同步
                    if (pendingBatchEventIds.isNotEmpty()) {
                        val ids = pendingBatchEventIds.toList()
                        pendingBatchEventIds.clear()
                        Toast.makeText(context, "Sync initializing", Toast.LENGTH_SHORT).show()
                        uploadingIds.clear()
                        uploadingIds.addAll(ids)
                        runCatching {
                            val result = viewModel.uploadSelectedEventsWithRetry(
                                context = context,
                                eventUids = ids.mapNotNull { id -> events.find { it.id == id }?.uid },
                                projectUid = chosenUid,
                                maxRetries = 5,
                                delayMs = 3000L
                            )
                            uploadingIds.clear()
                            if (result.first) {
                                Toast.makeText(context, "Sync completed", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Sync failed: ${result.second}", Toast.LENGTH_LONG).show()
                            }
                        }.onFailure { exception ->
                            uploadingIds.clear()
                            Toast.makeText(context, "Sync failed: ${exception.message}", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                }
            },
            onDismiss = {
                showProjectPicker = false
                pendingSingleSyncEventId = null
                pendingBatchEventIds.clear()
            }
        )
    }
    
    // 排序底部弹窗（按箭头调整顺序，按钮固定在底部）
    if (showSortDialog) {
        DefectSortBottomSheetByArrow(
            defects = defects,
            onDismiss = { showSortDialog = false },
            onConfirm = { reorderedDefects ->
                // 更新ViewModel中的缺陷顺序
                viewModel.updateDefectOrder(reorderedDefects)
                showSortDialog = false
            }
        )
    }
}

/**
 * 缺陷排序底部弹窗（箭头排序版本）
 *
 * 需求对齐：
 * 1) 从底部弹出，并固定底部按钮（Cancel/Confirm）；
 * 2) 列表布局参考历史缺陷卡片样式，但不显示图片与 +Event 按钮；
 * 3) 上移/下移图标放在每条卡片的右下角，点击进行相邻交换。
 *
 * @param defects 待排序的历史缺陷列表
 * @param onDismiss 关闭弹窗回调
 * @param onConfirm 确认排序回调，返回新的顺序
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DefectSortBottomSheetByArrow(
    defects: List<HistoryDefectItem>,
    onDismiss: () -> Unit,
    onConfirm: (List<HistoryDefectItem>) -> Unit
){
    var sortableDefects by remember { mutableStateOf(defects) }
    // 使底部弹窗默认全屏展开，避免半展开状态
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 顶部标题与关闭
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sort Defects",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap arrows to reorder defects",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            // 列表区域占满剩余空间，按钮固定在底部
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = sortableDefects,
                    key = { _, item -> item.no }
                ) { index, item ->
                    SortDefectListItem(
                        item = item,
                        index = index,
                        isFirst = index == 0,
                        isLast = index == sortableDefects.lastIndex,
                        onMoveUp = {
                            if (index > 0) {
                                val newList = sortableDefects.toMutableList()
                                val tmp = newList[index - 1]
                                newList[index - 1] = newList[index]
                                newList[index] = tmp
                                sortableDefects = newList
                            }
                        },
                        onMoveDown = {
                            if (index < sortableDefects.size - 1) {
                                val newList = sortableDefects.toMutableList()
                                val tmp = newList[index + 1]
                                newList[index + 1] = newList[index]
                                newList[index] = tmp
                                sortableDefects = newList
                            }
                        }
                    )
                }
            }

            // 底部按钮固定展示
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF666666)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = { onConfirm(sortableDefects) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1976D2)
                    )
                ) {
                    Text("Confirm", color = Color.White)
                }
            }
        }
    }
}

/**
 * 排序弹窗中的单条缺陷卡片（复用历史缺陷样式的简化版）
 * - 顶部：缺陷编号与风险标签
 * - 底部：左侧显示 `Related Events {count}`；右侧是上移/下移箭头（在底部右侧）。
 * - 移除图片缩略图与 +Event 按钮以满足设计。
 *
 * @param item 当前缺陷数据
 * @param index 当前索引，用于显示序号或交换判断
 * @param isFirst 是否第一项（禁用上移）
 * @param isLast 是否最后一项（禁用下移）
 * @param onMoveUp 点击上移回调
 * @param onMoveDown 点击下移回调
 */
@Composable
private fun SortDefectListItem(
    item: HistoryDefectItem,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // 顶部：编号与风险标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "No.${item.no}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF222222),
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.riskRating.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    RiskLevelTag(riskLevel = item.riskRating)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            // 底部：左侧事件计数，右侧上下箭头
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Related Events ${item.eventCount}",
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onMoveUp, enabled = !isFirst) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = "Move Up",
                            tint = if (!isFirst) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onMoveDown, enabled = !isLast) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDownward,
                            contentDescription = "Move Down",
                            tint = if (!isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 缺陷排序对话框
 * 使用原生 Compose 拖拽功能实现排序
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefectSortDialog(
    defects: List<HistoryDefectItem>,
    onDismiss: () -> Unit,
    onConfirm: (List<HistoryDefectItem>) -> Unit
) {
    var sortableDefects by remember { mutableStateOf(defects) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sort Defects",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 提示文本
                Text(
                    text = "Tap arrows to reorder defects",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 可排序列表 - 点击上下箭头交换相邻项，并添加位置动画
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = sortableDefects,
                        key = { _, item -> item.no }
                    ) { index, item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                // 信息区域（左上角）
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Defect",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    // 缺陷编号单独一行且禁止换行
                                    Text(
                                        text = item.no,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Risk: ${item.riskRating} | Events: ${item.eventCount}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (item.images.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Image,
                                                    contentDescription = "Images",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "${item.images.size}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(start = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // 右下角的上下箭头按钮
                                Row(
                                    modifier = Modifier.align(Alignment.BottomEnd),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val newList = sortableDefects.toMutableList()
                                                val tmp = newList[index - 1]
                                                newList[index - 1] = newList[index]
                                                newList[index] = tmp
                                                sortableDefects = newList
                                            }
                                        },
                                        enabled = index > 0
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = "Move up",
                                            tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (index < sortableDefects.size - 1) {
                                                val newList = sortableDefects.toMutableList()
                                                val tmp = newList[index + 1]
                                                newList[index + 1] = newList[index]
                                                newList[index] = tmp
                                                sortableDefects = newList
                                            }
                                        },
                                        enabled = index < sortableDefects.size - 1
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = "Move down",
                                            tint = if (index < sortableDefects.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (sortableDefects.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No data available",
                                    color = Color(0xFF9E9E9E),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 底部按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = { onConfirm(sortableDefects) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}

/**
 * 优化的可拖拽缺陷项
 * 实现实时拖拽位置交换和流畅的视觉反馈
 */
@Composable
private fun SimpleDraggableDefectItem(
    item: HistoryDefectItem,
    index: Int,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onMove: (Int, Int) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isLongPressing by remember { mutableStateOf(false) }
    var lastTargetIndex by remember { mutableIntStateOf(index) }
    
    // 拖拽时的视觉效果 - 增强动画效果
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 12.dp else 2.dp,
        animationSpec = tween(300),
        label = "elevation"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isDragging) 0.9f else 1f,
        animationSpec = tween(200),
        label = "alpha"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else 1f,
        animationSpec = tween(200),
        label = "scale"
    )
    
    // 拖拽时的旋转效果
    val rotation by animateFloatAsState(
        targetValue = if (isDragging) 2f else 0f,
        animationSpec = tween(200),
        label = "rotation"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                rotationZ = rotation
                translationY = if (isDragging) dragOffset.y else 0f
                // 添加阴影效果
                shadowElevation = if (isDragging) 16.dp.toPx() else 4.dp.toPx()
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { offset ->
                        // 长按开始拖拽
                        isLongPressing = true
                        lastTargetIndex = index
                        onDragStart()
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        Log.d("DefectSortDialog", "长按开始拖拽: index=$index")
                    }
                )
            }
            .pointerInput(isLongPressing) {
                if (isLongPressing) {
                    // 拖拽手势
                    detectDragGestures(
                        onDragEnd = { 
                            dragOffset = Offset.Zero
                            isLongPressing = false
                            lastTargetIndex = index
                            onDragEnd()
                            Log.d("DefectSortDialog", "结束拖拽")
                        },
                        onDrag = { change, _ ->
                            dragOffset += change.position
                            
                            // 优化的实时位置计算
                            val itemHeight = 88.dp.toPx() // 更精确的项目高度
                            val currentOffset = dragOffset.y
                            
                            // 计算目标索引 - 基于累积偏移量
                            val offsetSteps = (currentOffset / itemHeight).toInt()
                            val targetIndex = (index + offsetSteps).coerceAtLeast(0)
                            
                            // 实时位置交换 - 只在目标位置改变时触发
                            if (targetIndex != lastTargetIndex && targetIndex >= 0) {
                                Log.d("DefectSortDialog", "实时位置交换: $lastTargetIndex -> $targetIndex, offset=$currentOffset")
                                
                                onMove(lastTargetIndex, targetIndex)
                                lastTargetIndex = targetIndex
                                
                                // 提供触觉反馈
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                
                                // 部分重置偏移量，保持流畅的拖拽体验
                                dragOffset = Offset(dragOffset.x, currentOffset % itemHeight)
                            }
                        }
                    )
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surface
        ),
        border = if (isDragging) 
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 拖拽手柄
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag handle",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp)
            )
            
            // 缺陷信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Defect ${item.no}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "Risk: ${item.riskRating} | Events: ${item.eventCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 图片数量指示器
            if (item.images.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Images",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${item.images.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}
@Composable
private fun DefectSortBottomSheet(
    defects: List<HistoryDefectItem>,
    onDismiss: () -> Unit,
    onConfirm: (List<HistoryDefectItem>) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    
    // 可变的缺陷列表状态，用于拖拽重排序
    var sortableDefects by remember { mutableStateOf(defects.toMutableStateList()) }
    
    // 拖拽状态
    var draggedIndex by remember { mutableStateOf(-1) }
    var targetIndex by remember { mutableStateOf(-1) }
    
    @OptIn(ExperimentalMaterial3Api::class)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 标题
            Text(
                text = "Sort Defects",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // 提示文字
            Text(
                text = "Long press and drag to reorder defects",
                fontSize = 14.sp,
                color = Color(0xFF666666),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // 可拖拽的缺陷列表
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = sortableDefects,
                    key = { _, item -> item.no }
                ) { index, item ->
                    DraggableDefectItem(
                        item = item,
                        index = index,
                        isDragging = draggedIndex == index,
                        onDragStart = { 
                            draggedIndex = index
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragEnd = {
                            // 拖拽结束，重置状态
                            draggedIndex = -1
                            targetIndex = -1
                        },
                        onDragOver = { newTargetIndex ->
                            // 改进的拖拽逻辑：只在目标位置有效且不同时才交换
                            if (draggedIndex != -1 && newTargetIndex != draggedIndex && 
                                newTargetIndex >= 0 && newTargetIndex < sortableDefects.size) {
                                
                                val newList = sortableDefects.toMutableList()
                                val draggedItem = newList.removeAt(draggedIndex)
                                newList.add(newTargetIndex, draggedItem)
                                sortableDefects = newList.toMutableStateList()
                                
                                // 更新拖拽索引为新位置，这样后续的计算都基于新位置
                                draggedIndex = newTargetIndex
                                targetIndex = newTargetIndex
                            }
                        }
                    )
                }
            }
            
            // 底部按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 取消按钮
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF666666)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                ) {
                    Text("Cancel")
                }
                
                // 确认按钮
                Button(
                    onClick = { onConfirm(sortableDefects.toList()) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1976D2)
                    )
                ) {
                    Text("Confirm", color = Color.White)
                }
            }
        }
    }
}

/**
 * 可拖拽的缺陷项组件
 */
@Composable
private fun DraggableDefectItem(
    item: HistoryDefectItem,
    index: Int,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragOver: (Int) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    
    // 拖拽时的缩放动画
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "drag_scale"
    )
    
    // 拖拽时的透明度动画
    val alpha by animateFloatAsState(
        targetValue = if (isDragging) 0.8f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "drag_alpha"
    )
    
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                translationY = if (isDragging) dragOffset.y else 0f
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragOffset = Offset.Zero
                        onDragStart()
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = { 
                        dragOffset = Offset.Zero
                        onDragEnd()
                    },
                    onDrag = { _, dragAmount ->
                        dragOffset += dragAmount
                        
                        // 改进的拖拽逻辑：基于累积偏移量计算目标位置
                        val itemHeight = 80.dp.toPx()
                        val moveThreshold = itemHeight * 0.5f // 降低阈值，提高响应性
                        
                        // 计算应该移动到的目标位置
                        val offsetSteps = (dragOffset.y / moveThreshold).toInt()
                        val newIndex = index + offsetSteps
                        
                        // 确保目标索引在有效范围内
                        if (newIndex != index && newIndex >= 0) {
                            onDragOver(newIndex)
                            // 不重置dragOffset，保持累积效果
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) Color(0xFFF0F0F0) else Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 2.dp
        )
    ) {
        // 参照历史defect列表的标题布局：缺陷编号和风险等级标签
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 缺陷编号文本
            Text(
                text = "No.${item.no}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF222222),
                modifier = Modifier.weight(1f, fill = false)
            )
            
            // 风险等级标签
            if (item.riskRating.isNotBlank()) {
                RiskLevelTag(
                    riskLevel = item.riskRating,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

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
        onCreateEventForDefect = { _ -> onCreateEvent() },
        onOpenDefectSort = { _ -> } // 空实现，因为Legacy版本不支持排序
    )
}

/**
 * 顶部概览模块（去卡片化版本）。
 *
 * - 使用全宽深蓝背景，无外部留白与阴影，确保与导航栏视觉连续。
 * - 项目描述直接展示，最多两行，超出两行显示省略号（不再支持展开/收起）。
 * - 右侧保留白色胶囊 "New" 按钮。
 *
 * @param projectName 当前项目名称
 * @param projectDescription 当前项目描述（null 或空视为无）
 * @param isDescriptionExpanded 兼容旧参数，当前不使用（已移除展开/收起逻辑）
 * @param onToggleDescription 兼容旧参数，当前不使用（已移除展开/收起逻辑）
 * @param onCreateEvent 点击 "New" 按钮的回调
 * @param onOpenProjectInfo 点击标题右侧箭头打开项目详情的回调
 */
@Composable
private fun OverviewCard(
    projectName: String,
    projectDescription: String,
    isDescriptionExpanded: Boolean,
    onToggleDescription: () -> Unit,
    onCreateEvent: () -> Unit,
    onOpenProjectInfo: () -> Unit
) {
    // 外层使用 Box + 深蓝背景，全宽显示，无外边距
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B2E66))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 16.dp)
        ) {
            // 顶部：项目标题 + 右侧箭头
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = projectName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenProjectInfo) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Project Detail",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            // 描述信息：最多两行，超出显示省略号；过滤 "null" 为无内容
            val validDescription = if (projectDescription == "null" || projectDescription.isBlank()) "" else projectDescription
            if (validDescription.isNotBlank()) {
                Text(
                    text = validDescription,
                    fontSize = 12.sp,
                    color = Color(0xFFE2ECF9),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 顶部：项目标题与项目详情入口箭头
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
 * @param onSync 单个事件同步的回调
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
    onSync: (String) -> Unit,
    uploadingIds: List<String>,
    events: List<EventItem>,
    // 新增：滚动状态回传，用于驱动顶部标题动态切换
    onScrolledChange: (Boolean) -> Unit
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
    
    val listState = rememberLazyListState()
    // 监听列表滚动以通知顶部标题切换
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
            .distinctUntilChanged()
            .collect { onScrolledChange(it) }
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
        items(filteredEvents) { item ->
            val checked = selectedIds.contains(item.id)
            val uploading = uploadingIds.contains(item.id)
            EventCard(
                item = item,
                selectionMode = selectionMode,
                checked = checked,
                uploading = uploading,
                onToggle = { onToggle(item.id) },
                onOpen = { onOpen(item.id) },
                onSync = { onSync(item.id) }
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
 * Uses unified color configuration to maintain consistency across all interfaces.
 * Supported values: P0-P4, HIGH/MEDIUM/LOW. Others fall back to default gray.
 * - Parameters:
 *   - risk: String risk level text, case-insensitive.
 */
@Composable
private fun RiskTag(risk: String) {
    // 规整风险等级文案，移除所有空白和换行，保证如 "P\n1" 正常显示为 "P1"
    val sanitized = risk.replace(Regex("\\s+"), "").trim().uppercase()
    val colorPair = RiskTagColors.getColorPair(sanitized)
    
    Box(
        modifier = Modifier
            .background(colorPair.backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = sanitized,
            color = colorPair.textColor, 
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

/**
 * 数据模型：事件列表项（UI 层使用）。
 * 说明：为满足当前 ProjectDetailScreen 的编译需求，提供最小字段集合。
 */
data class EventItem(
    val id: String,        // 事件ID（数据库主键），用于数据库查询和导航
    val uid: String,       // 事件UID（唯一标识），用于文件系统目录定位和删除操作
    val title: String,
    val location: String,
    val defectNo: String,
    val date: String,
    val riskRating: String? = null
)

/**
 * 组件：键值行（多行值缩进对齐）。
 * 说明：用于在卡片中显示例如“DefectNo: xxx, yyy”的键值对，
 * 当值需要换行时，其后续行会与第一行的值左侧对齐（即与键的右侧对齐），
 * 保证视觉上与上一行 No 对齐，不会从键下方开始。
 *
 * 参数：
 * - label: 左侧标签文本（如 "DefectNo:"、"Location:"）。
 * - value: 右侧值文本，支持换行与省略。
 */
@Composable
private fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 72.dp
 ) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF666666),
            modifier = Modifier.width(labelWidth),
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = Color(0xFF666666),
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * One item card of Event list.
 * 在 selectionMode 下显示复选框并支持整卡点击切换选中。
 * 在非选择模式下显示同步按钮，支持单个事件同步。
 * 变更：当 uploading == true 时，在标题前展示一个小号 CircularProgressIndicator。
 * 参数：
 * - item: 事件项数据。
 * - selectionMode: 是否处于选择模式。
 * - checked: 当前项是否已选中。
 * - uploading: 当前项是否处于上传中状态。
 * - onToggle: 选择模式下切换选中回调。
 * - onOpen: 非选择模式下打开详情回调。
 * - onSync: 非选择模式下单个事件同步回调。
 */
@Composable
private fun EventCard(
    item: EventItem,
    selectionMode: Boolean,
    checked: Boolean,
    uploading: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onSync: () -> Unit = {}
) {
    // 整卡点击：选择模式切换选中；普通模式进入详情
    val rowModifier = Modifier.clickable { if (selectionMode) onToggle() else onOpen() }
    Card(
        modifier = rowModifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (uploading) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                // 标题 + 风险标签组合：统一放在同一行，风险标签紧随标题
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    val riskText = item.riskRating?.takeIf { it.isNotBlank() }
                    if (riskText != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        RiskTag(riskText)
                    }
                }
                if (selectionMode) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Checkbox(checked = checked, onCheckedChange = { onToggle() })
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            KeyValueRow(label = "Location:", value = item.location)
            KeyValueRow(label = "DefectNo:", value = item.defectNo)
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.date, 
                    fontSize = 11.sp, 
                    color = Color(0xFF9E9E9E),
                    modifier = Modifier
                )
                Spacer(modifier = Modifier.weight(1f))
                if (!selectionMode) {
                    // 同步按钮（在按钮内部将上传图标放在文本前面）
                    Button(
                        onClick = onSync,
                        enabled = !uploading,
                        modifier = Modifier
                            .height(28.dp)
                            .width(72.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE9F2FF),
                            contentColor = Color(0xFF0B2E66)
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        if (uploading) {
                            CircularProgressIndicator(
                                color = Color(0xFF0B2E66),
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.size(12.dp)
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = "Upload",
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Sync",
                                    fontSize = 10.sp,
                                    color = Color(0xFF0B2E66),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
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
    onCreateEventForDefect: (String) -> Unit,
    // 新增：滚动状态回传，用于驱动顶部标题动态切换
    onScrolledChange: (Boolean) -> Unit
) {
    val filtered = remember(searchText, defects) {
        val keyword = searchText.trim()
        if (keyword.isEmpty()) defects
        else defects.filter { it.no.contains(keyword, ignoreCase = true) || it.riskRating.contains(keyword, ignoreCase = true) }
    }
    val listState = rememberLazyListState()
    // 监听列表滚动以通知顶部标题切换
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
            .distinctUntilChanged()
            .collect { onScrolledChange(it) }
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
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
            // 标题布局：左侧单行省略的缺陷编号；右侧固定区域显示风险等级标签
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "No.${item.no}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF222222),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 右侧风险标签：为空时预留固定宽度，避免标题将其挤没
                val reservedTagWidth = 44.dp
                if (item.riskRating.isNotBlank()) {
                    RiskLevelTag(riskLevel = item.riskRating)
                } else {
                    Box(modifier = Modifier.width(reservedTagWidth))
                }
            }
            
            if (item.images.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                ThumbnailRow(
                    images = item.images,
                    onPhotoClick = { path -> largePhotoPath = path }
                )
            }
            
            // 关联事件数量与新增事件按钮（底部左侧为计数，右侧为按钮）
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Related Events ${item.eventCount}",
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { onCreateEvent() },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE9F2FF),
                        contentColor = Color(0xFF0B2E66)
                    ),
                    modifier = Modifier.height(28.dp).width(72.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = "+ Event", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0B2E66))
                }
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
/**
 * 文件级工具：禁用点击水波纹的 Indication。
 * 用于移除 Tab 点击时的背景动态效果（ripple）。
 */