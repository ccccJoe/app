/*
 * File: AuthRepository.kt
 * Description: Repository for authentication operations, handles bind and verify logic
 * Author: SIMS Team
 */
package com.simsapp.data.repository

import android.util.Log
import com.simsapp.data.local.AuthManager
import com.simsapp.data.local.UserAuthData
import com.simsapp.data.remote.AuthApiService
import com.simsapp.data.remote.BindRequest
import com.simsapp.data.remote.VerifyRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AuthRepository
 * 
 * 职责：
 * - 处理认证相关的业务逻辑
 * - 协调本地存储和网络请求
 * - 管理RSA密钥和用户认证状态
 * 
 * 设计思路：
 * - 统一的错误处理和日志记录
 * - 异步操作使用协程
 * - 将网络响应转换为业务结果
 */
@Singleton
class AuthRepository @Inject constructor(
    private val authApiService: AuthApiService,
    private val authManager: AuthManager
) {
    companion object {
        private const val TAG = "AuthRepository"
    }

    /**
     * 绑定手机设备
     * 首次扫码时调用，生成RSA密钥对并绑定设备
     * 
     * @param qrValue 扫码获取的值
     * @return Pair<Boolean, String> 成功标志和消息
     */
    suspend fun bindDevice(qrValue: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting device bind process")
            
            // 1. 生成RSA密钥对
            val keyPair = authManager.generateRSAKeyPair()
            val publicKeyString = authManager.publicKeyToString(keyPair.public)
            
            Log.d(TAG, "RSA key pair generated, public key length: ${publicKeyString.length}")
            
            // 2. 调用绑定接口
            val request = BindRequest(
                public_key = publicKeyString,
                qr = qrValue
            )
            
            val response = authApiService.bind(request)
            
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse?.success == true && apiResponse.data != null) {
                    // 3. 保存用户认证数据
                    val authData = UserAuthData(
                        userCode = apiResponse.data.user_code,
                        token = apiResponse.data.token
                    )
                    authManager.saveUserAuthData(authData)
                    
                    Log.d(TAG, "Device bind successful, userCode: ${authData.userCode}")
                    return@withContext true to "Device bound successfully"
                } else {
                    Log.e(TAG, "Bind API response invalid: ${apiResponse?.message}")
                    return@withContext false to (apiResponse?.message ?: "Bind failed")
                }
            } else {
                Log.e(TAG, "Bind API request failed: ${response.code()} ${response.message()}")
                return@withContext false to "Network error: ${response.message()}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bind device failed", e)
            return@withContext false to "Bind failed: ${e.message}"
        }
    }

    /**
     * 验证PC端登录
     * 已绑定设备扫码时调用，验证登录请求
     * 
     * @param qrValue 扫码获取的值
     * @return Pair<Boolean, String> 成功标志和消息
     */
    suspend fun verifyLogin(qrValue: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting login verify process")
            
            // 1. 获取缓存的私钥
            val privateKey = authManager.getPrivateKey()
            if (privateKey == null) {
                Log.e(TAG, "Private key not found")
                return@withContext false to "Private key not found"
            }
            
            val privateKeyString = authManager.privateKeyToString(privateKey)
            Log.d(TAG, "Private key loaded, length: ${privateKeyString.length}")
            
            // 2. 调用验证接口
            val request = VerifyRequest(
                private_key = privateKeyString,
                qr = qrValue
            )
            
            val response = authApiService.verify(request)
            
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse?.success == true) {
                    Log.d(TAG, "Login verify successful")
                    return@withContext true to "Login verified successfully"
                } else {
                    Log.e(TAG, "Verify API response invalid: ${apiResponse?.message}")
                    return@withContext false to (apiResponse?.message ?: "Verify failed")
                }
            } else {
                Log.e(TAG, "Verify API request failed: ${response.code()} ${response.message()}")
                return@withContext false to "Network error: ${response.message()}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Verify login failed", e)
            return@withContext false to "Verify failed: ${e.message}"
        }
    }

    /**
     * 检查是否已绑定设备
     * 
     * @return true表示已绑定，false表示未绑定
     */
    fun isDeviceBound(): Boolean {
        return authManager.hasAuthToken()
    }

    /**
     * 获取用户认证数据
     * 
     * @return 用户认证数据，如果未绑定则返回null
     */
    fun getUserAuthData(): UserAuthData? {
        return authManager.getUserAuthData()
    }

    /**
     * 清除认证数据
     * 用于退出登录或重置绑定
     */
    fun clearAuthData() {
        authManager.clearAuthData()
        Log.d(TAG, "Auth data cleared")
    }
}