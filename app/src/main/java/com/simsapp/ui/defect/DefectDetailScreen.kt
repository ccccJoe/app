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
import com.simsapp.ui.event.components.DigitalAssetCategorizedDisplay
import com.simsapp.ui.event.components.AssetPreviewDialog
import com.example.sims_android.ui.event.DigitalAssetDetail
import com.example.sims_android.ui.event.RiskAssessmentWizardDialog
import com.example.sims_android.ui.event.RiskMatrixLoader
import com.example.sims_android.ui.event.RiskMatrixUI
import com.example.sims_android.ui.event.ConsequenceItemUI
import com.example.sims_android.ui.event.LikelihoodItemUI
import com.example.sims_android.ui.event.PriorityItemUI
import com.simsapp.data.repository.ProjectRepository
import com.google.gson.Gson
import java.io.File

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
 * @param projectName Optional project name for event creation navigation
 * @param onCreateEventForDefect Callback to create an event bound to this defect
 * @param onBack Callback when back button tapped
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefectDetailScreen(
    defectNo: String,
    projectUid: String? = null,
    projectName: String? = null,
    onCreateEventForDefect: (String) -> Unit,
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
    // 非图片数字资产预览弹窗
    var previewAsset by remember { mutableStateOf<DigitalAssetDetail?>(null) }
    // 风险矩阵预览弹窗（使用新事件页面的弹窗，仅预览不保存）
    var showRiskMatrix by remember { mutableStateOf(false) }
    var riskLoader by remember { mutableStateOf<RiskMatrixLoader?>(null) }

    // 页面背景与内容列表 + 顶部栏
    Scaffold(
        topBar = {
            com.simsapp.ui.common.AppTopBar(
                title = "Defect Details",
                onBack = onBack,
                containerColor = Color.White,
                titleColor = Color.Black,
                actions = {
                    // 顶部栏右侧 + Event 文本按钮
                    TextButton(onClick = { onCreateEventForDefect(defectNo) }) {
                        Text(text = "+ Event")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF6F7FB))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Basic Info card
                item { DetailSectionCard(section = state.basic) }
                // Others card
                item { DetailSectionCard(section = state.others) }
                // Picture Information card（缩略图点击后展示全屏覆盖层）
                item { PictureSection(images = state.images, onPhotoClick = { path -> fullScreenPhotoPath = path }) }
                // Asset 模块（底部）：图片类型直接展示缩略图，其他文件列表展示并支持预览
                item {
                    AssetSection(
                        imageAssets = state.assetImages,
                        otherAssets = state.assetOthers,
                        onImageClick = { path -> fullScreenPhotoPath = path },
                        onAssetClick = { asset ->
                            if (asset.type.equals("RISK_MATRIX", ignoreCase = true)) {
                                val p = asset.localPath
                                riskLoader = p?.let { buildRiskMatrixLoaderFromFile(it) }
                                showRiskMatrix = true
                            } else {
                                previewAsset = asset
                            }
                        }
                    )
                }
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

            // 非图片资产预览弹窗
            previewAsset?.let { asset ->
                AssetPreviewDialog(asset = asset, onDismiss = { previewAsset = null })
            }

            // 风险矩阵预览弹窗：仅预览，不保存答案、不展示评估结果
            if (showRiskMatrix) {
                RiskAssessmentWizardDialog(
                    onDismiss = { _ -> showRiskMatrix = false },
                    loader = riskLoader,
                    // 预览模式：不传 projectUid / DAO，不提供初始答案
                    projectUid = null,
                    projectDigitalAssetDao = null
                )
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

/**
 * AssetSection
 *
 * 底部资产模块：
 * - 图片类型：复用 PictureSection 的缩略图样式直接展示（来自 defect_data_asset.local_path）。
 * - 其他文件类型：复用事件页面的 DigitalAssetCategorizedDisplay，按类型分类列表显示并支持点击预览。
 *
 * @param imageAssets 资产图片本地路径列表
 * @param otherAssets 非图片资产详情列表
 * @param onImageClick 点击图片缩略图回调
 * @param onAssetClick 点击非图片资产回调（打开预览）
 */
@Composable
private fun AssetSection(
    imageAssets: List<String>,
    otherAssets: List<DigitalAssetDetail>,
    onImageClick: (String) -> Unit,
    onAssetClick: (DigitalAssetDetail) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(16.dp)
                        .background(Color(0xFF4A90E2))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Assets", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF333333))
            }
            // 图片资产
            if (imageAssets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                ThumbnailRow(images = imageAssets, onPhotoClick = onImageClick)
            }
            // 其他资产分类列表
            if (otherAssets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                DigitalAssetCategorizedDisplay(
                    assets = otherAssets,
                    onAssetClick = onAssetClick,
                    showDeleteIcon = false,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (imageAssets.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No assets",
                    fontSize = 13.sp,
                    color = Color(0xFF90A4AE)
                )
            }
        }
    }
}

/**
 * 构建风险矩阵加载器（从本地 JSON 文件）
 * 仅用于预览模式：读取 `localPath` 的 JSON 并映射为 UI 数据结构。
 *
 * @param localPath 风险矩阵缓存文件的本地路径
 * @return RiskMatrixLoader 返回 UI 数据或错误
 */
private fun buildRiskMatrixLoaderFromFile(localPath: String): RiskMatrixLoader = suspend {
    try {
        val f = File(localPath)
        if (!f.exists()) {
            Result.failure(IllegalStateException("Risk matrix file not found: $localPath"))
        } else {
            val json = withContext(Dispatchers.IO) { f.readText() }
            val payload = Gson().fromJson(json, ProjectRepository.RiskMatrixPayload::class.java)

            val c = payload.consequenceData.map {
                ConsequenceItemUI(
                    severityFactor = it.severityFactor,
                    cost = it.cost,
                    productionLoss = it.productionLoss,
                    safety = it.safety,
                    other = it.other
                )
            }
            val l = payload.likelihoodData.map {
                LikelihoodItemUI(
                    likelihoodFactor = it.likelihoodFactor,
                    criteria = it.criteria
                )
            }
            val p = payload.priorityData.map {
                PriorityItemUI(
                    priority = it.priority,
                    minValue = it.minValue,
                    maxValue = it.maxValue,
                    minInclusive = it.minInclusive,
                    maxInclusive = it.maxInclusive
                )
            }

            Result.success(RiskMatrixUI(c, l, p))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}