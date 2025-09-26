/*
 * File: ProjectInfoScreen.kt
 * Description: Project info page showing grouped key-value details parsed from ProjectDetail.rawJson.
 * Author: SIMS-Android Development Team
 */
package com.simsapp.ui.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.simsapp.ui.project.ProjectDetailViewModel.DetailGroup

/**
 * ProjectInfoScreen
 *
 * 展示项目的分组键值详情列表。数据来源于 ProjectDetailViewModel.detailGroups 状态流，
 * 解析自缓存的 ProjectDetailEntity.rawJson。页面进入时会根据 projectUid 主动触发加载。
 *
 * 技术栈：Jetpack Compose + Material3；遵循 MVVM 架构与 Hilt 注入。
 *
 * @param projectName 当前项目名称（用于标题显示）
 * @param projectUid  项目唯一标识（用于从 Room 读取并解析 rawJson）
 * @param onBack      返回回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectInfoScreen(
    projectName: String,
    projectUid: String?,
    onBack: () -> Unit
) {
    val viewModel: ProjectDetailViewModel = hiltViewModel()
    // 页面进入时，根据 projectUid 加载分组详情
    LaunchedEffect(projectUid) {
        viewModel.loadProjectInfoByProjectUid(projectUid)
    }
    val groups by viewModel.detailGroups.collectAsState(emptyList())

    // 移除顶部返回标题栏，直接展示内容列表
    if (groups.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF6F7FB)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No detail info", color = Color(0xFF888888))
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF6F7FB)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
        ) {
            items(groups) { group ->
                DetailGroupCard(group)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

/**
 * DetailGroupCard
 *
 * 分组卡片：上方标题带左侧蓝色竖条；下方为键值对列表，符合移动端信息详情展示规范。
 * @param group 解析后的分组信息（标题 + 键值列表）
 */
@Composable
private fun DetailGroupCard(group: DetailGroup) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // 标题行：左侧蓝色竖条 + 组名
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(16.dp)
                        .background(Color(0xFF4A90E2))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = group.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF333333))
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 键值对列表
            for ((index, item) in group.items.withIndex()) {
                KeyValueRow(keyLabel = item.key, valueLabel = item.value)
                if (index != group.items.lastIndex) {
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

/**
 * KeyValueRow
 *
 * 单行键值展示：左侧为 key（次要色），右侧为 value（主要色），支持长文本自动换行。
 * @param keyLabel   键名
 * @param valueLabel 值内容
 */
@Composable
private fun KeyValueRow(keyLabel: String, valueLabel: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = keyLabel,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF7A7A7A),
            modifier = Modifier.widthIn(min = 120.dp).padding(end = 12.dp)
        )
        Text(
            text = valueLabel.ifBlank { "-" },
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF333333),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}