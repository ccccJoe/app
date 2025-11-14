/*
 * File: SectionCard.kt
 * Description: Common UI card component with a standardized header and optional trailing actions.
 * Author: SIMS Android Team
 *
 * 用途：统一事件表单等页面的卡片样式（圆角、阴影、分隔线），
 *      提供标题区与右侧动作位（如加号、箭头），避免各处硬编码样式不一致。
 */
package com.simsapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 类注释：统一的卡片组件，包含标题和可选的右侧动作区。
 * - 支持自定义形状与容器颜色
 * - 标题区内置左右结构，左标题文本、右动作位（IconButton等）
 * - 下方自动绘制分隔线，之后承载自定义内容
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    containerColor: Color = Color.White,
    headerContentColor: Color = Color(0xFF333333),
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = headerContentColor
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailing?.invoke(this)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

/**
 * 函数注释：统一的标题区动作按钮样式（灰底圆角），用于加号/箭头等。
 * @param icon 显示的图标
 * @param contentDescription 无障碍描述
 * @param tint 图标颜色（默认使用主题primary）
 * @param onClick 点击事件回调
 */
@Composable
fun HeaderActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF0F2F5)
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
            Icon(imageVector = icon, contentDescription = contentDescription, tint = tint)
        }
    }
}