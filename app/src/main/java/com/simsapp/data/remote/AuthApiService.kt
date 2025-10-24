/*
 * File: AuthApiService.kt
 * Description: API service for authentication operations including bind and verify
 * Author: SIMS Team
 */
package com.simsapp.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 绑定请求数据类
 * 
 * @property public_key RSA公钥
 * @property qr 扫码获取的值
 */
data class BindRequest(
    val public_key: String,
    val qr: String
)

/**
 * 验证请求数据类
 * 
 * @property private_key RSA私钥
 * @property qr 扫码获取的值
 */
data class VerifyRequest(
    val private_key: String,
    val qr: String
)

/**
 * 绑定响应数据类
 * 
 * @property user_code 用户代码，用于后续请求头
 * @property type 绑定类型
 * @property status 绑定状态
 * @property qr_code_value 二维码值
 * @property token 认证token
 * @property remark 备注信息
 */
data class BindResponseData(
    val user_code: String,
    val type: String,
    val status: String,
    val qr_code_value: String?,
    val token: String,
    val remark: String?
)

/**
 * API响应基础类
 * 
 * @property success 请求是否成功
 * @property message 响应消息
 * @property data 响应数据
 */
data class ApiResponse<T>(
    val success: Boolean,
    val message: String?,
    val data: T?
)

/**
 * AuthApiService
 * 
 * 职责：
 * - 定义认证相关的API接口
 * - 包含绑定手机和验证登录的接口
 * 
 * 设计思路：
 * - 使用Retrofit注解定义HTTP接口
 * - 统一的响应数据结构
 * - 支持异步调用
 */
interface AuthApiService {
    
    /**
     * 绑定手机接口
     * 用于首次扫码时绑定手机设备
     * 
     * @param request 绑定请求数据
     * @return 绑定响应
     */
    @POST("login_tmp/bind")
    suspend fun bind(@Body request: BindRequest): Response<ApiResponse<BindResponseData>>
    
    /**
     * 验证登录接口
     * 用于已绑定设备扫码登录PC端
     * 
     * @param request 验证请求数据
     * @return 验证响应
     */
    @POST("login_tmp/verify")
    suspend fun verify(@Body request: VerifyRequest): Response<ApiResponse<Any>>
}