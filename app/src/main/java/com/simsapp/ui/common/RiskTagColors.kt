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
     * 浏览器色值: rgb(200, 6, 6) → Android Compose: 0xFFC80606
     * 深红背景，白色文字，保障可读性与警示效果
     */
    val P1 = ColorPair(
        backgroundColor = Color(0xFFC80606), // 高风险红
        textColor = Color.White
    )
    
    /**
     * P2级别 - 中等风险
     * 浏览器色值: rgb(237, 197, 0) → Android Compose: 0xFFEDC500
     * 亮黄橙背景，为保证对比度使用黑色文字（白字对比度不足）
     */
    val P2 = ColorPair(
        backgroundColor = Color(0xFFEDC500), // 中风险橙黄
        textColor = Color.White // 黑色文字，增强对比度
    )
    
    /**
     * P3级别 - 低风险
     * 浏览器色值: rgb(65, 177, 246) → Android Compose: 0xFF41B1F6
     * 明亮蓝背景，白色文字对比度良好
     */
    val P3 = ColorPair(
        backgroundColor = Color(0xFF41B1F6), // 低风险蓝
        textColor = Color.White
    )
    
    /**
     * P4级别 - 极低风险
     * 浏览器色值: rgb(6, 178, 89) → Android Compose: 0xFF06B259
     * 绿色背景，白色文字
     */
    val P4 = ColorPair(
        backgroundColor = Color(0xFF06B259), // 极低风险绿
        textColor = Color.White
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
            "P1" -> ColorPair(Color(0xFFC80606), Color.White) // 红色+白色
            "P2" -> ColorPair(Color(0xFFEDC500), Color(0xFF000000)) // 橙黄+黑字（可读性更佳）
            "P3" -> ColorPair(Color(0xFF41B1F6), Color.White) // 蓝色+白色
            "P4" -> ColorPair(Color(0xFF06B259), Color.White) // 绿色+白色
            "HIGH" -> ColorPair(Color(0xFFC80606), Color.White)
            "MEDIUM" -> ColorPair(Color(0xFF41B1F6), Color.White)
            "LOW" -> ColorPair(Color(0xFF06B259), Color.White)
            else -> ColorPair(Color(0xFF9E9E9E), Color.White) // 灰色+白色
        }
    }
}