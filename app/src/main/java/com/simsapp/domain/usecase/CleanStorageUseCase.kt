/*
 * File: CleanStorageUseCase.kt
 * Description: Business use case to clean outdated cached files and orphan assets.
 * Author: SIMS Team
 */
package com.simsapp.domain.usecase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File

/**
 * CleanStorageUseCase
 *
 * Performs storage cleanup for outdated caches and temporary files.
 * Policy:
 * - Traverse internal and external cache directories
 * - Delete files older than [retentionDays] and all *.tmp files
 * - Skip directories and files that cannot be deleted safely
 */
@Singleton
class CleanStorageUseCase @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    /**
     * Execute cleanup task.
     * - Deletes files older than retention days under cache directories.
     * - Returns the number of deleted files.
     * @param retentionDays 保留天数（默认 3 天），早于该阈值的缓存文件会被删除
     */
    suspend fun execute(retentionDays: Int = 3): Int {
        val thresholdMs = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L
        var deleted = 0

        // 内部缓存目录
        deleted += cleanDir(appContext.cacheDir, thresholdMs)
        // 外部缓存目录（可能为多个）
        appContext.externalCacheDirs?.forEach { dir ->
            deleted += cleanDir(dir, thresholdMs)
        }
        return deleted
    }

    /**
     * 函数：cleanDir
     * 说明：递归遍历目录下的文件与子目录，按照策略删除文件并统计数量。
     * @param dir 目标目录
     * @param thresholdMs 删除阈值（最后修改时间早于该值）
     * @return 删除的文件数量
     */
    private fun cleanDir(dir: File?, thresholdMs: Long): Int {
        if (dir == null || !dir.exists() || !dir.isDirectory) return 0
        var count = 0
        dir.listFiles()?.forEach { f ->
            runCatching {
                if (f.isDirectory) {
                    count += cleanDir(f, thresholdMs)
                } else {
                    val isTmp = f.name.endsWith(".tmp", ignoreCase = true)
                    val isOld = f.lastModified() > 0 && f.lastModified() < thresholdMs
                    if (isTmp || isOld) {
                        if (f.delete()) count++
                    }
                }
            }.onFailure { /* 忽略单个文件删除失败，继续清理 */ }
        }
        return count
    }
}