/*
 * File: DigitalAssetCategorizedDisplay.kt
 * Description: Compose component for displaying digital assets categorized by type with preview and delete functionality.
 * Author: SIMS Team
 */
package com.simsapp.ui.event.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sims_android.ui.event.DigitalAssetDetail

/**
 * DigitalAssetCategorizedDisplay
 *
 * 数字资产分类显示组件，根据资产类型分组显示，并支持预览功能。
 * 
 * @param assets 数字资产详细信息列表
 * @param onAssetClick 资产点击回调，传递资产信息用于预览
 * @param onAssetDelete 资产删除回调，传递资产信息用于移除当前选择
 * @param modifier 修饰符
 */
@Composable
/**
 * DigitalAssetCategorizedDisplay
 *
 * 数字资产分类显示组件，根据资产类型分组显示，并支持预览功能。
 * 新增 `showDeleteIcon` 参数以控制删除按钮的可见性，默认显示，
 * 以保证其他页面（如事件编辑/创建页）的行为不受影响。
 *
 * @param assets 数字资产详细信息列表
 * @param onAssetClick 资产点击回调，传递资产信息用于预览
 * @param onAssetDelete 资产删除回调，传递资产信息用于移除当前选择
 * @param showDeleteIcon 是否显示删除按钮（默认 true）
 * @param modifier 修饰符
 */
fun DigitalAssetCategorizedDisplay(
    assets: List<DigitalAssetDetail>,
    onAssetClick: (DigitalAssetDetail) -> Unit = {},
    onAssetDelete: (DigitalAssetDetail) -> Unit = {},
    showDeleteIcon: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (assets.isEmpty()) {
        Text(
            text = "No files selected",
            fontSize = 13.sp,
            color = Color(0xFF90A4AE),
            modifier = Modifier.padding(8.dp)
        )
        return
    }

    // 根据资产类型分组
    val categorizedAssets = remember(assets) {
        assets.groupBy { it.type }.toSortedMap()
    }

    // 去除内部卡片样式与“Database Files”标题，直接按照分类展示
    Column(modifier = modifier.fillMaxWidth().padding(12.dp)) {
        if (categorizedAssets.isNotEmpty()) {
            // 按类型分组显示
            categorizedAssets.forEach { (type, assets) ->
                AssetCategorySection(
                    categoryType = type,
                    assets = assets,
                    onAssetClick = onAssetClick,
                    onAssetDelete = onAssetDelete,
                    showDeleteIcon = showDeleteIcon,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }
        } else {
            Text(
                text = "No files selected",
                fontSize = 13.sp,
                color = Color(0xFF90A4AE)
            )
        }
    }
}

/**
 * AssetCategorySection
 *
 * 单个资产类别的显示区域
 */
@Composable
/**
 * AssetCategorySection
 *
 * Displays one asset category section with list items.
 *
 * @param categoryType Category identifier string
 * @param assets Assets under this category
 * @param onAssetClick Preview callback
 * @param onAssetDelete Delete callback
 */
private fun AssetCategorySection(
    categoryType: String,
    assets: List<DigitalAssetDetail>,
    onAssetClick: (DigitalAssetDetail) -> Unit,
    onAssetDelete: (DigitalAssetDetail) -> Unit,
    showDeleteIcon: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // 类别标题
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = getCategoryIcon(categoryType),
                contentDescription = categoryType,
                tint = getCategoryColor(categoryType),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = getCategoryDisplayName(categoryType),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF546E7A)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "(${assets.size})",
                fontSize = 12.sp,
                color = Color(0xFF90A4AE)
            )
        }
        
        Spacer(Modifier.height(6.dp))
        
        // 资产列表 - 根据类型选择不同的显示方式
        when (categoryType) {
            "IMAGE" -> {
                // 图片类型使用横向滚动的缩略图显示
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(assets) { asset ->
                        ImageAssetItem(
                            asset = asset,
                            onClick = { onAssetClick(asset) },
                            onDelete = { onAssetDelete(asset) },
                            showDeleteIcon = showDeleteIcon
                        )
                    }
                }
            }
            else -> {
                // 其他类型使用列表显示
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    assets.forEach { asset ->
                        AssetListItem(
                            asset = asset,
                            categoryType = categoryType,
                            onClick = { onAssetClick(asset) },
                            onDelete = { onAssetDelete(asset) },
                            showDeleteIcon = showDeleteIcon
                        )
                    }
                }
            }
        }
    }
}

/**
 * ImageAssetItem
 *
 * 图片资产项的缩略图显示
 */
@Composable
/**
 * ImageAssetItem
 *
 * Thumbnail item for image asset with preview and delete overlay.
 *
 * @param asset Digital asset
 * @param onClick Preview handler
 * @param onDelete Delete handler
 */
private fun ImageAssetItem(
    asset: DigitalAssetDetail,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    showDeleteIcon: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF5F5F5))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Image,
            contentDescription = "Image",
            tint = Color(0xFF42A5F5),
            modifier = Modifier.size(24.dp)
        )

        // Delete overlay button (top-right corner), controlled by flag
        if (showDeleteIcon) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete",
                        tint = Color(0xFF90A4AE),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * AssetListItem
 *
 * 资产列表项显示
 */
@Composable
/**
 * AssetListItem
 *
 * List item for non-image assets with preview and delete icons.
 *
 * @param asset Digital asset
 * @param categoryType Asset category string
 * @param onClick Preview handler
 * @param onDelete Delete handler
 */
private fun AssetListItem(
    asset: DigitalAssetDetail,
    categoryType: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    showDeleteIcon: Boolean,
    modifier: Modifier = Modifier
) {
    // 风险矩阵也允许点击以触发预览弹窗（参考新建事件页面）
    val canPreview = true
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFF8F9FA))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = getCategoryIcon(categoryType),
            contentDescription = categoryType,
            tint = getCategoryColor(categoryType),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = asset.fileName,
            fontSize = 13.sp,
            color = Color(0xFF37474F),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        
        // Preview icon
        Icon(
            imageVector = Icons.Default.Visibility,
            contentDescription = "Preview",
            tint = Color(0xFF90A4AE),
            modifier = Modifier.size(16.dp)
        )

        Spacer(Modifier.width(8.dp))

        // Delete icon (close), controlled by flag
        if (showDeleteIcon) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete",
                    tint = Color(0xFF90A4AE),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 根据资产类型获取对应的图标
 */
private fun getCategoryIcon(type: String): ImageVector {
    return when (type.uppercase()) {
        "PDF" -> Icons.Default.PictureAsPdf
        "IMAGE", "PIC" -> Icons.Default.Image
        "MP3", "AUDIO" -> Icons.Default.AudioFile
        "REC", "RECORDING" -> Icons.Default.Mic
        "RISK_MATRIX" -> Icons.Default.Assessment
        "VIDEO" -> Icons.Default.VideoFile
        "DOC", "DOCUMENT" -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }
}

/**
 * 根据资产类型获取对应的颜色
 */
private fun getCategoryColor(type: String): Color {
    return when (type.uppercase()) {
        "PDF" -> Color(0xFFE53935)
        "IMAGE", "PIC" -> Color(0xFF42A5F5)
        "MP3", "AUDIO" -> Color(0xFF66BB6A)
        "REC", "RECORDING" -> Color(0xFFFF7043)
        "RISK_MATRIX" -> Color(0xFF9C27B0)
        "VIDEO" -> Color(0xFFEF5350)
        "DOC", "DOCUMENT" -> Color(0xFF5C6BC0)
        else -> Color(0xFF78909C)
    }
}

/**
 * 根据资产类型获取显示名称
 */
private fun getCategoryDisplayName(type: String): String {
    return when (type.uppercase()) {
        "PDF" -> "PDF Documents"
        "IMAGE", "PIC" -> "Images"
        "MP3", "AUDIO" -> "Audio Files"
        "REC", "RECORDING" -> "Recordings"
        "RISK_MATRIX" -> "Risk Matrix"
        "VIDEO" -> "Videos"
        "DOC", "DOCUMENT" -> "Documents"
        else -> "Other Files"
    }
}