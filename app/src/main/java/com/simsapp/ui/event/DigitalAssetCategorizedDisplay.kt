/*
 * File: DigitalAssetCategorizedDisplay.kt
 * Description: 数字资产分类显示组件，按类型分组显示数字资产
 * Author: SIMS Team
 */
package com.simsapp.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sims_android.ui.event.DigitalAssetDetail

/**
 * 数字资产分类显示组件
 * 
 * @param assets 数字资产详情列表
 * @param onAssetClick 资产点击回调
 */
@Composable
fun DigitalAssetCategorizedDisplay(
    assets: List<DigitalAssetDetail>,
    onAssetClick: (DigitalAssetDetail) -> Unit = {}
) {
    if (assets.isEmpty()) {
        Text(
            text = "No files selected",
            color = Color.Gray,
            modifier = Modifier.padding(16.dp)
        )
        return
    }

    // 按类型分组
    val groupedAssets = assets.groupBy { it.type }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        groupedAssets.forEach { (type, assetList) ->
            item {
                // 类型标题
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = getIconForType(type),
                        contentDescription = null,
                        tint = getColorForType(type),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = getDisplayNameForType(type),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = getColorForType(type)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${assetList.size})",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            items(assetList) { asset ->
                AssetItem(
                    asset = asset,
                    onClick = { onAssetClick(asset) }
                )
            }
        }
    }
}

/**
 * 单个资产项组件
 */
@Composable
private fun AssetItem(
    asset: DigitalAssetDetail,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { 
                // 只有非风险矩阵类型才能预览
                if (asset.type != "RISK_MATRIX") {
                    onClick()
                }
            }
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (asset.type == "RISK_MATRIX") Color(0xFFF5F5F5) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getIconForType(asset.type),
                contentDescription = null,
                tint = getColorForType(asset.type),
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = asset.fileName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = Color.Black
                )
                
                if (asset.localPath?.isNotEmpty() == true) {
                    Text(
                        text = "本地文件",
                        fontSize = 12.sp,
                        color = Color(0xFF4CAF50)
                    )
                } else {
                    Text(
                        text = "云端文件",
                        fontSize = 12.sp,
                        color = Color(0xFF2196F3)
                    )
                }
            }
            
            if (asset.type != "RISK_MATRIX") {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "预览",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 根据资产类型获取对应图标
 */
private fun getIconForType(type: String): ImageVector {
    return when (type) {
        "PIC" -> Icons.Default.Image
        "PDF" -> Icons.Default.PictureAsPdf
        "REC" -> Icons.Default.VideoFile
        "MP3" -> Icons.Default.AudioFile
        "RISK_MATRIX" -> Icons.Default.Assessment
        else -> Icons.Default.InsertDriveFile
    }
}

/**
 * 根据资产类型获取对应颜色
 */
private fun getColorForType(type: String): Color {
    return when (type) {
        "PIC" -> Color(0xFF4CAF50)
        "PDF" -> Color(0xFFF44336)
        "REC" -> Color(0xFF9C27B0)
        "MP3" -> Color(0xFFFF9800)
        "RISK_MATRIX" -> Color(0xFF607D8B)
        else -> Color.Gray
    }
}

/**
 * 根据资产类型获取显示名称
 */
private fun getDisplayNameForType(type: String): String {
    return when (type) {
        "PIC" -> "图片"
        "PDF" -> "PDF文档"
        "REC" -> "录像"
        "MP3" -> "音频"
        "RISK_MATRIX" -> "风险矩阵"
        else -> "其他文件"
    }
}