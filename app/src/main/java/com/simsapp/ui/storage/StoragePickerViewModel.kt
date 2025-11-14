package com.simsapp.ui.storage

/*
 * File: StoragePickerViewModel.kt
 * Description: 数字资产选择页面的ViewModel，处理project_digital_asset_tree数据和文件夹导航
 * Author: SIMS Team
 */

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simsapp.data.local.dao.ProjectDetailDao
import com.simsapp.data.local.dao.ProjectDigitalAssetDao
import com.simsapp.utils.DigitalAssetTreeParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

/**
 * DigitalAssetTreeNode
 * 
 * 数字资产树节点数据类
 */
data class DigitalAssetTreeNode(
    val id: String,
    val name: String,
    val treeNodeType: String,
    val parentId: String?,
    val level: Int,
    val children: List<DigitalAssetTreeNode> = emptyList(),
    val fileId: String? = null
)

/**
 * StoragePickerUiState
 * 
 * 数字资产选择页面UI状态
 */
data class StoragePickerUiState(
    val isLoading: Boolean = false,
    val currentPath: List<DigitalAssetTreeNode> = emptyList(), // 当前路径（面包屑导航）
    val currentItems: List<DigitalAssetTreeNode> = emptyList(), // 当前文件夹下的项目
    val selectedItems: Set<String> = emptySet(), // 选中的非文件夹项目ID集合
    val isMultiSelectMode: Boolean = false, // 是否处于多选模式
    val error: String? = null
)

/**
 * StoragePickerViewModel
 *
 * 数字资产选择页面的ViewModel
 * 负责从project_detail表的raw_json字段解析project_digital_asset_tree数据
 * 实现文件夹导航功能
 */
@HiltViewModel
class StoragePickerViewModel @Inject constructor(
    private val projectDetailDao: ProjectDetailDao,
    private val projectDigitalAssetDao: ProjectDigitalAssetDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoragePickerUiState())
    val uiState: StateFlow<StoragePickerUiState> = _uiState.asStateFlow()

    private var fullTree: DigitalAssetTreeNode? = null

    /**
     * 加载项目的数字资产树数据
     * 
     * @param projectUid 项目唯一标识符
     */
    fun loadDigitalAssetTree(projectUid: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                android.util.Log.d("StoragePickerViewModel", "开始加载项目数字资产树，projectUid: $projectUid")
                
                // 从project_detail表获取项目详情
                val projectDetail = projectDetailDao.getByProjectUid(projectUid)
                android.util.Log.d("StoragePickerViewModel", "查询项目详情结果: ${if (projectDetail != null) "找到" else "未找到"}")
                
                if (projectDetail == null) {
                    android.util.Log.w("StoragePickerViewModel", "项目详情未找到，projectUid: $projectUid")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "项目详情未找到，请检查项目UID: $projectUid"
                    )
                    return@launch
                }
                
                android.util.Log.d("StoragePickerViewModel", "找到项目详情: name=${projectDetail.name}, rawJson长度=${projectDetail.rawJson.length}")

                // 解析project_digital_asset_tree
                val tree = parseDigitalAssetTreeFromJson(projectDetail.rawJson)
                fullTree = tree
                
                // 显示根目录内容
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentPath = listOf(tree),
                    currentItems = tree.children,
                    error = null
                )
            } catch (e: Exception) {
                android.util.Log.e("StoragePickerViewModel", "Error loading digital asset tree", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "加载数字资产数据失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 进入文件夹
     * 
     * @param node 要进入的文件夹节点
     */
    fun enterFolder(node: DigitalAssetTreeNode) {
        if (isFolderType(node.treeNodeType)) {
            val currentPath = _uiState.value.currentPath.toMutableList()
            currentPath.add(node)
            
            _uiState.value = _uiState.value.copy(
                currentPath = currentPath,
                currentItems = node.children
            )
        }
    }

    /**
     * 返回上级文件夹
     */
    fun goBack() {
        val currentPath = _uiState.value.currentPath.toMutableList()
        if (currentPath.size > 1) {
            currentPath.removeAt(currentPath.size - 1)
            val parentNode = currentPath.last()
            
            _uiState.value = _uiState.value.copy(
                currentPath = currentPath,
                currentItems = parentNode.children
            )
        }
    }

    /**
     * 导航到指定路径节点
     * 
     * @param targetNode 目标节点
     */
    fun navigateToNode(targetNode: DigitalAssetTreeNode) {
        // 找到从根节点到目标节点的路径
        val pathToTarget = findPathToNode(fullTree, targetNode.id)
        if (pathToTarget.isNotEmpty()) {
            val targetNodeInPath = pathToTarget.last()
            _uiState.value = _uiState.value.copy(
                currentPath = pathToTarget,
                currentItems = targetNodeInPath.children
            )
        }
    }

    /**
     * 切换多选模式
     */
    fun toggleMultiSelectMode() {
        _uiState.value = _uiState.value.copy(
            isMultiSelectMode = !_uiState.value.isMultiSelectMode,
            selectedItems = emptySet() // 切换模式时清空选择
        )
    }

    /**
     * 切换项目选中状态（仅非文件夹项目）
     * 
     * @param item 要切换选中状态的项目
     */
    fun toggleItemSelection(item: DigitalAssetTreeNode) {
        if (isFolderType(item.treeNodeType)) return // 文件夹（含Setting）不能被选中
        
        val currentSelected = _uiState.value.selectedItems.toMutableSet()
        if (currentSelected.contains(item.id)) {
            currentSelected.remove(item.id)
        } else {
            currentSelected.add(item.id)
        }
        
        _uiState.value = _uiState.value.copy(selectedItems = currentSelected)
    }

    /**
     * 清空所有选择
     */
    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedItems = emptySet())
    }

    /**
     * 根据节点ID列表设置选中状态（用于精确回显）
     *
     * @param nodeIds 要选中的资产节点ID列表
     */
    fun setSelectedAssetsByNodeIds(nodeIds: List<String>) {
        // 直接将节点ID集合设置为选中项；节点ID在树中是唯一的
        val selectedIds = nodeIds.filter { it.isNotBlank() }.toSet()
        _uiState.value = _uiState.value.copy(
            selectedItems = selectedIds,
            isMultiSelectMode = selectedIds.isNotEmpty()
        )
        android.util.Log.d(
            "StoragePickerViewModel",
            "根据节点ID回显选中资产: ${selectedIds.joinToString()}"
        )
    }

    /**
     * 获取选中的项目列表
     * 
     * @return 选中的数字资产节点列表
     */
    fun getSelectedItems(): List<DigitalAssetTreeNode> {
        val selectedIds = _uiState.value.selectedItems
        val allItems = mutableListOf<DigitalAssetTreeNode>()
        
        // 递归收集所有节点
        fun collectAllNodes(node: DigitalAssetTreeNode) {
            allItems.add(node)
            node.children.forEach { collectAllNodes(it) }
        }
        
        fullTree?.let { collectAllNodes(it) }
        
        return allItems.filter { selectedIds.contains(it.id) }
    }

    /**
     * 根据资产fileId列表设置选中状态（用于回显）
     * 
     * @param assetFileIds 要选中的资产fileId列表
     */
    fun setSelectedAssetsByFileIds(assetFileIds: List<String>) {
        android.util.Log.d("StoragePickerViewModel", "开始根据fileId设置选中状态: ${assetFileIds.joinToString()}")
        
        // 如果数据还未加载完成，启动协程等待数据加载
        if (fullTree == null) {
            android.util.Log.d("StoragePickerViewModel", "数据尚未加载完成，启动协程等待...")
            viewModelScope.launch {
                // 等待数据加载完成（最多等待5秒）
                var retryCount = 0
                val maxRetries = 50 // 5秒 = 50 * 100ms
                
                while (fullTree == null && retryCount < maxRetries) {
                    kotlinx.coroutines.delay(100) // 等待100ms
                    retryCount++
                }
                
                if (fullTree != null) {
                    android.util.Log.d("StoragePickerViewModel", "数据加载完成，重新执行回显逻辑")
                    setSelectedAssetsByFileIdsInternal(assetFileIds)
                } else {
                    android.util.Log.w("StoragePickerViewModel", "等待超时，数据仍未加载完成")
                }
            }
            return
        }
        
        // 数据已加载，直接执行回显逻辑
        setSelectedAssetsByFileIdsInternal(assetFileIds)
    }
    
    /**
     * 内部方法：执行实际的回显逻辑
     * 
     * @param assetFileIds 要选中的资产fileId列表
     */
    private fun setSelectedAssetsByFileIdsInternal(assetFileIds: List<String>) {
        val allItems = mutableListOf<DigitalAssetTreeNode>()
        
        // 递归收集所有节点
        fun collectAllNodes(node: DigitalAssetTreeNode) {
            allItems.add(node)
            node.children.forEach { collectAllNodes(it) }
        }
        
        fullTree?.let { 
            collectAllNodes(it)
            android.util.Log.d("StoragePickerViewModel", "收集到${allItems.size}个节点")
        } ?: run {
            android.util.Log.w("StoragePickerViewModel", "fullTree为空，无法进行回显")
            return
        }
        
        // 根据fileId找到对应的节点ID
        val selectedIds = allItems
            .filter { !isFolderType(it.treeNodeType) && it.fileId != null && assetFileIds.contains(it.fileId) }
            .map { 
                android.util.Log.d("StoragePickerViewModel", "匹配到节点: id=${it.id}, name=${it.name}, fileId=${it.fileId}")
                it.id 
            }
            .toSet()
        
        android.util.Log.d("StoragePickerViewModel", "根据fileId找到${selectedIds.size}个匹配项: ${selectedIds.joinToString()}")
        
        // 同时记录所有非文件夹节点的信息用于调试
        val allNonFolderItems = allItems.filter { !isFolderType(it.treeNodeType) }
        android.util.Log.d("StoragePickerViewModel", "所有非文件夹节点(${allNonFolderItems.size}个):")
        allNonFolderItems.forEach { item ->
            android.util.Log.d("StoragePickerViewModel", "  - id=${item.id}, name=${item.name}, fileId=${item.fileId}, type=${item.treeNodeType}")
        }
        
        // 更新选中状态并自动切换到多选模式（如果有选中项）
        _uiState.value = _uiState.value.copy(
            selectedItems = selectedIds,
            isMultiSelectMode = selectedIds.isNotEmpty()
        )
        
        android.util.Log.d("StoragePickerViewModel", "已更新选中状态，当前选中项: ${_uiState.value.selectedItems.joinToString()}, 多选模式: ${_uiState.value.isMultiSelectMode}")
    }

    /**
     * 根据资产名称列表设置选中状态（用于回显）
     * 
     * @param assetNames 要选中的资产名称列表
     */
    fun setSelectedAssetsByNames(assetNames: List<String>) {
        val allItems = mutableListOf<DigitalAssetTreeNode>()
        
        // 递归收集所有节点
        fun collectAllNodes(node: DigitalAssetTreeNode) {
            allItems.add(node)
            node.children.forEach { collectAllNodes(it) }
        }
        
        fullTree?.let { collectAllNodes(it) }
        
        // 根据名称找到对应的节点ID
        val selectedIds = allItems
            .filter { it.treeNodeType != "Folder" && assetNames.contains(it.name) }
            .map { it.id }
            .toSet()
        
        _uiState.value = _uiState.value.copy(selectedItems = selectedIds)
        
        android.util.Log.d("StoragePickerViewModel", "根据名称回显选中资产: ${assetNames.joinToString()}, 找到${selectedIds.size}个匹配项")
    }



    /**
     * 从JSON中解析数字资产树
     * 
     * @param rawJson 项目详情的原始JSON字符串
     * @return 解析后的数字资产树根节点
     */
    private suspend fun parseDigitalAssetTreeFromJson(rawJson: String): DigitalAssetTreeNode {
        return try {
            android.util.Log.d("StoragePickerViewModel", "开始解析JSON，长度: ${rawJson.length}")
            val rootObject = JSONObject(rawJson)
            android.util.Log.d("StoragePickerViewModel", "JSON根对象解析成功，包含字段: ${rootObject.keys().asSequence().toList()}")
            
            // 查找数字资产树，支持多种字段名
            var assetTreeJson: JSONObject? = null
            
            // 1. 尝试查找 project_digital_asset_tree
            assetTreeJson = rootObject.optJSONObject("project_digital_asset_tree")
            android.util.Log.d("StoragePickerViewModel", "根级别查找project_digital_asset_tree: ${if (assetTreeJson != null) "找到" else "未找到"}")
            
            // 2. 如果没找到，尝试查找 digital_asset_tree
            if (assetTreeJson == null) {
                assetTreeJson = rootObject.optJSONObject("digital_asset_tree")
                android.util.Log.d("StoragePickerViewModel", "根级别查找digital_asset_tree: ${if (assetTreeJson != null) "找到" else "未找到"}")
            }
            
            // 3. 如果在根级别没找到，尝试在data对象中查找
            if (assetTreeJson == null) {
                val dataObject = rootObject.optJSONObject("data")
                android.util.Log.d("StoragePickerViewModel", "查找data对象: ${if (dataObject != null) "找到" else "未找到"}")
                
                if (dataObject != null) {
                    android.util.Log.d("StoragePickerViewModel", "data对象包含字段: ${dataObject.keys().asSequence().toList()}")
                    
                    assetTreeJson = dataObject.optJSONObject("project_digital_asset_tree")
                    android.util.Log.d("StoragePickerViewModel", "data中查找project_digital_asset_tree: ${if (assetTreeJson != null) "找到" else "未找到"}")
                    
                    if (assetTreeJson == null) {
                        assetTreeJson = dataObject.optJSONObject("digital_asset_tree")
                        android.util.Log.d("StoragePickerViewModel", "data中查找digital_asset_tree: ${if (assetTreeJson != null) "找到" else "未找到"}")
                    }
                }
            }
            
            if (assetTreeJson != null) {
                android.util.Log.d("StoragePickerViewModel", "找到数字资产树JSON，开始解析树结构")
                val treeNode = parseTreeNode(assetTreeJson, null, 1)
                android.util.Log.d("StoragePickerViewModel", "树结构解析完成，根节点: ${treeNode.name}, 子节点数量: ${treeNode.children.size}")
                treeNode
            } else {
                android.util.Log.w("StoragePickerViewModel", "未找到数字资产树数据，创建空根节点")
                // 创建一个空的根节点
                DigitalAssetTreeNode(
                    id = "root",
                    name = "数字资产",
                    treeNodeType = "Folder",
                    parentId = null,
                    level = 1,
                    children = emptyList()
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("StoragePickerViewModel", "解析数字资产树JSON时发生错误", e)
            // 返回空的根节点而不是null
            DigitalAssetTreeNode(
                id = "root",
                name = "数字资产",
                treeNodeType = "Folder",
                parentId = null,
                level = 1,
                children = emptyList()
            )
        }
    }

    /**
     * 递归解析树节点
     * 
     * @param jsonNode JSON节点对象
     * @param parentId 父节点ID
     * @param level 节点层级
     * @return 解析后的树节点
     */
    private suspend fun parseTreeNode(jsonNode: JSONObject, parentId: String?, level: Int): DigitalAssetTreeNode {
        val id = jsonNode.optString("id", "")
        val name = jsonNode.optString("name", "") 
            .ifEmpty { jsonNode.optString("node_name", "") } // 支持node_name字段
        var treeNodeType = jsonNode.optString("tree_node_type", "Folder")
        val fileId = jsonNode.optString("file_id")
        val fileIdValue = if (fileId.isNullOrEmpty()) null else fileId
        
        // 如果有file_id，尝试从本地数字资产表获取真实的type信息
        if (!fileIdValue.isNullOrEmpty()) {
            try {
                val digitalAsset = projectDigitalAssetDao.getByFileId(fileIdValue)
                if (digitalAsset != null) {
                    // 使用数据库中的type信息，如果存在的话
                    val dbType = digitalAsset.type
                    if (!dbType.isNullOrEmpty() && !isFolderType(dbType)) {
                        treeNodeType = dbType
                        android.util.Log.d("StoragePickerViewModel", "Updated type for file_id $fileIdValue: $dbType")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("StoragePickerViewModel", "Failed to get type from database for file_id: $fileIdValue", e)
            }
        }
        
        // 解析子节点
        val children = mutableListOf<DigitalAssetTreeNode>()
        val childrenArray = jsonNode.optJSONArray("children")
        if (childrenArray != null) {
            for (i in 0 until childrenArray.length()) {
                val childJson = childrenArray.optJSONObject(i)
                if (childJson != null) {
                    val childNode = parseTreeNode(childJson, id, level + 1)
                    children.add(childNode)
                }
            }
        }
        
        return DigitalAssetTreeNode(
            id = id.ifEmpty { "node_${System.currentTimeMillis()}_$level" }, // 生成默认ID
            name = name.ifEmpty { "未命名" }, // 提供默认名称
            treeNodeType = treeNodeType,
            parentId = parentId,
            level = level,
            children = children,
            fileId = fileIdValue
        )
    }

    /**
     * Helper: determine whether a node type should be treated as a folder.
     * "Folder" and "Setting" are both folder-like, and should not be selectable.
     *
     * @param type Node type string
     * @return true if folder-like; otherwise false
     */
    private fun isFolderType(type: String?): Boolean {
        return type.equals("Folder", ignoreCase = true) || type.equals("Setting", ignoreCase = true)
    }

    /**
     * 查找从根节点到指定节点的路径
     * 
     * @param root 根节点
     * @param targetId 目标节点ID
     * @return 路径节点列表
     */
    private fun findPathToNode(root: DigitalAssetTreeNode?, targetId: String): List<DigitalAssetTreeNode> {
        if (root == null) return emptyList()
        
        fun searchPath(node: DigitalAssetTreeNode, path: MutableList<DigitalAssetTreeNode>): Boolean {
            path.add(node)
            
            if (node.id == targetId) {
                return true
            }
            
            for (child in node.children) {
                if (searchPath(child, path)) {
                    return true
                }
            }
            
            path.removeAt(path.size - 1)
            return false
        }
        
        val path = mutableListOf<DigitalAssetTreeNode>()
        return if (searchPath(root, path)) path else emptyList()
    }
}