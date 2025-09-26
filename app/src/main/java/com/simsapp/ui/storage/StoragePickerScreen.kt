package com.simsapp.ui.storage

/*
 * File: StoragePickerScreen.kt
 * Description: 仿网盘风格的本地文件选择器（模拟数据），用于事件页面选择“数据库文件展示区”文件。
 * Author: SIMS Team
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * StoragePickerScreen
 *
 * 仓库内文件选择器页面（模拟数据）。
 * - 顶部搜索框与返回按钮
 * - 文件类型筛选 Tab（全部/图片/视频/文档/音频）
 * - 列表展示文件夹样式条目
 * - 支持多选，确认后回传所选名称列表
 *
 * @param projectName 当前项目名称，用于后续真实数据过滤
 * @param onBack 返回回调
 * @param onConfirm 确认选择回调，回传选中文件（名称列表）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoragePickerScreen(
    projectName: String,
    onBack: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var tabIndex by remember { mutableStateOf(0) }

    val tabs = listOf("全部", "图片", "视频", "文档", "音频")

    // 模拟数据：后续可替换为仓库/本地扫描结果
    val allItems = remember(projectName) {
        listOf(
            "通用标准" to "2023-05-27 21:46",
            "通用维修细节" to "2022-07-22 00:47",
            "主数据" to "2022-06-13 23:19",
            "Project过程数据" to "2022-04-12 15:44"
        )
    }

    val filtered = allItems.filter { it.first.contains(query) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "数字资产选择", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { onConfirm(selected.toList()) }, enabled = selected.isNotEmpty()) {
                        Text(text = "确认(${selected.size})")
                    }
                }
            )
        }
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner).padding(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索") },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))
            TabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = tabIndex == index, onClick = { tabIndex = index }, text = { Text(title) })
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(text = "按修改时间", color = Color(0xFF78909C), fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.first }) { item ->
                    val name = item.first
                    val time = item.second
                    val checked = selected.contains(name)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .clickable {
                                selected = if (checked) selected - name else selected + name
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = name, fontWeight = FontWeight.Medium)
                            Text(text = time, color = Color(0xFF90A4AE), fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        if (checked) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF1565C0))
                        } else {
                            Box(modifier = Modifier.size(24.dp)) {}
                        }
                    }
                }
            }
        }
    }
}