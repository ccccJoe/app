/*
 * File: ProjectDigitalAssetRepository.kt
 * Description: Repository for managing project digital assets download and caching.
 * Author: SIMS Team
 */
package com.simsapp.data.repository

import android.content.Context
import com.simsapp.data.local.dao.ProjectDigitalAssetDao
import com.simsapp.data.local.entity.ProjectDigitalAssetEntity
import com.simsapp.data.remote.ApiService
import com.simsapp.utils.DigitalAssetTreeParser
import com.simsapp.utils.ProjectUidsUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ProjectDigitalAssetRepository
 *
 * Manages project digital assets including parsing, downloading, and local caching.
 */
@Singleton
class ProjectDigitalAssetRepository @Inject constructor(
    private val projectDigitalAssetDao: ProjectDigitalAssetDao,
    private val apiService: ApiService,
    private val riskMatrixRepository: RiskMatrixRepository,
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val DOWNLOAD_URL_ENDPOINT = "storage/download/url"
        private const val DIGITAL_ASSETS_DIR = "digital_assets"
    }

    /** Get digital assets by project UID. */
    suspend fun getByProjectUid(projectUid: String): List<ProjectDigitalAssetEntity> =
        projectDigitalAssetDao.getByProjectUid(projectUid)

    /** Get completed digital assets by project UID. */
    suspend fun getCompletedByProjectUid(projectUid: String): List<ProjectDigitalAssetEntity> =
        projectDigitalAssetDao.getCompletedByProjectUid(projectUid)

    /** Get digital assets by file type for a project. */
    suspend fun getByProjectUidAndFileType(projectUid: String, fileType: String): List<ProjectDigitalAssetEntity> =
        projectDigitalAssetDao.getByProjectUid(projectUid).filter { 
            it.type.equals(fileType, ignoreCase = true) 
        }

    /**
     * Extract parent ID from node path.
     * For example: "root/children[0]/children[1]" -> "root/children[0]"
     * 
     * @param nodePath The node path from DigitalAssetTreeParser
     * @return Parent ID or null for root nodes
     */
    private fun extractParentIdFromPath(nodePath: String): String? {
        if (nodePath == "root") return null
        
        val lastSlashIndex = nodePath.lastIndexOf('/')
        return if (lastSlashIndex > 0) {
            nodePath.substring(0, lastSlashIndex)
        } else {
            null
        }
    }

    /**
     * Process project digital asset tree from project detail JSON.
     * Parse the tree, extract nodes with file_id, and download them.
     *
     * @param projectId Local project ID
     * @param projectUid Project UID
     * @param projectDetailJson Raw project detail JSON
     * @return Pair of (success count, total count)
     */
    suspend fun processDigitalAssetTree(
        projectId: Long,
        projectUid: String,
        projectDetailJson: String
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("ProjectDigitalAssetRepo", "Processing digital asset tree for project: $projectId")
            
            // Parse digital asset tree
            val assetNodes = DigitalAssetTreeParser.parseDigitalAssetTree(projectDetailJson)
            android.util.Log.d("ProjectDigitalAssetRepo", "Found ${assetNodes.size} asset nodes to process")
            
            if (assetNodes.isEmpty()) {
                return@withContext 0 to 0
            }

            // Convert ALL nodes (including risk matrix) to entities and save to database
            val entities = mutableListOf<ProjectDigitalAssetEntity>()
            
            for (node in assetNodes) {
                android.util.Log.d("ProjectDigitalAssetRepo", "Processing asset node: ${node.fileId}")
                
                // Use node's id as nodeId, and p_id as parentId from JSON
                val nodeId = node.nodeId ?: run {
                    // Fallback: use fileId if nodeId is not available
                    if (node.fileId != null) {
                        node.fileId
                    } else {
                        // For folders without fileId, use a hash of the nodePath to ensure consistency
                        "folder_${node.nodePath.hashCode().toString().replace("-", "")}"
                    }
                }
                
                val parentId = node.parentId // Use p_id from JSON directly
                
                // 检查是否已存在相同file_id的记录
                val existingAsset = if (node.fileId != null) {
                    projectDigitalAssetDao.getByFileId(node.fileId)
                } else {
                    null
                }
                
                if (existingAsset != null && node.fileId != null) {
                    // 如果已存在相同file_id的记录，只更新元数据和project_uids
                    android.util.Log.d("ProjectDigitalAssetRepo", "Found existing asset with file_id: ${node.fileId}, updating metadata and project_uids")
                    
                    val updatedProjectUids = ProjectUidsUtils.addProjectUidToArray(existingAsset.projectUids, projectUid)
                    val updatedAsset = existingAsset.copy(
                        projectUids = updatedProjectUids,
                        name = node.nodeName ?: existingAsset.name, // 更新名称
                        fileSize = node.fileSize ?: existingAsset.fileSize, // 更新文件大小
                        resourceId = node.resourceId ?: existingAsset.resourceId, // 更新resource_id
                        updatedAt = System.currentTimeMillis()
                    )
                    
                    entities.add(updatedAsset)
                } else {
                    // 创建新的数字资产记录
                    val newAsset = ProjectDigitalAssetEntity(
                        projectUids = ProjectUidsUtils.createProjectUidsArray(projectUid),
                        nodeId = nodeId,
                        parentId = parentId,
                        name = node.nodeName ?: "Unknown",
                        type = node.fileType ?: when {
                            node.fileId != null -> "file"
                            else -> "folder"
                        },
                        fileId = node.fileId,
                        resourceId = node.resourceId, // 保存resource_id
                        localPath = null, // 新资产没有本地路径
                        downloadStatus = "PENDING", // 新资产状态为待下载
                        downloadUrl = null, // 新资产没有下载URL
                        fileSize = node.fileSize,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    
                    entities.add(newAsset)
                }
            }

            // Insert or update entities using upsert to avoid duplicates
            projectDigitalAssetDao.upsertAll(entities)

            // All assets will be processed using the same download logic
            android.util.Log.d("ProjectDigitalAssetRepo", "Starting to process ${entities.size} digital assets")

            // Download all assets with file_id that are not already cached
            var successCount = 0
            for (node in assetNodes) {
                val entity = entities.find { it.fileId == node.fileId }
                if (entity != null && entity.fileId != null) {
                    // 检查文件是否已经缓存完成
                    if (entity.downloadStatus == "COMPLETED" && !entity.localPath.isNullOrEmpty()) {
                        val localFile = File(entity.localPath)
                        if (localFile.exists()) {
                            android.util.Log.d("ProjectDigitalAssetRepo", "File already cached for file_id: ${entity.fileId}, skipping download")
                            successCount++
                            continue
                        }
                    }
                    
                    try {
                        // First resolve download URL for the file_id
                        val downloadUrl = resolveDownloadUrl(entity.fileId)
                        if (downloadUrl != null) {
                            // Update download URL in database
                            projectDigitalAssetDao.updateDownloadUrl(
                                entity.nodeId,
                                downloadUrl,
                                System.currentTimeMillis()
                            )
                            android.util.Log.d("ProjectDigitalAssetRepo", "Updated download URL for file_id: ${entity.fileId}")
                            
                            // Then proceed with download
                            if (downloadDigitalAsset(entity)) {
                                successCount++
                            }
                        } else {
                            // Failed to resolve URL
                            projectDigitalAssetDao.updateDownloadStatus(
                                entity.nodeId, 
                                "FAILED",
                                System.currentTimeMillis()
                            )
                            android.util.Log.w("ProjectDigitalAssetRepo", "Failed to resolve download URL for file_id: ${entity.fileId}")
                        }
                    } catch (e: Exception) {
                        projectDigitalAssetDao.updateDownloadStatus(
                            entity.nodeId, 
                            "FAILED",
                            System.currentTimeMillis()
                        )
                        android.util.Log.e("ProjectDigitalAssetRepo", "Error processing asset ${entity.fileId}", e)
                    }
                }
            }

            // 清理：移除当前项目数字资产树中不存在的本地缓存与数据库记录
            // 仅针对file节点执行，避免误删文件夹占位记录
            try {
                val currentFileIds: Set<String> = assetNodes.mapNotNull { it.fileId }.toSet()
                val pruned = pruneAssetsNotInProjectTree(projectUid = projectUid, currentFileIds = currentFileIds)
                android.util.Log.d("ProjectDigitalAssetRepo", "Pruned $pruned obsolete assets for project uid=$projectUid")
            } catch (e: Exception) {
                android.util.Log.e("ProjectDigitalAssetRepo", "Error pruning obsolete assets for project uid=$projectUid", e)
            }

            android.util.Log.d("ProjectDigitalAssetRepo", "Completed processing digital asset tree for project: $projectId")
            successCount to entities.size
        } catch (e: Exception) {
            android.util.Log.e("ProjectDigitalAssetRepo", "Error processing digital asset tree for project: $projectId", e)
            0 to 0
        }
    }

    /**
     * 清理与当前项目不再关联的数字资产。
     * 逻辑：
     * - 取出本地表中project_uids包含当前projectUid的记录（文件型）；
     * - 若其file_id未出现在本次解析的项目数字资产树中，则从project_uids中移除此projectUid；
     * - 若移除后project_uids为空，则删除数据库记录并清理对应本地文件缓存；否则仅更新project_uids。
     * @param projectUid 当前项目UID
     * @param currentFileIds 当前项目树中的file_id集合
     * @return 实际删除的记录数量
     */
    private suspend fun pruneAssetsNotInProjectTree(projectUid: String, currentFileIds: Set<String>): Int = withContext(Dispatchers.IO) {
        val existing = projectDigitalAssetDao.getByProjectUid(projectUid)
        var deletedCount = 0

        existing.forEach { asset ->
            val fileId = asset.fileId
            // 仅处理文件资产（fileId不为null），避免误删文件夹记录
            if (fileId != null && !currentFileIds.contains(fileId)) {
                try {
                    // 更新project_uids，移除当前项目UID
                    val updatedJson = ProjectUidsUtils.removeProjectUidFromArray(asset.projectUids, projectUid)
                    val remaining = ProjectUidsUtils.parseProjectUidsToList(updatedJson)

                    if (remaining.isEmpty()) {
                        // 无项目再引用：删除本地文件与数据库记录
                        asset.localPath?.let { path ->
                            try {
                                val file = File(path)
                                if (file.exists()) file.delete()
                            } catch (_: Exception) { /* 忽略本地删除异常，继续删除DB */ }
                        }
                        projectDigitalAssetDao.deleteById(asset.id)
                        deletedCount++
                        android.util.Log.d("ProjectDigitalAssetRepo", "Deleted orphan asset fileId=$fileId, nodeId=${asset.nodeId}")
                    } else {
                        // 仍被其他项目引用：仅更新project_uids
                        projectDigitalAssetDao.updateProjectUids(asset.nodeId, updatedJson, System.currentTimeMillis())
                        android.util.Log.d("ProjectDigitalAssetRepo", "Unlinked project uid=$projectUid from asset fileId=$fileId, remain=${remaining.size}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ProjectDigitalAssetRepo", "Error pruning asset fileId=$fileId", e)
                }
            }
        }
        deletedCount
    }

    /**
     * Download a digital asset file.
     * 使用与风险矩阵相同的下载和状态更新逻辑
     *
     * @param asset Asset entity to download
     * @return True if download successful, false otherwise
     */
    private suspend fun downloadDigitalAsset(asset: ProjectDigitalAssetEntity): Boolean {
        return try {
            android.util.Log.d("ProjectDigitalAssetRepo", "Starting download for asset: ${asset.nodeId}, file_id: ${asset.fileId}")
            
            // Update status to downloading
            projectDigitalAssetDao.updateDownloadStatus(
                asset.nodeId, 
                "DOWNLOADING",
                System.currentTimeMillis()
            )

            // Get download URL from database or resolve it if not available
            val downloadUrl = asset.downloadUrl ?: resolveDownloadUrl(asset.fileId ?: return false)
            if (downloadUrl == null) {
                android.util.Log.e("ProjectDigitalAssetRepo", "Failed to get download URL for asset: ${asset.nodeId}")
                projectDigitalAssetDao.updateDownloadStatus(
                    asset.nodeId, 
                    "FAILED",
                    System.currentTimeMillis()
                )
                return false
            }

            // Update download URL in database if it wasn't already there
            if (asset.downloadUrl == null) {
                projectDigitalAssetDao.updateDownloadUrl(
                    asset.nodeId,
                    downloadUrl,
                    System.currentTimeMillis()
                )
                android.util.Log.d("ProjectDigitalAssetRepo", "Updated download URL for asset: ${asset.nodeId}")
            }

            // Download file
            val localPath = downloadFile(downloadUrl, asset.fileId!!, asset.type)
            if (localPath == null) {
                android.util.Log.e("ProjectDigitalAssetRepo", "Failed to download file for asset: ${asset.nodeId}")
                projectDigitalAssetDao.updateDownloadStatus(
                    asset.nodeId, 
                    "FAILED",
                    System.currentTimeMillis()
                )
                return false
            }

            // Update completion status - 使用与风险矩阵相同的更新方式
            projectDigitalAssetDao.updateDownloadComplete(
                nodeId = asset.nodeId,
                status = "COMPLETED",
                localPath = localPath,
                content = null, // 非风险矩阵文件不需要content字段
                updatedAt = System.currentTimeMillis()
            )
            
            android.util.Log.d("ProjectDigitalAssetRepo", "Successfully downloaded and cached asset: ${asset.nodeId} to $localPath")
            true
        } catch (e: Exception) {
            android.util.Log.e("ProjectDigitalAssetRepo", "Exception downloading asset: ${asset.nodeId}", e)
            projectDigitalAssetDao.updateDownloadStatus(
                asset.nodeId, 
                "FAILED",
                System.currentTimeMillis()
            )
            false
        }
    }

    /**
     * Resolve download URL for a file ID.
     * 使用与风险矩阵相同的URL解析逻辑，支持多种响应格式
     * 同时提取file_type字段并更新数据库中的type字段
     *
     * @param fileId File ID to resolve
     * @return Download URL or null if failed
     */
    private suspend fun resolveDownloadUrl(fileId: String): String? {
        return try {
            android.util.Log.d("ProjectDigitalAssetRepo", "Resolving download URL for file_id: $fileId")
            
            val response: Response<ResponseBody> = apiService.resolveDownloadUrl(
                endpoint = DOWNLOAD_URL_ENDPOINT,
                fileIds = listOf(fileId)
            )

            if (!response.isSuccessful) {
                android.util.Log.e("ProjectDigitalAssetRepo", "Failed to resolve download URL for file ID: $fileId - HTTP ${response.code()}")
                return null
            }

            val responseBody = response.body()?.string() ?: return null
            android.util.Log.d("ProjectDigitalAssetRepo", "URL resolve response: $responseBody")
            
            val jsonResponse = JSONObject(responseBody)
            
            // 支持多种响应格式（与风险矩阵保持一致）：
            // 1. { "data": [{"url": "...", "file_type": "..."}, ...] }  - 用户图片中的格式
            // 2. { "data": { "urls": [{"url": "...", "file_type": "..."}, ...] } }  - 原有格式
            // 3. { "urls": [{"url": "...", "file_type": "..."}, ...] }  - 直接格式
            var downloadUrl: String? = null
            var fileType: String? = null
            
            // 尝试格式1: data[0].url 和 data[0].file_type
            val dataArray = jsonResponse.optJSONArray("data")
            if (dataArray != null && dataArray.length() > 0) {
                val firstItem = dataArray.optJSONObject(0)
                downloadUrl = firstItem?.optString("url")
                fileType = firstItem?.optString("file_type")?.takeIf { it.isNotEmpty() }
                android.util.Log.d("ProjectDigitalAssetRepo", "Found URL using data[0].url format: $downloadUrl, file_type: $fileType")
            }
            
            // 尝试格式2: data.urls[0].url 和 data.urls[0].file_type
            if (downloadUrl.isNullOrEmpty()) {
                val data = jsonResponse.optJSONObject("data")
                if (data != null) {
                    val urlsArray = data.optJSONArray("urls")
                    if (urlsArray != null && urlsArray.length() > 0) {
                        val firstItem = urlsArray.optJSONObject(0)
                        downloadUrl = firstItem?.optString("url")
                        fileType = firstItem?.optString("file_type")?.takeIf { it.isNotEmpty() }
                        android.util.Log.d("ProjectDigitalAssetRepo", "Found URL using data.urls[0].url format: $downloadUrl, file_type: $fileType")
                    }
                }
            }
            
            // 尝试格式3: urls[0].url 和 urls[0].file_type
            if (downloadUrl.isNullOrEmpty()) {
                val urlsArray = jsonResponse.optJSONArray("urls")
                if (urlsArray != null && urlsArray.length() > 0) {
                    val firstItem = urlsArray.optJSONObject(0)
                    downloadUrl = firstItem?.optString("url")
                    fileType = firstItem?.optString("file_type")?.takeIf { it.isNotEmpty() }
                    android.util.Log.d("ProjectDigitalAssetRepo", "Found URL using urls[0].url format: $downloadUrl, file_type: $fileType")
                }
            }
            
            // 尝试格式4: 直接字符串或其他字段名
            if (downloadUrl.isNullOrEmpty()) {
                val data = jsonResponse.optJSONObject("data") ?: jsonResponse
                downloadUrl = data.optString("url").takeIf { it.isNotEmpty() }
                    ?: data.optString("download_url").takeIf { it.isNotEmpty() }
                fileType = data.optString("file_type").takeIf { it.isNotEmpty() }
                android.util.Log.d("ProjectDigitalAssetRepo", "Found URL using direct format: $downloadUrl, file_type: $fileType")
            }
            
            if (downloadUrl.isNullOrEmpty()) {
                android.util.Log.e("ProjectDigitalAssetRepo", "No download URL found in response for file ID: $fileId")
                return null
            }
            
            // 如果获取到了file_type，更新数据库中对应记录的type字段
            if (!fileType.isNullOrEmpty()) {
                try {
                    // 根据fileId查找对应的数字资产记录并更新type字段
                    val asset = projectDigitalAssetDao.getByFileId(fileId)
                    if (asset != null) {
                        projectDigitalAssetDao.updateType(
                            nodeId = asset.nodeId,
                            type = fileType,
                            updatedAt = System.currentTimeMillis()
                        )
                        android.util.Log.d("ProjectDigitalAssetRepo", "Updated type for asset ${asset.nodeId}: $fileType")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ProjectDigitalAssetRepo", "Error updating asset type for file_id: $fileId", e)
                }
            }
            
            android.util.Log.d("ProjectDigitalAssetRepo", "Successfully resolved download URL for file_id: $fileId -> $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            android.util.Log.e("ProjectDigitalAssetRepo", "Error resolving download URL for file_id: $fileId", e)
            null
        }
    }

    /**
     * Download file from URL and save to local storage.
     * This method handles downloading and caching of all types of digital assets.
     *
     * @param downloadUrl URL to download from
     * @param fileId File ID for naming
     * @param fileType File type for extension determination
     * @return Local file path or null if failed
     */
    private suspend fun downloadFile(
        downloadUrl: String,
        fileId: String,
        fileType: String?
    ): String? {
        return try {
            android.util.Log.d("ProjectDigitalAssetRepo", "Starting download for file_id: $fileId")
            android.util.Log.d("ProjectDigitalAssetRepo", "Download URL: $downloadUrl")
            
            val response: Response<ResponseBody> = apiService.downloadRiskMatrixByUrl(downloadUrl)
            
            if (!response.isSuccessful) {
                android.util.Log.e("ProjectDigitalAssetRepo", "Download failed for file_id: $fileId - HTTP ${response.code()}: ${response.message()}")
                return null
            }

            val responseBody = response.body() ?: return null
            val inputStream: InputStream = responseBody.byteStream()

            // Create local directory for digital assets cache
            val assetsDir = File(context.filesDir, DIGITAL_ASSETS_DIR)
            if (!assetsDir.exists()) {
                assetsDir.mkdirs()
            }

            // Generate file name with appropriate extension
            val extension = when {
                fileType != null -> ".$fileType"
                downloadUrl.contains(".pdf", ignoreCase = true) -> ".pdf"
                downloadUrl.contains(".json", ignoreCase = true) -> ".json"
                downloadUrl.contains(".jpg", ignoreCase = true) || downloadUrl.contains(".jpeg", ignoreCase = true) -> ".jpg"
                downloadUrl.contains(".png", ignoreCase = true) -> ".png"
                downloadUrl.contains(".mp3", ignoreCase = true) -> ".mp3"
                downloadUrl.contains(".mp4", ignoreCase = true) -> ".mp4"
                else -> ""
            }
            val fileName = "${fileId}${extension}"
            val localFile = File(assetsDir, fileName)

            android.util.Log.d("ProjectDigitalAssetRepo", "Local file path: ${localFile.absolutePath}")

            // Write file to local storage
            FileOutputStream(localFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            android.util.Log.d("ProjectDigitalAssetRepo", "Successfully downloaded and cached file_id: $fileId to ${localFile.absolutePath}")
            localFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("ProjectDigitalAssetRepo", "Exception downloading file_id: $fileId", e)
            null
        }
    }

    /**
     * 获取本地缓存的文件
     * 
     * @param fileId 文件ID
     * @return 本地文件，如果不存在则返回null
     */
    suspend fun getLocalFile(fileId: String): File? {
        return withContext(Dispatchers.IO) {
            val asset = projectDigitalAssetDao.getByFileId(fileId)
            if (asset != null && 
                asset.downloadStatus == "COMPLETED" && 
                !asset.localPath.isNullOrEmpty()) {
                val file = File(asset.localPath)
                if (file.exists()) {
                    file
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    /**
     * Get all cached digital assets for a project.
     *
     * @param projectUid Project UID
     * @return List of successfully downloaded assets
     */
    suspend fun getCachedAssets(projectUid: String): List<ProjectDigitalAssetEntity> {
        return projectDigitalAssetDao.getCompletedByProjectUid(projectUid)
    }

    /**
     * Check if a file is cached locally.
     *
     * @param fileId File ID to check
     * @return True if file is downloaded and cached, false otherwise
     */
    suspend fun isFileCached(fileId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val asset = projectDigitalAssetDao.getByFileId(fileId)
            asset != null && 
            asset.downloadStatus == "COMPLETED" && 
            !asset.localPath.isNullOrEmpty() && 
            File(asset.localPath).exists()
        }
    }

    /**
     * Retry failed downloads for a project.
     *
     * @param projectUid Project UID
     * @return Number of successful retries
     */
    suspend fun retryFailedDownloads(projectUid: String): Int = withContext(Dispatchers.IO) {
        val failedAssets = projectDigitalAssetDao.getFailedDownloadsByProjectUid(projectUid)
        
        var successCount = 0
        for (asset in failedAssets) {
            if (downloadDigitalAsset(asset)) {
                successCount++
            }
        }
        successCount
    }

    /**
     * Clear all digital assets for a project.
     *
     * @param projectUid Project UID
     */
    suspend fun clearProjectAssets(projectUid: String) {
        // Delete local files first
        val assets = projectDigitalAssetDao.getByProjectUid(projectUid)
        assets.forEach { asset ->
            asset.localPath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
        // Delete from database
        projectDigitalAssetDao.deleteByProjectUid(projectUid)
    }
}