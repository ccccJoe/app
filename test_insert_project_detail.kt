/*
 * File: test_insert_project_detail.kt
 * Description: 测试脚本，用于向数据库插入项目详情数据以验证StoragePickerViewModel的功能
 * Author: SIMS Team
 */

import kotlinx.coroutines.runBlocking
import com.simsapp.data.local.entity.ProjectDetailEntity
import com.simsapp.data.local.entity.ProjectEntity
import java.io.File

/**
 * 测试用的项目详情插入脚本
 * 
 * 该脚本用于测试StoragePickerViewModel中项目详情加载失败的问题
 * 通过插入测试数据来验证数据库查询和JSON解析逻辑
 */
fun main() {
    runBlocking {
        // 读取projectDetails.json文件内容
        val projectDetailsFile = File("d:\\Project\\SIMS-Android\\projectDetails.json")
        if (!projectDetailsFile.exists()) {
            println("错误：projectDetails.json文件不存在")
            return@runBlocking
        }
        
        val rawJson = projectDetailsFile.readText()
        println("成功读取projectDetails.json文件，大小：${rawJson.length}字符")
        
        // 创建测试用的ProjectEntity
        val testProject = ProjectEntity(
            projectId = 1L,
            projectUid = "1976202565639987200", // 使用projectDetails.json中的project_uid
            name = "LSMNPP",
            status = "COLLECTING",
            defectCount = 0,
            eventCount = 0,
            projectHash = "test_hash"
        )
        
        // 创建测试用的ProjectDetailEntity
        val testProjectDetail = ProjectDetailEntity(
            detailId = 0L, // 自动生成
            projectId = 1L,
            projectUid = "1976202565639987200",
            name = "LSMNPP",
            status = "COLLECTING",
            startDate = 1759227577000L,
            endDate = 1759227577000L,
            lastUpdateAt = System.currentTimeMillis(),
            rawJson = rawJson, // 使用完整的JSON数据
            lastFetchedAt = System.currentTimeMillis()
        )
        
        println("测试数据创建完成：")
        println("- Project UID: ${testProject.projectUid}")
        println("- Project Name: ${testProject.name}")
        println("- Detail JSON Size: ${testProjectDetail.rawJson.length}")
        
        // 验证JSON中是否包含project_digital_asset_tree
        if (rawJson.contains("project_digital_asset_tree")) {
            println("✓ JSON中包含project_digital_asset_tree字段")
        } else {
            println("✗ JSON中不包含project_digital_asset_tree字段")
        }
        
        println("\n注意：此脚本仅用于验证数据结构，实际插入需要在Android环境中进行")
        println("建议在StoragePickerViewModel的loadDigitalAssetTree方法中添加调试日志")
    }
}