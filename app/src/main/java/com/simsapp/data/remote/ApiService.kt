/*
 * File: ApiService.kt
 * Description: Retrofit HTTP API definition for sync and upload operations.
 * Author: SIMS Team
 */
package com.simsapp.data.remote

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.Body
import retrofit2.http.Url

/**
 * ApiService
 *
 * Define endpoints for syncing and uploading assets.
 * Replace base URL and parameters with your backend contracts.
 */
interface ApiService {
    /** Pull server time or metadata for health check. */
    @GET("/api/health")
    suspend fun health(): Response<ResponseBody>

    /** Upload a file asset (image/audio/pdf). */
    @Multipart
    @POST("/api/assets/upload")
    suspend fun uploadAsset(
        @Part file: MultipartBody.Part,
        @Query("projectId") projectId: Long,
        @Query("eventId") eventId: Long?
    ): Response<ResponseBody>

    // -------------------- Dynamic Endpoints --------------------
    /**
     * 获取上传凭证（临时票据）。
     * 等价于：GET /storage/upload/ticket
     * 需要在 Header 中传递 X-USERNAME 与 Authorization（可选，Bearer token）。
     *
     * 说明：默认由全局 OkHttp 拦截器统一注入这两个头；仅当需要覆盖默认值时再通过参数显式传入。
     * @param endpoint 绝对地址（包含 https 前缀）
     * @param fileName 文件名（zip 名称）
     * @param type     文件类型，示例：ZIP
     * @param remark   备注，可传入文件哈希值用于校验
     * @param username 可空：X-USERNAME 请求头（传 null 则不发送，由拦截器注入）
     * @param authorization 可空：Authorization（传 null 则不发送，由拦截器注入）
     */
    @GET
    suspend fun getUploadTicket(
        @Url endpoint: String,
        @Query("file_name") fileName: String,
        @Query("type") type: String,
        @Query("remark") remark: String?,
        @Header("X-USERNAME") username: String?,
        @Header("Authorization") authorization: String?
    ): Response<ResponseBody>

    /**
     * 动态获取项目列表（绝对地址）。
     * 示例：GET https://sims.ink-stone.win/zuul/sims-fdc/app/project/project_list
     * 可选头：X-USERNAME、Authorization（由拦截器统一注入；如需覆盖可显式传入）。
     * @param endpoint 绝对地址（包含 https 前缀）
     * @param username 可空：覆盖默认的 X-USERNAME
     * @param authorization 可空：覆盖默认的 Authorization（Bearer token）
     * @return 原始响应体，交由仓库层解析并映射为本地实体
     */
    @GET
    suspend fun getProjects(
        @Url endpoint: String,
        @Header("X-USERNAME") username: String? = null,
        @Header("Authorization") authorization: String? = null
    ): Response<ResponseBody>

    /**
     * 动态获取项目详情（绝对地址）。
     * 示例：GET /app/project/project?project_uid=xxx
     * @param endpoint 完整绝对地址（不含查询参数）
     * @param projectUid 查询参数 project_uid
     * @param username 可空：覆盖默认的 X-USERNAME
     * @param authorization 可空：覆盖默认的 Authorization（Bearer token）
     */
    @GET
    suspend fun getProjectDetail(
        @Url endpoint: String,
        @Query("project_uid") projectUid: String,
        @Header("X-USERNAME") username: String? = null,
        @Header("Authorization") authorization: String? = null
    ): Response<ResponseBody>

    /**
     * 直传到 OSS（表单上传）。
     * 前端示例中通过 formData.append 依次添加字段并将文件作为 file 字段上传。
     * 这里使用动态 @Url 以响应中返回的 host 作为完整上传地址。
     */
    @Multipart
    @POST
    suspend fun ossFormUpload(
        @Url host: String,
        @Part("key") key: RequestBody,
        @Part("policy") policy: RequestBody,
        @Part("OSSAccessKeyId") accessId: RequestBody,
        @Part("signature") signature: RequestBody,
        @Part("success_action_status") successActionStatus: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<ResponseBody>

    // -------------------- Risk Matrix Endpoints --------------------
    /**
     * Resolve a temporary download url for risk matrix config by posting file id list.
     * Using @Url to allow full absolute endpoint independent of baseUrl.
     *
     * @param endpoint Full absolute url like "/storage/download/url"
     * @param fileIds  The request body as a JSON array of file id strings (from screenshot parameter)
     */
    @POST
    suspend fun resolveDownloadUrl(
        @Url endpoint: String,
        @Body fileIds: List<String>
    ): Response<ResponseBody>

    /**
     * Download risk matrix json using a dynamic url returned by resolveDownloadUrl.
     */
    @GET
    suspend fun downloadRiskMatrixByUrl(@Url fileUrl: String): Response<ResponseBody>

    // -------------------- Event Sync Endpoints --------------------
    /**
     * 创建事件上传任务，支持多个事件同步
     * POST /app/event/create_event_upload
     * 
     * @param endpoint 完整的接口地址
     * @param requestBody 请求体，包含 task_uid, target_project_uid, upload_list 等字段
     */
    @POST
    suspend fun createEventUpload(
        @Url endpoint: String,
        @Body requestBody: RequestBody
    ): Response<ResponseBody>

    /**
     * 轮询查询事件上传成功状态
     * GET /app/event/notice_event_upload_success
     * 
     * @param endpoint 完整的接口地址
     * @param taskUid 任务UID，用于查询对应任务的状态
     */
    @GET
    suspend fun noticeEventUploadSuccess(
        @Url endpoint: String,
        @Query("task_uid") taskUid: String
    ): Response<ResponseBody>


}