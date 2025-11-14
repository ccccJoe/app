/*
 * File: DefectDataAssetRepository.kt
 * Description: 缺陷数字资产仓库，负责解析历史缺陷中的 defect_data_assets 字段，解析下载链接，下载并缓存到本地，同时写入 Room 表。
 * Author: SIMS Team
 */
package com.simsapp.data.repository

import android.content.Context
import android.util.Log
import com.simsapp.data.local.dao.DefectDataAssetDao
import com.simsapp.data.local.entity.DefectDataAssetEntity
import com.simsapp.data.remote.ApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
 * 类：DefectDataAssetRepository
 * 职责：
 * - 解析项目详情中的 `history_defect_list` 每项的 `defect_data_assets` 字段。
 * - 调用后端解析接口通过 `file_id` 获得下载URL与文件类型。
 * - 下载文件并缓存到本地目录 `files/defect_assets/<defectUid>/`，写入数据库。
 * 设计思路：
 * - 参考 ProjectDigitalAssetRepository 的稳健解析逻辑，但按缺陷维度实现，不照抄，字段最小集。
 */
@Singleton
class DefectDataAssetRepository @Inject constructor(
    private val dao: DefectDataAssetDao,
    private val api: ApiService,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DefectDataAssetRepo"
        private const val DOWNLOAD_URL_ENDPOINT = "storage/download/url"
        private const val DEFECT_ASSETS_DIR = "defect_assets"
    }

    /**
     * 内部数据类：ResolvedAsset
     * 说明：封装解析接口返回的下载直链、文件类型与原始文件名。
     * - url: 下载直链
     * - fileType: 服务端返回的文件类型（如 JPG/PNG/PDF/MP3 等）
     * - fileName: 服务端返回的原始文件名
     */
    private data class ResolvedAsset(
        val url: String,
        val fileType: String?,
        val fileName: String?
    )

    /**
     * 函数：processFromProjectDetail
     * 说明：遍历项目详情 JSON 的历史缺陷列表，解析并下载每条缺陷的 `defect_data_assets`。
     *
     * @param projectUid 项目UID
     * @param detailJson 项目详情原始JSON
     */
    suspend fun processFromProjectDetail(projectUid: String, detailJson: String) = withContext(Dispatchers.IO) {
        if (detailJson.isBlank()) return@withContext
        try {
            val obj = findHistoryDefectsObject(detailJson)
            val arr = obj.optJSONArray("history_defect_list") ?: return@withContext
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val defectUid = when {
                    item.has("uid") -> item.optString("uid")
                    item.has("defect_uid") -> item.optString("defect_uid")
                    else -> ""
                }.orEmpty()

                // 必须存在缺陷UID才能建立关联
                if (defectUid.isBlank()) continue

                val assets = parseDefectDataAssets(item)
                if (assets.isEmpty()) continue

                // 插入占位并解析下载URL、类型、文件名，然后下载
                assets.forEach { (fileId, typeHint) ->
                    try {
                        // 先写入占位记录（避免并发重复解析）
                        val exists = dao.getByFileId(fileId)
                        val assetEntity = exists ?: DefectDataAssetEntity(
                            projectUid = projectUid,
                            defectUid = defectUid,
                            fileId = fileId,
                            type = typeHint ?: "UNKNOWN"
                        )
                        if (exists == null) dao.insert(assetEntity)

                        // 解析下载URL（同时可能返回 file_type 与 file_name）
                        val resolved = resolveDownloadUrl(fileId)
                        val downloadUrl = resolved?.url
                        val resolvedType = resolved?.fileType
                        val resolvedName = resolved?.fileName
                        if (downloadUrl.isNullOrBlank()) {
                            dao.updateDownloadStatus(fileId, "FAILED", System.currentTimeMillis())
                            Log.w(TAG, "resolve url failed for file_id=$fileId")
                            return@forEach
                        }
                        if (!resolvedType.isNullOrBlank()) {
                            dao.updateType(fileId, resolvedType, System.currentTimeMillis())
                        }
                        dao.updateDownloadUrl(fileId, downloadUrl, System.currentTimeMillis())
                        if (!resolvedName.isNullOrBlank()) {
                            dao.updateFileName(fileId, resolvedName, System.currentTimeMillis())
                        }

                        // 下载文件
                        val local = downloadFile(downloadUrl, defectUid, fileId, resolvedType ?: assetEntity.type, resolvedName)
                        if (local.isNullOrBlank()) {
                            dao.updateDownloadStatus(fileId, "FAILED", System.currentTimeMillis())
                        } else {
                            dao.updateLocalPath(fileId, local, System.currentTimeMillis())
                            dao.updateDownloadStatus(fileId, "COMPLETED", System.currentTimeMillis())
                        }
                    } catch (e: Exception) {
                        dao.updateDownloadStatus(fileId, "FAILED", System.currentTimeMillis())
                        Log.e(TAG, "process asset error file_id=$fileId: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "processFromProjectDetail error: ${e.message}", e)
        }
    }

    /**
     * 函数：findHistoryDefectsObject
     * 说明：从原始JSON中定位含有 `history_defect_list` 的对象（兼容 data/item/result 包裹）。
     * @param raw 原始JSON字符串
     * @return 包含 `history_defect_list` 的对象或根对象
     */
    private fun findHistoryDefectsObject(raw: String): JSONObject {
        val trimmed = raw.trim()
        return try {
            if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                if (root.has("history_defect_list")) return root
                val wrappers = listOf("data", "item", "result")
                for (k in wrappers) {
                    val child = root.optJSONObject(k)
                    if (child != null && child.has("history_defect_list")) return child
                }
                root
            } else if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                arr.optJSONObject(0) ?: JSONObject()
            } else JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }
    }

    /**
     * 函数：parseDefectDataAssets
     * 说明：解析单个缺陷项中的 `defect_data_assets`，兼容多种格式。
     * 支持：
     * - 字符串 "id1,id2"
     * - 字符串数组 ["id1","id2"]
     * - 对象数组 [{"file_id":"...","type":"PIC"}, ...]
     * - 对象或字符串中无类型时，type 返回 null，后续解析接口补齐。
     * @return List<Pair<fileId, typeHint?>>
     */
    private fun parseDefectDataAssets(item: JSONObject): List<Pair<String, String?>> {
        val v = item.opt("defect_data_assets") ?: return emptyList()
        return when (v) {
            is JSONArray -> {
                val out = mutableListOf<Pair<String, String?>>()
                for (i in 0 until v.length()) {
                    val elem = v.opt(i)
                    when (elem) {
                        is String -> if (elem.isNotBlank()) out += (elem to null)
                        is JSONObject -> {
                            val fid = elem.optString("file_id").takeIf { it.isNotBlank() }
                                ?: elem.optString("id").takeIf { it.isNotBlank() }
                            val t = elem.optString("type").takeIf { it.isNotBlank() }
                                ?: elem.optString("file_type").takeIf { it.isNotBlank() }
                            if (!fid.isNullOrBlank()) out += (fid to t)
                        }
                    }
                }
                out
            }
            is String -> v.split(',').mapNotNull { s ->
                val id = s.trim().takeIf { it.isNotBlank() }
                id?.let { it to null }
            }
            is JSONObject -> {
                val fid = v.optString("file_id").takeIf { it.isNotBlank() }
                    ?: v.optString("id").takeIf { it.isNotBlank() }
                val t = v.optString("type").takeIf { it.isNotBlank() }
                    ?: v.optString("file_type").takeIf { it.isNotBlank() }
                if (!fid.isNullOrBlank()) listOf(fid to t) else emptyList()
            }
            else -> emptyList()
        }
    }

    /**
     * 函数：resolveDownloadUrl
     * 说明：通过文件ID解析下载直链，兼容多种响应结构；同时尝试抽取 `file_type`。
     * @param fileId 文件ID
     * @return Pair(url, fileType) 或 null
     */
    private suspend fun resolveDownloadUrl(fileId: String): ResolvedAsset? = withContext(Dispatchers.IO) {
        try {
            val resp: Response<ResponseBody> = api.resolveDownloadUrl(
                endpoint = DOWNLOAD_URL_ENDPOINT,
                fileIds = listOf(fileId)
            )
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body()?.string() ?: return@withContext null
            val obj = JSONObject(body)

            var url: String? = null
            var type: String? = null
            var name: String? = null

            // 优先 data[0]
            obj.optJSONArray("data")?.let { arr ->
                if (arr.length() > 0) {
                    val first = arr.optJSONObject(0)
                    url = first?.optString("url").takeIf { !it.isNullOrBlank() }
                    type = first?.optString("file_type").takeIf { !it.isNullOrBlank() }
                    name = first?.optString("file_name").takeIf { !it.isNullOrBlank() }
                }
            }
            if (url.isNullOrBlank()) {
                val dataObj = obj.optJSONObject("data")
                val urls = dataObj?.optJSONArray("urls")
                if (urls != null && urls.length() > 0) {
                    val first = urls.optJSONObject(0)
                    url = first?.optString("url").takeIf { !it.isNullOrBlank() }
                    type = first?.optString("file_type").takeIf { !it.isNullOrBlank() }
                    name = first?.optString("file_name").takeIf { !it.isNullOrBlank() }
                }
            }
            if (url.isNullOrBlank()) {
                obj.optJSONArray("urls")?.let { urls ->
                    if (urls.length() > 0) {
                        val first = urls.optJSONObject(0)
                        url = first?.optString("url").takeIf { !it.isNullOrBlank() }
                        type = first?.optString("file_type").takeIf { !it.isNullOrBlank() }
                        name = first?.optString("file_name").takeIf { !it.isNullOrBlank() }
                    }
                }
            }
            if (url.isNullOrBlank()) {
                val d = obj.optJSONObject("data") ?: obj
                url = d.optString("url").takeIf { it.isNotEmpty() }
                    ?: d.optString("download_url").takeIf { it.isNotEmpty() }
                type = d.optString("file_type").takeIf { it.isNotEmpty() }
                name = d.optString("file_name").takeIf { it.isNotEmpty() }
            }
            if (url.isNullOrBlank()) return@withContext null
            return@withContext ResolvedAsset(url!!, type, name)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 函数：downloadFile
     * 说明：根据 URL 下载文件，并存储到缺陷专属目录；
     *       为避免远端同名文件在同一缺陷目录下相互覆盖，统一以 `fileId` 作为本地文件名基准。
     *       扩展名优先从远端文件名解析，其次使用 `fileType` 映射，最后回退 URL 解析。
     * 参数：
     * - url：下载直链
     * - defectUid：关联缺陷 UID
     * - fileId：唯一文件 ID（用于生成唯一文件名）
     * - fileType：文件类型提示（如 JPG/PNG/PDF/MP3 等）
     * - fileName：远端原始文件名（仅用于解析扩展名，不参与本地命名）
     * 返回：本地绝对路径或 null
     */
    private suspend fun downloadFile(url: String, defectUid: String, fileId: String, fileType: String, fileName: String?): String? = withContext(Dispatchers.IO) {
        try {
            val resp: Response<ResponseBody> = api.downloadRiskMatrixByUrl(url)
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body() ?: return@withContext null
            val dir = File(context.filesDir, "$DEFECT_ASSETS_DIR/${sanitize(defectUid)}")
            if (!dir.exists()) dir.mkdirs()

            // 统一以 fileId 生成唯一文件名，扩展名解析策略：fileName -> fileType -> url
            val extFromName = try {
                if (!fileName.isNullOrBlank() && fileName.contains('.')) {
                    "." + fileName.substringAfterLast('.').lowercase()
                } else null
            } catch (_: Exception) { null }

            val extFromType = when (fileType.lowercase()) {
                "jpg", "jpeg" -> ".jpg"
                "png" -> ".png"
                "gif" -> ".gif"
                "bmp" -> ".bmp"
                "webp" -> ".webp"
                "pdf" -> ".pdf"
                "mp3" -> ".mp3"
                "wav" -> ".wav"
                "m4a" -> ".m4a"
                else -> null
            }

            val finalExt = extFromName ?: extFromType ?: guessExtFromUrl(url)
            val finalName = "${sanitize(fileId)}${finalExt}"

            val target = File(dir, finalName)
            body.byteStream().use { input ->
                FileOutputStream(target).use { out ->
                    val buf = ByteArray(8 * 1024)
                    while (true) {
                        val r = input.read(buf)
                        if (r == -1) break
                        out.write(buf, 0, r)
                    }
                    out.flush()
                }
            }
            target.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "downloadFile error: ${e.message}", e)
            null
        }
    }

    /** 简单清洗文件名。 */
    private fun sanitize(s: String): String = s.replace(Regex("[^A-Za-z0-9._-]"), "_")

    /** 从URL粗略推断扩展名。 */
    private fun guessExtFromUrl(url: String): String {
        return try {
            val path = java.net.URI(url).path ?: ""
            val name = path.substringAfterLast('/')
            when {
                name.endsWith(".jpg", true) -> ".jpg"
                name.endsWith(".jpeg", true) -> ".jpg"
                name.endsWith(".png", true) -> ".png"
                name.endsWith(".pdf", true) -> ".pdf"
                name.endsWith(".mp3", true) -> ".mp3"
                name.endsWith(".m4a", true) -> ".m4a"
                name.endsWith(".wav", true) -> ".wav"
                else -> ".bin"
            }
        } catch (_: Exception) { ".bin" }
    }
}