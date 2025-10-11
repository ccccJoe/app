/**
 * 文件：DefectSortScreen.kt
 * 说明：缺陷排序页面，使用原生 Compose 拖拽功能实现排序
 * 作者：SIMS-Android 开发团队
 */
package com.example.sims_android.ui.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import android.util.Log
import com.simsapp.ui.project.HistoryDefectItem
import com.simsapp.ui.common.RiskTagColors

/**
 * 缺陷排序页面
 * 
 * @param defects 缺陷列表
 * @param onBack 返回回调
 * @param onConfirm 确认排序回调，返回重新排序后的缺陷列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefectSortScreen(
    defects: List<HistoryDefectItem>,
    viewModel: com.simsapp.ui.project.ProjectDetailViewModel,
    onBack: () -> Unit,
    onConfirm: (List<HistoryDefectItem>) -> Unit
) {
    // 简化状态管理 - 只保留必要的状态
    var sortableDefects by remember { mutableStateOf(defects.toMutableList()) }
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var isDragging by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Defect Sorting",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onConfirm(sortableDefects) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirm",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Confirm",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 说明文字
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = "Long press the drag handle (⋮⋮) on the left to reorder defects. The new order will be saved when you confirm.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            // 可拖拽的缺陷列表 - 移除所有滑动限制
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
                        onDragStart = { startIndex ->
                            draggedIndex = startIndex
                            isDragging = true
                            Log.d("DefectSort", "开始拖拽: index=$startIndex")
                        },
                        onDragEnd = { 
                            draggedIndex = -1
                            isDragging = false
                            Log.d("DefectSort", "结束拖拽")
                            
                            // 保存排序后的顺序到 ViewModel
                            viewModel.updateDefectOrder(sortableDefects.toList())
                        },
                        onMove = { fromIndex, toIndex ->
                            // 改进的实时位置交换逻辑
                            if (fromIndex != toIndex && 
                                fromIndex in sortableDefects.indices && 
                                toIndex in 0 until sortableDefects.size) {
                                
                                Log.d("DefectSort", "位置交换: $fromIndex -> $toIndex")
                                
                                // 创建新列表并交换位置
                                val newList = sortableDefects.toMutableList()
                                
                                // 安全的位置交换
                                val clampedToIndex = toIndex.coerceIn(0, newList.size - 1)
                                if (fromIndex < newList.size && clampedToIndex < newList.size) {
                                    val item = newList.removeAt(fromIndex)
                                    newList.add(clampedToIndex, item)
                                    
                                    // 立即更新状态，触发重组
                                    sortableDefects = newList
                                    draggedIndex = clampedToIndex // 更新拖拽索引
                                    
                                    println("交换完成，新列表大小: ${sortableDefects.size}")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * 可拖拽的缺陷项组件
 * 
 * @param item 缺陷项数据
 * @param index 当前索引
 * @param isDragging 是否正在拖拽
 * @param onDragStart 开始拖拽回调
 * @param onDragEnd 结束拖拽回调
 * @param onMove 移动回调
 */
@Composable
private fun DraggableDefectItem(
    item: HistoryDefectItem,
    index: Int,
    isDragging: Boolean,
    onDragStart: (Int) -> Unit,
    onDragEnd: () -> Unit,
    onMove: (Int, Int) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isLongPressing by remember { mutableStateOf(false) }
    
    // 拖拽时的视觉效果
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 2.dp,
        animationSpec = tween(200),
        label = "elevation"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isDragging) 0.8f else 1f,
        animationSpec = tween(200),
        label = "alpha"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        animationSpec = tween(200),
        label = "scale"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                translationY = if (isDragging) dragOffset.y else 0f
            },
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) 
                MaterialTheme.colorScheme.surfaceVariant 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 拖拽手柄 - 使用简化的长按拖拽逻辑
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { offset ->
                                // 长按开始拖拽
                                isLongPressing = true
                                onDragStart(index)
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                Log.d("DefectSort", "长按开始拖拽: index=$index")
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
                                    onDragEnd()
                                    Log.d("DefectSort", "拖拽结束: index=$index")
                                },
                                onDrag = { change, dragAmount ->
                                    // 累积拖拽偏移
                                    dragOffset += dragAmount
                                    
                                    // 改进的位置计算 - 更精确的移动检测
                                    val itemHeight = 88.dp.toPx() // 项目高度 + 间距
                                    val threshold = itemHeight * 0.5f // 移动阈值设为项目高度的一半
                                    
                                    if (kotlin.math.abs(dragOffset.y) > threshold) {
                                        val direction = if (dragOffset.y > 0) 1 else -1
                                        val newIndex = (index + direction).coerceIn(0, Int.MAX_VALUE)
                                        
                                        if (newIndex != index) {
                                            Log.d("DefectSort", "拖拽移动: 从 $index 到 $newIndex, offset=${dragOffset.y}")
                                            onMove(index, newIndex)
                                            // 重置偏移，避免累积误差
                                            dragOffset = Offset.Zero
                                        }
                                    }
                                }
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Long press to drag",
                    tint = if (isLongPressing || isDragging) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 缺陷信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                         text = item.no,
                         fontWeight = FontWeight.Bold,
                         fontSize = 16.sp,
                         color = MaterialTheme.colorScheme.onSurface
                     )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    RiskLevelTag(riskLevel = item.riskRating)
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                     text = "Risk: ${item.riskRating}",
                     fontSize = 14.sp,
                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                     maxLines = 2,
                     overflow = TextOverflow.Ellipsis
                 )
            }
            
            // 排序序号 - 显示实时位置
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDragging) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "${index + 1}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDragging) 
                        MaterialTheme.colorScheme.onPrimary 
                    else 
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * 风险等级标签组件
 * 
 * @param riskLevel 风险等级
 * @param modifier 修饰符
 */
@Composable
private fun RiskLevelTag(
    riskLevel: String,
    modifier: Modifier = Modifier
) {
    val colorPair = RiskTagColors.getColorPair(riskLevel)
     
     Surface(
         modifier = modifier,
         shape = RoundedCornerShape(4.dp),
         color = colorPair.backgroundColor,
         border = BorderStroke(1.dp, colorPair.backgroundColor)
     ) {
         Text(
             text = riskLevel,
             modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
             fontSize = 10.sp,
             fontWeight = FontWeight.Medium,
             color = colorPair.textColor
         )
     }
}