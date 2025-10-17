/*
 * File: DigitalAssetCategorizedDisplay.kt
 * Description: Compose component for displaying digital assets categorized by type with preview functionality.
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
 * @param modifier 修饰符
 */
@Composable
fun DigitalAssetCategorizedDisplay(
    assets: List<DigitalAssetDetail>,
    onAssetClick: (DigitalAssetDetail) -> Unit = {},
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

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = "Database Files",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Color(0xFF333333)
            )
            HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
            
            if (categorizedAssets.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                
                // 按类型分组显示
                categorizedAssets.forEach { (type, assets) ->
                    AssetCategorySection(
                        categoryType = type,
                        assets = assets,
                        onAssetClick = onAssetClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No files selected",
                    fontSize = 13.sp,
                    color = Color(0xFF90A4AE)
                )
            }
        }
    }
}

/**
 * AssetCategorySection
 *
 * 单个资产类别的显示区域
 */
@Composable
private fun AssetCategorySection(
    categoryType: String,
    assets: List<DigitalAssetDetail>,
    onAssetClick: (DigitalAssetDetail) -> Unit,
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
                            onClick = { onAssetClick(asset) }
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
                            onClick = { onAssetClick(asset) }
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
private fun ImageAssetItem(
    asset: DigitalAssetDetail,
    onClick: () -> Unit,
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
    }
}

/**
 * AssetListItem
 *
 * 资产列表项显示
 */
@Composable
private fun AssetListItem(
    asset: DigitalAssetDetail,
    categoryType: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canPreview = categoryType.uppercase() != "RISK_MATRIX"
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (canPreview) Color(0xFFF8F9FA) else Color(0xFFF0F0F0))
            .clickable(enabled = canPreview) { 
                if (canPreview) {
                    onClick() 
                }
            }
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
            color = if (canPreview) Color(0xFF37474F) else Color(0xFF90A4AE),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        
        // 根据是否可预览显示不同图标
        if (canPreview) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = "Preview",
                tint = Color(0xFF90A4AE),
                modifier = Modifier.size(16.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = "Cannot preview",
                tint = Color(0xFF90A4AE),
                modifier = Modifier.size(16.dp)
            )
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