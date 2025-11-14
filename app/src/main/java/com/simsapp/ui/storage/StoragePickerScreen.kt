package com.simsapp.ui.storage

/*
 * File: StoragePickerScreen.kt
 * Description: 数字资产选择页面，用于选择和管理数字资产，基于project_digital_asset_tree数据
 * Author: SIMS Team
 * Change Log:
 *  - 2025-11-06: Treat 'Setting' nodes as folder-like for navigation and selection.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * StoragePickerScreen
 *
 * 数字资产选择页面的主要Composable函数
 * 基于project_digital_asset_tree数据显示数字资产列表，支持文件夹导航
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun StoragePickerScreen(
    projectUid: String,
    onBackClick: () -> Unit,
    onItemSelected: (DigitalAssetTreeNode) -> Unit = {},
    onMultipleItemsSelected: (List<DigitalAssetTreeNode>) -> Unit = {},
    selectedAssetFileIds: List<String> = emptyList(), // 回显：之前选中的资产fileId列表（兼容旧数据）
    selectedAssetNodeIds: List<String> = emptyList(), // 回显：优先使用节点ID进行精确回显
    viewModel: StoragePickerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 初始化加载数据
    LaunchedEffect(projectUid) {
        if (projectUid.isNotEmpty()) {
            viewModel.loadDigitalAssetTree(projectUid)
        } else {
            android.util.Log.e("StoragePickerScreen", "项目UID为空")
        }
    }

    // 回显之前选中的资产（优先 nodeId，fallback fileId）
    LaunchedEffect(selectedAssetNodeIds, selectedAssetFileIds) {
        when {
            selectedAssetNodeIds.isNotEmpty() -> {
                android.util.Log.d("StoragePickerScreen", "按节点ID回显数字资产: ${selectedAssetNodeIds.joinToString()}")
                viewModel.setSelectedAssetsByNodeIds(selectedAssetNodeIds)
            }
            selectedAssetFileIds.isNotEmpty() -> {
                android.util.Log.d("StoragePickerScreen", "按文件ID回显数字资产: ${selectedAssetFileIds.joinToString()}")
                viewModel.setSelectedAssetsByFileIds(selectedAssetFileIds)
            }
            else -> {
                android.util.Log.d("StoragePickerScreen", "没有需要回显的数字资产")
            }
        }
    }

    Scaffold(
        topBar = {
            com.simsapp.ui.common.AppTopBar(
                title = "Digital Asset Selection",
                onBack = {
                    if (uiState.currentPath.size > 1) {
                        viewModel.goBack()
                    } else {
                        onBackClick()
                    }
                },
                containerColor = Color.White,
                titleColor = Color.Black
            )
        },
        floatingActionButton = {
            // 多选模式下显示确认按钮 - 现在一直显示，因为列表始终可选
            if (uiState.selectedItems.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        val selectedItems = viewModel.getSelectedItems()
                        // 调用多选回调函数，传递选中的项目到new event页面
                        onMultipleItemsSelected(selectedItems)
                        android.util.Log.d("StoragePickerScreen", "选中的项目: ${selectedItems.map { "${it.name}(${it.treeNodeType})" }}")
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "Confirm(${uiState.selectedItems.size})",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
    ) { paddingValues ->
        // 使用LazyColumn作为主容器，将面包屑导航作为header item
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5)),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 面包屑导航作为header item
            if (uiState.currentPath.size > 1) {
                item {
                    BreadcrumbNavigation(
                        path = uiState.currentPath,
                        onNavigateToNode = { node ->
                            viewModel.navigateToNode(node)
                        }
                    )
                }
            }

            when {
                uiState.isLoading -> {
                    // 加载状态
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                
                uiState.error != null -> {
                    // 错误状态
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Loading Failed",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Red
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = uiState.error ?: "Unknown Error",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
                
                uiState.currentItems.isEmpty() -> {
                    // 空状态
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "This folder is empty",
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
                
                else -> {
                    // 内容列表项
                    items(uiState.currentItems) { item ->
                        DigitalAssetItemCard(
                            item = item,
                            isSelected = uiState.selectedItems.contains(item.id),
                            isMultiSelectMode = true, // 始终为true，因为列表一直可选
                            onClick = {
                                if (!isFolderType(item.treeNodeType)) {
                                    // 非文件夹项目切换选中状态
                                    viewModel.toggleItemSelection(item)
                                } else {
                                    // 文件夹始终可以进入
                                    viewModel.enterFolder(item)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * BreadcrumbNavigation
 *
 * 面包屑导航组件，显示当前路径并支持点击导航
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BreadcrumbNavigation(
    path: List<DigitalAssetTreeNode>,
    onNavigateToNode: (DigitalAssetTreeNode) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        // 使用FlowRow实现自动换行的面包屑导航
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            path.forEachIndexed { index, node ->
                val isLast = index == path.size - 1
                
                // 面包屑项目容器
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (index == 0) "Root" else node.name,
                        fontSize = 14.sp,
                        color = if (isLast) Color.Black else Color(0xFF1976D2),
                        fontWeight = if (isLast) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (!isLast) {
                            Modifier.clickable { onNavigateToNode(node) }
                        } else {
                            Modifier
                        }
                    )
                    
                    if (!isLast) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = ">",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }
        }
    }
}

/**
 * DigitalAssetItemCard
 *
 * 数字资产项目卡片组件
 * 显示文件夹图标、名称和进入箭头，支持多选模式
 */
@Composable
fun DigitalAssetItemCard(
    item: DigitalAssetTreeNode,
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 多选模式下显示选择状态图标（仅非文件夹/Setting）
            if (isMultiSelectMode && !isFolderType(item.treeNodeType)) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Selected" else "Not Selected",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            // 文件夹/文件图标
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = if (isFolderType(item.treeNodeType)) "Folder" else "File",
                tint = if (isFolderType(item.treeNodeType)) Color(0xFFFFA726) else Color(0xFF42A5F5),
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 项目信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
                
                // 只为非文件夹显示类型信息
                if (!isFolderType(item.treeNodeType)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Type: ${item.treeNodeType}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
            
            // 进入箭头（仅文件夹显示）
            if (isFolderType(item.treeNodeType)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Enter",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Helper: determine whether a type should be treated as a folder in UI.
 * "Folder" and "Setting" are both folder-like.
 *
 * @param type Node type string
 * @return true if folder-like; otherwise false
 */
private fun isFolderType(type: String?): Boolean {
    return type.equals("Folder", ignoreCase = true) || type.equals("Setting", ignoreCase = true)
}