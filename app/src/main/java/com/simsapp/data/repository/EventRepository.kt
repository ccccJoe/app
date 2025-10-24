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

    /** Create or update an event. */
    suspend fun upsert(event: EventEntity): Long = dao.insert(event)
    
    /** Create or update multiple events. */
    suspend fun upsertAll(events: List<EventEntity>): List<Long> = dao.insertAll(events)

    /** Get event by id. */
    suspend fun getEventById(eventId: Long): EventEntity? = dao.getById(eventId)

    /** Get event by UID. */
    suspend fun getEventByUid(uid: String): EventEntity? = dao.getByUid(uid)

    /** Delete event by id. */
    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Delete all events for a project by project_id. */
    suspend fun deleteByProjectId(projectId: Long) = dao.deleteByProjectId(projectId)

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

        // Fix legacy placeholder extensions in meta.json and on disk before zipping (one-time migration)
        fixLegacyPlaceholderExtensions(eventDir)

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
     * Fix legacy filenames that used placeholder extension "$ext" produced by early versions.
     * This function renames affected audio files to use ".m4a" extension and updates meta.json accordingly.
     * Photos are kept unchanged unless a similar issue is later reported.
     * This is a safe, idempotent operation and will only run for the current event directory before zipping.
     */
    private fun fixLegacyPlaceholderExtensions(eventDir: File) {
        val metaFile = File(eventDir, "meta.json")
        if (!metaFile.exists()) return
        try {
            val obj = JSONObject(metaFile.readText())
            val audios = obj.optJSONArray("audios")
            if (audios != null) {
                val updated = mutableListOf<String>()
                for (i in 0 until audios.length()) {
                    val name = audios.optString(i)
                    // Only handle legacy placeholder suffix ".$ext"
                    if (name.endsWith(".\$ext")) { // matches literal ".$ext"
                        val oldFile = File(eventDir, name)
                        val base = name.removeSuffix(".\$ext")
                        // Default audio extension is m4a according to recorder settings
                        var newName = "$base.m4a"
                        var newFile = File(eventDir, newName)
                        // Avoid overwrite: if exists, append a numeric suffix
                        var attempt = 1
                        while (newFile.exists()) {
                            newName = "${base}_$attempt.m4a"
                            newFile = File(eventDir, newName)
                            attempt += 1
                        }
                        // Rename if the old file exists
                        if (oldFile.exists()) {
                            try {
                                if (!oldFile.renameTo(newFile)) {
                                    // Fallback: copy+delete
                                    FileInputStream(oldFile).channel.use { inCh ->
                                        FileOutputStream(newFile).channel.use { outCh ->
                                            inCh.transferTo(0, inCh.size(), outCh)
                                        }
                                    }
                                    try { oldFile.delete() } catch (_: Exception) {}
                                }
                            } catch (_: Exception) {}
                        }
                        updated.add(newName)
                    } else {
                        updated.add(name)
                    }
                }
                // Write back updated audios list
                obj.put("audios", org.json.JSONArray(updated))
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