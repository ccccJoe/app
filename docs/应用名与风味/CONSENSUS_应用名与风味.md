<!--
  File: CONSENSUS_应用名与风味.md
  Author: SIMS Android Team
  Purpose: 记录应用名与产品风味调整的需求、边界与验收。
-->

# 应用名与产品风味调整共识

## 需求与目标

- prod 版本应用名改为 `SIMS`
- uat 版本应用名改为 `SIMS-uat`
- 新增 dev 版本应用，应用名为 `SIMS-dev`

## 技术实现方案

- 使用 Gradle `productFlavors` 在 `env` 维度下配置 `prod`、`uat`、`dev`
- 通过 `resValue("string", "app_name", ...)` 为各风味覆盖 Manifest 使用的 `@string/app_name`
- 为每个风味注入 `BuildConfig.BASE_URL`（prod/uat 已存在，dev 设为占位地址 `https://sims-dev.ink-stone.win/zuul/sims-master/`）
- `AndroidManifest.xml` 保持 `android:label="@string/app_name"` 不变

## 边界与不影响项

- 未改动默认 `strings.xml` 中的 `app_name`（用于未指定风味时的占位）
- 未改动应用图标与主题等其他资源
- dev 风味的 `BASE_URL` 为占位，后续可根据环境进行调整

## 验收标准

- `./gradlew assembleProdDebug`、`assembleUatDebug`、`assembleDevDebug` 均构建成功
- 安装到设备后：
  - prod 构建显示应用名 `SIMS`
  - uat 构建显示应用名 `SIMS-uat`
  - dev 构建显示应用名 `SIMS-dev`
- 三个构建变体的 `BuildConfig.BASE_URL` 按各自风味值生成

## 风险与不确定性

- dev 环境地址需要后续确认，当前为占位；如与后端约定不同需更新
- 并存安装需保证 `applicationIdSuffix` 唯一，当前已分别使用 `.uat` 和 `.dev`

## 结论

本次改动与现有架构对齐，变更集中在 Gradle 配置层，已通过 UAT/Prod/Dev Debug 构建验证，符合需求与验收标准。