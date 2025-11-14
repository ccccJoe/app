# 共识：新建事件页返回数据清空与离开多次自动保存的修复

## 1. 明确需求与问题

- 问题一：在新建 Event 页面选择数字资产并返回后，已填写的其他数据项（如 Location、Description）被清空，期望保留。
- 问题二：离开新建 Event 页面触发自动保存时，偶尔会新建多个事件，期望仅新建一次；后续均为更新。

## 2. 验收标准

- 返回新建页面后，`Location` 与 `Description` 等输入不丢失，保持与返回前一致。
- 首次自动保存仅插入一条草稿事件（赋值为本地 `eventRoomId`）。
- 同一次操作中，风险评估变化、数字资产变化、缺陷选择变化、页面退出任意组合仅产生一次插入；其后保存均走更新分支。
- 构建通过且运行时无新增崩溃；日志能看到互斥保护与 `eventRoomId` 赋值行为。

## 3. 技术实现与约束

- 技术栈：Kotlin + MVVM + Jetpack Compose；Room；Hilt；Android 12+；JDK17。
- 文件与类注释规范：已在修改处补充函数/类/文件注释。
- 变更范围限定在 UI 层（`EventFormScreen.kt`），不影响既有仓库和领域层逻辑。

### 3.1 状态持久化（问题一修复）

- 将 `EventFormScreen` 中的输入状态：
  - `var location`、`var description` 从 `remember` 改为 `rememberSaveable(projectName, eventId)`。
- 原理：Compose 导航到资产选择页时，`EventFormScreen` 暂离组合；`remember` 状态随组合销毁丢失；`rememberSaveable` 借助 `SavedStateRegistry` 在返回时自动恢复。

### 3.2 并发自动保存互斥（问题二修复）

- 新增互斥标记：`var isAutoSaving by remember { mutableStateOf(false) }`。
- 在以下触发点加互斥与赋值：
  - `LaunchedEffect(riskResult, currentSelectedStorageFileIds)`：保存前检查 `isAutoSaving`，设置 true；完成后置 false；首插入后赋值 `eventRoomId = savedEventId`。
  - 缺陷选择确认（`DefectSelectionDialog.onConfirm`）：同上，首插入后赋值 `eventRoomId`。
  - 页面 `onDispose` 自动保存：若已在保存中则跳过；否则置 true 进行保存，并在首次插入后赋值 `eventRoomId`，最后置 false。
- 更新判断：统一以 `existingEventId = eventId.toLongOrNull() ?: eventRoomId` 判定编辑模式，避免仅依赖字符串 `eventId` 导致重复插入。

## 4. 任务边界与不确定性

- 边界：不调整 `EventFormViewModel.saveEventToRoom` 的存储结构与字段语义；仅在 UI 层修复状态与并发行为。
- 不确定性：
  - 若用户在极短时间内同时触发多个保存入口（如资产选择与缺陷确认），互斥标记能保证只执行一次，但无法防止外部取消导致的竞态；现版本通过在退出时强制完成一次保存来兜底。

## 5. 验收步骤

1. 编译与安装 UAT Debug；进入项目详情创建新事件。
2. 输入 `Location/Description`，打开数字资产选择器并选择若干资产后返回。
   - 期望：输入未被清空；列表正常回显。
3. 在风险评估或缺陷选择中分别做更改；观察保存日志不重复创建事件。
   - 期望：首次保存插入一条并打印 `eventRoomId` 赋值日志；其后为更新。
4. 直接返回上一页触发退出自动保存；确认只更新，不再新建。

## 6. 变更清单

- `EventFormScreen.kt`
  - 引入 `rememberSaveable`；将 `location/description` 切换为可保存状态。
  - 新增 `isAutoSaving` 互斥标记；在风险/资产变化、缺陷选择确认、页面退出自动保存分别加互斥保护与 `eventRoomId` 赋值。
  - 保持既有日志与初始状态更新，避免重复触发。

## 7. 影响评估

- 正向影响：
  - 导航往返后数据不丢失，显著提升填写体验。
  - 统一保存串行化，消除重复插入造成的“多个事件”。
- 负向风险：
  - 在极端竞态下可能跳过某次保存；页面退出保存作为兜底确保最终状态落库。

## 8. 结论

两项问题的根因分别为：`remember` 状态在导航离开时丢失与多入口自动保存并发导致的重复插入。通过 `rememberSaveable` 与互斥串行化修复后，满足验收标准并与现有架构对齐。