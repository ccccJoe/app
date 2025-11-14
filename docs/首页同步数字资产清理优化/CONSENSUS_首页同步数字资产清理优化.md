# CONSENSUS — 首页同步数字资产清理优化

## 需求描述

- 在首页同步解析项目数字资产树后，检查本地缓存的 `project_digital_asset` 表：
  - 若某个资产在当前 `project_uid` 的最新数字资产树中不再出现，则从该资产记录的 `project_uids`（JSON 数组）中移除该 `project_uid`；
  - 若移除后 `project_uids` 为空（不再被任何项目引用），则删除该数据库记录，并且删除其对应的本地文件缓存。

## 验收标准

- 触发首页同步后，`ProjectDigitalAssetRepository.processDigitalAssetTree(...)` 能在完成下载流程后执行清理：
  - 针对当前项目解析到的 `file_id` 集合，移除该项目不再关联的资产；
  - 被其他项目仍引用的资产，仅更新 `project_uids`；
  - 无任何项目引用的资产，数据库记录被删除且本地缓存文件被删除；
  - 构建通过（UAT Debug/Prod Debug），现有功能不受影响。

## 技术实现方案

- 新增 DAO 方法：`updateProjectUids(nodeId: String, projectUids: String, updatedAt: Long)`，用于更新记录的 `project_uids`。
- 在 `ProjectDigitalAssetRepository` 中新增方法：
  - `pruneAssetsNotInProjectTree(projectUid: String, currentFileIds: Set<String>): Int`
  - 逻辑：
    1. 查询本地表中 `project_uids` 包含当前 `projectUid` 的资产记录（仅处理 `file_id != null` 的文件型资产）；
    2. 若其 `file_id` 不在当前解析树的 `currentFileIds` 集合中：
       - 从 `project_uids` 中移除当前 `projectUid`；
       - 如果移除后列表为空，删除 DB 记录并删除本地缓存文件；否则仅更新 `project_uids`。
- 在 `processDigitalAssetTree(...)` 的下载循环完成后调用 `pruneAssetsNotInProjectTree(...)`。

## 技术约束与集成

- 语言：Kotlin，JDK 17；架构：MVVM + Jetpack；数据库：Room。
- 依赖注入：Hilt；网络：Retrofit + OkHttp；文件缓存目录：`files/digital_assets`。
- 与现有解析/下载逻辑保持兼容，不调整 API、Entity 结构；仅增加清理步骤与 DAO 更新方法。

## 任务边界

- 只清理文件型资产（`file_id != null`）；不处理纯目录（folder）占位记录，避免误删结构节点。
- 不改变已有的下载与类型解析流程；不做跨项目全量清理（按项目增量执行）。

## 不确定性与确认

- 若未来需要清理文件夹型节点，可在解析器返回的节点集中补充目录节点集合，并扩展清理策略；当前版本保持保守。
- 本地文件路径以 `local_path` 字段为准；若为空或文件不存在，跳过物理删除，仅处理数据库。

## 验收方法

- 构建并安装 UAT Debug 包，执行首页同步；
- 在数据库中验证：被移除的资产 `project_uids` 不再包含当前 `projectUid`；
- 对于仅由该项目引用的资产，记录和本地文件均被删除；
- 现有功能（预览、选择、下载重试）均正常。

## 变更影响与回滚

- 变更范围仅限 DAO 和 Repository；如需回滚，删除新增 DAO 方法和清理调用/方法即可。

---

最后更新：由 SIMS 团队生成，用于对齐首页同步数字资产清理优化的需求与实现细节。