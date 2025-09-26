package com.simsapp.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 饼图组件（环形样式，带指示线与外部标签）
 * @param data 饼图数据，Pair<标签, 数值>，例如 ["已创建" to 5f, "收集中" to 12f]
 * @param modifier 修饰符
 */
@Composable
fun PieChart(
    data: List<Pair<String, Float>>,
    modifier: Modifier = Modifier
) {
    val total = data.sumOf { it.second.toDouble() }.toFloat()
    val colors = listOf(
        Color(0xFF4A90E2), // 蓝色 - 收集中/创建（二选一按顺序）
        Color(0xFF7ED321)  // 绿色 - 另一部分
    )
    val holeRatio = 0.55f // 环形中空比例

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 环形饼图绘制（Donut）+ 指示线与外部文本
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            if (total > 0f) {
                var startAngle = -90f // 从顶部开始
                // 以绘制区域中心和半径为基础
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val outerRadius = size.minDimension * 0.35f
                val holeRadius = outerRadius * holeRatio

                // 绘制各扇区
                data.forEachIndexed { index, (label, value) ->
                    if (value <= 0f) return@forEachIndexed
                    val sweepAngle = (value / total) * 360f
                    drawArc(
                        color = colors.getOrElse(index) { Color.Gray },
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = true,
                        topLeft = Offset(size.width * 0.15f, size.height * 0.15f),
                        size = Size(size.width * 0.7f, size.height * 0.7f)
                    )
                    startAngle += sweepAngle
                }

                // 中空形成环形
                drawCircle(
                    color = Color.White,
                    radius = holeRadius,
                    center = Offset(centerX, centerY)
                )

                // 重置角度用于绘制指示线和外部文本
                startAngle = -90f
                data.forEachIndexed { index, (label, value) ->
                    if (value <= 0f) return@forEachIndexed
                    val sweepAngle = (value / total) * 360f
                    val midAngleDeg = startAngle + sweepAngle / 2f
                    val rad = Math.toRadians(midAngleDeg.toDouble())

                    val lineLen = 10.dp.toPx()
                    val stroke = 1.dp.toPx()

                    val sx = (centerX + cos(rad).toFloat() * outerRadius)
                    val sy = (centerY + sin(rad).toFloat() * outerRadius)
                    val ex = (centerX + cos(rad).toFloat() * (outerRadius + lineLen))
                    val ey = (centerY + sin(rad).toFloat() * (outerRadius + lineLen))

                    // 第一段：从扇区边缘向外
                    drawLine(
                        color = Color(0xFFBDBDBD),
                        start = Offset(sx, sy),
                        end = Offset(ex, ey),
                        strokeWidth = stroke
                    )

                    // 计算象限（左上/右上/左下/右下）并将标签锚定至象限角落
                    val isRight = cos(rad) >= 0
                    val isTop = sin(rad) < 0
                    val margin = 6.dp.toPx()     // 角落外边距，避免触碰到画布边缘
                    val inset = 4.dp.toPx()      // 文本内缩

                    val anchorX = if (isRight) size.width - margin else margin
                    val anchorY = if (isTop) margin else size.height - margin

                    // 第二段：水平折线，指向角落的 X
                    drawLine(
                        color = Color(0xFFBDBDBD),
                        start = Offset(ex, ey),
                        end = Offset(anchorX, ey),
                        strokeWidth = stroke
                    )
                    // 第三段：竖直折线，指向角落的 Y（完成指向左上/右上/左下/右下）
                    drawLine(
                        color = Color(0xFFBDBDBD),
                        start = Offset(anchorX, ey),
                        end = Offset(anchorX, anchorY),
                        strokeWidth = stroke
                    )

                    // 文本：放置在角落附近，内缩以避免裁剪
                    val text = "${label}: ${value.toInt()}"
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = android.graphics.Color.parseColor("#666666")
                        textSize = 10.sp.toPx()
                    }
                    val textWidth = paint.measureText(text)
                    val tx = if (isRight) anchorX - inset - textWidth else anchorX + inset
                    val ty = if (isTop) anchorY + paint.textSize else anchorY - inset
                    drawContext.canvas.nativeCanvas.drawText(text, tx, ty, paint)

                    startAngle += sweepAngle
                }
            }
        }

        // 中心文字（总数）
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${total.toInt()}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            )
            Text(
                text = "项目",
                fontSize = 10.sp,
                color = Color(0xFF666666)
            )
        }
    }
}

/**
 * 分组柱状图组件（每个项目3根并列柱：历史Defect、已关联Defect、未关联Defect）
 * @param data 形如 Triple<ProjectName, SeriesLabel, Value> 的数据集合
 *             SeriesLabel 需使用："历史Defect"/"已关联Defect"/"未关联Defect"
 * @param modifier Compose 修饰符
 */
@Composable
fun BarChart(
    data: List<Triple<String, String, Float>>,
    modifier: Modifier = Modifier
) {
    // 将不同项目的数据按系列合计，只展示三根柱子
    val seriesOrder = listOf("历史Defect", "已关联Defect", "未关联Defect")
    val totals: List<Pair<String, Float>> = seriesOrder.map { label ->
        val sum = data.filter { it.second.contains(label) }
            .sumOf { it.third.toDouble() }
            .toFloat()
        label to sum
    }

    val yMax = (totals.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(1f)
    val barWidth = 18.dp

    BoxWithConstraints(modifier = modifier) {
        // 预留图例高度与间距，防止被父容器固定高度裁剪
        val legendHeight = 24.dp
        val spacing = 8.dp
        val available = maxHeight - legendHeight - spacing
        val chartHeight = if (available < 60.dp) 60.dp else available

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight),
                verticalAlignment = Alignment.Bottom
            ) {
                // Y轴刻度
                val tickCount = 4
                val ticks = (0..tickCount).map { i -> (yMax * i / tickCount) }

                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .fillMaxHeight()
                ) {
                    // 刻度数字（自上而下 yMax..0）
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        ticks.reversed().forEach { v ->
                            Text(
                                text = v.toInt().toString(),
                                fontSize = 9.sp,
                                color = Color(0xFF999999)
                            )
                        }
                    }
                }

                // 图形区域（网格 + 三根柱子）
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // 网格线
                    Column(
                        modifier = Modifier.matchParentSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        repeat(5) {
                            // Replace deprecated Divider usage
                            HorizontalDivider(color = Color(0xFFEAEAEA), thickness = 1.dp)
                        }
                    }

                    // 三根并列柱
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        totals.forEachIndexed { index, (_, value) ->
                            val heightDp = ((value / yMax) * chartHeight.value).dp
                            val color = when (index) {
                                0 -> Color(0xFF4A90E2) // 历史Defect
                                1 -> Color(0xFF7ED321) // 已关联Defect
                                else -> Color(0xFF2E7D32) // 未关联Defect
                            }
                            Box(
                                modifier = Modifier
                                    .width(barWidth)
                                    .height(heightDp)
                                    .background(color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing))
            // 图例：一行显示在柱状图下方
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(legendHeight),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(color = Color(0xFF4A90E2), label = "历史Defect")
                Spacer(modifier = Modifier.width(16.dp))
                LegendItem(color = Color(0xFF7ED321), label = "已关联Defect")
                Spacer(modifier = Modifier.width(16.dp))
                LegendItem(color = Color(0xFF2E7D32), label = "未关联Defect")
            }
        }
    }
}

/**
 * 图例项组件
 * @param color 颜色
 * @param label 标签
 */
@Composable
private fun LegendItem(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color(0xFF666666)
        )
    }
}

/**
 * 饼图图例组件（保留：当前不在首页使用）
 * @param data 饼图数据
 */
@Composable
fun PieChartLegend(
    data: List<Pair<String, Float>>
) {
    val colors = listOf(
        Color(0xFF4A90E2),
        Color(0xFF7ED321),
        Color(0xFFE8F4FD)
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        data.forEachIndexed { index, (label, value) ->
            LegendItem(
                color = colors.getOrElse(index) { Color.Gray },
                label = "$label: ${value.toInt()}"
            )
        }
    }
}