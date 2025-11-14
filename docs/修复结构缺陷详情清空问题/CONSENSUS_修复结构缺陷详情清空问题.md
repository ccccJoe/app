# 共识文档：修复“Structural Defect Details 在选择数字资产后被清空”的问题

## 明确的需求描述

- 在新事件页面（New Event），用户先填写“Structural Defect Details”，随后进入“DataBase files/数字资产选择”页面并确认返回时，之前填写的结构缺陷详情不得被清空，应保持原样并参与后续自动保存与手动保存。

## 验收标准

- 执行顺序：先填写结构缺陷详情 → 进入数字资产选择 → 选择并确认返回。
- 返回后，结构缺陷详情仍然显示为之前填写的内容，不被清空；随后触发的自动保存不会用空值覆盖该字段。
- 多次往返数字资产选择页面、以及普通导航/重组均不丢失结构缺陷详情。
- 构建通过，无运行时崩溃；不影响其它模块功能（照片、音频、风险评估、缺陷关联、同步）。

## 技术实现方案与技术约束

- 架构：保持 MVVM + Jetpack Compose 原有结构不变。
- 方案：
  - 将 `EventFormScreen.kt` 中的 `structuralDefectJsonRaw` 从 `remember` 改为 `rememberSaveable`，通过 `SavedInstanceState` 持久化，避免跳转后状态丢失。
  - 新增 `LaunchedEffect(structuralDefectJsonRaw)`，在 JSON 恢复时解析为 `StructuralDefectData`，保证对象态与 JSON 同步。
  - 保持现有保存逻辑：保存时优先使用 `structuralDefectJsonRaw ?: structuralDefectResult?.let { Gson().toJson(it) }`，在状态持久化后可避免空值覆盖数据库。
- 约束：
  - 不修改 Room、Repository、UseCase 的字段定义与接口签名。
  - 不新增 UI 控件与交互路径（无视觉变更）。
  - 文件行数遵循单文件 ≤ 500 行（本次改动为局部状态管理，不引入大体量代码）。

## 任务边界限制

- 仅修复新事件页面 `EventFormScreen.kt` 的结构缺陷详情被清空问题。
- 不改动数字资产功能本身的数据结构与选择流程。
- 不变更同步逻辑、权限逻辑、音频录制、风险评估等其它模块。

## 集成方案

- 代码改动点：
  - `EventFormScreen.kt`：
    - `structuralDefectJsonRaw` 改为 `rememberSaveable`。
    - 增加 `LaunchedEffect(structuralDefectJsonRaw)` 解析恢复对象。
  - 构建：`./gradlew :app:assembleDebug` 已通过。

## 不确定性清单（已解决）

- 结构缺陷详情在返回后为何为空：原因是 `remember` 状态丢失，返回后自动保存以空值覆盖数据库。
- 如何避免覆盖：持久化 JSON，并在保存时使用持久化值，避免空值覆盖。

## 关键假设

- `StructuralDefectData` 的 JSON 序列化/反序列化稳定、字段兼容当前版本。
- 数字资产选择流程可能导致页面临时移除或重组，需使用 `rememberSaveable` 来持久化关键输入。

## 验收建议（可测试）

- 手动流程测试：
  - 新建事件，填入结构缺陷详情 → 进入数字资产选择 → 确认返回 → 结构缺陷详情仍在。
  - 重复上述流程多次，内容不丢失。
- 编辑已有事件：
  - 打开事件，确保数据库已有结构缺陷详情 → 进入数字资产选择 → 返回后仍正常显示。
- 触发自动保存：
  - 修改资产后观察自动保存日志，确保 `structural_defect_details` 不为空或未被重置。

## 质量门控对齐

- 需求边界清晰无歧义：是。
- 技术方案与现有架构对齐：是（Compose 状态管理增强）。
- 验收标准具体可测试：是（步骤、结果明确）。
- 关键假设已确认：是。
- 项目特性规范已对齐：是（Jetpack、MVVM、Room、Kotlin）。
- 使用原生安卓开发：是。
- 不影响原有代码功能：是（无跨模块修改）。
- 提醒缺少的依赖库和文件：无新增依赖，现有环境满足。
- 不能偷懒不能省略代码且有对应注释：已在关键改动处添加函数/文件级注释。