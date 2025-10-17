# 数据流向调整任务共识文档

## 任务概述

本次任务主要针对 SIMS Android 应用中的数据流向进行优化调整，将原本通过 `projectName` 查找项目信息的逻辑改为直接使用 `projectUid`，以提升性能并简化数据流向。

## 需求描述

### 原始需求
- EventFormScreen → 传递 projectName
- StoragePickerViewModel → 通过 projectName 查找 projectUid  
- 使用 projectUid 从 project_detail 表查项目详情
- 解析 rawJson 中的 project_digital_asset_tree 数据

### 优化目标
- 直接传递 `projectUid` 而不是 `projectName`
- 减少不必要的数据库查询
- 简化数据流向，提升性能
- 保持向后兼容性

## 技术实现方案

### 1. EventFormScreen 调整
**文件**: `EventFormScreen.kt`
- **修改内容**: 数字资产按钮点击事件传递 `projectUid` 而不是空参数
- **实现方式**: 
  ```kotlin
  .clickable { 
      if (projectUid.isNotBlank()) {
          onOpenStorage(projectUid)
      } else {
          Log.w("EventFormScreen", "Project UID is not available for storage access")
      }
  }
  ```

### 2. MainActivity 路由调整
**文件**: `MainActivity.kt`
- **修改内容**: EventFormScreen 的 `onOpenStorage` 回调直接使用 `projectUid` 导航
- **实现方式**:
  ```kotlin
  onOpenStorage = { projectUid ->
      navController.navigate("${AppDestination.StoragePicker}?projectUid=$projectUid")
  }
  ```

### 3. 风险矩阵逻辑优化
**文件**: `EventFormViewModel.kt`
- **修改内容**: 
  - 新增接受 `projectUid` 参数的 `createRiskMatrixLoader` 方法
  - 将原有接受 `projectName` 参数的方法重命名为 `createRiskMatrixLoaderByName` 并标记为 `@Deprecated`
- **实现方式**:
  ```kotlin
  fun createRiskMatrixLoader(projectUid: String): RiskMatrixLoader
  
  @Deprecated("Use createRiskMatrixLoader(projectUid: String) instead")
  fun createRiskMatrixLoaderByName(projectName: String): RiskMatrixLoader
  ```

**文件**: `EventFormScreen.kt`
- **修改内容**: 风险评估对话框优先使用 `projectUid` 版本的加载器
- **实现方式**:
  ```kotlin
  loader = projectUid?.let { viewModel.createRiskMatrixLoader(it) } 
      ?: viewModel.createRiskMatrixLoaderByName(projectName)
  ```

### 4. StoragePickerViewModel 清理
**文件**: `StoragePickerViewModel.kt`
- **修改内容**:
  - 移除 `loadDigitalAssetTreeByName` 方法
  - 移除 `loadDigitalAssetTree` 方法中的调试代码
  - 移除构造函数中不再需要的 `ProjectDao` 依赖
- **简化后的逻辑**: 直接通过 `projectUid` 加载数字资产树

### 5. StoragePickerScreen 参数清理
**文件**: `StoragePickerScreen.kt`
- **修改内容**:
  - 移除 `projectName` 参数
  - 将 `projectUid` 参数类型从 `String?` 改为 `String`
  - 简化 `LaunchedEffect` 中的数据加载逻辑

**文件**: `MainActivity.kt`
- **修改内容**: StoragePickerScreen 调用时移除 `projectName` 参数传递

## 技术约束与集成方案

### 兼容性保证
- 保留了通过 `projectName` 的旧版本方法，标记为 `@Deprecated`
- 在 `projectUid` 不可用时提供回退机制
- 不影响现有功能的正常使用

### 性能优化
- 减少了通过 `projectName` 查找 `projectUid` 的数据库查询
- 直接使用 `projectUid` 进行数据访问
- 移除了不必要的调试代码和依赖

### 错误处理
- 在 `projectUid` 为空时记录警告日志
- 提供回退到远程加载的机制
- 异常情况下的优雅降级处理

## 验收标准

### 功能验收
- [x] EventFormScreen 数字资产按钮正常工作
- [x] StoragePicker 能够正确接收和使用 `projectUid`
- [x] 风险矩阵功能正常加载和显示
- [x] 所有原有功能保持正常工作

### 技术验收
- [x] 代码编译通过，无语法错误
- [x] 移除了不必要的 `projectName` 参数传递
- [x] 简化了数据流向，减少了数据库查询
- [x] 保持了向后兼容性

### 代码质量
- [x] 添加了完整的函数级、类级和文件级注释
- [x] 遵循 Kotlin 官方编码规范
- [x] 单文件代码行数控制在合理范围内
- [x] 使用了适当的日志记录和错误处理

## 影响范围

### 修改的文件
1. `EventFormScreen.kt` - 数字资产按钮点击事件和风险矩阵加载器调用
2. `EventFormViewModel.kt` - 风险矩阵加载器方法重构
3. `StoragePickerViewModel.kt` - 移除通过 `projectName` 的查找逻辑
4. `StoragePickerScreen.kt` - 参数简化和逻辑优化
5. `MainActivity.kt` - 路由调整和参数传递优化

### 不受影响的功能
- 项目列表和详情显示
- 缺陷记录和管理
- 事件创建和编辑的其他功能
- 数据同步机制
- 媒体文件处理

## 后续建议

### 进一步优化
1. 考虑在更多场景中直接使用 `projectUid` 而不是 `projectName`
2. 评估是否可以完全移除通过 `projectName` 的查找逻辑
3. 优化数据库查询性能，考虑添加适当的索引

### 监控要点
1. 关注 `projectUid` 为空的情况，确保回退机制正常工作
2. 监控数据库查询性能的改善情况
3. 收集用户反馈，确保功能体验无异常

## 总结

本次数据流向调整成功实现了以下目标：
- 简化了从 EventFormScreen 到 StoragePicker 的数据传递流程
- 优化了风险矩阵加载逻辑，提升了性能
- 清理了不必要的代码和依赖
- 保持了向后兼容性和功能完整性

所有修改均已通过编译验证，功能测试正常，符合项目的技术规范和代码质量要求。