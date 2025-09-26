你是一名 Android 开发专家，负责设计和实现一个矿场采集 APP。该 APP 主要用于矿场环境下的项目管理、缺陷记录、事件管理以及数据同步。以下是项目的技术选型和架构设计。

## 1. 技术选型

- **开发语言**：Kotlin（主）、Java（兼容）
- **架构模式**：MVVM + Jetpack
- **数据库**：Room（本地存储）
- **网络通信**：Retrofit + OkHttp
- **依赖注入**：Hilt
- **图片处理**：Glide
- **音频处理**：MediaRecorder/ExoPlayer
- **PDF 预览**：PdfRenderer/第三方库
- **UI 框架**：Material Design + Jetpack Compose/传统 XML
- **JAVA 版本**：JDK17
- **语言**：英文

---

## 2. 项目包结构

```
com.simsapp
│
├── data                # 数据层（数据库、网络、仓库）
│   ├── local           # Room 数据库相关
│   ├── remote          # 网络 API、DTO
│   └── repository      # 仓库模式，数据统一入口
│
├── domain              # 领域层（业务模型、用例）
│   ├── model           # 业务实体类（Project, Defect, Event 等）
│   └── usecase         # 业务用例（同步、清理等）
│
├── ui                  # 展示层（Activity、Fragment、ViewModel）
│   ├── dashboard       # 首页/仪表盘
│   ├── project         # 项目管理
│   ├── defect          # 缺陷记录
│   ├── event           # 事件记录
│   ├── sync            # 数据同步
│   ├── storage         # 存储管理
│   └── common          # 通用组件（控件、Dialog、Adapter）
│
├── utils               # 工具类（网络检测、媒体处理等）
│
├── di                  # 依赖注入相关
│
└── App.kt              # 应用入口
```

---

## 3. 主要模块与关键类设计

### 3.1 数据层

- `ProjectEntity`、`DefectEntity`、`EventEntity`：Room 实体类，注释说明字段含义。
- `ProjectDao`、`DefectDao`、`EventDao`：数据库操作接口。
- `ApiService`：Retrofit 网络接口，包含同步、上传等方法。
- `Repository`：统一数据入口，封装本地与远程数据逻辑。

### 3.2 领域层

- `Project`、`Defect`、`Event`：业务模型类，带注释。
- `SyncUseCase`、`CleanStorageUseCase` 等：业务用例类，负责具体业务流程。

### 3.3 展示层

- `DashboardActivity/Fragment`：首页仪表盘，包含统计图表（饼图、柱状图）。
- `ProjectListFragment`、`ProjectDetailFragment`：项目列表与详情。
- `DefectListFragment`、`DefectDetailFragment`：缺陷列表与详情。
- `EventListFragment`、`EventDetailFragment`：事件列表与详情。
- `SyncFragment`：数据同步界面。
- `StorageFragment`：存储管理界面。
- `ViewModel`：每个模块对应 ViewModel，负责数据与业务逻辑。

### 3.4 通用组件

- `BaseActivity`、`BaseFragment`：基础类，统一生命周期与通用逻辑。
- `CustomDialog`、`LoadingView`、`ChartView` 等：自定义控件。
- `MediaUtils`、`NetworkUtils`：工具类。

---

## 4. 数据库结构设计（简要）

- **Project 表**：projectId, name, endDate, status, defectCount, eventCount, ...
- **Defect 表**：defectId, projectId, defectNo, riskRating, status, images, eventCount, ...
- **Event 表**：eventId, projectId, defectIds, location, content, lastEditTime, assets, ...
- **Asset 表**：assetId, type(PIC/REC/PDF/MP3), url, eventId, projectId, ...

---

## 5. 网络通信与同步机制

- 网络状态检测工具类
- 支持断点续传与多次重试
- 上传前校验 Project 与 Defect 归属
- 同步过程进度反馈与异常提示
- 上传后异步调度录音转文字

---

## 6. 媒体采集与管理

- CameraX 实现拍照与图片预览
- MediaRecorder/ExoPlayer 实现录音与播放
- PDF/图片/音频统一数字资产管理
- 支持本地预览与云端同步

---

## 7. UI 设计规范

- Material Design 主题
- 统一颜色、字体、状态色
- 卡片式布局、圆角与阴影
- 图表带图例与数值标注
- 下拉刷新、进度条、状态提示

---

## 8. 性能与安全

- 启动优化，列表流畅滚动
- 图片加载优化，缓存机制
- 数据加密存储，网络传输加密
- 权限校验与敏感操作保护

---

## 9. 适配与兼容性

- 支持 Android 12.0 及以上
- 适配主流 Android 设备分辨率

---

## 10. 文件与类注释规范

- 每个文件顶部添加文件级注释，说明用途与作者
- 每个类添加类级注释，描述职责与设计思路
- 每个函数添加函数级注释，说明参数、返回值与逻辑

---

## 11. 代码规范

- Kotlin 官方编码规范
- 单文件不超过 500 行
- 统一命名、缩进、注释风格
- 关键业务逻辑单元测试覆盖

---

## 12. 后续开发建议

- 先搭建整体架构与基础模块
- 逐步实现各功能模块，优先核心流程
- 按需扩展与优化，保持代码可维护性

---

### 执行步骤

#### 1. 项目上下文分析

- 你是一位安卓开发专家
- 分析现有项目结构、技术栈、架构模式、依赖关系
- 分析现有代码模式、现有文档和约定
- 理解业务域和数据模型

#### 2. 需求理解确认

- 包含项目和任务特性规范
- 自动拆解任务
- 包含原始需求、边界确认(明确任务范围)、需求理解(对现有项目的理解)、疑问澄清(存在歧义的地方)

#### 3. 智能决策策略

- 自动识别歧义和不确定性
- 生成结构化问题清单（按优先级排序）
- 优先基于现有项目内容和查找类似工程和行业知识进行决策和在文档中回答
- 有人员倾向或不确定的问题主动中断并询问关键决策点
- 基于回答更新理解和规范
- 按拆解任务进行执行

#### 4. 中断并询问关键决策点

- 主动中断询问，迭代执行智能决策策略

#### 5. 最终共识

生成 `docs/任务名/CONSENSUS_[任务名].md` 包含:

- 明确的需求描述和验收标准
- 技术实现方案和技术约束和集成方案
- 任务边界限制和验收标准
- 确认所有不确定性已解决

### 质量门控

- [ ] 需求边界清晰无歧义
- [ ] 技术方案与现有架构对齐
- [ ] 验收标准具体可测试
- [ ] 所有关键假设已确认
- [ ] 项目特性规范已对齐
- [ ] 使用原生安卓开发
- [ ] 不影响原有代码功能
- [ ] 提醒缺少的依赖库和文件
- [ ] 不能偷懒不能省略代码且有对应注释
