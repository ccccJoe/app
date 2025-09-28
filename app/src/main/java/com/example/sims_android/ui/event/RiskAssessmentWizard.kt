/**
 * File: RiskAssessmentWizard.kt
 * Purpose: Compose UI components for a step-by-step risk assessment wizard and entry icon.
 * Author: SIMS Android Team
 *
 * This file provides:
 * - RiskAssessmentIcon: a lightweight blue swirl-like icon drawn with Canvas.
 * - RiskAssessmentWizardDialog: a 5-step questionnaire wizard used to evaluate risk level.
 *
 * Notes:
 * - Keep UI texts in Chinese based on product requirement screenshots.
 * - The wizard is self-contained and returns a simple RiskAssessmentResult.
 */
package com.example.sims_android.ui.event

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.simsapp.data.repository.ProjectRepository
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import com.simsapp.R

/**
 * Data class: RiskAnswer
 * - Represents a single selection in the 5-step questionnaire.
 * - Persisted as part of risk meta so that backend can audit how the result is obtained.
 *
 * @param stepIndex 1-based step index (1..5)
 * @param question The question title shown to user
 * @param optionIndex The index of selected option within the step
 * @param optionText The text of selected option
 * @param value The numeric value behind the option
 */
data class RiskAnswer(
    val stepIndex: Int,
    val question: String,
    val optionIndex: Int,
    val optionText: String,
    val value: Double
)

/**
 * Data class: RiskAssessmentResult
 * - Holds final risk level text and optional total score for future analytics.
 * - answers: optional detailed selections for each step, used for local persistence and sync upload.
 */
data class RiskAssessmentResult(
    val level: String,
    val score: Double,
    val answers: List<RiskAnswer>? = null
)

/**
 * Composable: RiskAssessmentIcon
 * - A simple blue swirl-style icon using arcs; serves as entry point before the Media section.
 *
 * @param size Desired square size of icon area.
 */
@Composable
fun RiskAssessmentIcon(size: Dp) {
    // 使用矢量资源展示风险矩阵图标，保持与需求提供图片风格一致
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_tornado),
            contentDescription = "Risk Matrix Icon",
            tint = Color.Unspecified, // 使用图标本身的描边颜色
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 以下轻量模型复制自仓库定义，避免直接 import 仓库层以降低 UI 耦合度。
 */
data class ConsequenceItemUI(
     val severityFactor: Double,
     val cost: String,
     val productionLoss: String,
     val safety: String,
     val other: String
 )
 data class LikelihoodItemUI(
     val likelihoodFactor: Double,
     val criteria: String?
 )
 data class PriorityItemUI(
     val priority: String,
     val minValue: Double,
     val maxValue: Double,
     val minInclusive: String,
     val maxInclusive: String
 )
 data class RiskMatrixUI(
     val consequenceData: List<ConsequenceItemUI>,
     val likelihoodData: List<LikelihoodItemUI>,
     val priorityData: List<PriorityItemUI>
 )

 /**
 * 类型别名：用于注入远程加载函数。
 */
typealias RiskMatrixLoader = suspend () -> Result<RiskMatrixUI>

/**
 * Composable: RiskAssessmentWizardDialog
 * - 按产品要求从远程下载风险矩阵配置，生成 5 个问题：
 *   1-4题：来自 consequenceData 每个元素的 cost / productionLoss / safety / other 字段，外加 Not applicable；分值为对应元素的 severity_factor
 *   5题：来自 likelihoodData 的 criteria 文本，分值为 likelihood_factor
 * - Not applicable 最多允许选择 3 次
 * - 风险等级计算：max(前四题分值) * 第五题分值，按 priorityData 的 criteria 区间匹配，区间符号 [ ] 为含，( ) 为不含
 *
 * @param onDismiss 关闭回调，返回结果或 null
 * @param loader 远程配置加载函数，默认不提供实现方需传入
 */
@Composable
fun RiskAssessmentWizardDialog(
    onDismiss: (RiskAssessmentResult?) -> Unit,
    loader: RiskMatrixLoader? = null,
    // initialAnswers: 上次保存的答案列表，用于在弹窗中预选各步骤的选项，提升编辑体验
    initialAnswers: List<RiskAnswer>? = null
) {
    // 加载远程配置
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var payload by remember { mutableStateOf<RiskMatrixUI?>(null) }

    LaunchedEffect(Unit) {
        if (loader == null) {
            error = "缺少配置加载器"
            loading = false
        } else {
            val r = loader.invoke()
            if (r.isSuccess) payload = r.getOrNull() else error = r.exceptionOrNull()?.message
            loading = false
        }
    }

    fun inclusiveLeft(c: String) = c.startsWith("[")
    fun inclusiveRight(c: String) = c.endsWith("]")

    /**
     * 计算结果并构造答案清单。
     *
     * @param selections 用户在 5 个步骤中的选择索引（允许 null）
     * @return RiskAssessmentResult 包含 level / score / answers（answers 可为空以兼容旧数据）
     */
    fun computeResult(selections: List<Int?>): RiskAssessmentResult? {
        val p = payload ?: return null
        // 防越界：前四题存在 "Not applicable" 追加项，其索引等于 consequenceData.size，需按 0 分处理
        val s1 = selections.take(4).map { sel ->
            sel?.let { i -> if (i in p.consequenceData.indices) p.consequenceData[i].severityFactor else 0.0 } ?: 0.0
        }
        // 防越界：第五题无 NA 选项，但仍做索引保护
        val s5 = selections.getOrNull(4)?.let { i -> if (i in p.likelihoodData.indices) p.likelihoodData[i].likelihoodFactor else 0.0 } ?: 0.0
        val finalScore = (s1.maxOrNull() ?: 0.0) * s5
        var level = "P4"
        for (pi in p.priorityData) {
            val leftOk = if (inclusiveLeft(pi.minInclusive)) finalScore >= pi.minValue else finalScore > pi.minValue
            val rightOk = if (inclusiveRight(pi.maxInclusive)) finalScore <= pi.maxValue else finalScore < pi.maxValue
            if (leftOk && rightOk) {
                level = pi.priority
                break
            }
        }
        // 组装 answers：1..4题文本来自 consequenceData 对应字段 + NA 兜底；第5题来自 likelihoodData
        val answers = mutableListOf<RiskAnswer>()
        // 1..4 题
        for (step in 0 until 4) {
            val sel = selections.getOrNull(step)
            if (sel != null) {
                val isNA = sel !in p.consequenceData.indices
                val text = if (!isNA) {
                    val item = p.consequenceData[sel]
                    when (step) {
                        0 -> item.cost
                        1 -> item.productionLoss
                        2 -> item.safety
                        else -> item.other
                    }
                } else "Not applicable"
                val value = if (!isNA) p.consequenceData[sel].severityFactor else 0.0
                answers.add(
                    RiskAnswer(
                        stepIndex = step + 1,
                        question = listOf("COST Consequence","Potential Production Loss","SAFETY Consequence","OTHER Consequence")[step],
                        optionIndex = sel,
                        optionText = text,
                        value = value
                    )
                )
            }
        }
        // 第 5 题（概率）
        val sel5 = selections.getOrNull(4)
        if (sel5 != null && sel5 in p.likelihoodData.indices) {
            val item = p.likelihoodData[sel5]
            val text = item.criteria ?: item.likelihoodFactor.toString()
            answers.add(
                RiskAnswer(
                    stepIndex = 5,
                    question = "PROBABILITY",
                    optionIndex = sel5,
                    optionText = text,
                    value = item.likelihoodFactor
                )
            )
        }
        // 返回 priorityData 中定义的优先级编码（P1~P4），score 保持 Double 精度，不做取整或四舍五入
        return RiskAssessmentResult(level = level, score = finalScore, answers = answers)
    }

    Dialog(onDismissRequest = { onDismiss(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0x33000000))) {
            Surface(
                modifier = Modifier.padding(24.dp).align(Alignment.Center),
                shape = RoundedCornerShape(16.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier.widthIn(min = 300.dp, max = 460.dp).padding(18.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "风险矩阵评估", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
                    Spacer(Modifier.height(10.dp))

                    when {
                        loading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("正在加载配置…")
                            }
                        }
                        error != null -> {
                            Text(text = error ?: "加载失败", color = Color(0xFFD32F2F))
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { onDismiss(null) }) { Text("关闭") }
                        }
                        payload != null -> {
                            val p = payload!!
                            // 构造 5 步问题
                            data class Step(val title: String, val options: List<String>, val valueProvider: (Int) -> Double)
                            val notApplicable = "Not applicable"
                            val consequenceOptions = List(4) { idx ->
                                // 对应 cost/productionLoss/safety/other
                                val labelProvider: (ConsequenceItemUI) -> String = when (idx) {
                                    0 -> { it -> it.cost }
                                    1 -> { it -> it.productionLoss }
                                    2 -> { it -> it.safety }
                                    else -> { it -> it.other }
                                }
                                val opts = p.consequenceData.map(labelProvider) + notApplicable
                                val valueProvider: (Int) -> Double = { i -> if (i < p.consequenceData.size) p.consequenceData[i].severityFactor else 0.0 }
                                Step(
                                    title = listOf("COST Consequence","Potential Production Loss","SAFETY Consequence","OTHER Consequence")[idx],
                                    options = opts,
                                    valueProvider = valueProvider
                                )
                            }
                            val likelihoodStep = Step(
                                title = "PROBABILITY",
                                options = p.likelihoodData.map { it.criteria ?: it.likelihoodFactor.toString() },
                                valueProvider = { i -> p.likelihoodData[i].likelihoodFactor }
                            )
                            val steps = consequenceOptions + likelihoodStep

                            var index by remember { mutableStateOf(0) }
                            val selections = remember { mutableStateListOf<Int?>(*(Array(steps.size) { null })) }
                            var naCount by remember { mutableStateOf(0) }

                            // 根据 initialAnswers 进行预选与 NA 次数回显
                            LaunchedEffect(initialAnswers, p) {
                                if (!initialAnswers.isNullOrEmpty()) {
                                    initialAnswers.forEach { ans ->
                                        val stepIdx = ans.stepIndex - 1
                                        if (stepIdx in selections.indices) {
                                            selections[stepIdx] = ans.optionIndex
                                        }
                                    }
                                    // 统计前四题 NA 次数（索引等于 consequenceData.size 视为 NA）
                                    naCount = (0 until minOf(4, selections.size)).count { sIdx ->
                                        val sel = selections[sIdx]
                                        sel != null && sel >= p.consequenceData.size
                                    }
                                }
                            }

                            // 渲染当前步骤
                            val current = steps[index]
                            Text(text = current.title, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(10.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                current.options.forEachIndexed { i, opt ->
                                    val selected = selections[index] == i
                                    val borderColor = if (selected) Color(0xFF1565C0) else Color(0xFF90CAF9)
                                    val bg = if (selected) Color(0xFFE3F2FD) else Color.White
                                    val textColor = if (selected) Color(0xFF0D47A1) else Color(0xFF1F2937)
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                // 统一处理 NA 次数与状态切换（仅针对前四题）
                                                val old = selections[index]
                                                if (index < 4) {
                                                    val isNAOption = (opt == "Not applicable")
                                                    val wasNA = (old != null && old >= p.consequenceData.size)
                                                    // 选择 NA：最多 3 次
                                                    if (isNAOption && !selected) {
                                                        if (naCount >= 3) return@clickable
                                                        naCount += 1
                                                    }
                                                    // 从 NA 切换到非 NA：回收一次 NA 计数
                                                    if (!isNAOption && wasNA) {
                                                        naCount = (naCount - 1).coerceAtLeast(0)
                                                    }
                                                    // 再次点击已选的 NA：视作取消选择，回收 NA 计数并清空该题选择
                                                    if (isNAOption && selected) {
                                                        naCount = (naCount - 1).coerceAtLeast(0)
                                                        selections[index] = null
                                                        return@clickable
                                                    }
                                                }
                                                selections[index] = i
                                            },
                                        color = bg,
                                        tonalElevation = 0.dp,
                                        shadowElevation = 0.dp,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                         Text(
                                             text = opt,
                                             modifier = Modifier.padding(12.dp),
                                             color = textColor,
                                             style = MaterialTheme.typography.bodyMedium
                                         )
                                     }
                             }

                            Spacer(Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                OutlinedButton(onClick = {
                                    if (index == 0) onDismiss(null) else index -= 1
                                }) { Text(if (index == 0) "取消" else "上一步") }
                                Button(
                                    onClick = {
                                        if (index < steps.lastIndex) {
                                            if (selections[index] == null) return@Button
                                            index += 1
                                        } else {
                                            // 完成计算
                                            val r = computeResult(selections.toList())
                                            if (r != null) onDismiss(r) else onDismiss(null)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                                ) { Text(if (index < steps.lastIndex) "下一步" else "完成") }
                            }
                        }
                    }
                }
        } // Close Column
      } // Close Surface
    } // Close Box
  } // Close Dialog
} // Close RiskAssessmentWizardDialog

/**
 * 函数：buildRiskMatrixLoaderFromRepository
 * 说明：
 * - 将仓库层 ProjectRepository.fetchRiskMatrix 返回的数据映射为本文件内部 UI 数据模型
 * - 以函数类型返回，供调用处直接传给 RiskAssessmentWizardDialog 的 loader 参数
 *
 * @param repo ProjectRepository 实例（通过 Hilt 注入）
 * @param endpoint 解析真实下载地址接口
 * @param fileIds 文件ID数组（仅第一个使用）
 */
fun buildRiskMatrixLoaderFromRepository(
    repo: ProjectRepository,
    endpoint: String,
    fileIds: List<String>
): RiskMatrixLoader = suspend {
    repo.fetchRiskMatrix(endpoint, fileIds).map { payload ->
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
        RiskMatrixUI(c, l, p)
    }
}

/**
 * Composable: RiskResultBar
 * 文件级说明：用于在页面展示风险矩阵评估的结果条。
 * - 左到右依次为 P4(低)、P3(中)、P2(高)、P1(极高)，颜色由绿到红。
 * - 高亮当前等级对应的段，并展示主结果（P 码与 score）。
 *
 * @param result 评估向导计算出来的结果对象（包含等级与累积分数）
 * @param modifier 外部布局修饰符
 */
@Composable
fun RiskResultBar(
    result: RiskAssessmentResult,
    modifier: Modifier = Modifier
) {
    // 将 P4(低)、P3(中)、P2(高)、P1(极高) 从左到右映射到 0..3 段位
    val levelToIndex = remember(result.level) {
        mapOf("P4" to 0, "P3" to 1, "P2" to 2, "P1" to 3)
    }
    val idx = levelToIndex[result.level] ?: 0
    val colors = listOf(Color(0xFF4CAF50), Color(0xFFFFC107), Color(0xFFFF7043), Color(0xFFD32F2F))

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().height(18.dp)) {
            (0..3).forEach { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (i == idx) colors[i] else colors[i].copy(alpha = 0.35f))
                ) {}
                if (i != 3) Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        // 主显示：P1~P4；score 直接显示 Double 原值
        Text(text = "评估结果：${result.level} (score=${result.score})", color = Color(0xFF37474F))
    }
}

// 删除重复 RiskAssessmentIcon（modifier 版本）以避免冲突
// @Composable
// fun RiskAssessmentIcon(
//     modifier: Modifier = Modifier
// ) {
//     Icon(
//         painter = painterResource(id = R.drawable.ic_tornado),
//         contentDescription = "Risk Assessment Tornado Icon",
//         tint = Color.Unspecified,
//         modifier = modifier
//     )
// }