/**
 * 文件级注释：风险等级颜色配置
 * 
 * 该文件定义了整个应用中风险等级标签的统一颜色方案，确保所有界面中的风险等级显示保持一致性。
 * 采用浅色背景+深色文字的Material Design风格，提供良好的可读性和视觉效果。
 * 
 * 支持的风险等级：P0(极高)、P1(高)、P2(中)、P3(低)、P4(极低)，以及HIGH、MEDIUM、LOW等级别。
 * 
 * @author SIMS Android Team
 * @since 1.0.0
 */
package com.simsapp.ui.common

import androidx.compose.ui.graphics.Color

/**
 * 风险等级颜色配置对象
 * 
 * 提供统一的风险等级颜色方案，包括背景色和文字色的配对。
 * 所有风险等级相关的UI组件都应该使用此配置来保持一致性。
 */
object RiskTagColors {
    
    /**
     * 风险等级颜色配对数据类
     * 
     * @property backgroundColor 背景颜色
     * @property textColor 文字颜色
     */
    data class ColorPair(
        val backgroundColor: Color,
        val textColor: Color
    )
    
    /**
     * P0级别 - 极高风险
     * 深红色背景，白色文字，用于最高级别的风险警示
     */
    val P0 = ColorPair(
        backgroundColor = Color(0xFFB71C1C), // 深红色
        textColor = Color.White
    )
    
    /**
     * P1级别 - 高风险
     * 浅红色背景，深红色文字
     */
    val P1 = ColorPair(
        backgroundColor = Color(0xFFFFEBEE), // 浅红色背景
        textColor = Color(0xFFD32F2F) // 深红色文字
    )
    
    /**
     * P2级别 - 中等风险
     * 浅橙色背景，深橙色文字
     */
    val P2 = ColorPair(
        backgroundColor = Color(0xFFFFF3E0), // 浅橙色背景
        textColor = Color(0xFFFF5722) // 深橙色文字
    )
    
    /**
     * P3级别 - 低风险
     * 浅黄色背景，深黄色文字
     */
    val P3 = ColorPair(
        backgroundColor = Color(0xFFFFF8E1), // 浅黄色背景
        textColor = Color(0xFFF57C00) // 深黄色文字
    )
    
    /**
     * P4级别 - 极低风险
     * 浅绿色背景，深绿色文字
     */
    val P4 = ColorPair(
        backgroundColor = Color(0xFFE8F5E8), // 浅绿色背景
        textColor = Color(0xFF388E3C) // 深绿色文字
    )
    
    /**
     * HIGH级别 - 高风险（等同于P1）
     */
    val HIGH = P1
    
    /**
     * MEDIUM级别 - 中等风险（等同于P3）
     */
    val MEDIUM = P3
    
    /**
     * LOW级别 - 低风险（等同于P4）
     */
    val LOW = P4
    
    /**
     * 默认级别 - 未知或其他风险等级
     * 浅灰色背景，深灰色文字
     */
    val DEFAULT = ColorPair(
        backgroundColor = Color(0xFFF5F5F5), // 浅灰色背景
        textColor = Color(0xFF666666) // 深灰色文字
    )
    
    /**
     * 根据风险等级字符串获取对应的颜色配对
     * 
     * @param riskLevel 风险等级字符串（不区分大小写）
     * @return 对应的颜色配对，如果未找到匹配项则返回默认配对
     */
    fun getColorPair(riskLevel: String): ColorPair {
        return when (riskLevel.trim().uppercase()) {
            "P0" -> P0
            "P1" -> P1
            "P2" -> P2
            "P3" -> P3
            "P4" -> P4
            "HIGH" -> HIGH
            "MEDIUM" -> MEDIUM
            "LOW" -> LOW
            else -> DEFAULT
        }
    }
    
    /**
     * 获取实心风险标签的颜色配对（用于关联缺陷列表等需要突出显示的场景）
     * 
     * 对于P0级别保持深色背景+白色文字的强烈警示效果，
     * 其他级别使用对应的深色背景+白色文字组合。
     * 
     * @param riskLevel 风险等级字符串（不区分大小写）
     * @return 实心标签的颜色配对
     */
    fun getSolidColorPair(riskLevel: String): ColorPair {
        return when (riskLevel.trim().uppercase()) {
            "P0" -> P0 // 保持原有的深红色+白色
            "P1" -> ColorPair(Color(0xFFD32F2F), Color.White) // 红色+白色
            "P2" -> ColorPair(Color(0xFFFF5722), Color.White) // 橙色+白色
            "P3" -> ColorPair(Color(0xFFF57C00), Color.White) // 黄色+白色
            "P4" -> ColorPair(Color(0xFF388E3C), Color.White) // 绿色+白色
            "HIGH" -> ColorPair(Color(0xFFD32F2F), Color.White)
            "MEDIUM" -> ColorPair(Color(0xFFF57C00), Color.White)
            "LOW" -> ColorPair(Color(0xFF388E3C), Color.White)
            else -> ColorPair(Color(0xFF9E9E9E), Color.White) // 灰色+白色
        }
    }
}