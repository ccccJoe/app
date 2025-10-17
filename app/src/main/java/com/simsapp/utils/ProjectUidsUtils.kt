/*
 * File: ProjectUidsUtils.kt
 * Description: Utility functions for handling project_uids JSON array operations.
 * Author: SIMS Team
 */
package com.simsapp.utils

import org.json.JSONArray
import org.json.JSONException

/**
 * ProjectUidsUtils
 *
 * 提供处理project_uids JSON数组的工具方法，用于数字资产表的多项目关联功能。
 */
object ProjectUidsUtils {
    
    /**
     * 创建包含单个project_uid的JSON数组字符串
     *
     * @param projectUid 项目UID
     * @return JSON数组字符串，例如: ["project_uid_1"]
     */
    fun createProjectUidsArray(projectUid: String): String {
        return JSONArray().apply {
            put(projectUid)
        }.toString()
    }
    
    /**
     * 向现有的project_uids JSON数组中添加新的project_uid
     *
     * @param existingProjectUids 现有的project_uids JSON数组字符串
     * @param newProjectUid 要添加的新project_uid
     * @return 更新后的JSON数组字符串
     */
    fun addProjectUidToArray(existingProjectUids: String, newProjectUid: String): String {
        return try {
            val jsonArray = JSONArray(existingProjectUids)
            
            // 检查是否已存在，避免重复添加
            for (i in 0 until jsonArray.length()) {
                if (jsonArray.getString(i) == newProjectUid) {
                    return existingProjectUids // 已存在，直接返回原数组
                }
            }
            
            // 添加新的project_uid
            jsonArray.put(newProjectUid)
            jsonArray.toString()
        } catch (e: JSONException) {
            // 如果解析失败，创建新的数组
            createProjectUidsArray(newProjectUid)
        }
    }
    
    /**
     * 从project_uids JSON数组中移除指定的project_uid
     *
     * @param existingProjectUids 现有的project_uids JSON数组字符串
     * @param projectUidToRemove 要移除的project_uid
     * @return 更新后的JSON数组字符串
     */
    fun removeProjectUidFromArray(existingProjectUids: String, projectUidToRemove: String): String {
        return try {
            val jsonArray = JSONArray(existingProjectUids)
            val newArray = JSONArray()
            
            // 复制除了要移除的项目之外的所有项目
            for (i in 0 until jsonArray.length()) {
                val uid = jsonArray.getString(i)
                if (uid != projectUidToRemove) {
                    newArray.put(uid)
                }
            }
            
            newArray.toString()
        } catch (e: JSONException) {
            existingProjectUids // 解析失败，返回原数组
        }
    }
    
    /**
     * 检查project_uids JSON数组中是否包含指定的project_uid
     *
     * @param projectUids project_uids JSON数组字符串
     * @param projectUid 要检查的project_uid
     * @return true如果包含，false如果不包含
     */
    fun containsProjectUid(projectUids: String, projectUid: String): Boolean {
        return try {
            val jsonArray = JSONArray(projectUids)
            for (i in 0 until jsonArray.length()) {
                if (jsonArray.getString(i) == projectUid) {
                    return true
                }
            }
            false
        } catch (e: JSONException) {
            false
        }
    }
    
    /**
     * 将project_uids JSON数组字符串转换为List<String>
     *
     * @param projectUids project_uids JSON数组字符串
     * @return 项目UID列表
     */
    fun parseProjectUidsToList(projectUids: String): List<String> {
        return try {
            val jsonArray = JSONArray(projectUids)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (e: JSONException) {
            emptyList()
        }
    }
    
    /**
     * 将List<String>转换为project_uids JSON数组字符串
     *
     * @param projectUidsList 项目UID列表
     * @return JSON数组字符串
     */
    fun createProjectUidsFromList(projectUidsList: List<String>): String {
        val jsonArray = JSONArray()
        projectUidsList.forEach { uid ->
            jsonArray.put(uid)
        }
        return jsonArray.toString()
    }
    
    /**
     * 获取project_uids数组中的第一个project_uid
     * 用于向后兼容需要单个project_uid的场景
     *
     * @param projectUids project_uids JSON数组字符串
     * @return 第一个project_uid，如果数组为空则返回null
     */
    fun getFirstProjectUid(projectUids: String): String? {
        return try {
            val jsonArray = JSONArray(projectUids)
            if (jsonArray.length() > 0) {
                jsonArray.getString(0)
            } else {
                null
            }
        } catch (e: JSONException) {
            null
        }
    }
}