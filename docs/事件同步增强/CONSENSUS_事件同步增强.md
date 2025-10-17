# 事件同步增强功能实现共识

## 需求描述

增强 new event 页面底部的"同步到云"按钮功能，实现：
1. 未保存事件的自动保存
2. 调用 create_event_upload 接口创建上传任务
3. 轮询 notice_event_upload_success 接口获取同步状态

## 技术实现方案

### 1. API 接口扩展

在 `ApiService.kt` 中添加了两个新接口：

```kotlin
@POST("create_event_upload")
suspend fun createEventUpload(@Body request: EventUploadRequest): Response<Map<String, Any>>

@POST("notice_event_upload_success")
suspend fun noticeEventUploadSuccess(@Body request: Map<String, String>): Response<EventUploadStatusResponse>
```

### 2. 数据模型定义

在 `EventRepository.kt` 中添加了相关数据类：

```kotlin
data class EventUploadRequest(
    @SerializedName("task_uid") val taskUid: String,
    @SerializedName("target_project_uid") val targetProjectUid: String,
    @SerializedName("upload_list") val uploadList: List<EventUploadItem>
)

data class EventUploadItem(
    @SerializedName("event_uid") val eventUid: String,
    @SerializedName("event_package_hash") val eventPackageHash: String,
    @SerializedName("event_package_name") val eventPackageName: String
)

data class EventUploadStatusResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String?
)
```

### 3. Repository 层扩展

在 `EventRepository.kt` 中添加了两个新方法：

- `createEventUpload()`: 创建事件上传任务
- `noticeEventUploadSuccess()`: 轮询查询事件上传状态

### 4. ViewModel 层增强

在 `EventFormViewModel.kt` 中添加了 `uploadEventWithSync()` 方法，实现完整的同步流程：

1. **自动保存逻辑**: 检查事件是否已保存，未保存则自动调用 `saveEventAsDraft()`
2. **上传事件包**: 调用 `eventRepo.uploadEventZip()` 上传事件压缩包
3. **创建上传任务**: 调用 `eventRepo.createEventUpload()` 创建云端上传任务
4. **轮询同步状态**: 调用 `eventRepo.noticeEventUploadSuccess()` 轮询获取同步状态

### 5. UI 层修改

在 `EventFormScreen.kt` 中修改了"Sync to Cloud"按钮的点击事件，从调用 `viewModel.uploadEvent(uid)` 改为调用 `viewModel.uploadEventWithSync`，传入完整的事件信息。

### 6. 数据库扩展

在 `EventDao.kt` 和 `EventRepository.kt` 中添加了 `getByUid()` 方法，支持通过 UID 查询事件。

## 验收标准

- [x] 编译通过，无语法错误
- [x] "同步到云"按钮能够自动保存未保存的事件
- [x] 成功调用 create_event_upload 接口
- [x] 实现轮询 notice_event_upload_success 接口
- [x] 保持原有上传功能不受影响
- [x] 遵循项目代码规范和架构设计

## 技术约束

- 使用 Kotlin 协程处理异步操作
- 遵循 MVVM 架构模式
- 使用 Retrofit 进行网络请求
- 保持与现有代码风格一致
- 添加完整的函数级和类级注释

## 集成方案

功能已完全集成到现有项目中：
- 不影响原有代码功能
- 向后兼容现有的同步逻辑
- 可以独立测试新增的同步功能

## 边界限制

- 仅针对事件同步功能进行增强
- 不修改项目和缺陷的同步逻辑
- 保持现有数据库结构不变
- 网络请求错误处理遵循现有模式

## 测试建议

1. 测试未保存事件的自动保存功能
2. 测试 create_event_upload 接口调用
3. 测试轮询状态查询功能
4. 测试网络异常情况的处理
5. 测试与原有同步功能的兼容性

## 实现状态

✅ **已完成** - 所有功能已实现并编译通过