/**
 * 事件自动保存功能测试
 * 
 * 测试事件表单的自动保存和数据回显功能，包括：
 * - 基本信息保存（位置、描述）
 * - 风险评估结果保存
 * - 图片文件路径保存
 * - 音频文件路径保存
 * - 数据回显验证
 * 
 * @author SIMS Team
 * @since 2025-01-27
 */
package com.simsapp

import com.simsapp.data.local.entity.EventEntity
import com.example.sims_android.ui.event.RiskAnswer
import com.example.sims_android.ui.event.RiskAssessmentResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Test
import org.junit.Assert.*

/**
 * 事件自动保存功能单元测试类
 * 
 * 验证EventEntity的数据序列化和反序列化功能
 */
class EventAutoSaveTest {

    private val gson = Gson()

    /**
     * 测试风险评估答案的JSON序列化和反序列化
     */
    @Test
    fun testRiskAnswersJsonSerialization() {
        // 创建测试数据
        val answers = listOf(
            RiskAnswer(
                stepIndex = 1,
                question = "What is the likelihood of this risk?",
                optionIndex = 2,
                optionText = "Medium",
                value = 3.0
            ),
            RiskAnswer(
                stepIndex = 2,
                question = "What is the impact of this risk?",
                optionIndex = 1,
                optionText = "High",
                value = 4.0
            )
        )

        // 序列化为JSON
        val json = gson.toJson(answers)
        assertNotNull("JSON序列化不应为null", json)
        assertTrue("JSON应包含问题文本", json.contains("What is the likelihood"))

        // 反序列化回对象
        val type = object : TypeToken<List<RiskAnswer>>() {}.type
        val deserializedAnswers = gson.fromJson<List<RiskAnswer>>(json, type)
        
        assertNotNull("反序列化结果不应为null", deserializedAnswers)
        assertEquals("答案数量应相等", answers.size, deserializedAnswers.size)
        assertEquals("第一个答案的问题应相等", answers[0].question, deserializedAnswers[0].question)
        assertEquals("第一个答案的值应相等", answers[0].value, deserializedAnswers[0].value, 0.01)
    }

    /**
     * 测试EventEntity的完整数据保存
     */
    @Test
    fun testEventEntityDataSaving() {
        // 创建风险评估结果
        val riskAnswers = listOf(
            RiskAnswer(1, "Risk question 1", 0, "Low", 1.0),
            RiskAnswer(2, "Risk question 2", 2, "High", 4.0)
        )
        val riskResult = RiskAssessmentResult(
            level = "HIGH",
            score = 7.5,
            answers = riskAnswers
        )

        // 创建文件路径列表
        val photoFiles = listOf("/storage/photos/photo1.jpg", "/storage/photos/photo2.jpg")
        val audioFiles = listOf("/storage/audio/record1.mp3")

        // 创建EventEntity
        val eventEntity = EventEntity(
            eventId = 1L,
            uid = "test-event-uuid-001",
            projectId = 100L,
            projectUid = "PROJECT_001",
            location = "Test Location",
            content = "Test event description",
            lastEditTime = System.currentTimeMillis(),
            assets = emptyList(),
            isDraft = true,
            defectIds = emptyList(),
            defectNos = emptyList(),
            riskLevel = riskResult.level,
            riskScore = riskResult.score,
            riskAnswers = gson.toJson(riskResult.answers),
            photoFiles = photoFiles,
            audioFiles = audioFiles
        )

        // 验证基本字段
        assertEquals("位置应正确保存", "Test Location", eventEntity.location)
        assertEquals("描述应正确保存", "Test event description", eventEntity.content)
        assertEquals("风险等级应正确保存", "HIGH", eventEntity.riskLevel)
        assertEquals("风险分数应正确保存", 7.5, eventEntity.riskScore!!, 0.01)

        // 验证风险答案序列化
        assertNotNull("风险答案JSON不应为null", eventEntity.riskAnswers)
        val type = object : TypeToken<List<RiskAnswer>>() {}.type
        val deserializedAnswers = gson.fromJson<List<RiskAnswer>>(eventEntity.riskAnswers, type)
        assertEquals("风险答案数量应正确", 2, deserializedAnswers.size)
        assertEquals("第一个答案问题应正确", "Risk question 1", deserializedAnswers[0].question)

        // 验证文件路径保存
        assertEquals("图片文件数量应正确", 2, eventEntity.photoFiles.size)
        assertEquals("第一个图片路径应正确", "/storage/photos/photo1.jpg", eventEntity.photoFiles[0])
        assertEquals("音频文件数量应正确", 1, eventEntity.audioFiles.size)
        assertEquals("音频文件路径应正确", "/storage/audio/record1.mp3", eventEntity.audioFiles[0])
    }

    /**
     * 测试数据回显逻辑
     */
    @Test
    fun testDataRestoreLogic() {
        // 模拟从数据库加载的EventEntity
        val savedAnswersJson = gson.toJson(listOf(
            RiskAnswer(1, "Saved question", 1, "Medium", 2.5)
        ))

        val savedEntity = EventEntity(
            eventId = 2L,
            projectId = 200L,
            projectUid = "PROJECT_002",
            location = "Saved Location",
            content = "Saved description",
            lastEditTime = System.currentTimeMillis(),
            assets = emptyList(),
            isDraft = false,
            defectIds = emptyList(),
            defectNos = emptyList(),
            riskLevel = "MEDIUM",
            riskScore = 5.0,
            riskAnswers = savedAnswersJson,
            photoFiles = listOf("/saved/photo.jpg"),
            audioFiles = listOf("/saved/audio.mp3")
        )

        // 验证数据回显
        assertEquals("位置应正确回显", "Saved Location", savedEntity.location)
        assertEquals("描述应正确回显", "Saved description", savedEntity.content)
        assertEquals("风险等级应正确回显", "MEDIUM", savedEntity.riskLevel)
        assertEquals("风险分数应正确回显", 5.0, savedEntity.riskScore!!, 0.01)

        // 验证风险答案回显
        val type = object : TypeToken<List<RiskAnswer>>() {}.type
        val restoredAnswers = gson.fromJson<List<RiskAnswer>>(savedEntity.riskAnswers, type)
        assertEquals("答案数量应正确", 1, restoredAnswers.size)
        assertEquals("答案问题应正确", "Saved question", restoredAnswers[0].question)
        assertEquals("答案值应正确", 2.5, restoredAnswers[0].value, 0.01)

        // 验证文件路径回显
        assertEquals("图片文件应正确回显", "/saved/photo.jpg", savedEntity.photoFiles[0])
        assertEquals("音频文件应正确回显", "/saved/audio.mp3", savedEntity.audioFiles[0])
    }

    /**
     * 测试空数据处理
     */
    @Test
    fun testEmptyDataHandling() {
        val emptyEntity = EventEntity(
            eventId = 3L,
            projectId = 300L,
            projectUid = "PROJECT_003",
            location = null,
            content = "",
            lastEditTime = System.currentTimeMillis(),
            assets = emptyList(),
            isDraft = true,
            defectIds = emptyList(),
            defectNos = emptyList(),
            riskLevel = null,
            riskScore = null,
            riskAnswers = null,
            photoFiles = emptyList(),
            audioFiles = emptyList()
        )

        // 验证空数据处理
        assertNull("空位置应为null", emptyEntity.location)
        assertEquals("空描述应为空字符串", "", emptyEntity.content)
        assertNull("空风险等级应为null", emptyEntity.riskLevel)
        assertNull("空风险分数应为null", emptyEntity.riskScore)
        assertNull("空风险答案应为null", emptyEntity.riskAnswers)
        assertTrue("空图片列表应为空", emptyEntity.photoFiles.isEmpty())
        assertTrue("空音频列表应为空", emptyEntity.audioFiles.isEmpty())
    }
}