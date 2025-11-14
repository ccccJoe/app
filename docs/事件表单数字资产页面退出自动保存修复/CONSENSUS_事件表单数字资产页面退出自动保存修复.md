# CONSENSUS ｜事件表单数字资产页面退出自动保存修复

> 任务名：事件表单数字资产页面退出自动保存修复

## 需求与现象

- 在 Event 详情页，仅添加数字资产（不修改其他字段）后直接离开页面，应自动更新当前 Event；但本地 Room 表未发生变化。
- 若添加数字资产后同时修改了其他字段（如位置/描述），离开页面会自动保存全部改动。

## 根因分析

- 页面退出自动保存逻辑的进入条件未包含“数字资产非空”，导致仅资产变更场景被误判为“无内容无需保存”。
- 退出时事件 ID 解析对 `eventId` 为 UID 的场景不够稳健，可能导致编辑态误判为新建态（风险：重复插入）。

## 技术实现方案

1. 补充页面退出自动保存的进入条件：
   - 增加 `currentSelectedStorageFileIds.isNotEmpty()` 或 `lastNonEmptySelectedStorageFileIds.isNotEmpty()` 判断。
2. 强化事件 ID 解析：
   - 先尝试 `eventId.toLongOrNull()` 作为 Room 主键；若失败，则按 UID 反查主键 `viewModel.getEventRoomIdByUid(eventId)`；
   - 回退到已加载的 `loadedEventUid` 与已有的 `eventRoomId`，确保编辑态更新而非插入。
3. 日志增强：
   - 在跳过保存与成功保存处增加资产计数与模式信息，便于现场排查。

## 变更范围与影响

- 修改文件：`EventFormScreen.kt`（页面退出保存逻辑）。
- 不涉及 UI 可视变更，属行为逻辑修复。
- 与现有 `LaunchedEffect` 的首次插入与资产回显逻辑保持一致，不影响已存在的自动保存与兜底策略。

## 验收标准（可测试）

1. 仅资产变更：
   - 打开已有 Event，不改位置/描述/风险，仅从存储选择一个数字资产后返回上一页；
   - 预期：Room 中该 Event 的 `assets` 更新；日志包含 `Auto-saved event on page exit` 且 `isEditMode=true`。
2. 资产+其他字段变更：
   - 与现状一致，能正常保存；日志显示保存成功。
3. 新建态首次保存：
   - 新建 Event 仅添加数字资产后退出；
   - 预期：插入成功，`eventRoomId` 被赋值；日志显示 `isEditMode=false` 与保存成功。
4. 空内容退出：
   - 不添加任何字段与资产直接退出；
   - 预期：不保存；日志显示 `Skipped auto-save on page exit: no data changes detected`。

## 边界与限制

- 如果用户在资产选择返回后立即二次返回，极端情况下可能与 Compose 生命周期竞态，但由于本修复将“进入条件”与“兜底资产”整合，此场景仍能保存。
- 不涉及服务端同步与断点续传逻辑，专注本地 Room 更新。

## 不确定性与确认

- 事件 UID 与 Room 主键的互查接口已在 `EventFormViewModel` 与仓库层实现；本次修复沿用现有接口，无需新增 API。
- 现有风险评估与结构化缺陷的回显逻辑不变，不纳入本次修复范围。

## 与现有架构对齐

- 技术栈：Kotlin + MVVM + Room + Compose，符合工程规范。
- 代码规范：新增函数级注释，单文件修改控制在 500 行内，无额外依赖。

## 回归建议

- 针对 Event 编辑/新建两种模式分别测试：仅资产变更、仅文本变更、两者都变更、空内容退出。
- 检查 `StoragePicker` 资产回显与 `lastNonEmptySelectedStorageFileIds` 兜底是否生效。

—— End of Consensus ——