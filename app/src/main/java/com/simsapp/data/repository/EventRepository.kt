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

    // ==================== New: Event Zip Sync ====================
    /**
     * 将指定事件目录压缩为zip并上传到OSS。
     * @param context Android 上下文，用于定位内部存储目录
     * @param eventUid 事件UID（目录名）
     * @param username X-USERNAME 请求头（默认 test）。说明：默认由拦截器注入，此参数仅用于覆盖默认值。
     * @param authorization Authorization（如："Bearer abc"）。说明：默认由拦截器注入，此参数仅用于覆盖默认值。
     * @return Pair(success, responseText)
     */
    suspend fun uploadEventZip(
        context: Context,
        eventUid: String,
        username: String = "test",
        authorization: String ?= null
    ): Pair<Boolean, String> {
        val eventDir = File(File(context.filesDir, "events"), eventUid)
        if (!eventDir.exists()) return false to "event directory not found: ${eventDir.absolutePath}"

        // Fix legacy placeholder extensions in meta.json and on disk before zipping (one-time migration)
        fixLegacyPlaceholderExtensions(eventDir)

        // 1) 压缩为zip
        val cacheDir = File(context.cacheDir, "sync_zip").apply { mkdirs() }
        val zipFile = File(cacheDir, "event-$eventUid-${System.currentTimeMillis()}.zip")
        try {
            zipDirectory(eventDir, zipFile)
        } catch (e: Exception) {
            return false to "zip error: ${e.message}"
        }

        // 2) 计算SHA-256，作为remark
        val sha256 = sha256Of(zipFile)

        // 3) 获取上传票据（Header由拦截器统一注入，除非显式覆写参数）
        val ticketUrl = "https://sims.ink-stone.win/zuul/sims-ym/storage/upload/ticket"
        val ticketResp = try {
            api.getUploadTicket(
                endpoint = ticketUrl,
                fileName = zipFile.name,
                type = "ZIP",
                remark = sha256,
                username = null,
                authorization = null
            )
        } catch (e: Exception) {
            return false to "ticket request failed: ${e.message}"
        }
        if (!ticketResp.isSuccessful) {
            return false to "ticket http ${ticketResp.code()}"
        }
        val ticketText = ticketResp.body()?.string() ?: return false to "empty ticket body"
        val parsed = runCatching { JSONObject(ticketText) }.getOrElse { return false to "invalid ticket json" }
        val data = parsed.optJSONObject("data") ?: return false to "ticket missing data"
        val host = data.optString("host")
        val dir = data.optString("dir")
        val fileId = data.optString("file_id")
        val policy = data.optString("policy")
        val signature = data.optString("signature")
        val accessId = data.optString("accessid")
        val keyStr = (if (dir.isNotBlank()) dir else "") + fileId
        val uploadUrl = if (host.startsWith("http")) host else "https://$host"

        // 4) 表单直传
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
            zipFile.delete()
            return false to "upload error: ${e.message}"
        }
        val bodyText = uploadResp.body()?.string() ?: ""
        // 清理临时zip
        zipFile.delete()
        return uploadResp.isSuccessful to (if (bodyText.isNotBlank()) bodyText else "http ${uploadResp.code()}")
    }

    /** 递归压缩目录 */
    private fun zipDirectory(srcDir: File, outZip: File) {
        ZipOutputStream(FileOutputStream(outZip)).use { zos ->
            val basePath = srcDir.absolutePath
            srcDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val rel = file.absolutePath.removePrefix(basePath).trimStart(File.separatorChar)
                val entryName = if (rel.isBlank()) file.name else rel
                val entry = ZipEntry(entryName)
                zos.putNextEntry(entry)
                FileInputStream(file).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
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