# 共识文档：结构缺陷详情（structuralDefectDetails）写入修复

## 背景与问题

- 线上现象：事件打包并上传后，`meta.json` 中的 `structuralDefectDetails` 为空对象 `{}`。
- 本地佐证：Room 表 `event.structural_defect_details` 字段存在大量 JSON 内容，且页面正常回显。
- 触发链路：打包前生成/修复 `meta.json` → 压缩 → 上传。

## 核心原因

- 打包阶段的 `meta.json` 修复逻辑仅在键缺失或空字符串时补写结构缺陷详情；若该键已存在但为“空对象 `{}`”，逻辑视为“已有”而不覆盖，导致最终包内仍为 `{}`。

## 技术实现方案

- 位置：`EventRepository.fixLegacyPlaceholderExtensions(...)`（打包前对 `meta.json` 的整理函数）。
- 变更点：将 `structuralDefectDetails` 判空从“缺失或空字符串”扩展为“缺失、空字符串、空对象（`JSONObject.length()==0`）”。
- 行为：当满足上述判空且数据库 `structuralDefectDetails` 非空时，解析为 `JSONObject` 写回；解析失败则写入空对象以确保类型一致。

## 代码约束与集成

- 架构：沿用 MVVM + Repository；未改动数据模型与 API。
- 兼容性：
  - `EventFormViewModel.saveEventToRoom/saveEventToLocal` 已按对象写入 `structuralDefectDetails`，不受影响。
  - 迁移工具 `EventDirectoryMigrationUtil` 亦按对象写入，保持一致。
- 安全性：仅在打包前、对单个事件目录内的 `meta.json` 做幂等更新；失败不阻断压缩流程。

## 任务边界

- 仅修复 `meta.json` 中 `structuralDefectDetails` 的补写判定；不调整 UI、数据表结构或网络接口。
- 不修改其它键（除已有的缺陷编号/UID 补全与音频占位符扩展修正逻辑）。

## 验收标准（可测试）

1. 新建或编辑事件，填写“Structural Defect Details”，本地 Room 表可见 JSON 内容。
2. 触发打包上传（或仅打包）：
   - 打包前 `events/<uid>/meta.json` 的 `structuralDefectDetails` 若为 `{}`，打包步骤会将其替换为对象化 JSON（含向导生成的 `*_summary` 字段）。
   - 压缩包内 `meta.json` 同样为该对象化 JSON。
3. 构建通过，无新增警告或崩溃；不影响其它事件字段（照片、音频、数字资产、风险评估、缺陷关联）。

## 不确定性已解决

- 空对象 `{}` 被视为缺失并会用数据库值补写。
- 数据库字段以字符串形式保存 JSON，解析失败时降级为空对象而非空串。

## 回归与建议测试

- 旧事件目录存在 `{}` 的样本上验证修复逻辑；
- 覆盖三种场景：键缺失、键为空字符串、键为空对象；
- 观察 `fixLegacyPlaceholderExtensions` 的日志确认补写生效。

## 质量门控

- [x] 需求边界清晰无歧义
- [x] 技术方案与现有架构对齐
- [x] 验收标准具体可测试
- [x] 所有关键假设已确认
- [x] 项目特性规范已对齐（原生 Android / Kotlin / MVVM）
- [x] 不影响原有代码功能
- [x] 提醒并修复 `meta.json` 判空边界（包含空对象）