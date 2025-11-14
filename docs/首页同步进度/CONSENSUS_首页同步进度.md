# 首页同步进度（项目级）

## 1. 需求与验收
- 需求：点击首页“Sync”按钮后，在同步过程中显示 Loading 叠层，并实时展示项目级进度信息：`已完成 X / 总计 Y`。
- 验收标准：
  - 同步开始时显示全屏遮罩与进度文本/进度条，阻断其他交互。
  - 进度中的 `X` 随每个需更新项目完成而递增，`Y` 为本次需要更新的项目总数（增量更新集合）。
  - 同步成功时隐藏遮罩，并出现完成提示（如 Snackbar）。
  - 同步失败时隐藏遮罩，显示错误原因，进度停止在当前值。
  - 网络不可用或网络质量差时，同步被拦截并给出友好提示，不出现进度遮罩。

## 2. 现状分析（基于当前代码）
- 入口：`DashboardViewModel.syncProjects()` 触发同步；UI 在 `DashboardScreen.kt` 中通过 `SyncConfirmDialog` 进行网络校验，并调用 `viewModel.syncProjects()`。
- 状态：`SyncUiState(isLoading, count, error)` 暴露加载态与最终新增数量，但未提供中间进度。
- 仓库：`ProjectRepository.syncProjectsFromEndpoint()` 完成列表拉取、增量判定与详情缓存。在 `projectsToUpdate` 循环中逐个处理项目详情与数字资产，是最佳进度钩点。
- 参考：`ProjectDetailViewModel` 已有事件上传进度 `MutableStateFlow(SyncProgress)` 可复用设计模式。

## 3. 技术实现方案（推荐）
### 3.1 数据结构与状态流
- 新增数据类：`ProjectSyncProgress(completed: Int, total: Int, isLoading: Boolean, message: String? = null)`。
- 在 `DashboardViewModel` 中新增：`private val _projectSyncProgress = MutableStateFlow(ProjectSyncProgress(0, 0, false)); val projectSyncProgress: StateFlow<ProjectSyncProgress>`。
- `SyncUiState` 保持不变，用于开始/结束与错误提示；进度采用单独的 `projectSyncProgress` 管理（关注粒度更清晰）。

### 3.2 仓库层进度回调
- 给 `ProjectRepository.syncProjectsFromEndpoint()` 增加可选回调参数：`onProjectProgress: ((completed: Int, total: Int) -> Unit)? = null`。
- 实现：
  - 解析远端列表后构建 `projectsToUpdate`，令 `total = projectsToUpdate.size`；调用一次 `onProjectProgress?.invoke(0, total)`。
  - 在循环处理每个项目详情与资产缓存完成后，累加 `completed` 并回调 `onProjectProgress?.invoke(completed, total)`。
  - 结束时返回 `Result<Int>`（保持兼容）。

### 3.3 ViewModel 更新逻辑
- 在 `DashboardViewModel.syncProjects()` 中：
  - 同步开始：`_syncState.value = SyncUiState(isLoading = true)`；`_projectSyncProgress.value = ProjectSyncProgress(0, 0, true, "Initializing…")`。
  - 调用仓库方法并传入回调：回调内更新 `_projectSyncProgress.value`（completed/total）。
  - 成功/失败：结束时 `isLoading=false`，并通过 `_uiEvents` 弹出完成或错误提示。

### 3.4 UI 展示
- `DashboardScreen`：
  - 继续使用 `FullscreenLoadingOverlay`，将其扩展为可传入 `progressText` 与 `progressFraction`（`completed/total`）。
  - 文案示例：`"同步中：已完成 ${completed} / ${total} 个项目"`；当 `total==0` 时显示 `"同步中：准备项目列表…"`。
  - 按钮禁用：`isSyncing` 控制 Sync 按钮禁用；遮罩阻断其他交互。

## 4. 同步流程（逻辑时序）
1) 用户点击 Sync → 弹出确认 → 通过网络校验。
2) ViewModel 设置 Loading 与初始进度（0/0）。
3) 仓库拉取项目列表，计算 `projectsToUpdate` → 回调 `0/Y`。
4) 循环处理每个待更新项目详情与资产 → 每完成一个项目回调 `X/Y`。
5) 完成后更新计数器与日志 → ViewModel 关闭 Loading，触发 UI 完成提示。
6) 异常：任何阶段出错均关闭 Loading，并提示错误信息，进度停留在当前值。

## 5. 异常处理与健壮性
- 网络：沿用 `SyncConfirmDialog` 的网络检测逻辑；网络不佳直接拦截，不进入进度。
- 解析为空：远端列表为空时 `Y=0`；显示 `"无待更新项目"`，快速结束。
- 单项失败：某个项目详情失败时仅记录日志并跳过，不影响其他项目；进度仍按已完成数量前进。
- 取消：后续可扩展为提供取消按钮，通过共享 `Job` 取消协程并保持当前进度显示。

## 6. 任务边界与非目标
- 仅显示“项目级”同步进度（X/Y）；不涉及事件/缺陷上传的子阶段进度。
- 不改变现有接口返回结构与本地数据模型（除新增进度流与仓库回调）。
- 不引入新第三方库；仅使用 Kotlin Flow/StateFlow。

## 7. 验收与测试
- 手动测试：
  - 线上/测试环境各一次；包含列表为空、部分更新、大量更新三类场景。
  - 断网/弱网校验弹窗拦截与不展示进度遮罩。
- 日志校验：`SIMS-SYNC` 与 `SIMS-SYNC-PROGRESS` 输出，核对 `X/Y` 的单调递增与最终一致性。
- UI 验收：遮罩出现/消失节奏正确；文案准确；按钮禁用与解锁正确。

## 8. 关键决策点（请确认）
- Y 的定义：
  - 方案 A（推荐）：`projectsToUpdate.size`（增量需更新总数）。
  - 方案 B：`remoteEntities.size`（远端列表总数，含未变更项目）。
- 进度粒度：每“完成一个项目详情与资产缓存”算 1；是否需要更细粒度（如资产节点）暂不纳入本需求。
- 文案语言：中文 UI 文案是否固定为“项目”，是否需要多语言切换。

---
以上为不改动代码前的实现逻辑与集成方案，待确认后可按本方案在 ViewModel/Repository/UI 三处进行最小改动接入进度显示。