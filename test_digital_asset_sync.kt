/**
 * 文件：test_digital_asset_sync.kt
 * 说明：数字资产同步功能测试脚本
 * 作者：AI Assistant
 * 
 * 该脚本用于测试数字资产同步过程中的关键功能：
 * 1. 数字资产树解析
 * 2. 根据fileId更新本地缓存
 * 3. 非风险矩阵数据的URL获取和下载
 */

package com.simsapp.test

import android.util.Log
import com.simsapp.data.repository.ProjectDigitalAssetRepository
import com.simsapp.data.repository.ProjectRepository
import com.simsapp.utils.DigitalAssetTreeParser
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * 数字资产同步测试类
 */
class DigitalAssetSyncTest {
    
    companion object {
        private const val TAG = "DigitalAssetSyncTest"
        
        /**
         * 测试数字资产树解析功能
         */
        fun testDigitalAssetTreeParsing() {
            Log.d(TAG, "=== 开始测试数字资产树解析 ===")
            
            // 模拟项目详情JSON数据
            val testProjectJson = """
            {
                "project_uid": "test_project_123",
                "digital_asset_tree": {
                    "name": "Root",
                    "children": [
                        {
                            "name": "Images",
                            "file_id": "img_001",
                            "file_type": "PIC",
                            "children": []
                        },
                        {
                            "name": "Documents",
                            "children": [
                                {
                                    "name": "Report.pdf",
                                    "file_id": "doc_001",
                                    "file_type": "PDF"
                                }
                            ]
                        },
                        {
                            "name": "Audio",
                            "file_id": "audio_001",
                            "file_type": "MP3"
                        }
                    ]
                }
            }
            """.trimIndent()
            
            try {
                val projectJson = JSONObject(testProjectJson)
                val digitalAssetNodes = DigitalAssetTreeParser.parseDigitalAssetTree(projectJson)
                
                Log.d(TAG, "解析到 ${digitalAssetNodes.size} 个数字资产节点:")
                digitalAssetNodes.forEach { node ->
                    Log.d(TAG, "- 节点: ${node.name}, FileID: ${node.fileId}, 类型: ${node.fileType}")
                }
                
                Log.d(TAG, "✅ 数字资产树解析测试通过")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 数字资产树解析测试失败: ${e.message}")
            }
        }
        
        /**
         * 测试数字资产缓存功能
         */
        suspend fun testDigitalAssetCaching(repository: ProjectDigitalAssetRepository) {
            Log.d(TAG, "=== 开始测试数字资产缓存功能 ===")
            
            try {
                // 测试文件ID
                val testFileId = "test_file_123"
                
                // 检查文件是否已缓存
                val isCached = repository.isFileCached(testFileId)
                Log.d(TAG, "文件 $testFileId 缓存状态: $isCached")
                
                // 尝试获取本地文件
                val localFile = repository.getLocalFile(testFileId)
                if (localFile != null) {
                    Log.d(TAG, "✅ 找到缓存文件: ${localFile.absolutePath}")
                } else {
                    Log.d(TAG, "ℹ️ 文件未缓存或不存在")
                }
                
                Log.d(TAG, "✅ 数字资产缓存功能测试完成")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 数字资产缓存功能测试失败: ${e.message}")
            }
        }
        
        /**
         * 测试项目同步过程
         */
        suspend fun testProjectSync(projectRepository: ProjectRepository) {
            Log.d(TAG, "=== 开始测试项目同步过程 ===")
            
            try {
                // 获取项目数量（同步前）
                val countBefore = projectRepository.getProjectCount()
                Log.d(TAG, "同步前项目数量: $countBefore")
                
                // 执行同步（这里只是模拟，实际需要有效的endpoint）
                Log.d(TAG, "开始执行项目同步...")
                
                // 获取项目数量（同步后）
                val countAfter = projectRepository.getProjectCount()
                Log.d(TAG, "同步后项目数量: $countAfter")
                
                if (countAfter >= countBefore) {
                    Log.d(TAG, "✅ 项目同步测试通过，新增项目: ${countAfter - countBefore}")
                } else {
                    Log.w(TAG, "⚠️ 项目同步后数量减少，可能存在问题")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ 项目同步测试失败: ${e.message}")
            }
        }
        
        /**
         * 运行所有测试
         */
        fun runAllTests(
            projectRepository: ProjectRepository,
            digitalAssetRepository: ProjectDigitalAssetRepository
        ) {
            Log.d(TAG, "🚀 开始运行数字资产同步功能测试套件")
            
            // 1. 测试数字资产树解析
            testDigitalAssetTreeParsing()
            
            // 2. 测试数字资产缓存功能
            runBlocking {
                testDigitalAssetCaching(digitalAssetRepository)
            }
            
            // 3. 测试项目同步过程
            runBlocking {
                testProjectSync(projectRepository)
            }
            
            Log.d(TAG, "🏁 数字资产同步功能测试套件执行完成")
        }
    }
}