# CONSENSUS_事件表单读取修复

## 背景与问题
- 历史事件的风险评估答案在 `EventEntity.riskAnswers` 中可能存储为两种格式：
  - 旧格式：`List<RiskAnswer>` 的 JSON 数组；
  - 新格式：`RiskAssessmentData` 的对象（包含 `risk_cost`、`risk_likelihood`、`risk_rating` 等字段）。
- `meta.json` 的风险结构中，等级键名历史上有过差异（`priority` vs `level`）。
- 事件读取仅通过 `defectIds` 和 `defectNos` 关联缺陷，未统一兼容远端缺陷 `uid`。

## 需求与范围（明确且无歧义）
- 兼容双格式风险答案读取：优先解析对象 `RiskAssessmentData`，否则解析数组 `List<RiskAnswer>`。
- 从本地 `meta.json` 读取风险评估时：
  - 风险等级优先使用 `risk.level`，兼容旧键 `risk.priority`；
  - `risk.answers` 支持 `JSONObject`（对象）、`JSONArray`（数组）、`String`（字符串）三种形态。
- 事件关联缺陷读取扩展支持 `defectUids`，并优先以 `projectUid + uid` 定位缺陷，若项目 `uid` 不可用则直接以缺陷 `uid` 回退。
- 保持既有 `defectIds` 和 `defectNos` 的逻辑不变，不修改 Room 表结构或 DAO。

## 技术实现方案与约束
- 文件改动：
  - `EventFormViewModel.kt`
    - 新增 `getDefectByUid(uid: String)` 与 `getDefectByProjectUidAndUid(projectUid: String, uid: String)` 方法，封装仓库调用与异常日志。
  - `EventFormScreen.kt`
    - 数据库读取风险评估部分：尝试将 `riskAnswers` 解析为 `RiskAssessmentData`（对象），若失败再解析为 `List<RiskAnswer>`（数组）；填充 `RiskAssessmentResult(assessmentData/answers)`。
    - `meta.json` 回显风险评估：等级优先 `level`，回退 `priority`；`answers` 三态解析（`JSONObject`/`JSONArray`/`String`）。
    - 关联缺陷读取：聚合 `defectIds`、`defectNos`、`defectUids` 三源，并去重（以 `defectId` 比较）。
- 约束与对齐：
  - 技术栈：Kotlin、Jetpack、Gson；不引入新依赖；遵循 MVVM。
  - 不改变数据库结构和网络接口；仅 UI/VM 层读取逻辑增强。

## 集成方案
- 与当前事件保存与同步逻辑对齐：
  - `saveEventToRoom` 写入 `riskAnswers`（对象/数组均可），`meta.json` 在写入时使用 `risk.level` 与原始 `risk_answers` 字符串作为 `risk.answers`；读取时现已双向兼容。
  - 事件的缺陷计数更新逻辑不变（依赖 `defectIds`）。`defectUids` 仅用于读取补全，不更改计数规则。

## 验收标准（可测试）
- 打开历史事件：
  - 风险等级与分数正确显示；
  - `risk.answers` 为对象时解析成功并不报错；
  - `risk.answers` 为数组时解析成功并不报错；
  - `risk.answers` 为字符串时能尽量解析为对象/数组，解析失败时不崩溃且记录日志。
- 关联缺陷：
  - 当 `EventEntity` 仅包含 `defectUids` 时，能正确加载并展示关联缺陷；
  - 同时存在 `defectIds`/`defectNos`/`defectUids` 时，最终集合去重且完整。
- 构建与测试：
  - 运行 `:app:testDebugUnitTest` 通过（无编译错误）。
- 对原有功能无回归：事件保存、同步、计数更新均正常。

## 边界与限制
- 不改动 Room 表结构与迁移；不调整事件计数的增减算法；
- 当 `uid` 所属项目不可确定时，允许直接以 `uid` 尝试读取；失败时不抛异常，仅记录日志。

## 不确定性与解决方案
- `answers` 字段历史数据可能存在非标准 JSON：已提供字符串兜底解析与日志记录；必要时增加进一步清洗。
- 兼容解析对性能的影响可忽略（一次性解析，数据量较小）。

## 风险控制与回滚策略
- 改动集中在 UI/VM 层，若出现问题可直接回滚对应代码段；不影响数据库与网络层。

## 影响评估
- 受影响文件：`EventFormViewModel.kt`、`EventFormScreen.kt`；
- 依赖模块：`DefectRepository`、`DefectDao` 现有方法已具备 `uid` 读取能力，无需改动；
- 与同步上传流程不冲突（仅读取路径增强）。

## 术语表
- `answers`：风险评估的详细答案集合，旧格式为数组，新格式为对象。
- `assessmentData`：风险评估对象化数据（`risk_cost`、`risk_likelihood`、`risk_rating` 等）。
- `defectIds/defectNos/defectUids`：缺陷的数据库主键、编号、远端唯一标识。

## 结论
- 本次修复在不改变存储结构的前提下，统一了事件读取的兼容性，提升了历史数据的可用性，满足了风险评估与缺陷关联在多数据源下的正确回显与稳定性。