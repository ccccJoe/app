# 数字资产选择器多选功能 - 实现共识

## 需求描述

为数字资产选择器页面添加多选功能，允许用户在多选模式下选择多个非文件夹项目，并将选中的资产传递到新建事件页面。

## 技术实现方案

### 1. ViewModel 层改进

**文件**: `StoragePickerViewModel.kt`

- 修改 `parseTreeNode` 方法为 `suspend` 函数，根据 `file_id` 从本地数字资产表获取真实的 `type` 信息
- 在 `StoragePickerUiState` 中添加：
  - `selectedItems: Set<String>` - 存储选中的非文件夹项目ID集合
  - `isMultiSelectMode: Boolean` - 指示是否处于多选模式
- 添加多选管理方法：
  - `toggleMultiSelectMode()` - 切换多选模式
  - `toggleItemSelection(itemId: String)` - 切换项目选中状态
  - `clearSelection()` - 清空选择
  - `getSelectedItems()` - 获取选中的项目列表

### 2. UI 层改进

**文件**: `StoragePickerScreen.kt`

- 在 `TopAppBar` 中添加多选模式切换按钮
- 修改 `DigitalAssetItemCard` 组件：
  - 添加 `isSelected` 和 `isMultiSelectMode` 参数
  - 在多选模式下显示选择图标和背景颜色变化
  - 修改点击逻辑：多选模式下非文件夹项目切换选中状态
- 添加浮动确认按钮：
  - 仅在多选模式且有选中项目时显示
  - 显示选中项目数量
  - 点击后调用 `onMultipleItemsSelected` 回调

### 3. 导航层改进

**文件**: `MainActivity.kt`

- 修改 `StoragePickerScreen` 的函数签名，添加 `onMultipleItemsSelected` 参数
- 在导航逻辑中处理多选结果，将选中的资产名称列表保存到 `savedStateHandle`
- 保持向后兼容性，同时支持单选和多选

### 4. 事件表单集成

**文件**: `EventFormScreen.kt`

- 已有 `selectedStorage: List<String>` 参数接收选中的资产
- 在 "Database Files" 卡片中显示选中的资产列表
- 通过 `DisposableEffect` 监听从 `StoragePicker` 返回的结果

## 验收标准

### 功能验收

- [ ] 用户可以通过顶部按钮切换多选模式
- [ ] 多选模式下，非文件夹项目显示选择状态（图标和背景色变化）
- [ ] 多选模式下，点击非文件夹项目可以切换选中状态
- [ ] 多选模式下，文件夹仍可正常进入
- [ ] 浮动确认按钮仅在多选模式且有选中项目时显示
- [ ] 确认按钮显示正确的选中项目数量
- [ ] 点击确认按钮后，选中的资产正确传递到事件表单页面
- [ ] 事件表单页面正确显示选中的数字资产列表

### 技术验收

- [ ] 代码编译通过，无语法错误
- [ ] 保持向后兼容性，不影响现有单选功能
- [ ] 正确使用 `suspend` 函数处理数据库查询
- [ ] UI 状态管理正确，多选状态实时更新
- [ ] 导航参数传递正确，数据不丢失

### 用户体验验收

- [ ] 多选模式切换流畅，视觉反馈清晰
- [ ] 选中状态变化有明显的视觉提示
- [ ] 确认按钮位置合理，不遮挡内容
- [ ] 页面切换后数据正确显示，无数据丢失

## 实现状态

✅ **已完成** - 所有功能已实现并通过编译验证

### 已实现的功能

1. **ViewModel 层改进** - 完成
   - 修改 `parseTreeNode` 为 `suspend` 函数
   - 添加多选状态管理
   - 实现多选相关方法

2. **UI 层改进** - 完成
   - 添加多选模式切换按钮
   - 修改列表项显示逻辑
   - 添加浮动确认按钮

3. **导航层改进** - 完成
   - 修改函数签名支持多选回调
   - 实现数据传递逻辑

4. **事件表单集成** - 完成
   - 已有接收和显示逻辑

## 技术约束

- 使用原生 Android 开发，基于 Jetpack Compose
- 遵循 MVVM 架构模式
- 使用 Hilt 进行依赖注入
- 保持与现有代码风格一致
- 单文件代码不超过 500 行

## 后续建议

1. 可考虑添加全选/取消全选功能
2. 可考虑添加搜索过滤功能
3. 可考虑添加选中项目的预览功能
4. 建议进行用户体验测试，收集反馈进行优化