# CONSENSUS\_风险矩阵纯预览

## 需求描述

- 在缺陷详情页（`DefectDetailScreen`）的资产区域中：
  - 允许点击 `RISK_MATRIX` 类型数字资产以进行“纯预览”。
  - 打开风险评估弹窗（复用 `RiskAssessmentWizardDialog`），仅展示向导，不保存任何评估结果。
- 调整事件资产分类组件（`com.simsapp.ui.event.components.DigitalAssetCategorizedDisplay`）：
  - 移除内部“Database Files”卡片样式与标题，仅按分类直接展示。
  - 允许所有类型（包括 `RISK_MATRIX`）点击进入预览回调。

## 边界与范围

- 仅影响缺陷详情页的非图片资产点击行为与资产列表视觉样式（移除“Database Files”卡片）。
- 不修改事件创建/编辑页（`EventFormScreen`）对 `RISK_MATRIX` 的点击限制逻辑。
- 不修改仓库层、DAO 或同步逻辑，不改变本地/云端数据结构。
- 预览模式不进行任何保存到数据库或本地文件的操作。

## 技术实现方案

- 组件层（Compose）：
  - 文件：`com.simsapp.ui.event.components.DigitalAssetCategorizedDisplay.kt`
    - 去除内层 `Card` 和“Database Files”标题，直接按 `type` 分组渲染。
    - 将列表项的 `canPreview` 固定为 `true`，使 `RISK_MATRIX` 可点击。
    - 保持图标、颜色与显示名的类型映射不变（已支持 `RISK_MATRIX`）。
- 页面层（缺陷详情）：
  - 文件：`com.simsapp.ui.defect.DefectDetailScreen.kt`
    - 在 `AssetSection` 的 `onAssetClick` 中对 `RISK_MATRIX` 分支：
      - 从资产的 `localPath` 构建本地 JSON 加载器 `buildRiskMatrixLoaderFromFile`。
      - 设置弹窗显示 `showRiskMatrix = true`，并向 `RiskAssessmentWizardDialog` 传入该加载器。
    - 预览模式不传 `projectUid` 与 `projectDigitalAssetDao`，不提供初始答案回显。
  - 新增函数：`buildRiskMatrixLoaderFromFile(localPath: String): RiskMatrixLoader`
    - 读取本地 JSON（`ProjectDigitalAssetEntity.content` 的落地文件），用 `Gson` 解析为 `ProjectRepository.RiskMatrixPayload`，映射为 `RiskMatrixUI`。

## 依赖与集成

- 语言与版本：Kotlin，JDK17（`app/build.gradle.kts` 中 `jvmToolchain(17)` 已对齐）。
- 依赖：Compose、Hilt（已存在），`Gson`（已在项目中使用）。
- 复用文件：`RiskAssessmentWizard.kt` 中的数据模型与弹窗组件。

## 验收标准（可测试）

- 构建通过：`assembleUatDebug` 成功，无编译错误。
- UI：
  - 缺陷详情页资产区域不再显示“Database Files”卡片，仅按分类列表展示。
  - `RISK_MATRIX` 类型资产在缺陷详情页可点击。
  - 点击后弹出风险评估向导，能正常加载本地 JSON 并显示题目与选项。
  - 关闭弹窗后不发生任何保存或自动上传动作。
- 行为隔离：事件表单页仍不允许 `RISK_MATRIX` 点击预览（保留原限制）。
- 兼容性：未影响图片、PDF、音频等其他类型预览行为。

## 风险与不确定性

- 若 `localPath` 为空或无法读取文件，弹窗将提示加载器缺失或读取失败。
  - 后续可优化：在点击前做更严格的文件存在性检测并反馈 Toast。
- 项目存在两个 `DigitalAssetCategorizedDisplay` 文件（`ui.event` 与 `ui.event.components`），本次改动仅影响缺陷详情页引用的 `components` 版本。

## 结论

- 方案与现有架构对齐，改动集中于展示层（Compose），不改动数据与领域层。
- 纯预览需求已实现并通过 UAT Debug 构建验证。