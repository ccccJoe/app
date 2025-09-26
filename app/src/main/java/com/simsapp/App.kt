/*
 * File: App.kt
 * Description: Application entry annotated with Hilt and WorkManager configuration for DI-enabled workers.
 * Author: SIMS Team
 */
package com.simsapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * App
 *
 * Application class that bootstraps Hilt DI graph and provides WorkManager configuration.
 * Keeping global initialization lightweight to optimize app startup.
 */
@HiltAndroidApp
class App : Application(), Configuration.Provider {

    // Hilt-provided WorkerFactory for DI-enabled WorkManager workers
    @Inject lateinit var workerFactory: HiltWorkerFactory

    // 新增：注入 DAO（调试环境用于植入示例数据）
    @Inject lateinit var projectDao: com.simsapp.data.local.dao.ProjectDao
    @Inject lateinit var projectDetailDao: com.simsapp.data.local.dao.ProjectDetailDao

    // 新增：应用级协程作用域（调试数据写入使用 IO 线程）
    private val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    /**
     * onCreate
     *
     * Perform minimal startup initialization. Avoid heavy work here.
     */
    override fun onCreate() {
        super.onCreate()
        // Initialize global lightweight components if needed
        // 调试模式下植入示例项目与项目详情，用于联调缺陷详情与图片缩略图；首页通过 ViewModel 进行过滤隐藏
        if (BuildConfig.DEBUG) {
            seedDebugData()
        }
    }

    /**
     * WorkManager configuration provider to inject HiltWorkerFactory.
     * Returning minimal logging level by default to reduce noise in production.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * 植入调试数据（仅在 DEBUG 构建生效）
     * - 创建示例项目（Demo Project）
     * - 写入 ProjectDetailEntity.raw_json，包含 history_defect_list 示例数据
     * - 生成两张本地缩略图图片到 files/history_defects/{projectUid}/{defectNo}/
     */
    private fun seedDebugData() {
        appScope.launch {
            try {
                val demoUid = "demo-uid"
                val defectNo = "DF-001"
                // 判断数据库是否为空，仅在首次运行时插入示例项目与详情，避免重复写入
                val empty = runCatching { projectDao.count() }.getOrDefault(0) == 0
                if (empty) {
                    // 1) 插入示例项目
                    val projectId = projectDao.insert(
                        com.simsapp.data.local.entity.ProjectEntity(
                            name = "Demo Project",
                            projectUid = demoUid,
                            status = "ACTIVE"
                        )
                    )
                    // 2) 写入示例详情 JSON（覆盖边界数据）
                    val rawJson = buildSeedJson()
                    val detail = com.simsapp.data.local.entity.ProjectDetailEntity(
                        projectId = projectId,
                        projectUid = demoUid,
                        name = "Demo Project",
                        status = "ACTIVE",
                        startDate = null,
                        endDate = null,
                        lastUpdateAt = System.currentTimeMillis(),
                        rawJson = rawJson
                    )
                    projectDetailDao.insert(detail)
                }
                // 3) 无论数据库是否为空，都确保示例缩略图存在，便于预览联调
                val thumbDir = java.io.File(filesDir, "history_defects/$demoUid/${sanitize(defectNo)}")
                val needCreate = !thumbDir.exists() || (thumbDir.isDirectory && (thumbDir.listFiles()?.isEmpty() != false))
                if (needCreate) {
                    generateSampleThumbnails(demoUid, defectNo)
                }
            } catch (_: Exception) {
                // 安静失败，避免影响正常启动
            }
        }
    }

    /**
     * 构造调试用的原始 JSON 字符串
     * 内容包含 history_defect_list，字段覆盖空字符串、null 字符串、布尔值类型等边界
     */
    private fun buildSeedJson(): String {
        return """
            {
              "data": {
                "history_defect_list": [
                  {
                    "no": "DF-001",
                    "risk_rating": "HIGH",
                    "type": "Crack",
                    "building_material": "Steel",
                    "status": "OPEN",
                    "inspection_date": "2024-08-01",
                    "functional_area_name": "Area A",
                    "location": null,
                    "asset_name": "Pump #2",
                    "recommendations": "Fix",
                    "engineering_required": true,
                    "responsible_stakeholder": "John",
                    "shutdown_required": false,
                    "overdue": "null",
                    "dropped_object": "false"
                  },
                  {
                    "no": "DF-EMPTY",
                    "risk_rating": "",
                    "type": "",
                    "building_material": "",
                    "status": "",
                    "inspection_date": "",
                    "functional_area_name": "",
                    "location": "null",
                    "asset_name": "",
                    "recommendations": "",
                    "engineering_required": false,
                    "responsible_stakeholder": "",
                    "shutdown_required": false,
                    "overdue": "null",
                    "dropped_object": "true"
                  }
                ]
              }
            }
        """.trimIndent()
    }

    /**
     * 生成两张示例 PNG 缩略图到 files/history_defects/{projectUid}/{defectNo}/ 目录
     */
    private fun generateSampleThumbnails(projectUid: String, defectNo: String) {
        try {
            val dir = java.io.File(filesDir, "history_defects/$projectUid/${sanitize(defectNo)}")
            if (!dir.exists()) dir.mkdirs()
            val colors = listOf(0xFFE53935.toInt(), 0xFF1E88E5.toInt())
            colors.forEachIndexed { idx, color ->
                val bmp = android.graphics.Bitmap.createBitmap(128, 128, android.graphics.Bitmap.Config.ARGB_8888)
                bmp.eraseColor(color)
                val outFile = java.io.File(dir, "sample_${idx + 1}.png")
                java.io.FileOutputStream(outFile).use { fos ->
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, fos)
                }
                bmp.recycle()
            }
        } catch (_: Exception) {
            // 忽略图像生成失败，保持页面可用
        }
    }

    /**
     * 生成安全的文件名
     */
    private fun sanitize(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
}