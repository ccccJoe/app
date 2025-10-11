/*
 * File: RiskMatrixRepositoryTest.kt
 * Description: Unit tests for RiskMatrixRepository functionality.
 * Author: SIMS Team
 */
package com.simsapp.data.repository

import android.content.Context
import com.simsapp.data.local.dao.ProjectDigitalAssetDao
import com.simsapp.data.local.entity.ProjectDigitalAssetEntity
import com.simsapp.data.remote.ApiService
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.Response
import java.io.ByteArrayInputStream
import org.junit.Assert.*

/**
 * RiskMatrixRepositoryTest
 * 
 * 测试风险矩阵数据的下载、缓存和管理功能
 */
@RunWith(RobolectricTestRunner::class)
class RiskMatrixRepositoryTest {

    @Mock
    private lateinit var mockApiService: ApiService
    
    @Mock
    private lateinit var mockProjectDigitalAssetDao: ProjectDigitalAssetDao
    
    private lateinit var context: Context
    private lateinit var repository: RiskMatrixRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        context = RuntimeEnvironment.getApplication()
        repository = RiskMatrixRepository(mockApiService, mockProjectDigitalAssetDao, context)
    }

    /**
     * 测试风险矩阵数据处理功能 - 成功场景
     */
    @Test
    fun testProcessRiskMatrixData_Success() = runTest {
        // 准备测试数据
        val projectUid = "test_project_123"
        val riskMatrixAsset = ProjectDigitalAssetEntity(
            id = 1L,
            projectUid = projectUid,
            nodeId = "risk_matrix_node_1",
            parentId = null,
            name = "risk_matrix.json",
            type = "risk_matrix",
            fileId = "risk_matrix_file_123",
            localPath = null,
            downloadStatus = "PENDING",
            downloadUrl = null,
            fileSize = 1024L,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            content = null
        )
        
        val digitalAssets = listOf(riskMatrixAsset)
        
        // 模拟API响应
        val mockJsonContent = """
        {
            "consequenceData": [
                {
                    "level": "1",
                    "severity_factor": 1.0,
                    "cost": "< $10,000",
                    "productionLoss": "无停产",
                    "safety": "无伤害",
                    "other": "无环境影响"
                }
            ],
            "likelihoodData": [
                {
                    "level": "A",
                    "description": "几乎确定",
                    "likelihood_factor": 3.0,
                    "criteria": "频繁发生"
                }
            ],
            "priorityData": [
                {
                    "priority": "Low",
                    "criteria": "低风险",
                    "minValue": 0.0,
                    "maxValue": 3.0,
                    "minInclusive": "true",
                    "maxInclusive": "false"
                }
            ]
        }
        """.trimIndent()
        
        // 模拟下载URL解析响应 - 使用data数组格式
        val mockUrlResponse = """{"data": [{"url": "https://example.com/risk_matrix.json"}]}"""
        val mockUrlResponseBody = mock(ResponseBody::class.java)
        whenever(mockUrlResponseBody.string()).thenReturn(mockUrlResponse)
        whenever(mockApiService.resolveDownloadUrl(any(), any())).thenReturn(Response.success(mockUrlResponseBody))
        
        // 模拟文件下载响应
        val mockFileResponseBody = mock(ResponseBody::class.java)
        whenever(mockFileResponseBody.string()).thenReturn(mockJsonContent)
        whenever(mockApiService.downloadRiskMatrixByUrl(any())).thenReturn(Response.success(mockFileResponseBody))
        
        // 执行测试
        val result = repository.processRiskMatrixData(projectUid, digitalAssets)
        
        // 验证结果
        assertTrue("处理应该成功", result.isSuccess)
        assertEquals("应该处理1个风险矩阵节点", 1, result.getOrNull())
        
        // 验证数据库更新调用
        verify(mockProjectDigitalAssetDao).updateDownloadComplete(
            eq("risk_matrix_node_1"),
            eq("COMPLETED"),
            any(),
            eq(mockJsonContent),
            any()
        )
    }

    /**
     * 测试风险矩阵数据处理功能 - 无风险矩阵节点
     */
    @Test
    fun testProcessRiskMatrixData_NoRiskMatrixNodes() = runTest {
        // 准备测试数据 - 没有风险矩阵节点
        val projectUid = "test_project_123"
        val normalAsset = ProjectDigitalAssetEntity(
            id = 1L,
            projectUid = projectUid,
            nodeId = "normal_node_1",
            parentId = null,
            name = "document.pdf",
            type = "document",
            fileId = "normal_file_123",
            localPath = null,
            downloadStatus = "PENDING",
            downloadUrl = null,
            fileSize = 2048L,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            content = null
        )
        
        val digitalAssets = listOf(normalAsset)
        
        // 执行测试
        val result = repository.processRiskMatrixData(projectUid, digitalAssets)
        
        // 验证结果
        assertTrue("处理应该成功", result.isSuccess)
        assertEquals("应该处理0个风险矩阵节点", 0, result.getOrNull())
        
        // 验证没有调用API
        verify(mockApiService, never()).resolveDownloadUrl(any(), any())
        verify(mockApiService, never()).downloadRiskMatrixByUrl(any())
    }

    /**
     * 测试风险矩阵数据处理功能 - 已缓存场景
     */
    @Test
    fun testProcessRiskMatrixData_AlreadyCached() = runTest {
        // 准备测试数据 - 已缓存的风险矩阵
        val projectUid = "test_project_123"
        val cachedRiskMatrixAsset = ProjectDigitalAssetEntity(
            id = 1L,
            projectUid = projectUid,
            nodeId = "risk_matrix_node_1",
            parentId = null,
            name = "risk_matrix.json",
            type = "risk_matrix",
            fileId = "risk_matrix_file_123",
            localPath = "/cache/risk_matrix.json",
            downloadStatus = "COMPLETED",
            downloadUrl = "https://example.com/risk_matrix.json",
            fileSize = 1024L,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            content = """{"test": "data"}"""
        )
        
        val digitalAssets = listOf(cachedRiskMatrixAsset)
        
        // 执行测试
        val result = repository.processRiskMatrixData(projectUid, digitalAssets)
        
        // 验证结果
        assertTrue("处理应该成功", result.isSuccess)
        assertEquals("应该处理1个风险矩阵节点", 1, result.getOrNull())
        
        // 验证没有调用API（因为已缓存）
        verify(mockApiService, never()).resolveDownloadUrl(any(), any())
        verify(mockApiService, never()).downloadRiskMatrixByUrl(any())
        
        // 验证没有更新数据库（因为已缓存）
        verify(mockProjectDigitalAssetDao, never()).updateDownloadComplete(any(), any(), any(), any(), any())
    }

    /**
     * 测试风险矩阵缓存检查功能
     */
    @Test
    fun testIsRiskMatrixCached() = runTest {
        val projectUid = "test_project_123"
        val fileId = "risk_matrix_file_123"
        
        // 测试缓存不存在的情况
        val isNotCached = repository.isRiskMatrixCached(projectUid, fileId)
        assertFalse(isNotCached)
        
        // 注意：实际的缓存测试需要模拟文件系统操作
        // 这里只测试方法调用不会抛出异常
    }
}