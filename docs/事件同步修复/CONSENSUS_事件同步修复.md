# 事件同步修复 - 共识文档

## 问题描述

在事件同步上传过程中，`meta.json` 文件中缺少 `event` 表中的 `risk_answers` 数据。具体表现为：

1. `meta.json` 中 `risk.answers` 字段只包含解析后的简化数据结构
2. 原始的 `risk_answers` JSON 数据在打包上传时丢失
3. 导致服务端无法获取完整的风险评估答案数据

## 问题分析

### 根本原因

经过深入分析，发现问题出现在 `EventFormViewModel.kt` 的 `saveEventToLocal` 方法中：

1. **第 520-535 行**：使用了 `RiskMeta` 数据类来构建 `meta.json`
2. **RiskMeta 类定义**：只包含 `priority`、`score` 和简化的 `answers` 字段
3. **数据转换问题**：`RiskMeta` 的 `answers` 字段只保存了 `optionText`，丢失了原始的完整 JSON 数据

### 影响范围

- `EventFormViewModel.kt` 中的 `saveEventToLocal` 方法
- 所有通过该方法生成的 `meta.json` 文件
- 事件同步上传功能的数据完整性

## 解决方案

### 1. 修复 EventFormViewModel.kt

**文件位置**：`app/src/main/java/com/example/sims_android/ui/event/EventFormViewModel.kt`

**修复内容**：第 520-565 行，完全重写 `meta.json` 生成逻辑

**修复前**：

```kotlin
// Build meta content
val meta = EventMeta(
    uid = uid,
    projectName = projectName,
    location = location,
    description = description,
    risk = riskResult?.let { RiskMeta(priority = it.level, score = it.score, answers = it.answers?.map { answer -> answer.optionText } ?: emptyList()) },
    photos = savedPhotos.toList(),
    audios = savedAudios.toList(),
    digitalAssets = digitalAssetFileIds,
    createdAt = createdAtPersisted
)
val metaFile = File(eventDir, "meta.json")
metaFile.writeText(gson.toJson(meta))
```

**修复后**：

```kotlin
// Build meta content with original risk_answers data
val riskData = mutableMapOf<String, Any>()
riskData["level"] = riskResult?.level ?: ""
riskData["score"] = riskResult?.score ?: 0.0

// 获取原始的risk_answers数据
val savedEvent = eventRepo.getEventByUid(uid)
val originalRiskAnswers = savedEvent?.riskAnswers

if (!originalRiskAnswers.isNullOrEmpty()) {
    try {
        // 直接将原始的risk_answers JSON字符串作为answers字段的值
        val rawAnswersData = gson.fromJson(originalRiskAnswers, Any::class.java)
        riskData["answers"] = rawAnswersData
    } catch (e: Exception) {
        Log.w("EventFormVM", "Failed to parse risk answers JSON: ${e.message}")
        riskData["answers"] = originalRiskAnswers as Any
    }
} else {
    riskData["answers"] = ""
}

val metaData = mapOf(
    "eventId" to (savedEvent?.eventId ?: 0),
    "uid" to uid,
    "projectId" to (savedEvent?.projectId ?: 0),
    "projectUid" to (savedEvent?.projectUid ?: ""),
    "location" to location,
    "content" to description,
    "lastEditTime" to createdAtPersisted,
    "risk" to riskData,
    "photoFiles" to savedPhotos.toList(),
    "audioFiles" to savedAudios.toList(),
    "defectIds" to (savedEvent?.defectIds ?: emptyList<Long>()),
    "defectNos" to (savedEvent?.defectNos ?: emptyList<String>()),
    "isDraft" to (savedEvent?.isDraft ?: false)
)

val metaFile = File(eventDir, "meta.json")
metaFile.writeText(gson.toJson(metaData))
```

### 2. EventDirectoryMigrationUtil.kt 修复状态

**文件位置**：`app/src/main/java/com/simsapp/utils/EventDirectoryMigrationUtil.kt`

**修复状态**：✅ **已完成** - 第 108-124 行的修复逻辑正确，直接使用原始 `risk_answers` 数据

## 验收标准

1. **数据完整性**：`meta.json` 中 `risk.answers` 字段包含 `event` 表中完整的 `risk_answers` 原始数据
2. **向后兼容**：修复不影响现有功能的正常运行
3. **异常处理**：JSON 解析失败时有适当的降级处理
4. **编译成功**：修改后的代码能够正常编译通过

## 影响范围

- 事件创建和编辑时的 `meta.json` 生成逻辑
- 事件目录迁移时的 `meta.json` 生成逻辑
- 事件同步上传功能的数据完整性

### 3. 移除重复字段

**问题**：在修复过程中发现 `meta.json` 中同时存在 `risk.answers` 和 `risk.risk_answers` 两个字段，造成数据冗余。

**解决方案**：

- 移除了 `EventFormViewModel.kt` 中 `saveEventToLocal` 和 `saveEventToRoom` 方法中的重复 `risk_answers` 字段
- 移除了 `EventDirectoryMigrationUtil.kt` 中的重复 `risk_answers` 字段
- 保留 `risk.answers` 字段作为唯一的风险答案数据源

## 修复后的 meta.json 数据结构

```json
{
  "eventId": 23,
  "uid": "c339549-d85f-47ed-a534-c3da4f31d22e",
  "projectId": 27,
  "projectUid": "197618537781629337",
  "location": "111",
  "content": "222",
  "lastEditTime": 1761297762961,
  "risk": {
    "level": "P3",
    "score": 3,
    "answers": {
      "risk_action_required_remediation_timeframe": "Action required within 1 month",
      "risk_cost": "1.0",
      "risk_likelihood": "1.0",
      "risk_matrix_id": "default_matrix_id",
      "risk_other": "3.0",
      "risk_potential_production_loss": "3.0",
      "risk_rating": "P3",
      "risk_safety": "2.0"
    }
  },
  "photoFiles": [],
  "audioFiles": []
}
```

## 验收标准

1. **数据完整性**：`meta.json` 中 `risk.answers` 字段包含 `event` 表中完整的 `risk_answers` 原始数据
2. **向后兼容**：修复不影响现有功能的正常运行
3. **异常处理**：JSON 解析失败时有适当的降级处理
4. **编译成功**：修改后的代码能够正常编译通过
5. **数据唯一性**：移除重复的 `risk_answers` 字段，避免数据冗余

## 影响范围

- 事件创建和编辑时的 `meta.json` 生成逻辑
- 事件目录迁移时的 `meta.json` 生成逻辑
- 事件同步上传功能的数据完整性

## 风险评估

**低风险**：

1. 修改仅涉及 `meta.json` 生成逻辑，不影响数据库存储
2. 保持了向后兼容性，现有数据不受影响
3. 添加了异常处理，确保系统稳定性

## 实施状态

- ✅ **已完成**：`EventFormViewModel.kt` 修复
- ✅ **已完成**：`EventDirectoryMigrationUtil.kt` 修复
- ✅ **已完成**：移除重复 `risk_answers` 字段
- ✅ **已完成**：代码编译验证

## 后续计划

1. **功能测试**：验证修改后的打包上传功能，确保 `risk_answers` 数据正确包含在 `meta.json` 中
2. **回归测试**：确保现有事件管理功能不受影响
3. **性能监控**：观察修改后的同步上传性能表现

## 技术细节

1. **数据保存策略**：直接使用数据库中的原始 `risk_answers` JSON 数据
2. **异常处理**：JSON 解析失败时降级为字符串存储
3. **数据结构优化**：移除冗余字段，保持数据结构清晰

---

**文档版本**：v2.1  
**最后更新**：2025-01-27  
**更新内容**：完成 EventFormViewModel.kt 的关键修复，项目编译成功