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
        private const val DOWNLOAD_URL_ENDPOINT = "https://sims.ink-stone.win/zuul/sims-ym/storage/download/url"
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
            val entities = assetNodes.map { node ->
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
                
                ProjectDigitalAssetEntity(
                    projectUid = projectUid,
                    nodeId = nodeId,
                    parentId = parentId,
                    name = node.nodeName ?: "Unknown",
                    type = when {
                        node.isRiskMatrix -> "risk_matrix"
                        node.fileId != null -> "file"
                        else -> "folder"
                    },
                    fileId = node.fileId,
                    localPath = null,
                    downloadStatus = "PENDING",
                    downloadUrl = null,
                    fileSize = node.fileSize,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }

            // Insert or update entities using upsert to avoid duplicates
            projectDigitalAssetDao.upsertAll(entities)

            // Process risk matrix data separately (download to cache)
            val riskMatrixEntities = entities.filter { it.type == "risk_matrix" }
            val riskMatrixResult = riskMatrixRepository.processRiskMatrixData(projectUid, riskMatrixEntities)
            if (riskMatrixResult.isSuccess) {
                android.util.Log.d("ProjectDigitalAssetRepo", "Successfully processed ${riskMatrixResult.getOrNull()} risk matrix nodes")
            } else {
                android.util.Log.w("ProjectDigitalAssetRepo", "Risk matrix processing failed", riskMatrixResult.exceptionOrNull())
            }

            // Download ALL assets (including both risk matrix and regular assets)
            // This ensures that all file_id nodes get their download URLs resolved and data fetched
            var successCount = 0
            for (node in assetNodes) {
                val entity = entities.find { it.fileId == node.fileId }
                if (entity != null) {
                    if (node.isRiskMatrix) {
                        // Risk matrix nodes are processed by RiskMatrixRepository
                        // RiskMatrixRepository already handles status and localPath updates
                        if (riskMatrixResult.isSuccess && riskMatrixResult.getOrNull()!! > 0) {
                            // No need to update status or localPath here as RiskMatrixRepository already did it
                            // Just count the success
                            successCount++
                            android.util.Log.d("ProjectDigitalAssetRepo", "Risk matrix node ${entity.fileId} processed successfully")
                        } else {
                            projectDigitalAssetDao.updateDownloadStatus(
                                entity.nodeId, 
                                "FAILED",
                                System.currentTimeMillis()
                            )
                            android.util.Log.w("ProjectDigitalAssetRepo", "Risk matrix node ${entity.fileId} processing failed")
                        }
                    } else {
                        // Process regular assets through normal download flow
                        if (downloadDigitalAsset(entity)) {
                            successCount++
                        }
                    }
                }
            }

            android.util.Log.d("ProjectDigitalAssetRepo", "Completed processing digital asset tree for project: $projectId")
            successCount to entities.size
        } catch (e: Exception) {
            android.util.Log.e("ProjectDigitalAssetRepo", "Error processing digital asset tree for project: $projectId", e)
            0 to 0
        }
    }

    /**
     * Download a digital asset file.
     *
     * @param asset Asset entity to download
     * @return True if download successful, false otherwise
     */
    private suspend fun downloadDigitalAsset(asset: ProjectDigitalAssetEntity): Boolean {
        return try {
            // Update status to downloading
            projectDigitalAssetDao.updateDownloadStatus(
                asset.nodeId, 
                "DOWNLOADING",
                System.currentTimeMillis()
            )

            // Resolve download URL
            val downloadUrl = resolveDownloadUrl(asset.fileId ?: return false)
            if (downloadUrl == null) {
                projectDigitalAssetDao.updateDownloadStatus(
                    asset.nodeId, 
                    "FAILED",
                    System.currentTimeMillis()
                )
                return false
            }

            // Download file
            val localPath = downloadFile(downloadUrl, asset.fileId!!, asset.type)
            if (localPath == null) {
                projectDigitalAssetDao.updateDownloadStatus(
                    asset.nodeId, 
                    "FAILED",
                    System.currentTimeMillis()
                )
                return false
            }

            // Update completion status
            projectDigitalAssetDao.updateLocalPath(asset.nodeId, localPath, System.currentTimeMillis())
            projectDigitalAssetDao.updateDownloadStatus(asset.nodeId, "COMPLETED", System.currentTimeMillis())
            true
        } catch (e: Exception) {
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
     *
     * @param fileId File ID to resolve
     * @return Download URL or null if failed
     */
    private suspend fun resolveDownloadUrl(fileId: String): String? {
        return try {
            val response: Response<ResponseBody> = apiService.resolveDownloadUrl(
                endpoint = DOWNLOAD_URL_ENDPOINT,
                fileIds = listOf(fileId)
            )

            if (!response.isSuccessful) {
                return null
            }

            val responseBody = response.body()?.string() ?: return null
            val jsonResponse = JSONObject(responseBody)
            
            // Try different response structures
            val data = jsonResponse.optJSONObject("data") 
                ?: jsonResponse.optJSONObject("result")
                ?: jsonResponse

            // Handle array response
            val urlsArray = data.optJSONArray("urls") 
                ?: data.optJSONArray("download_urls")
                ?: data.optJSONArray("files")

            if (urlsArray != null && urlsArray.length() > 0) {
                val firstItem = urlsArray.optJSONObject(0)
                return firstItem?.optString("url") 
                    ?: firstItem?.optString("download_url")
                    ?: urlsArray.optString(0)
            }

            // Handle single URL response
            data.optString("url").takeIf { it.isNotEmpty() }
                ?: data.optString("download_url").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Download file from URL and save to local storage.
     *
     * @param downloadUrl URL to download from
     * @param fileId File ID for naming
     * @param fileType File type for extension
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

            // Create local directory
            val assetsDir = File(context.filesDir, DIGITAL_ASSETS_DIR)
            if (!assetsDir.exists()) {
                assetsDir.mkdirs()
            }

            // Generate file name
            val extension = fileType?.let { ".$it" } ?: ""
            val fileName = "${fileId}${extension}"
            val localFile = File(assetsDir, fileName)

            android.util.Log.d("ProjectDigitalAssetRepo", "Local file path: ${localFile.absolutePath}")

            // Write file
            FileOutputStream(localFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            android.util.Log.d("ProjectDigitalAssetRepo", "Successfully downloaded file_id: $fileId to ${localFile.absolutePath}")
            localFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("ProjectDigitalAssetRepo", "Exception downloading file_id: $fileId", e)
            null
        }
    }

    /**
     * Get local file for a file ID.
     *
     * @param fileId File ID to look up
     * @return File object or null if not found or not downloaded
     */
    suspend fun getLocalFile(fileId: String): File? {
        val assets = projectDigitalAssetDao.getByFileId(fileId)
        val asset = assets.firstOrNull()
        return if (asset?.downloadStatus == "COMPLETED" && asset.localPath != null) {
            val file = File(asset.localPath)
            if (file.exists()) file else null
        } else {
            null
        }
    }

    /**
     * Retry failed downloads for a project.
     *
     * @param projectUid Project UID
     * @return Number of successful retries
     */
    suspend fun retryFailedDownloads(projectUid: String): Int = withContext(Dispatchers.IO) {
        val failedAssets = projectDigitalAssetDao.getFailedDownloads()
            .filter { it.projectUid == projectUid }
        
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