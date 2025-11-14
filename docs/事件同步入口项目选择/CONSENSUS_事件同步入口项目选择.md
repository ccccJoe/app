# 事件同步入口项目选择

## 需求描述

- 在事件表单 `EventFormScreen` 中，用户点击 Sync 按钮时，若存在未完成项目（状态非 Finish/Close），先弹出项目选择底部弹窗（ProjectPickerBottomSheet）让用户选择目标项目；确认后携带所选项目的 `projectUid` 作为覆盖参数上传同步。
- 若不存在未完成项目，则保持现有逻辑，直接上传同步，不弹窗。

## 验收标准

- 当存在未完成项目时，点击 Sync 弹出底部弹窗；默认选中项为当前页面内的 `projectUid`（若有效）或列表第一项。
- 用户选择并确认后，调用 `viewModel.uploadEventWithSyncRetry` 时 `overrideTargetProjectUid` 参数为所选项目的 `projectUid`。
- 当没有未完成项目时，点击 Sync 不弹窗，且旧的同步逻辑无回归。
- 代码编译通过（UAT 变体已验证），不引入其他模块的错误。

## 技术实现方案

- 组件：复用 `com.simsapp.ui.common.ProjectPickerBottomSheet`（Material3 `ModalBottomSheet`，单选列表）。
- 订阅：在 `EventFormScreen` 顶层通过 `viewModel.getNotFinishedProjects().collectAsState()` 获取未完成项目列表。
- 状态：在 `EventFormScreen` 顶层声明 `var showProjectPicker by remember { mutableStateOf(false) }` 控制弹窗显示。
- 触发逻辑：Sync 按钮点击处判定 `notFinishedProjects.isNotEmpty()` 则 `showProjectPicker = true`；否则直接执行上传。
- 确认逻辑：底部弹窗 `onConfirm` 中计算最终事件 UID（优先通过 Room ID 解析 UID；否则使用已加载 UID），并调用 `viewModel.uploadEventWithSyncRetry(..., overrideTargetProjectUid = target.projectUid)`。
- 兼容：保持原有结构缺陷、数字资产、风险评估等参数传递一致，不改动其业务逻辑。

## 技术约束与集成

- 架构：MVVM + Jetpack Compose，遵循已有的 ViewModel/Repository 数据流。
- 依赖：Material3 底部弹窗需 `@OptIn(ExperimentalMaterial3Api::class)`；`ProjectEntity.isDeleted` 为 Boolean 类型，预览数据修正为 `false`。
- 语言与版本：Kotlin、JDK17；与工程当前 Gradle 插件和 Compose 版本兼容。

## 任务边界

- 仅在 `EventFormScreen` 的 Sync 入口增加“目标项目选择”能力；不改动 Dashboard、其他同步入口及服务器端接口定义。
- 保持原有图片、音频、PDF、结构缺陷和数字资产保存与回显逻辑不变。
- 不引入新的网络接口或数据库结构变更。

## 不确定性与解决

- `ProjectPickerBottomSheet` 预览数据中 `isDeleted` 类型不匹配：已修正为 Boolean。
- Material3 `ModalBottomSheet` 实验性 API：已在组件内添加 `@OptIn(ExperimentalMaterial3Api::class)`。
- `projectUid` 默认选中项：从页面状态加载，若为空回退为列表第一项。

## 验收验证

- 已完成 `app:compileUatDebugKotlin` 编译验证，编译成功。
- 交互流程：存在未完成项目时弹窗选择目标项目，确认后成功传参触发上传。

## 后续建议

- 增加 UI 测试：校验弹窗出现与默认选中项行为。
- 增加集成测试：在离线/弱网场景下复核 `overrideTargetProjectUid` 的同步一致性与重试策略。