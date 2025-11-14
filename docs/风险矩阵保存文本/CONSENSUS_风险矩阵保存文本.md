# CONSENSUS – 新建事件风险矩阵保存文本与回显

## 需求与验收

- 需求：`event` 表的 `risk_answers` 字段保存“所选答案的文本”而非评分数值；编辑时能正确回显。
- 验收：
  - 新建事件完成风险评估后，`risk_answers` JSON 对象中的 `risk_cost`、`risk_safety`、`risk_other`、`risk_potential_production_loss`、`risk_likelihood` 为明确的选项文本（如 “50million - 100 million”、“频繁发生”、“Not applicable”）。
  - 打开同一事件的编辑页面，风险矩阵弹窗预选状态与上次选择一致，优先按文本匹配，旧数据（数值字符串）也能回显。
  - 不改变数据库结构与网络接口，不影响其他功能。

## 技术实现

- 修改 `RiskAssessmentWizard.computeResult`：
  - 构造 `RiskAssessmentData` 时将上述五个字段保存为“选项文本”，不再保存评分数值字符串；`risk_rating`、`risk_action_required_remediation_timeframe`、`risk_matrix_id` 保持不变。
  - 回显兼容：弹窗已有文本优先、数值兜底的匹配逻辑（四个后果字段新增了按 `severityFactor` 的数值回退匹配，`likelihood` 也已支持数值回退）。
- 保持 `EventFormViewModel.saveEventToRoom` 的保存路径不变（优先保存对象格式 `assessmentData`），因此数据库中的 `risk_answers` 将是文本对象。
- `meta.json` 中 `risk.answers` 同步写入原始对象，形态与数据库一致。

## 边界与约束

- 无数据库 schema 变更，无 Room Migration 改动。
- 旧数据：若历史事件中 `risk_answers` 是数值字符串，对话框仍可回显（兼容逻辑已覆盖）。
- 不修改服务端上传协议与远程解析逻辑（本改动仅影响本地持久化与 UI 回显）。

## 验收步骤（可测试）

1. 编译并安装 UAT Debug，进入“New Event”。
2. 完成风险评估：选择若干具体文本项与 1~3 次 “Not applicable”。
3. 保存并在调试数据库查看 `event.risk_answers`：字段值应为文本（非数值）。
4. 返回事件列表，进入该事件编辑，打开风险矩阵弹窗：预选状态与上次一致。

## 不确定性与结论

- 现阶段服务端是否依赖本地 `risk_answers` 数值未发现使用处；如后续需要数值可在上传前按文本 → 数值映射转换，不影响本地存储与回显。

## 变更影响清单

- 代码文件：
  - `ui/event/RiskAssessmentWizard.kt`：`computeResult()` 构造的 `RiskAssessmentData` 改为保存文本；补充函数注释说明。
  - 其他涉及读取的逻辑保持不变，文本优先、数值兜底已实现。

## 质量门控对齐

- 需求边界清晰且无歧义。
- 技术方案与既有架构（MVVM + Room + Compose）对齐。
- 验收标准可测试（查表与回显）。
- 关键假设：服务端不直接依赖本地该字段的数值；如依赖可在上传层做转换。
- 使用原生安卓开发与既有依赖，不影响原有功能。
- 变更集中、可维护，具备注释说明。