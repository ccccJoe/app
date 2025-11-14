/*
 * File: EventRepository.kt
 * Description: Repository for event-related data and asset upload.
 * Author: SIMS Team
 */
package com.simsapp.data.repository

import android.content.Context
import com.simsapp.data.local.dao.EventDao
import com.simsapp.data.local.entity.EventEntity
import com.simsapp.data.remote.ApiService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import com.google.gson.annotations.SerializedName

/**
 * EventRepository
 *
 * Provides unified operations for events, including optional asset upload.
 */
@Singleton
class EventRepository @Inject constructor(
    private val dao: EventDao,
    private val api: ApiService
) {
    /** Observe events by project id. */
    fun getEventsByProject(projectId: Long): Flow<List<EventEntity>> = dao.getByProject(projectId)
    
    /** Observe events by project uid. */
    fun getEventsByProjectUid(projectUid: String): Flow<List<EventEntity>> = dao.getByProjectUid(projectUid)
    
    /** Observe events that reference a specific defect by defect_id. */
    fun getEventsByDefectId(defectId: Long): Flow<List<EventEntity>> = dao.getByDefectId(defectId)
    
    /** Observe events that reference a specific defect by defect_no and project_uid. */
    fun getEventsByDefectNoAndProjectUid(projectUid: String, defectNo: String): Flow<List<EventEntity>> = 
        dao.getByDefectNoAndProjectUid(projectUid, defectNo)

    /** Observe events that reference a specific defect by defect_uid. */
    fun getEventsByDefectUid(defectUid: String): Flow<List<EventEntity>> = dao.getByDefectUid(defectUid)

    /** Observe events that reference a specific defect by defect_uid and project_uid. */
    fun getEventsByDefectUidAndProjectUid(projectUid: String, defectUid: String): Flow<List<EventEntity>> =
        dao.getByDefectUidAndProjectUid(projectUid, defectUid)

    /** Observe unsynced events by project_uid. */
    fun getUnsyncedEventsByProjectUid(projectUid: String): Flow<List<EventEntity>> = dao.getUnsyncedByProjectUid(projectUid)

    /**
     * Observe count of events that are not linked to any defect.
     * 对应 Unlinked Events：defect_uids 为空或空数组。
     */
    fun countUnlinkedEvents(): Flow<Int> = dao.countUnlinkedEvents()

    /** Create or update an event. */
    suspend fun upsert(event: EventEntity): Long = dao.insert(event)
    
    /** Create or update multiple events. */
    suspend fun upsertAll(events: List<EventEntity>): List<Long> = dao.insertAll(events)

    /** Get event by id. */
    suspend fun getEventById(eventId: Long): EventEntity? = dao.getById(eventId)

    /** Get event by UID. */
    suspend fun getEventByUid(uid: String): EventEntity? = dao.getByUid(uid)

    /** Mark single event as synced by UID. */
    suspend fun markEventSynced(uid: String) = dao.markSyncedByUid(uid)

    /** Mark multiple events as synced by UIDs. */
    suspend fun markEventsSynced(uids: List<String>) = dao.markSyncedByUids(uids)

    /** Delete event by id. */
    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Delete all events for a project by project_id. */
    suspend fun deleteByProjectId(projectId: Long) = dao.deleteByProjectId(projectId)

    /**
     * 覆盖指定事件目录下 meta.json 的 projectUid / project_uid 字段。
     * 用途：在开始同步前，根据用户选择的目标项目动态调整事件归属。
     * @param context 应用上下文，用于定位内部 files 目录
     * @param eventUid 事件唯一标识（目录名）
     * @param newProjectUid 用户选择的目标项目UID
     * @return Pair(Boolean, String) 是否成功及说明信息
     */
    suspend fun overrideMetaProjectUid(
        context: Context,
        eventUid: String,
        newProjectUid: String
    ): Pair<Boolean, String> {
        return try {
            val eventDir = File(File(context.filesDir, "events"), eventUid)
            if (!eventDir.exists()) {
                return false to "event dir not found: ${eventDir.absolutePath}"
            }
            val metaFile = File(eventDir, "meta.json")
            if (!metaFile.exists()) {
                // 若不存在，则创建最小meta结构
                val init = JSONObject().apply {
                    put("eventUid", eventUid)
                    put("projectUid", newProjectUid)
                    put("project_uid", newProjectUid)
                    put("updatedAt", System.currentTimeMillis())
                }
                metaFile.writeText(init.toString())
                return true to "meta.json initialized and projectUid=${newProjectUid}"
            }

            // 读取并更新
            val text = metaFile.readText()
            val json = try { JSONObject(text) } catch (_: Exception) { JSONObject() }
            json.put("projectUid", newProjectUid)
            json.put("project_uid", newProjectUid)
            json.put("updatedAt", System.currentTimeMillis())
            metaFile.writeText(json.toString())
            true to "meta.json updated projectUid=${newProjectUid}"
        } catch (e: Exception) {
            android.util.Log.e("EventRepository", "overrideMetaProjectUid failed: ${e.message}", e)
            false to (e.message ?: "unknown error")
        }
    }

    /**
     * 更新指定事件目录下 meta.json 的风险评估数据（level/score/answers）。
     *
     * 设计原则：
     * - 从本地数据库读取最新的 `riskLevel`/`riskScore`/`riskAnswers`，并写入 meta.json 的 `risk` 字段。
     * - 对 `answers` 保持原始 JSON 结构（对象或数组）；解析失败时降级为字符串或空串。
     * - 幂等、安全：不存在 meta.json 时创建最小结构；已有字段时进行覆盖更新，不影响其他键。
     *
     * @param context 应用上下文
     * @param eventUid 事件 UID（目录名）
     * @return Pair<Boolean, String> 是否成功及信息
     */
    suspend fun updateMetaRiskFromDb(
        context: Context,
        eventUid: String
    ): Pair<Boolean, String> {
        return try {
            val event = getEventByUid(eventUid)
                ?: return false to "event not found for uid=$eventUid"
            val eventDir = File(File(context.filesDir, "events"), eventUid)
            if (!eventDir.exists()) {
                return false to "event dir not found: ${eventDir.absolutePath}"
            }
            val metaFile = File(eventDir, "meta.json")
            val obj = try {
                if (metaFile.exists()) JSONObject(metaFile.readText()) else JSONObject()
            } catch (_: Exception) { JSONObject() }

            val riskObj = try { obj.optJSONObject("risk") ?: JSONObject() } catch (_: Exception) { JSONObject() }
            // 写入等级与分数（优先数据库，回退原值）
            run {
                val levelDb = event.riskLevel
                val scoreDb = event.riskScore
                if (levelDb != null) riskObj.put("level", levelDb) else if (!riskObj.has("level")) riskObj.put("level", "")
                if (scoreDb != null) riskObj.put("score", scoreDb) else if (!riskObj.has("score")) riskObj.put("score", 0.0)
            }

            // 写入 answers：保持原始结构
            val answersRaw = event.riskAnswers
            if (!answersRaw.isNullOrBlank()) {
                var written = false
                try {
                    // 尝试按对象解析
                    val objAns = JSONObject(answersRaw)
                    riskObj.put("answers", objAns)
                    written = true
                } catch (_: Exception) {
                    try {
                        // 尝试按数组解析
                        val arrAns = org.json.JSONArray(answersRaw)
                        riskObj.put("answers", arrAns)
                        written = true
                    } catch (_: Exception) {
                        // 降级为字符串
                    }
                }
                if (!written) {
                    riskObj.put("answers", answersRaw)
                }
            } else {
                // 保持空串，避免缺键导致解析异常
                riskObj.put("answers", "")
            }

            obj.put("risk", riskObj)
            metaFile.writeText(obj.toString())
            true to "meta risk updated for $eventUid"
        } catch (e: Exception) {
            android.util.Log.w("EventRepository", "updateMetaRiskFromDb failed for $eventUid: ${e.message}")
            false to "updateMetaRisk error: ${e.message}"
        }
    }

    /**
     * 函数：updateMetaCoreFromDb
     * 说明：将事件的核心字段（content/location/lastEditTime 以及必要的标识）从数据库更新到 meta.json。
     * 行为：
     * - 若事件目录或数据库记录不存在，返回失败信息；
     * - 若 meta.json 不存在则创建，存在则仅覆盖上述核心字段；
     * - 不修改项目归属字段（projectUid/project_uid），该字段由 overrideMetaProjectUid 负责覆盖；
     * - 幂等、安全：重复调用会以数据库内容为准进行更新。
     *
     * @param context 应用上下文
     * @param eventUid 事件 UID（目录名）
     * @return Pair<Boolean, String> 是否成功及信息
     */
    suspend fun updateMetaCoreFromDb(
        context: Context,
        eventUid: String
    ): Pair<Boolean, String> {
        return try {
            val event = getEventByUid(eventUid)
                ?: return false to "event not found for uid=$eventUid"
            val eventDir = File(File(context.filesDir, "events"), eventUid)
            if (!eventDir.exists()) {
                return false to "event dir not found: ${eventDir.absolutePath}"
            }

            val metaFile = File(eventDir, "meta.json")
            val obj = try {
                if (metaFile.exists()) JSONObject(metaFile.readText()) else JSONObject()
            } catch (_: Exception) { JSONObject() }

            // 写入或覆盖核心字段
            obj.put("uid", event.uid)
            obj.put("eventUid", event.uid)
            obj.put("content", event.content)
            // location 可能为 null，保持为 null 或空串以便解析安全
            obj.put("location", event.location ?: "")
            obj.put("lastEditTime", event.lastEditTime)

            // 可选标识字段，仅在不存在时补充，避免覆盖上游覆盖逻辑
            if (!obj.has("projectId")) obj.put("projectId", event.projectId)

            metaFile.writeText(obj.toString())
            true to "meta core updated for $eventUid"
        } catch (e: Exception) {
            android.util.Log.w("EventRepository", "updateMetaCoreFromDb failed for $eventUid: ${e.message}")
            false to "updateMetaCore error: ${e.message}"
        }
    }

    /** Upload a local file to the server, returns success flag. */
    suspend fun uploadAsset(file: File, projectId: Long, eventId: Long?): Boolean = try {
        val body = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", file.name, body)
        api.uploadAsset(part, projectId, eventId).isSuccessful
    } catch (e: Exception) {
        false
    }

    /**
     * 创建事件上传任务
     * @param taskUid 任务唯一标识
     * @param targetProjectUid 目标项目UID
     * @param uploadList 上传列表，包含事件UID、包哈希值、包名称
     * @return Pair(success, EventUploadResponse?) 成功标志和响应数据
     */
    suspend fun createEventUpload(
        taskUid: String,
        targetProjectUid: String,
        uploadList: List<EventUploadItem>
    ): Pair<Boolean, EventUploadResponse?> {
        return try {
            val requestData = EventUploadRequest(
                taskUid = taskUid,
                targetProjectUid = targetProjectUid,
                uploadList = uploadList
            )
            val gson = com.google.gson.Gson()
            val jsonString = gson.toJson(requestData)
            val requestBody = jsonString.toRequestBody("application/json".toMediaTypeOrNull())
            
            // 添加详细日志
            android.util.Log.d("EventRepository", "Creating event upload request:")
            android.util.Log.d("EventRepository", "Task UID: $taskUid")
            android.util.Log.d("EventRepository", "Target Project UID: $targetProjectUid")
            android.util.Log.d("EventRepository", "Upload List Size: ${uploadList.size}")
            android.util.Log.d("EventRepository", "Upload List Details:")
            uploadList.forEachIndexed { index, item ->
                android.util.Log.d("EventRepository", "  Item $index: eventUid=${item.eventUid}, hash=${item.eventPackageHash}, name=${item.eventPackageName}")
            }
            android.util.Log.d("EventRepository", "Request JSON: $jsonString")
            
            val response = api.createEventUpload(
                endpoint = "app/event/create_event_upload",
                requestBody = requestBody
            )
            
            android.util.Log.d("EventRepository", "API Response Code: ${response.code()}")
            android.util.Log.d("EventRepository", "API Response Success: ${response.isSuccessful}")
            
            if (response.isSuccessful) {
                val responseText = response.body()?.string() ?: ""
                android.util.Log.d("EventRepository", "API Response Body: $responseText")
                val uploadResponse = gson.fromJson(responseText, EventUploadResponse::class.java)
                true to uploadResponse
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                android.util.Log.e("EventRepository", "API Error: $errorBody")
                false to null
            }
        } catch (e: Exception) {
            android.util.Log.e("EventRepository", "Exception in createEventUpload: ${e.message}", e)
            false to null
        }
    }

    /**
     * 轮询查询事件上传成功状态
     * @param taskUid 任务UID
     * @return Pair(success, isCompleted) success表示请求是否成功，isCompleted表示任务是否完成
     */
    suspend fun noticeEventUploadSuccess(taskUid: String): Pair<Boolean, Boolean> {
        return try {
            android.util.Log.d("EventRepository", "Polling upload status for task: $taskUid")
            
            val response = api.noticeEventUploadSuccess(
                endpoint = "app/event/notice_event_upload_success",
                taskUid = taskUid
            )
            
            android.util.Log.d("EventRepository", "Status polling response code: ${response.code()}")
            
            if (response.isSuccessful) {
                val responseText = response.body()?.string() ?: ""
                android.util.Log.d("EventRepository", "Status polling response body: $responseText")
                
                // 假设返回的是 JSON 格式，包含 success 字段表示是否完成
                val gson = com.google.gson.Gson()
                val result = gson.fromJson(responseText, EventUploadStatusResponse::class.java)
                
                android.util.Log.d("EventRepository", "Parsed status result: success=${result?.success}, code=${result?.code}, message=${result?.message}")
                
                true to (result?.success == true)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                android.util.Log.e("EventRepository", "Status polling failed with code ${response.code()}: $errorBody")
                false to false
            }
        } catch (e: Exception) {
            android.util.Log.e("EventRepository", "Status polling exception: ${e.message}", e)
            false to false
        }
    }

    // ==================== New: Event Zip Creation ====================
    /**
     * 将指定事件目录压缩为zip文件并计算哈希值。
     * @param context Android 上下文，用于定位内部存储目录
     * @param eventUid 事件UID（目录名）
     * @return Pair(success, sha256Hash) 成功标志和SHA-256哈希值
     */
    suspend fun createEventZip(
        context: Context,
        eventUid: String
    ): Pair<Boolean, String> {
        android.util.Log.d("EventRepository", "Creating zip for event: $eventUid")
        
        val eventDir = File(File(context.filesDir, "events"), eventUid)
        android.util.Log.d("EventRepository", "Event directory path: ${eventDir.absolutePath}")
        
        if (!eventDir.exists()) {
            android.util.Log.e("EventRepository", "Event directory not found: ${eventDir.absolutePath}")
            return false to "event directory not found: ${eventDir.absolutePath}"
        }

        // 检查目录内容
        val files = eventDir.listFiles()
        android.util.Log.d("EventRepository", "Event directory contains ${files?.size ?: 0} files:")
        files?.forEach { file ->
            android.util.Log.d("EventRepository", "  - ${file.name} (${if (file.isDirectory) "DIR" else "FILE"}, ${file.length()} bytes)")
        }

        // 先根据数据库记录补齐事件目录中的媒体文件，并更新 meta.json
        val eventEntity = try { getEventByUid(eventUid) } catch (_: Exception) { null }
        reconcileAssetsFromDb(eventDir, eventEntity)

        // 再修复旧版占位符扩展并确保 meta.json 关键字段完整
        fixLegacyPlaceholderExtensions(
            eventDir,
            eventEntity?.structuralDefectDetails,
            defectNosFromDb = eventEntity?.defectNos,
            defectUidsFromDb = eventEntity?.defectUids
        )

        // 1) 压缩为zip
        val cacheDir = File(context.cacheDir, "sync_zip").apply { mkdirs() }
        val zipFile = File(cacheDir, "event-$eventUid-${System.currentTimeMillis()}.zip")
        android.util.Log.d("EventRepository", "Creating zip file: ${zipFile.absolutePath}")
        
        try {
            zipDirectory(eventDir, zipFile)
            android.util.Log.d("EventRepository", "Zip file created successfully, size: ${zipFile.length()} bytes")
        } catch (e: Exception) {
            android.util.Log.e("EventRepository", "Zip creation failed for event $eventUid", e)
            return false to "zip error: ${e.message}"
        }

        // 2) 计算SHA-256哈希值
        val sha256 = sha256Of(zipFile)
        
        // 3) 将压缩包保存到指定位置，以便后续上传使用
        val finalZipFile = File(cacheDir, "${eventUid}.zip")
        if (finalZipFile.exists()) {
            finalZipFile.delete()
        }
        zipFile.renameTo(finalZipFile)
        
        return true to sha256
    }

    /**
     * 根据事件包哈希值和票据信息上传压缩包到OSS
     * @param context Android 上下文
     * @param eventUid 事件UID
     * @param eventPackageHash 事件包哈希值
     * @param ticketData 票据数据（包含host, dir, file_id, policy, signature, accessid等）
     * @return Pair(success, message) 上传结果
     */
    suspend fun uploadEventZipWithTicket(
        context: Context,
        eventUid: String,
        eventPackageHash: String,
        ticketData: Map<String, Any>
    ): Pair<Boolean, String> {
        // 1) 查找对应的压缩包文件
        val cacheDir = File(context.cacheDir, "sync_zip")
        val zipFile = File(cacheDir, "${eventUid}.zip")
        
        if (!zipFile.exists()) {
            return false to "zip file not found: ${zipFile.absolutePath}"
        }
        
        // 2) 验证哈希值是否匹配
        val actualHash = sha256Of(zipFile)
        if (actualHash != eventPackageHash) {
            return false to "hash mismatch: expected $eventPackageHash, actual $actualHash"
        }
        
        // 3) 解析票据信息
        val host = ticketData["host"] as? String ?: return false to "missing host in ticket"
        val dir = ticketData["dir"] as? String ?: ""
        val fileId = ticketData["file_id"] as? String ?: return false to "missing file_id in ticket"
        val policy = ticketData["policy"] as? String ?: return false to "missing policy in ticket"
        val signature = ticketData["signature"] as? String ?: return false to "missing signature in ticket"
        val accessId = ticketData["accessid"] as? String ?: return false to "missing accessid in ticket"
        
        val keyStr = (if (dir.isNotBlank()) dir else "") + fileId
        val uploadUrl = if (host.startsWith("http")) host else "https://$host"
        
        // 4) 表单直传到OSS
        val plain = "text/plain".toMediaType()
        val key: RequestBody = keyStr.toRequestBody(plain)
        val policyRB: RequestBody = policy.toRequestBody(plain)
        val accRB: RequestBody = accessId.toRequestBody(plain)
        val sigRB: RequestBody = signature.toRequestBody(plain)
        val statusRB: RequestBody = "200".toRequestBody(plain)
        val part = MultipartBody.Part.createFormData(
            name = "file",
            filename = zipFile.name,
            body = zipFile.asRequestBody("application/zip".toMediaType())
        )
        
        val uploadResp = try {
            api.ossFormUpload(uploadUrl, key, policyRB, accRB, sigRB, statusRB, part)
        } catch (e: Exception) {
            return false to "upload error: ${e.message}"
        }
        
        val bodyText = uploadResp.body()?.string() ?: ""
        
        // 5) 上传完成后清理临时文件
        zipFile.delete()
        
        return if (uploadResp.isSuccessful) {
            true to "upload successful"
        } else {
            false to (if (bodyText.isNotBlank()) bodyText else "http ${uploadResp.code()}")
        }
    }

    /** 递归压缩目录 */
    private fun zipDirectory(srcDir: File, outZip: File) {
        android.util.Log.d("EventRepository", "Starting zip compression from ${srcDir.absolutePath} to ${outZip.absolutePath}")
        
        ZipOutputStream(FileOutputStream(outZip)).use { zos ->
            val basePath = srcDir.absolutePath
            val filesToZip = srcDir.walkTopDown().filter { it.isFile }.toList()
            
            android.util.Log.d("EventRepository", "Found ${filesToZip.size} files to compress:")
            filesToZip.forEach { file ->
                android.util.Log.d("EventRepository", "  - ${file.absolutePath} (${file.length()} bytes)")
            }
            
            if (filesToZip.isEmpty()) {
                android.util.Log.w("EventRepository", "No files found in directory ${srcDir.absolutePath}")
                // 创建一个空的zip文件，至少包含目录结构
                val entry = ZipEntry("empty.txt")
                zos.putNextEntry(entry)
                zos.write("This directory was empty during compression.".toByteArray())
                zos.closeEntry()
                return@use
            }
            
            filesToZip.forEach { file ->
                val rel = file.absolutePath.removePrefix(basePath).trimStart(File.separatorChar)
                val entryName = if (rel.isBlank()) file.name else rel
                android.util.Log.d("EventRepository", "Adding file to zip: $entryName")
                
                try {
                    val entry = ZipEntry(entryName)
                    zos.putNextEntry(entry)
                    FileInputStream(file).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                    android.util.Log.d("EventRepository", "Successfully added $entryName to zip")
                } catch (e: Exception) {
                    android.util.Log.e("EventRepository", "Failed to add file $entryName to zip", e)
                    throw e
                }
            }
        }
        
        android.util.Log.d("EventRepository", "Zip compression completed. Final zip size: ${outZip.length()} bytes")
    }

    /** 计算文件SHA-256并转十六进制字符串 */
    private fun sha256Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(8 * 1024)
            while (true) {
                val n = fis.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString(separator = "") { b ->
            val v = (b.toInt() and 0xFF)
            v.toString(16).padStart(2, '0')
        }
    }

    /**
     * 根据数据库中的 photoFiles/audioFiles，将缺失的媒体文件复制到事件目录，并同步更新 meta.json 的 photos/audios 列表。
     * 该方法为幂等操作：如果文件已存在且 meta.json 已记录，则不会重复复制或写入。
     * 目标命名规则保持与事件表单保存一致：photo_<index>.<ext>、audio_<index>.m4a。
     */
    private fun reconcileAssetsFromDb(eventDir: File, eventEntity: EventEntity?) {
        if (eventEntity == null) return

        val metaFile = File(eventDir, "meta.json")
        val metaObj = try {
            if (metaFile.exists()) org.json.JSONObject(metaFile.readText()) else org.json.JSONObject()
        } catch (_: Exception) { org.json.JSONObject() }

        // 读取 meta 中的两套键（兼容旧字段）并与目录实际文件合并去重
        fun jsonArrayToList(obj: org.json.JSONObject, key: String): MutableList<String> {
            val arr = obj.optJSONArray(key)
            val list = mutableListOf<String>()
            if (arr != null) for (i in 0 until arr.length()) list.add(arr.optString(i))
            return list
        }

        val metaPhotos = jsonArrayToList(metaObj, "photos")
        val metaPhotoFiles = jsonArrayToList(metaObj, "photoFiles")
        val metaAudios = jsonArrayToList(metaObj, "audios")
        val metaAudioFiles = jsonArrayToList(metaObj, "audioFiles")

        val dirPhotoFiles = eventDir.listFiles()?.filter { it.isFile && it.name.startsWith("photo_") }?.map { it.name } ?: emptyList()
        val dirAudioFiles = eventDir.listFiles()?.filter { it.isFile && it.name.startsWith("audio_") }?.map { it.name } ?: emptyList()

        val existingPhotos = (metaPhotos + metaPhotoFiles + dirPhotoFiles).distinct().toMutableList()
        val existingAudios = (metaAudios + metaAudioFiles + dirAudioFiles).distinct().toMutableList()

        // 解析已有文件的最大索引，避免从 size 计数导致 0/1 重复
        fun nextIndex(names: List<String>, prefix: String): Int {
            var maxIdx = -1
            names.forEach { name ->
                if (name.startsWith(prefix)) {
                    // 支持 photo_0.jpg、photo_0_1.jpg 取主索引 0
                    val base = name.removePrefix(prefix)
                    val numberPart = base.takeWhile { it.isDigit() }
                    val idx = numberPart.toIntOrNull()
                    if (idx != null && idx > maxIdx) maxIdx = idx
                }
            }
            return maxIdx + 1
        }

        var photoIndex = nextIndex(dirPhotoFiles, "photo_")
        var audioIndex = nextIndex(dirAudioFiles, "audio_")

        // 修复：仅当目录中的媒体数量小于数据库记录数量时才进行复制，避免重复拷贝
        // 说明：saveEventToRoom/saveEventToLocal 已将媒体按 photo_<index>.jpg/audio_<index>.m4a 拷贝到事件目录。
        // 在打包前的对齐阶段不应再次复制同一来源文件，否则会生成 photo_1.jpg 等重复文件。
        val shouldCopyPhotos = dirPhotoFiles.size < eventEntity.photoFiles.size
        val shouldCopyAudios = dirAudioFiles.size < eventEntity.audioFiles.size

        if (shouldCopyPhotos) {
            eventEntity.photoFiles.forEach { path ->
                try {
                    val src = File(path)
                    if (!src.exists()) {
                        android.util.Log.w("EventRepository", "Photo src missing: $path")
                        return@forEach
                    }
                    // 如果源文件已在事件目录中，跳过复制（避免自我复制导致重复）
                    val srcParent = src.parentFile?.absolutePath
                    if (srcParent != null && srcParent == eventDir.absolutePath) {
                        android.util.Log.d("EventRepository", "Skip photo already inside eventDir: ${src.name}")
                        return@forEach
                    }

                    // 以当前目录现状计算下一个索引，避免 size 计数造成重复
                    val currentPhotoNames = eventDir.listFiles()
                        ?.filter { it.isFile && it.name.startsWith("photo_") }
                        ?.map { it.name } ?: emptyList()
                    // 统一照片扩展名为 .jpg，并在文件名中加入时间戳
                    val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                        .format(java.util.Date(src.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()))
                    val nextIdx = run {
                        var maxIdx = -1
                        currentPhotoNames.forEach { name ->
                            if (name.startsWith("photo_")) {
                                val base = name.removePrefix("photo_")
                                val numberPart = base.takeWhile { it.isDigit() }
                                val idx = numberPart.toIntOrNull()
                                if (idx != null && idx > maxIdx) maxIdx = idx
                            }
                        }
                        maxIdx + 1
                    }
                    val targetName = "photo_${nextIdx}_${ts}.jpg"
                    val target = File(eventDir, targetName)
                    if (target.exists()) {
                        android.util.Log.d("EventRepository", "Photo target exists, skip: ${target.name}")
                        existingPhotos.add(targetName)
                        return@forEach
                    }
                    src.copyTo(target, overwrite = false)
                    existingPhotos.add(targetName)
                    android.util.Log.d("EventRepository", "Copied photo from DB: ${src.absolutePath} -> ${target.name}")
                } catch (e: Exception) {
                    android.util.Log.w("EventRepository", "Failed to copy photo $path: ${e.message}")
                }
            }
        }

        if (shouldCopyAudios) {
            eventEntity.audioFiles.forEach { path ->
                try {
                    val src = File(path)
                    if (!src.exists()) {
                        android.util.Log.w("EventRepository", "Audio src missing: $path")
                        return@forEach
                    }
                    val srcParent = src.parentFile?.absolutePath
                    if (srcParent != null && srcParent == eventDir.absolutePath) {
                        android.util.Log.d("EventRepository", "Skip audio already inside eventDir: ${src.name}")
                        return@forEach
                    }

                    val currentAudioNames = eventDir.listFiles()
                        ?.filter { it.isFile && it.name.startsWith("audio_") }
                        ?.map { it.name } ?: emptyList()
                    val nextIdx = run {
                        var maxIdx = -1
                        currentAudioNames.forEach { name ->
                            if (name.startsWith("audio_")) {
                                val base = name.removePrefix("audio_")
                                val numberPart = base.takeWhile { it.isDigit() }
                                val idx = numberPart.toIntOrNull()
                                if (idx != null && idx > maxIdx) maxIdx = idx
                            }
                        }
                        maxIdx + 1
                    }
                    // 统一音频扩展名为 .m4a，并在文件名中加入时间戳
                    val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                        .format(java.util.Date(src.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()))
                    val targetName = "audio_${nextIdx}_${ts}.m4a"
                    val target = File(eventDir, targetName)
                    if (target.exists()) {
                        android.util.Log.d("EventRepository", "Audio target exists, skip: ${target.name}")
                        existingAudios.add(targetName)
                        return@forEach
                    }
                    src.copyTo(target, overwrite = false)
                    existingAudios.add(targetName)
                    android.util.Log.d("EventRepository", "Copied audio from DB: ${src.absolutePath} -> ${target.name}")
                } catch (e: Exception) {
                    android.util.Log.w("EventRepository", "Failed to copy audio $path: ${e.message}")
                }
            }
        }

        // 同步写回：仅使用 photoFiles/audioFiles，并补充 assets（来自事件表）。不主动新增旧键 photos/audios。
        try {
            val photosArr = org.json.JSONArray(existingPhotos)
            val audiosArr = org.json.JSONArray(existingAudios)
            metaObj.put("photoFiles", photosArr)
            metaObj.put("audioFiles", audiosArr)

            // 读取现有 assets（如果有），避免重复；然后以数据库为准进行合并
            // 改为保存完整对象，包含 nodeId（若存在），键为 fileId 以保持向后兼容。
            val existingAssetsMap = mutableMapOf<String, com.simsapp.data.local.entity.DigitalAssetItem>() // fileId -> item
            run {
                try {
                    val arr = metaObj.optJSONArray("assets")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i)
                            val fid = obj?.optString("fileId") ?: ""
                            val fname = obj?.optString("fileName") ?: ""
                            val nid = obj?.optString("nodeId")
                            if (fid.isNotBlank()) existingAssetsMap[fid] = com.simsapp.data.local.entity.DigitalAssetItem(
                                fileId = fid,
                                fileName = fname,
                                nodeId = nid?.takeIf { !it.isNullOrBlank() }
                            )
                        }
                    }
                } catch (_: Exception) { /* ignore */ }
            }

            // 以数据库 assets 作为权威来源，覆盖或补全
            eventEntity.assets.forEach { item ->
                val fid = item.fileId
                if (fid.isNotBlank()) {
                    existingAssetsMap[fid] = item
                }
            }

            // 写入 assets（对象数组）与 digitalAssets（仅 fileId 列表）两套键，保证前后兼容
            val assetsArr = org.json.JSONArray()
            val digitalAssetsArr = org.json.JSONArray()
            existingAssetsMap.forEach { (fid, item) ->
                val obj = org.json.JSONObject()
                obj.put("fileId", item.fileId)
                obj.put("fileName", item.fileName)
                // 写入 nodeId（若存在），用于更精确的回显与选择
                item.nodeId?.let { obj.put("nodeId", it) }
                assetsArr.put(obj)
                digitalAssetsArr.put(fid)
            }
            metaObj.put("assets", assetsArr)
            metaObj.put("digitalAssets", digitalAssetsArr)

            metaFile.writeText(metaObj.toString())
            android.util.Log.d(
                "EventRepository",
                "meta.json updated: photoFiles=${existingPhotos.size}, audioFiles=${existingAudios.size}, assets=${existingAssetsMap.size}"
            )
        } catch (e: Exception) {
            android.util.Log.w("EventRepository", "Failed to update meta.json for assets: ${e.message}")
        }
    }

    /**
     * Fix legacy filenames that used placeholder extension "$ext" produced by early versions.
     * This function renames affected audio files to use ".m4a" extension and updates meta.json accordingly.
     * Photos are kept unchanged unless a similar issue is later reported.
     * This is a safe, idempotent operation and will only run for the current event directory before zipping.
     */
    /**
     * 函数：fixLegacyPlaceholderExtensions
     * 说明：
     * - 修复旧版音频文件名占位符扩展（.$ext → .m4a）。
     * - 在打包前补全 meta.json 的关键字段，包含结构缺陷详情与缺陷关联信息。
     * - 结构缺陷详情补写逻辑：若 meta.json 中为缺失、空字符串或空对象（JSONObject.length()==0），
     *   且数据库中存在非空的 `structuralDefectDetails`（JSON 字符串，含向导生成的 summary 字段），
     *   则以数据库为准写入对象。
     * 参数：
     * - eventDir 事件目录
     * - structuralDefectDetails 数据库中的结构缺陷详情原始 JSON 字符串
     * - defectNosFromDb 数据库中的缺陷编号列表
     * - defectUidsFromDb 数据库中的缺陷UID列表
     * 返回：无（副作用：可能更新 meta.json 内容）
     */
    private fun fixLegacyPlaceholderExtensions(
        eventDir: File,
        structuralDefectDetails: String?,
        defectNosFromDb: List<String>? = null,
        defectUidsFromDb: List<String>? = null
    ) {
        val metaFile = File(eventDir, "meta.json")
        if (!metaFile.exists()) return
        try {
            val obj = JSONObject(metaFile.readText())
            var changed = false
            val audios = obj.optJSONArray("audios")
            if (audios != null) {
                val updated = mutableListOf<String>()
                for (i in 0 until audios.length()) {
                    val name = audios.optString(i)
                    // Only handle legacy placeholder suffix ".$ext"
                    if (name.endsWith(".\$ext")) { // matches literal ".$ext"
                        val oldFile = File(eventDir, name)
                        val base = name.removeSuffix(".\$ext")
                        val canonicalName = "$base.m4a"
                        val canonicalFile = File(eventDir, canonicalName)
                        var finalName = canonicalName

                        if (oldFile.exists()) {
                            try {
                                if (canonicalFile.exists()) {
                                    // 若已有规范文件，比较大小；相同则删除占位，否则保留占位为重复（附加后缀）
                                    if (oldFile.length() == canonicalFile.length()) {
                                        try { oldFile.delete() } catch (_: Exception) {}
                                        finalName = canonicalName
                                    } else {
                                        var attempt = 1
                                        var altName: String
                                        var altFile: File
                                        do {
                                            altName = "${base}_$attempt.m4a"
                                            altFile = File(eventDir, altName)
                                            attempt += 1
                                        } while (altFile.exists())
                                        // 使用复制+删除，避免 rename 在部分设备失败
                                        FileInputStream(oldFile).channel.use { inCh ->
                                            FileOutputStream(altFile).channel.use { outCh ->
                                                inCh.transferTo(0, inCh.size(), outCh)
                                            }
                                        }
                                        try { oldFile.delete() } catch (_: Exception) {}
                                        finalName = altName
                                    }
                                } else {
                                    // 直接重命名为规范名
                                    if (!oldFile.renameTo(canonicalFile)) {
                                        FileInputStream(oldFile).channel.use { inCh ->
                                            FileOutputStream(canonicalFile).channel.use { outCh ->
                                                inCh.transferTo(0, inCh.size(), outCh)
                                            }
                                        }
                                        try { oldFile.delete() } catch (_: Exception) {}
                                    }
                                    finalName = canonicalName
                                }
                            } catch (_: Exception) {}
                        }
                        updated.add(finalName)
                    } else {
                        updated.add(name)
                    }
                }
                // Write back updated audios list
                obj.put("audios", org.json.JSONArray(updated))
                changed = true
            }

            // Ensure structuralDefectDetails exists in meta.json when available in DB
            // 旧逻辑仅在缺失或空字符串时填充，若为空对象 {} 将被误判为“已存在”。
            // 修复：将空对象（JSONObject.length()==0）也视为缺失，从数据库补写。
            run {
                val existingStructAny = obj.opt("structuralDefectDetails")
                val isMissingOrEmpty: Boolean = when (existingStructAny) {
                    null -> true
                    is org.json.JSONObject -> {
                        // 视空对象为缺失；其余保持不覆盖，避免误删已有内容
                        try { existingStructAny.length() == 0 } catch (_: Exception) { true }
                    }
                    is String -> existingStructAny.isBlank()
                    else -> true
                }

                if (isMissingOrEmpty && !structuralDefectDetails.isNullOrBlank()) {
                    try {
                        val structObj = org.json.JSONObject(structuralDefectDetails)
                        obj.put("structuralDefectDetails", structObj)
                        changed = true
                    } catch (_: Exception) {
                        // 如果解析失败，写入空对象确保类型为对象
                        obj.put("structuralDefectDetails", org.json.JSONObject())
                        changed = true
                    }
                }
            }

            // Ensure defectNos populated from DB when meta.json missing or empty
            run {
                try {
                    val arr = obj.optJSONArray("defectNos")
                    val isEmptyInMeta = (arr == null || arr.length() == 0)
                    val hasDbNos = !defectNosFromDb.isNullOrEmpty()
                    if (isEmptyInMeta && hasDbNos) {
                        val jsonArr = org.json.JSONArray()
                        defectNosFromDb!!.forEach { jsonArr.put(it) }
                        obj.put("defectNos", jsonArr)
                        changed = true
                    }
                } catch (_: Exception) {
                    // noop
                }
            }

            // Ensure defectUids populated from DB when meta.json missing or empty
            run {
                try {
                    val arr = obj.optJSONArray("defectUids")
                    val isEmptyInMeta = (arr == null || arr.length() == 0)
                    val hasDbUids = !defectUidsFromDb.isNullOrEmpty()
                    if (isEmptyInMeta && hasDbUids) {
                        val jsonArr = org.json.JSONArray()
                        defectUidsFromDb!!.forEach { jsonArr.put(it) }
                        obj.put("defectUids", jsonArr)
                        changed = true
                    }
                } catch (_: Exception) {
                    // noop
                }
            }

            if (changed) {
                metaFile.writeText(obj.toString())
            }
        } catch (_: Exception) {
            // Swallow errors to avoid blocking sync; if anything fails we still try zipping
        }
    }
}

/**
 * 事件上传状态响应数据模型
 * 用于解析 notice_event_upload_success 接口返回的数据
 */
data class EventUploadStatusResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String?
)

/**
 * 事件上传请求数据类
 */
data class EventUploadRequest(
    @SerializedName("task_uid")
    val taskUid: String,
    @SerializedName("target_project_uid")
    val targetProjectUid: String,
    @SerializedName("upload_list")
    val uploadList: List<EventUploadItem>
)

/**
 * 事件上传项数据类
 */
data class EventUploadItem(
    @SerializedName("event_uid")
    val eventUid: String,
    @SerializedName("event_package_hash")
    val eventPackageHash: String,
    @SerializedName("event_package_name")
    val eventPackageName: String
)

/**
 * 事件上传响应数据类
 */
data class EventUploadResponse(
    @SerializedName("data")
    val data: List<EventUploadResponseItem>?,
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String?,
    @SerializedName("success")
    val success: Boolean
)

/**
 * 事件上传响应项数据类
 */
data class EventUploadResponseItem(
    @SerializedName("task_uid")
    val taskUid: String,
    @SerializedName("event_uid")
    val eventUid: String,
    @SerializedName("event_package_hash")
    val eventPackageHash: String,
    @SerializedName("event_package_name")
    val eventPackageName: String,
    @SerializedName("ticket")
    val ticket: TicketData
)

/**
 * 票据数据类
 */
data class TicketData(
    @SerializedName("file_id")
    val fileId: String,
    @SerializedName("accessid")
    val accessId: String,
    @SerializedName("policy")
    val policy: String,
    @SerializedName("signature")
    val signature: String,
    @SerializedName("dir")
    val dir: String,
    @SerializedName("host")
    val host: String,
    @SerializedName("expire")
    val expire: String
)