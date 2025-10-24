/*
 * File: CryptoUtils.kt
 * Description: Cryptographic utilities for SHA256 hashing and RSA signing
 * Author: SIMS Team
 */
package com.simsapp.utils

import android.util.Base64
import android.util.Log
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import javax.crypto.Cipher

/**
 * CryptoUtils
 * 
 * 职责：
 * - 提供SHA256哈希计算功能
 * - 提供RSA签名功能
 * - 提供nonce生成功能
 * 
 * 设计思路：
 * - 使用标准的Java加密API
 * - 提供静态方法便于调用
 * - 统一的错误处理和日志记录
 * - 支持Base64编码输出
 */
object CryptoUtils {
    private const val TAG = "CryptoUtils"
    
    /**
     * 生成nonce（时间戳（毫秒）+ 五位随机数）
     * 
     * @return 时间戳拼接五位随机数的字符串
     */
    fun generateNonce(): String {
        val timestamp = System.currentTimeMillis().toString()
        val randomNumber = (10000..99999).random().toString()
        val nonce = "$timestamp$randomNumber"
        Log.d(TAG, "Generated nonce: $nonce (timestamp: $timestamp, random: $randomNumber)")
        return nonce
    }
    
    /**
     * 计算字符串的SHA256哈希值
     * 
     * @param input 输入字符串
     * @return SHA256哈希值的字节数组
     */
    fun sha256Hash(input: String): ByteArray {
        return try {
            Log.d(TAG, "Computing SHA256 hash for input length: ${input.length}")
            
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            
            Log.d(TAG, "SHA256 hash computed successfully, hash length: ${hashBytes.size}")
            hashBytes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute SHA256 hash", e)
            throw RuntimeException("SHA256 hash computation failed", e)
        }
    }
    
    /**
     * 使用RSA私钥对数据进行签名
     * 使用SHA256withRSA算法
     * 
     * @param data 要签名的数据
     * @param privateKey RSA私钥
     * @return Base64编码的签名字符串
     */
    fun rsaSign(data: ByteArray, privateKey: PrivateKey): String {
        return try {
            // Log.d(TAG, "Starting RSA signature for data length: ${data.length}")
            
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initSign(privateKey)
            signature.update(data)
            
            val signatureBytes = signature.sign()
            val base64Signature = Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
            
            Log.d(TAG, "RSA signature completed successfully, signature length: ${base64Signature.length}")
            base64Signature
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create RSA signature", e)
            throw RuntimeException("RSA signature creation failed", e)
        }
    }
    
    /**
     * 使用RSA私钥对哈希值进行签名（备用方法）
     * 直接对哈希值进行签名，不再进行二次哈希
     * 
     * @param hashHex 十六进制哈希值字符串
     * @param privateKey RSA私钥
     * @return Base64编码的签名字符串
     */
    fun rsaSignHash(hashHex: String, privateKey: PrivateKey): String {
        return try {
            Log.d(TAG, "Starting RSA signature for hash: $hashHex")
            
            // 将十六进制字符串转换为字节数组
            val hashBytes = hashHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            
            val signature = Signature.getInstance("NONEwithRSA")
            signature.initSign(privateKey)
            signature.update(hashBytes)
            
            val signatureBytes = signature.sign()
            val base64Signature = Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
            
            Log.d(TAG, "RSA hash signature completed successfully, signature length: ${base64Signature.length}")
            base64Signature
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create RSA hash signature", e)
            throw RuntimeException("RSA hash signature creation failed", e)
        }
    }
    
    /**
     * 生成请求签名
     * 
     * @param nonce 已拼接的nonce（用户工号+时间戳）
     * @param privateKey 私钥
     * @return Base64编码的签名字符串
     */
    /**
     * 生成请求签名
     * 使用工号对nonce做盐，执行SHA256哈希，然后用私钥进行RSA签名
     * 
     * @param nonce nonce值
     * @param userCode 用户工号（用作盐）
     * @param privateKey RSA私钥
     * @return Base64编码的签名字符串
     */
    fun generateRequestSignature(nonce: String, userCode: String, privateKey: PrivateKey): String {
        return try {
            Log.d(TAG, "Generating request signature for nonce: $nonce, userCode: $userCode")
            
            // 1. 用工号对nonce做盐
            val saltedNonce = "$userCode$nonce"
            Log.d(TAG, "Salted nonce: $saltedNonce")
            
            // 2. 对加盐后的nonce进行SHA256哈希
            val hashBytes = sha256Hash(saltedNonce)
            
            // 3. 将哈希字节数组转换为十六进制字符串
            // val hashHex = hashBytes.joinToString("") { byte ->
            //     "%02x".format(byte)
            // }
            // Log.d(TAG, "SHA256 hash: $hashHex")
            
            // 4. 使用私钥对哈希结果进行RSA签名
            val signature = rsaSign(hashBytes, privateKey)
            Log.d(TAG, "Request signature generated successfully")
            
            signature
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate request signature", e)
            throw RuntimeException("Request signature generation failed", e)
        }
    }
    
    /**
     * 验证输入参数的有效性
     * 
     * @param nonce nonce值
     * @param userCode 用户工号
     * @param privateKey 私钥
     * @throws IllegalArgumentException 参数无效时抛出
     */
    private fun validateSignatureParams(nonce: String, userCode: String, privateKey: PrivateKey?) {
        if (nonce.isBlank()) {
            throw IllegalArgumentException("Nonce cannot be blank")
        }
        if (userCode.isBlank()) {
            throw IllegalArgumentException("User code cannot be blank")
        }
        if (privateKey == null) {
            throw IllegalArgumentException("Private key cannot be null")
        }
        if (privateKey.algorithm != "RSA") {
            throw IllegalArgumentException("Private key must be RSA key")
        }
    }
}