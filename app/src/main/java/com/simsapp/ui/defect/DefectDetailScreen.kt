/*
 * File: DefectDetailScreen.kt
 * Description: Compose screen for defect details. Displays Basic Info, Others, and Picture Information sections.
 * Author: SIMS Team
 */
package com.simsapp.ui.defect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as itemsRow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DefectDetailScreen
 *
 * Compose entry screen for defect details. It renders:
 * - Top app bar with back navigation
 * - Basic Info section (key-value list)
 * - Others section (key-value list)
 * - Picture Information with local thumbnails (same logic as history defect list)
 *
 * UI Style: Align with Project Info Detail screen
 * - Page background: light gray (0xFFF6F7FB)
 * - Card container: white with slight elevation
 * - Section title: left blue vertical bar (0xFF4A90E2)
 *
 * @param defectNo Target defect number to display in title
 * @param projectUid Optional project UID to load details; null shows blank state
 * @param onBack Callback when back button tapped
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefectDetailScreen(
    defectNo: String,
    projectUid: String? = null,
    onBack: () -> Unit,
    viewModel: DefectDetailViewModel = hiltViewModel()
) {
    // Trigger loading on first composition
    LaunchedEffect(projectUid, defectNo) {
        if (!projectUid.isNullOrBlank() && defectNo.isNotBlank()) {
            viewModel.load(projectUid, defectNo)
        }
    }
    val state by viewModel.uiState.collectAsState()

    // 全屏图片预览状态（覆盖层，不使用弹窗）
    var fullScreenPhotoPath by remember { mutableStateOf<String?>(null) }

    // 页面背景与内容列表
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF6F7FB))) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Basic Info card
            item { DetailSectionCard(section = state.basic) }
            // Others card
            item { DetailSectionCard(section = state.others) }
            // Picture Information card（缩略图点击后展示全屏覆盖层）
            item { PictureSection(images = state.images, onPhotoClick = { path -> fullScreenPhotoPath = path }) }
        }

        // 全屏图片覆盖层：点击图片或右上角关闭按钮退出
        if (fullScreenPhotoPath != null) {
            val path = fullScreenPhotoPath!!
            val bmp = remember(path) { BitmapFactory.decodeFile(path) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(path) {
                        detectTapGestures(onTap = { fullScreenPhotoPath = null })
                    }
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                IconButton(onClick = { fullScreenPhotoPath = null }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

/**
 * DetailSectionCard
 *
 * 卡片：统一样式（白色卡片 + 轻微阴影），标题行含左侧蓝色竖条；下方键值对列表按行展示。
 * @param section Section model (title + key-values)
 */
@Composable
private fun DetailSectionCard(section: DetailSection) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // 标题行：左侧蓝色竖条 + 标题文本（与 ProjectInfoScreen 一致）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(16.dp)
                        .background(Color(0xFF4A90E2))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = section.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF333333))
            }
            Spacer(modifier = Modifier.height(8.dp))
            section.items.forEach { kv ->
                // 字段与值之间添加垂直间距，值过长自动换行
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = kv.label, fontSize = 14.sp, color = Color(0xFF667085))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = kv.value,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFE5E7EB))
            }
        }
    }
}

/**
 * PictureSection
 *
 * 图片信息卡片：标题行左侧蓝色竖条；下方缩略图横向列表，点击触发全屏预览。
 * @param images 本地图片路径列表
 * @param onPhotoClick 点击缩略图时回调
 */
@Composable
private fun PictureSection(images: List<String>, onPhotoClick: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // 标题行：左侧蓝色竖条 + 标题文本（与 ProjectInfoScreen 一致）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(16.dp)
                        .background(Color(0xFF4A90E2))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Picture Information", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF333333))
            }
            Spacer(modifier = Modifier.height(8.dp))
            ThumbnailRow(images = images, onPhotoClick = onPhotoClick)
        }
    }
}

/**
 * Horizontal thumbnails row (copied look and feel from ProjectDetailScreen)
 */
@Composable
private fun ThumbnailRow(images: List<String>, onPhotoClick: (String) -> Unit) {
    if (images.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsRow(images) { path ->
            FileThumbnail(path = path, size = 72.dp, onClick = { onPhotoClick(path) })
        }
    }
}

/**
 * Single file thumbnail rendering from local path.
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
 * Fullscreen image dialog for preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullscreenImageDialog(path: String, onDismiss: () -> Unit) {
    val bmp = remember(path) { BitmapFactory.decodeFile(path) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        modifier = Modifier.fillMaxSize(),
        title = {},
        text = {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    )
}