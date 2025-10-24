/*
 * File: AuthManagerTest.kt
 * Description: Unit tests for AuthManager RSA key generation and Base64 conversion
 * Author: SIMS Team
 */
package com.simsapp

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.simsapp.data.local.AuthManager
import org.mockito.kotlin.*
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * AuthManagerTest
 * 
 * 职责：
 * - 测试RSA密钥生成功能
 * - 验证Base64转换逻辑
 * - 确保密钥存储和读取的正确性
 * 
 * 设计思路：
 * - 使用Robolectric运行Android相关测试
 * - 使用Mockito模拟SharedPreferences
 * - 测试密钥生成的完整流程
 * - 验证Base64编码解码的一致性
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AuthManagerTest {

    private lateinit var authManager: AuthManager
    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setup() {
        // Mock Android dependencies
        mockContext = mock()
        mockPrefs = mock()
        mockEditor = mock()

        whenever(mockContext.getSharedPreferences(any(), any())).thenReturn(mockPrefs)
        whenever(mockPrefs.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putString(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.apply()).then { }

        authManager = AuthManager(mockContext)
    }

    /**
     * 测试RSA密钥对生成功能
     * 验证生成的密钥对是否有效
     */
    @Test
    fun testGenerateRSAKeyPair() {
        // 执行密钥生成
        val keyPair = authManager.generateRSAKeyPair()

        // 验证密钥对不为空
        assertNotNull("Key pair should not be null", keyPair)
        assertNotNull("Public key should not be null", keyPair.public)
        assertNotNull("Private key should not be null", keyPair.private)

        // 验证密钥算法
        assertEquals("RSA", keyPair.public.algorithm)
        assertEquals("RSA", keyPair.private.algorithm)

        // 验证密钥长度（RSA 2048位密钥的编码长度应该在合理范围内）
        assertTrue("Public key should have reasonable size", keyPair.public.encoded.size > 200)
        assertTrue("Private key should have reasonable size", keyPair.private.encoded.size > 1000)
    }

    /**
     * 测试公钥Base64转换功能
     * 验证转换后的字符串可以正确解码回原始密钥
     */
    @Test
    fun testPublicKeyBase64Conversion() {
        // 生成密钥对
        val keyPair = authManager.generateRSAKeyPair()
        val originalPublicKey = keyPair.public

        // 转换为Base64字符串
        val base64String = authManager.publicKeyToString(originalPublicKey)

        // 验证Base64字符串不为空且格式正确
        assertNotNull("Base64 string should not be null", base64String)
        assertTrue("Base64 string should not be empty", base64String.isNotEmpty())
        assertTrue("Base64 string should have reasonable length", base64String.length > 300)

        // 验证可以解码回原始密钥
        val decodedBytes = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(decodedBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val reconstructedKey = keyFactory.generatePublic(keySpec)

        // 验证重构的密钥与原始密钥相同
        assertArrayEquals("Reconstructed key should match original", 
            originalPublicKey.encoded, reconstructedKey.encoded)
    }

    /**
     * 测试私钥Base64转换功能
     * 验证转换后的字符串可以正确解码回原始密钥
     */
    @Test
    fun testPrivateKeyBase64Conversion() {
        // 生成密钥对
        val keyPair = authManager.generateRSAKeyPair()
        val originalPrivateKey = keyPair.private

        // 转换为Base64字符串
        val base64String = authManager.privateKeyToString(originalPrivateKey)

        // 验证Base64字符串不为空且格式正确
        assertNotNull("Base64 string should not be null", base64String)
        assertTrue("Base64 string should not be empty", base64String.isNotEmpty())
        assertTrue("Base64 string should have reasonable length", base64String.length > 1000)

        // 验证可以解码回原始密钥
        val decodedBytes = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)
        val keySpec = PKCS8EncodedKeySpec(decodedBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val reconstructedKey = keyFactory.generatePrivate(keySpec)

        // 验证重构的密钥与原始密钥相同
        assertArrayEquals("Reconstructed key should match original", 
            originalPrivateKey.encoded, reconstructedKey.encoded)
    }

    /**
     * 测试私钥存储和读取功能
     * 验证Base64编码的私钥可以正确存储和读取
     */
    @Test
    fun testPrivateKeyStorageAndRetrieval() {
        // 生成密钥对
        val keyPair = authManager.generateRSAKeyPair()
        val originalPrivateKey = keyPair.private
        val base64String = authManager.privateKeyToString(originalPrivateKey)

        // Mock存储的私钥
        whenever(mockPrefs.getString("private_key", null)).thenReturn(base64String)

        // 读取私钥
        val retrievedKey = authManager.getPrivateKey()

        // 验证读取的密钥与原始密钥相同
        assertNotNull("Retrieved key should not be null", retrievedKey)
        assertArrayEquals("Retrieved key should match original", 
            originalPrivateKey.encoded, retrievedKey!!.encoded)

        // 验证调用了存储方法
        verify(mockEditor).putString("private_key", base64String)
    }

    /**
     * 测试密钥生成的一致性
     * 验证多次生成的密钥都是有效的且格式一致
     */
    @Test
    fun testKeyGenerationConsistency() {
        // 生成多个密钥对
        val keyPair1 = authManager.generateRSAKeyPair()
        val keyPair2 = authManager.generateRSAKeyPair()

        // 验证每个密钥对都是有效的
        assertNotNull(keyPair1.public)
        assertNotNull(keyPair1.private)
        assertNotNull(keyPair2.public)
        assertNotNull(keyPair2.private)

        // 验证不同的密钥对是不同的
        assertFalse("Different key pairs should have different public keys",
            keyPair1.public.encoded.contentEquals(keyPair2.public.encoded))
        assertFalse("Different key pairs should have different private keys",
            keyPair1.private.encoded.contentEquals(keyPair2.private.encoded))

        // 验证Base64转换的一致性
        val base64_1 = authManager.publicKeyToString(keyPair1.public)
        val base64_2 = authManager.publicKeyToString(keyPair2.public)
        
        assertTrue("Base64 strings should have consistent format", base64_1.length > 300)
        assertTrue("Base64 strings should have consistent format", base64_2.length > 300)
        assertNotEquals("Different keys should produce different Base64 strings", base64_1, base64_2)
    }
}