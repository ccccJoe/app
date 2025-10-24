/*
 * File: AuthManager.kt
 * Description: Manages user authentication data including RSA keys and user credentials
 * Author: SIMS Team
 */
package com.simsapp.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户认证数据类
 * 
 * @property userCode 用户代码，用于后续请求头
 * @property token 认证token
 */
data class UserAuthData(
    val userCode: String,
    val token: String
)

/**
 * AuthManager
 * 
 * 职责：
 * - 管理RSA公私钥对的生成和存储
 * - 管理用户认证信息的本地缓存
 * - 提供认证状态查询接口
 * 
 * 设计思路：
 * - 使用SharedPreferences存储认证信息
 * - RSA密钥使用Base64编码存储
 * - 提供线程安全的访问接口
 */
@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREF_NAME = "sims_auth"
        private const val KEY_PRIVATE_KEY = "private_key"
        private const val KEY_PUBLIC_KEY = "public_key"
        private const val KEY_USER_TOKEN = "user_token"
        private const val KEY_USER_CODE = "user_code"
        private const val TAG = "AuthManager"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * 检查是否已有缓存的认证token
     * 
     * @return true表示已绑定，false表示未绑定
     */
    fun hasAuthToken(): Boolean {
        val token = prefs.getString(KEY_USER_TOKEN, null)
        return !token.isNullOrBlank()
    }

    /**
     * 获取缓存的用户认证数据
     * 
     * @return 用户认证数据，如果未缓存则返回null
     */
    fun getUserAuthData(): UserAuthData? {
        val token = prefs.getString(KEY_USER_TOKEN, null)
        val userCode = prefs.getString(KEY_USER_CODE, null)
        
        Log.d(TAG, "Getting user auth data - token: ${if (token.isNullOrBlank()) "null/empty" else "exists"}, userCode: ${if (userCode.isNullOrBlank()) "null/empty" else "exists"}")
        
        return if (!token.isNullOrBlank() && !userCode.isNullOrBlank()) {
            UserAuthData(userCode, token)
        } else {
            null
        }
    }

    /**
     * 缓存用户认证数据
     * 
     * @param authData 用户认证数据
     */
    fun saveUserAuthData(authData: UserAuthData) {
        Log.d(TAG, "Saving user auth data - userCode: ${authData.userCode}, token: ${if (authData.token.isBlank()) "empty" else "exists"}")
        
        prefs.edit()
            .putString(KEY_USER_TOKEN, authData.token)
            .putString(KEY_USER_CODE, authData.userCode)
            .apply()
        
        Log.d(TAG, "User auth data saved successfully")
    }

    /**
     * 生成RSA 2048位公私钥对
     * 在生成后立即进行Base64转换和验证
     * 
     * @return 生成的密钥对
     */
    fun generateRSAKeyPair(): KeyPair {
        try {
            Log.d(TAG, "Starting RSA key pair generation")
            
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(2048)
            val keyPair = keyPairGenerator.generateKeyPair()
            
            Log.d(TAG, "Raw RSA key pair generated successfully")
            
            // 立即进行Base64转换验证
            val publicKeyBase64 = publicKeyToString(keyPair.public)
            val privateKeyBase64 = privateKeyToString(keyPair.private)
            
            Log.d(TAG, "Public key Base64 conversion completed, length: ${publicKeyBase64.length}")
            Log.d(TAG, "Private key Base64 conversion completed, length: ${privateKeyBase64.length}")
            
            // 验证Base64格式是否正确
            validateBase64Key(publicKeyBase64, "Public")
            validateBase64Key(privateKeyBase64, "Private")
            
            // 保存私钥和公钥到本地（已经是Base64格式）
            savePrivateKeyBase64(privateKeyBase64)
            savePublicKeyBase64(publicKeyBase64)
            
            Log.d(TAG, "RSA key pair generation and Base64 conversion completed successfully")
            return keyPair
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate RSA key pair", e)
            throw e
        }
    }

    /**
     * 保存公钥到本地存储（已Base64编码）
     * 
     * @param publicKeyBase64 已Base64编码的公钥字符串
     */
    private fun savePublicKeyBase64(publicKeyBase64: String) {
        Log.d(TAG, "Saving Base64 encoded public key, length: ${publicKeyBase64.length}")
        prefs.edit()
            .putString(KEY_PUBLIC_KEY, publicKeyBase64)
            .apply()
        Log.d(TAG, "Base64 public key saved successfully")
    }

    /**
     * 保存私钥到本地存储（已Base64编码）
     * 
     * @param privateKeyBase64 已Base64编码的私钥字符串
     */
    private fun savePrivateKeyBase64(privateKeyBase64: String) {
        Log.d(TAG, "Saving Base64 encoded private key, length: ${privateKeyBase64.length}")
        prefs.edit()
            .putString(KEY_PRIVATE_KEY, privateKeyBase64)
            .apply()
        Log.d(TAG, "Base64 private key saved successfully")
    }

    /**
     * 保存私钥到本地存储（原方法保留兼容性）
     * 
     * @param privateKey 私钥
     */
    private fun savePrivateKey(privateKey: PrivateKey) {
        val encoded = Base64.encodeToString(privateKey.encoded, Base64.DEFAULT)
        savePrivateKeyBase64(encoded)
    }

    /**
     * 获取缓存的私钥
     * 
     * @return 私钥，如果未缓存则返回null
     */
    fun getPrivateKey(): PrivateKey? {
        return try {
            val encodedKey = prefs.getString(KEY_PRIVATE_KEY, null) ?: return null
            Log.d(TAG, "Loading private key from cache, Base64 length: ${encodedKey.length}")
            
            // 验证Base64格式
            validateBase64Key(encodedKey, "Cached Private")
            
            val keyBytes = Base64.decode(encodedKey, Base64.DEFAULT)
            Log.d(TAG, "Private key Base64 decoded, byte size: ${keyBytes.size}")
            
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKey = keyFactory.generatePrivate(keySpec)
            
            Log.d(TAG, "Private key successfully reconstructed from Base64")
            return privateKey
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load private key from Base64", e)
            null
        }
    }

    /**
     * 获取本地存储的公钥
     * 
     * @return 公钥对象，如果不存在或解析失败则返回null
     */
    fun getPublicKey(): PublicKey? {
        return try {
            val publicKeyBase64 = prefs.getString(KEY_PUBLIC_KEY, null)
            if (publicKeyBase64.isNullOrBlank()) {
                Log.d(TAG, "No public key found in local storage")
                return null
            }
            
            Log.d(TAG, "Found public key in local storage, Base64 length: ${publicKeyBase64.length}")
            
            // 验证Base64格式
            validateBase64Key(publicKeyBase64, "Public")
            
            // 解码Base64字符串
            val keyBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT)
            Log.d(TAG, "Public key Base64 decoded, byte array size: ${keyBytes.size}")
            
            // 创建公钥规范并生成公钥对象
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(keySpec)
            
            Log.d(TAG, "Public key object created successfully, algorithm: ${publicKey.algorithm}")
            publicKey
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve public key from local storage", e)
            null
        }
    }

    /**
     * 获取本地存储的公钥Base64字符串
     * 
     * @return 公钥Base64字符串，如果不存在则返回null
     */
    fun getPublicKeyBase64(): String? {
        return try {
            val publicKeyBase64 = prefs.getString(KEY_PUBLIC_KEY, null)
            if (publicKeyBase64.isNullOrBlank()) {
                Log.d(TAG, "No public key found in local storage")
                return null
            }
            
            Log.d(TAG, "Retrieved public key Base64 from local storage, length: ${publicKeyBase64.length}")
            
            // 验证Base64格式
            validateBase64Key(publicKeyBase64, "Public")
            
            publicKeyBase64
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve public key Base64 from local storage", e)
            null
        }
    }

    /**
     * 将公钥转换为Base64字符串
     * 
     * @param publicKey 公钥
     * @return Base64编码的公钥字符串
     */
    fun publicKeyToString(publicKey: PublicKey): String {
        val base64String = Base64.encodeToString(publicKey.encoded, Base64.DEFAULT)
        Log.d(TAG, "Public key converted to Base64, original size: ${publicKey.encoded.size} bytes, Base64 length: ${base64String.length}")
        return base64String
    }

    /**
     * 将私钥转换为Base64字符串
     * 
     * @param privateKey 私钥
     * @return Base64编码的私钥字符串
     */
    fun privateKeyToString(privateKey: PrivateKey): String {
        val base64String = Base64.encodeToString(privateKey.encoded, Base64.DEFAULT)
        Log.d(TAG, "Private key converted to Base64, original size: ${privateKey.encoded.size} bytes, Base64 length: ${base64String.length}")
        return base64String
    }

    /**
     * 验证Base64密钥格式是否正确
     * 
     * @param base64Key Base64编码的密钥字符串
     * @param keyType 密钥类型（用于日志）
     */
    private fun validateBase64Key(base64Key: String, keyType: String) {
        try {
            // 尝试解码Base64字符串
            val decoded = Base64.decode(base64Key, Base64.DEFAULT)
            Log.d(TAG, "$keyType key Base64 validation successful, decoded size: ${decoded.size} bytes")
            
            // 验证解码后的数据不为空
            if (decoded.isEmpty()) {
                throw IllegalArgumentException("$keyType key decoded data is empty")
            }
            
            // 验证Base64字符串格式
            if (base64Key.isBlank()) {
                throw IllegalArgumentException("$keyType key Base64 string is blank")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "$keyType key Base64 validation failed", e)
            throw IllegalArgumentException("Invalid $keyType key Base64 format: ${e.message}", e)
        }
    }

    /**
     * 清除所有认证数据
     */
    fun clearAuthData() {
        prefs.edit().clear().apply()
        Log.d(TAG, "All auth data cleared")
    }
}