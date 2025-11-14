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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.simsapp.ui.project.ProjectDetailViewModel.DetailGroup
import com.simsapp.ui.project.ProjectDetailViewModel.KeyValueItem

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
    val headerItems by viewModel.headerItems.collectAsState(emptyList())
    val projectStatus by viewModel.projectStatus.collectAsState("")
    val projectStatusColorHex by viewModel.projectStatusColorHex.collectAsState("#2E5EA3")

    // 恢复：顶部返回与标题栏，内容区域为项目键值详情列表
    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(Color.White)) {
                com.simsapp.ui.common.AppTopBar(
                    title = "Project Details",
                    onBack = onBack,
                    containerColor = Color.White,
                    titleColor = Color.Black
                )
                // 只固定第一行：项目名 + 状态标签
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    HeaderRow(
                        projectName = projectName,
                        status = projectStatus,
                        statusColorHex = projectStatusColorHex
                    )
                }
            }
        }
    ) { padding ->

        if (groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF6F7FB)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No detail info", color = Color(0xFF888888))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF6F7FB)),
                // 去除顶部与左右的整体 contentPadding，让首个模块（头部键值）与项目名行看起来是同一模块
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                // 将顶部键值信息（Inspector/Project No/Type Of Inspection/Project Description）放入可滚动内容
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        HeaderKeyInfoList(headerItems = headerItems)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(groups) { group ->
                    // 由于移除了 LazyColumn 的左右 contentPadding，这里为分组卡片补充水平边距保持整体布局一致
                    Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                        DetailGroupCard(group)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
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
/**
 * HeaderRow
 *
 * 仅渲染顶部固定区域的第一行：项目名 + 状态胶囊标签。
 * 放置于 Scaffold 的 topBar 中，使其在页面滚动时保持固定，避免覆盖下方内容。
 *
 * @param projectName 项目名称
 * @param status 状态文案（如 Finished/Collecting）
 * @param statusColorHex 状态背景色（例如 "#2E5EA3"）
 */
@Composable
private fun HeaderRow(projectName: String, status: String, statusColorHex: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = projectName.ifBlank { "ProjectName" },
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111111),
            modifier = Modifier.weight(1f)
        )
        if (status.isNotBlank()) {
            val bgColor = try {
                androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(statusColorHex))
            } catch (_: Exception) {
                Color(0xFF2E5EA3)
            }
            StatusTag(text = status, bgColor = bgColor)
        }
    }
}

/**
 * HeaderKeyInfoList
 *
 * 顶部的非固定键值信息列表（Inspector / Project No / Type Of Inspection / Project Description）。
 * 该模块放在可滚动内容中，避免当描述内容较长时被顶部固定区域遮挡。
 *
 * @param headerItems 键值条目列表
 */
@Composable
private fun HeaderKeyInfoList(headerItems: List<KeyValueItem>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        for ((index, kv) in headerItems.withIndex()) {
            KeyValueRow(keyLabel = kv.key, valueLabel = kv.value)
            if (index != headerItems.lastIndex) {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

/**
 * StatusTag
 *
 * 项目状态胶囊标签，右上角显示在项目名右侧。
 * @param text 状态文案（驼峰形式）
 * @param bgColor 胶囊背景色
 */
@Composable
private fun StatusTag(text: String, bgColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}