# 数字资产表优化方案

## 需求描述

数字资产树的节点可能出现在不同的project中，需要调整本地缓存的数字资产表，将project_uid字段改为数组，并且若当前文件的file_id已经存在在本地缓存的数字资产表，那么不用再次下载缓存，只需要更新元数据（比如name）和project_uid等。

## 当前问题分析

1. **单一项目绑定问题**：当前 `ProjectDigitalAssetEntity` 的 `project_uid` 字段是单一字符串，无法支持同一个数字资产出现在多个项目中
2. **重复下载问题**：相同 `file_id` 的文件会在不同项目中重复下载和存储，浪费存储空间和网络资源
3. **数据冗余问题**：同一个文件的元数据在多个项目中重复存储

## 技术方案

### 1. 数据库结构调整

#### 方案A：JSON数组存储（推荐）
- 将 `project_uid` 字段类型改为 `String`，存储JSON数组格式
- 优点：Room支持良好，查询简单，迁移容易
- 缺点：查询性能略低于关系表

#### 方案B：关系表分离
- 创建独立的 `DigitalAssetEntity` 和 `ProjectDigitalAssetRelation` 表
- 优点：查询性能最佳，完全符合关系数据库设计
- 缺点：需要重构大量现有代码

**选择方案A**，因为：
- 现有代码改动最小
- Room对JSON字段支持良好
- 查询性能对当前业务场景足够

### 2. 新的实体类设计

```kotlin
@Entity(
    tableName = "project_digital_asset",
    indices = [
        Index(value = ["project_uids"]),
        Index(value = ["node_id"]),
        Index(value = ["file_id"], unique = true) // file_id改为唯一索引
    ]
)
data class ProjectDigitalAssetEntity(
    @PrimaryKey(autoGenerate = true) 
    @ColumnInfo(name = "id") 
    val id: Long = 0,
    
    /** Project UIDs array stored as JSON string. */
    @ColumnInfo(name = "project_uids") 
    val projectUids: String, // JSON数组: ["uid1", "uid2"]
    
    /** Composite node ID: file_id + first_project_uid for uniqueness. */
    @ColumnInfo(name = "node_id") 
    val nodeId: String,
    
    /** File ID from project_digital_asset_tree node (unique across projects). */
    @ColumnInfo(name = "file_id") 
    val fileId: String?,
    
    /** Node name/title (can be updated from different projects). */
    @ColumnInfo(name = "name") 
    val name: String,
    
    /** Node type (folder, file, etc.). */
    @ColumnInfo(name = "type") 
    val type: String,
    
    /** Local file absolute path (shared across projects). */
    @ColumnInfo(name = "local_path") 
    val localPath: String?,
    
    /** Download status: PENDING, DOWNLOADING, COMPLETED, FAILED. */
    @ColumnInfo(name = "download_status") 
    val downloadStatus: String,
    
    // ... 其他字段保持不变
)
```

### 3. DAO层调整

新增查询方法：
- `getByFileId()`: 根据file_id查询（用于检查是否已存在）
- `getByProjectUid()`: 根据project_uid查询（支持JSON数组查询）
- `addProjectUid()`: 为现有记录添加新的project_uid
- `updateMetadata()`: 更新元数据（name等）

### 4. 缓存逻辑优化

```kotlin
suspend fun processDigitalAssetTree(projectUid: String, assetTree: String) {
    val assetNodes = parseAssetTree(assetTree)
    
    for (node in assetNodes) {
        if (node.fileId != null) {
            // 检查file_id是否已存在
            val existingAsset = projectDigitalAssetDao.getByFileId(node.fileId)
            
            if (existingAsset != null) {
                // 文件已存在，只更新元数据和project_uid
                val updatedProjectUids = addProjectUidToArray(existingAsset.projectUids, projectUid)
                projectDigitalAssetDao.updateMetadataAndProjectUids(
                    fileId = node.fileId,
                    name = node.name,
                    projectUids = updatedProjectUids,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                // 新文件，创建记录并下载
                val newAsset = ProjectDigitalAssetEntity(
                    projectUids = createProjectUidsArray(projectUid),
                    fileId = node.fileId,
                    name = node.name,
                    // ... 其他字段
                )
                projectDigitalAssetDao.insert(newAsset)
                downloadDigitalAsset(newAsset)
            }
        }
    }
}
```

## 验收标准

1. **数据完整性**：同一个file_id在数据库中只有一条记录
2. **项目关联**：每个数字资产可以关联多个项目
3. **下载优化**：相同file_id的文件只下载一次
4. **元数据更新**：支持从不同项目更新文件名等元数据
5. **向后兼容**：现有功能不受影响
6. **查询性能**：查询性能不显著下降

## 实施步骤

1. 创建数据库迁移脚本
2. 更新 `ProjectDigitalAssetEntity` 实体类
3. 更新 `ProjectDigitalAssetDao` 查询方法
4. 修改 `ProjectDigitalAssetRepository` 缓存逻辑
5. 更新相关的ViewModel和UI层代码
6. 编写单元测试验证功能
7. 进行数据迁移测试

## 风险评估

- **低风险**：数据库迁移可能失败 - 通过充分测试和备份机制规避
- **中风险**：查询性能下降 - 通过索引优化和查询优化解决
- **低风险**：现有功能受影响 - 通过全面的回归测试确保兼容性