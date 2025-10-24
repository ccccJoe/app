/*
 * File: RiskMatrixRepository.kt
 * Description: Repository for handling risk matrix data download and caching from Alibaba Cloud.
 * Author: SIMS Team
 */
package com.simsapp.data.repository

import android.content.Context
import android.util.Log
import com.simsapp.data.local.dao.ProjectDigitalAssetDao
import com.simsapp.data.local.entity.ProjectDigitalAssetEntity
import com.simsapp.data.remote.ApiService
import com.simsapp.utils.DigitalAssetTreeParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RiskMatrixRepository
 *
 * 负责：
 * - 识别数字资产树中的风险矩阵数据（第一个子节点）
 * - 调用阿里云API获取风险矩阵数据
 * - 将数据缓存到本地文件系统
 * - 提供本地缓存文件的访问接口
 *
 * 设计：使用Hilt注入ApiService和Context，支持异步下载和本地缓存管理
 */
@Singleton
class RiskMatrixRepository @Inject constructor(
    private val apiService: ApiService,
    private val projectDigitalAssetDao: ProjectDigitalAssetDao,
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "RiskMatrixRepository"
        private const val CACHE_DIR_NAME = "risk_matrix_cache"
    }

    /**
     * 处理风险矩阵数据：筛选风险矩阵节点，下载数据并缓存到本地
     * Process risk matrix data: filter risk matrix nodes, download data and cache locally
     * 
     * @param projectUid 项目唯一标识符
     * @param digitalAssets 数字资产列表
     * @return Result<Int> 成功处理的风险矩阵节点数量
     */
    suspend fun processRiskMatrixData(projectUid: String, digitalAssets: List<ProjectDigitalAssetEntity>): Result<Int> {
        return try {
            // 筛选风险矩阵节点
        val riskMatrixAssets = digitalAssets.filter { it.type == "risk_matrix" }
            
            if (riskMatrixAssets.isEmpty()) {
                Log.d(TAG, "No risk matrix assets found for project: $projectUid")
                return Result.success(0)
            }
            
            Log.d(TAG, "Processing ${riskMatrixAssets.size} risk matrix assets for project: $projectUid")
            
            var successCount = 0
            
            // 处理每个风险矩阵资产
            for (asset in riskMatrixAssets) {
                try {
                    // 检查是否已经缓存
                    if (asset.downloadStatus == "COMPLETED" && !asset.localPath.isNullOrEmpty() && !asset.content.isNullOrEmpty()) {
                        Log.d(TAG, "Risk matrix asset already cached: ${asset.nodeId}")
                        successCount++
                        continue
                    }
                    
                    // 下载风险矩阵数据
                    asset.fileId?.let { fileId ->
                        val result = downloadRiskMatrixById(fileId)
                        if (result.isSuccess) {
                            val jsonContent = result.getOrNull()
                            if (!jsonContent.isNullOrEmpty()) {
                                // 保存到本地文件
                                val localFile = saveRiskMatrixToLocal(projectUid, asset.nodeId, jsonContent)
                                
                                // 更新数字资产状态，包括content字段
                                projectDigitalAssetDao.updateDownloadComplete(
                                    asset.nodeId,
                                    "COMPLETED",
                                    localFile.absolutePath,
                                    jsonContent,
                                    System.currentTimeMillis()
                                )
                                
                                Log.d(TAG, "Successfully processed risk matrix asset: ${asset.nodeId}")
                                successCount++
                            } else {
                                // 下载内容为空
                                projectDigitalAssetDao.updateDownloadStatus(asset.nodeId, "FAILED", System.currentTimeMillis())
                                Log.w(TAG, "Downloaded risk matrix content is empty for asset: ${asset.nodeId}")
                            }
                        } else {
                            // 下载失败
                            projectDigitalAssetDao.updateDownloadStatus(asset.nodeId, "FAILED", System.currentTimeMillis())
                            Log.e(TAG, "Failed to download risk matrix for asset: ${asset.nodeId}", result.exceptionOrNull())
                        }
                    } ?: run {
                        // file_id 为空
                        projectDigitalAssetDao.updateDownloadStatus(asset.nodeId, "FAILED", System.currentTimeMillis())
                        Log.w(TAG, "Risk matrix asset has no file_id: ${asset.nodeId}")
                    }
                    
                } catch (e: Exception) {
                    // 处理单个资产时出错
                    projectDigitalAssetDao.updateDownloadStatus(asset.nodeId, "FAILED", System.currentTimeMillis())
                    Log.e(TAG, "Error processing risk matrix asset: ${asset.nodeId}", e)
                }
            }
            
            Log.d(TAG, "Processed $successCount out of ${riskMatrixAssets.size} risk matrix assets")
            Result.success(successCount)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing risk matrix data for project: $projectUid", e)
            Result.failure(e)
        }
    }

    /**
     * 将风险矩阵数据保存到本地缓存
     *
     * @param projectUid 项目唯一标识
     * @param fileId 文件ID
     * @param responseBody 响应体数据
     * @param fileName 文件名
     * @return 缓存文件对象，失败时返回null
     */
    private suspend fun saveRiskMatrixToCache(
        projectUid: String,
        fileId: String,
        responseBody: ResponseBody,
        fileName: String
    ): File? = withContext(Dispatchers.IO) {
        return@withContext try {
            // 创建缓存目录
            val cacheDir = File(context.cacheDir, "$CACHE_DIR_NAME/$projectUid")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            // 确定文件扩展名
            val fileExtension = when {
                fileName.contains(".") -> fileName.substringAfterLast(".")
                else -> "json" // 默认为JSON格式
            }
            
            // 创建缓存文件
            val cacheFile = File(cacheDir, "${fileId}_${fileName}.${fileExtension}")
            
            // 写入数据
            responseBody.byteStream().use { inputStream ->
                FileOutputStream(cacheFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            Log.d(TAG, "Risk matrix cached to: ${cacheFile.absolutePath}, size: ${cacheFile.length()} bytes")
            cacheFile
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save risk matrix to cache", e)
            null
        }
    }

    /**
     * 获取项目的风险矩阵缓存文件列表
     *
     * @param projectUid 项目唯一标识
     * @return 缓存文件列表
     */
    suspend fun getRiskMatrixCacheFiles(projectUid: String): List<File> = withContext(Dispatchers.IO) {
        return@withContext try {
            val cacheDir = File(context.cacheDir, "$CACHE_DIR_NAME/$projectUid")
            if (cacheDir.exists() && cacheDir.isDirectory) {
                cacheDir.listFiles()?.toList() ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting risk matrix cache files", e)
            emptyList()
        }
    }

    /**
     * 清理项目的风险矩阵缓存
     *
     * @param projectUid 项目唯一标识，为null时清理所有缓存
     * @return 清理的文件数量
     */
    suspend fun clearRiskMatrixCache(projectUid: String? = null): Int = withContext(Dispatchers.IO) {
        return@withContext try {
            val targetDir = if (projectUid != null) {
                File(context.cacheDir, "$CACHE_DIR_NAME/$projectUid")
            } else {
                File(context.cacheDir, CACHE_DIR_NAME)
            }
            
            var deletedCount = 0
            if (targetDir.exists()) {
                targetDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        if (file.delete()) {
                            deletedCount++
                        }
                    }
                }
                
                // 删除空目录
                if (targetDir.isDirectory && targetDir.listFiles()?.isEmpty() == true) {
                    targetDir.delete()
                }
            }
            
            Log.i(TAG, "Cleared $deletedCount risk matrix cache files")
            deletedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing risk matrix cache", e)
            0
        }
    }

    /**
     * 检查风险矩阵是否已缓存
     *
     * @param projectUid 项目唯一标识
     * @param fileId 文件ID
     * @return 是否已缓存
     */
    suspend fun isRiskMatrixCached(projectUid: String, fileId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val cacheDir = File(context.cacheDir, "$CACHE_DIR_NAME/$projectUid")
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.any { file ->
                    file.name.startsWith("${fileId}_")
                } ?: false
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking risk matrix cache", e)
            false
        }
    }

    /**
     * 通过文件ID下载风险矩阵数据
     * 
     * @param fileId 文件ID
     * @return 下载响应
     */
    /**
     * 根据文件ID下载风险矩阵数据
     * Download risk matrix data by file ID
     * 
     * @param fileId 文件ID
     * @return Result<String> 包含JSON内容的结果
     */
    private suspend fun downloadRiskMatrixById(fileId: String): Result<String> {
        return try {
            Log.d(TAG, "Starting download for risk matrix file ID: $fileId")
            
            // 首先解析下载URL
            val urlResponse = apiService.resolveDownloadUrl(
                endpoint = "storage/download/url",
                fileIds = listOf(fileId)
            )
            
            if (!urlResponse.isSuccessful) {
                Log.e(TAG, "Failed to resolve download URL for file ID: $fileId - HTTP ${urlResponse.code()}")
                return Result.failure(Exception("Failed to resolve download URL: HTTP ${urlResponse.code()}"))
            }
            
            // 解析响应获取下载URL
            val responseBody = urlResponse.body()?.string() ?: ""
            Log.d(TAG, "URL resolve response: $responseBody")
            
            val jsonResponse = org.json.JSONObject(responseBody)
            
            // 支持多种响应格式：
            // 1. { "data": [{"url": "..."}, ...] }  - 用户图片中的格式
            // 2. { "data": { "urls": [{"url": "..."}, ...] } }  - 原有格式
            // 3. { "urls": [{"url": "..."}, ...] }  - 直接格式
            var downloadUrl: String? = null
            
            // 尝试格式1: data[0].url
            val dataArray = jsonResponse.optJSONArray("data")
            if (dataArray != null && dataArray.length() > 0) {
                val firstItem = dataArray.optJSONObject(0)
                downloadUrl = firstItem?.optString("url")
                Log.d(TAG, "Found URL using data[0].url format: $downloadUrl")
            }
            
            // 尝试格式2: data.urls[0].url
            if (downloadUrl.isNullOrEmpty()) {
                val data = jsonResponse.optJSONObject("data")
                if (data != null) {
                    val urlsArray = data.optJSONArray("urls")
                    if (urlsArray != null && urlsArray.length() > 0) {
                        val firstItem = urlsArray.optJSONObject(0)
                        downloadUrl = firstItem?.optString("url")
                        Log.d(TAG, "Found URL using data.urls[0].url format: $downloadUrl")
                    }
                }
            }
            
            // 尝试格式3: urls[0].url
            if (downloadUrl.isNullOrEmpty()) {
                val urlsArray = jsonResponse.optJSONArray("urls")
                if (urlsArray != null && urlsArray.length() > 0) {
                    val firstItem = urlsArray.optJSONObject(0)
                    downloadUrl = firstItem?.optString("url")
                    Log.d(TAG, "Found URL using urls[0].url format: $downloadUrl")
                }
            }
            
            if (downloadUrl.isNullOrEmpty()) {
                Log.e(TAG, "No download URL found in response for file ID: $fileId")
                return Result.failure(Exception("Download URL not found"))
            }
            
            Log.d(TAG, "Downloading risk matrix from URL: $downloadUrl")
            
            // 下载实际内容
            val downloadResponse = apiService.downloadRiskMatrixByUrl(downloadUrl)
            if (downloadResponse.isSuccessful) {
                val content = downloadResponse.body()?.string()
                if (!content.isNullOrEmpty()) {
                    Log.d(TAG, "Successfully downloaded risk matrix content, size: ${content.length} characters")
                    Result.success(content)
                } else {
                    Log.w(TAG, "Downloaded content is empty for file ID: $fileId")
                    Result.failure(Exception("Downloaded content is empty"))
                }
            } else {
                Log.e(TAG, "Failed to download risk matrix content: HTTP ${downloadResponse.code()}")
                Result.failure(Exception("Failed to download content: HTTP ${downloadResponse.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading risk matrix by ID: $fileId", e)
            Result.failure(e)
        }
    }

    /**
     * 保存风险矩阵数据到本地文件
     * Save risk matrix data to local file
     * 
     * @param projectUid 项目唯一标识符
     * @param fileId 文件ID
     * @param jsonContent JSON内容
     * @return 本地文件
     */
    private suspend fun saveRiskMatrixToLocal(
        projectUid: String,
        fileId: String,
        jsonContent: String
    ): File = withContext(Dispatchers.IO) {
        // 创建缓存目录
        val cacheDir = File(context.cacheDir, "$CACHE_DIR_NAME/$projectUid")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        
        // 创建缓存文件
        val cacheFile = File(cacheDir, "${fileId}_risk_matrix.json")
        
        // 写入JSON数据
        cacheFile.writeText(jsonContent, Charsets.UTF_8)
        
        Log.d(TAG, "Risk matrix saved to: ${cacheFile.absolutePath}, size: ${cacheFile.length()} bytes")
        return@withContext cacheFile
    }

    /**
     * 更新数字资产表中的下载状态和本地路径
     * @param fileId 文件ID
     * @param localPath 本地文件路径（可为null）
     * @param status 下载状态
     */
    private suspend fun updateDigitalAssetStatus(
        projectUid: String,
        fileId: String,
        localPath: String?,
        status: String
    ) {
        try {
            // 根据fileId查找对应的数字资产记录
            val projectAsset = projectDigitalAssetDao.getByFileId(fileId)
            
            if (projectAsset != null) {
                // 更新下载状态
                projectDigitalAssetDao.updateDownloadStatus(
                    nodeId = projectAsset.nodeId,
                    status = status,
                    updatedAt = System.currentTimeMillis()
                )
                
                // 如果有本地路径，也更新本地路径
                if (localPath != null) {
                    projectDigitalAssetDao.updateLocalPath(
                        nodeId = projectAsset.nodeId,
                        localPath = localPath,
                        updatedAt = System.currentTimeMillis()
                    )
                }
                
                Log.d(TAG, "Updated digital asset status: fileId=$fileId, status=$status, localPath=$localPath")
            } else {
                Log.w(TAG, "Digital asset not found for fileId: $fileId, projectUid: $projectUid")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating digital asset status", e)
        }
    }
}