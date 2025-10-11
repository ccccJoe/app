/*
 * File: DigitalAssetTreeParser.kt
 * Description: Utility class for parsing and traversing project_digital_asset_tree structure.
 * Author: SIMS Team
 */
package com.simsapp.utils

import org.json.JSONArray
import org.json.JSONObject

/**
 * DigitalAssetTreeParser
 *
 * Provides utilities to recursively traverse project_digital_asset_tree and extract file_id nodes.
 * According to the business rule: children[0].children[0] is always the risk matrix node.
 */
object DigitalAssetTreeParser {

    /**
     * Enum to track position in the tree structure for risk matrix identification.
     */
    private enum class TreePosition {
        ROOT,                    // Root node
        FIRST_LEVEL_CHILD,      // children[0] - first child of root
        RISK_MATRIX_LEVEL,      // children[0].children[0] - risk matrix position
        OTHER                   // All other positions
    }

    /**
     * Data class representing a digital asset node with file_id.
     */
    data class DigitalAssetNode(
        val fileId: String,
        val nodeName: String?,
        val nodePath: String,
        val fileType: String?,
        val fileSize: Long?,
        val rawNodeJson: String,
        val isRiskMatrix: Boolean = false, // 标识是否为风险矩阵数据
        val nodeId: String?, // 节点的id字段
        val parentId: String? // 节点的p_id字段
    )

    /**
     * Parse project_digital_asset_tree from project detail JSON and extract all nodes with file_id.
     * The first child of the first child node (children[0].children[0]) is treated as risk matrix data.
     *
     * @param projectDetailJson The raw JSON string of project detail
     * @return List of DigitalAssetNode containing file_id and metadata
     */
    fun parseDigitalAssetTree(projectDetailJson: String): List<DigitalAssetNode> {
        return try {
            android.util.Log.d("DigitalAssetParser", "Parsing project detail JSON: ${projectDetailJson.take(500)}...")
            val rootObject = JSONObject(projectDetailJson)
            
            // First check if project_digital_asset_tree is at root level
            var assetTree = rootObject.optJSONObject("project_digital_asset_tree")
                ?: rootObject.optJSONArray("project_digital_asset_tree")?.let { 
                    // If it's an array, wrap it in an object
                    JSONObject().apply { put("children", it) }
                }
            
            // If not found at root, check inside "data" object
            if (assetTree == null) {
                val dataObject = rootObject.optJSONObject("data")
                if (dataObject != null) {
                    android.util.Log.d("DigitalAssetParser", "Checking for project_digital_asset_tree in data object")
                    assetTree = dataObject.optJSONObject("project_digital_asset_tree")
                        ?: dataObject.optJSONArray("project_digital_asset_tree")?.let { 
                            // If it's an array, wrap it in an object
                            JSONObject().apply { put("children", it) }
                        }
                }
            }
            
            if (assetTree == null) {
                android.util.Log.w("DigitalAssetParser", "No project_digital_asset_tree found in JSON")
                return emptyList()
            }

            android.util.Log.d("DigitalAssetParser", "Found asset tree: ${assetTree.toString().take(200)}...")
            val assetNodes = mutableListOf<DigitalAssetNode>()
            
            // Start traversal from root, no initial risk matrix flag
            traverseTreeNode(assetTree, "root", assetNodes, TreePosition.ROOT)
            
            android.util.Log.d("DigitalAssetParser", "Parsed ${assetNodes.size} asset nodes with file_id")
            assetNodes
        } catch (e: Exception) {
            android.util.Log.e("DigitalAssetParser", "Error parsing digital asset tree", e)
            emptyList()
        }
    }

    /**
     * Recursively traverse a tree node and collect nodes with file_id.
     * Skips nodes with tree_node_type="Folder" and nodes with null/empty file_id.
     * Identifies risk matrix based on position: children[0].children[0] is always risk matrix.
     *
     * @param node Current JSON node (JSONObject or JSONArray)
     * @param currentPath Current path in the tree structure
     * @param assetNodes Mutable list to collect asset nodes
     * @param position Current position in the tree structure
     */
    private fun traverseTreeNode(
        node: Any,
        currentPath: String,
        assetNodes: MutableList<DigitalAssetNode>,
        position: TreePosition = TreePosition.OTHER
    ) {
        when (node) {
            is JSONObject -> {
                // Skip nodes with tree_node_type="Folder"
                val nodeType = node.optString("tree_node_type")
                if (nodeType.equals("Folder", ignoreCase = true)) {
                    android.util.Log.d("DigitalAssetParser", "Skipping Folder node at path: $currentPath")
                    // Still traverse children of folder nodes
                    traverseChildren(node, currentPath, assetNodes, position)
                    return
                }

                // Check if this node has file_id and it's not null/empty
                val fileId = node.optString("file_id")
                if (fileId.isNotEmpty() && fileId != "null") {
                    val isRiskMatrix = (position == TreePosition.RISK_MATRIX_LEVEL)
                    android.util.Log.d("DigitalAssetParser", "Found file_id: $fileId at path: $currentPath, position: $position, isRiskMatrix: $isRiskMatrix")
                    
                    // Extract node id and parent id from JSON
                    val nodeId = node.optString("id").takeIf { it.isNotEmpty() && it != "null" }
                    val parentId = node.optString("p_id").takeIf { it.isNotEmpty() && it != "null" }
                    
                    val assetNode = DigitalAssetNode(
                        fileId = fileId,
                        nodeName = node.optString("node_name").takeIf { it.isNotEmpty() }
                            ?: node.optString("name").takeIf { it.isNotEmpty() }
                            ?: node.optString("title").takeIf { it.isNotEmpty() },
                        nodePath = currentPath,
                        fileType = extractFileType(node),
                        fileSize = node.optLong("file_size").takeIf { it > 0 }
                            ?: node.optLong("size").takeIf { it > 0 },
                        rawNodeJson = node.toString(),
                        isRiskMatrix = isRiskMatrix,
                        nodeId = nodeId,
                        parentId = parentId
                    )
                    assetNodes.add(assetNode)
                } else if (fileId.isEmpty() || fileId == "null") {
                    android.util.Log.d("DigitalAssetParser", "Skipping node with null/empty file_id at path: $currentPath")
                }

                // Traverse children
                traverseChildren(node, currentPath, assetNodes, position)
            }
            is JSONArray -> {
                // Traverse each item in the array
                for (i in 0 until node.length()) {
                    val childNode = node.opt(i)
                    if (childNode != null) {
                        val childPath = "$currentPath[$i]"
                        // Determine child position based on current position and index
                        val childPosition = when (position) {
                            TreePosition.ROOT -> if (i == 0) TreePosition.FIRST_LEVEL_CHILD else TreePosition.OTHER
                            TreePosition.FIRST_LEVEL_CHILD -> if (i == 0) TreePosition.RISK_MATRIX_LEVEL else TreePosition.OTHER
                            else -> TreePosition.OTHER
                        }
                        traverseTreeNode(childNode, childPath, assetNodes, childPosition)
                    }
                }
            }
        }
    }

    /**
     * Traverse children of a JSON object.
     *
     * @param parentNode Parent JSON object
     * @param parentPath Parent path in the tree
     * @param assetNodes Mutable list to collect asset nodes
     * @param parentPosition Current position of the parent node
     */
    private fun traverseChildren(
        parentNode: JSONObject,
        parentPath: String,
        assetNodes: MutableList<DigitalAssetNode>,
        parentPosition: TreePosition
    ) {
        // Common child field names to check
        val childFieldNames = listOf("children", "items", "nodes", "files", "assets", "content")
        
        for (fieldName in childFieldNames) {
            when (val childValue = parentNode.opt(fieldName)) {
                is JSONArray -> {
                    for (i in 0 until childValue.length()) {
                        val childNode = childValue.opt(i)
                        if (childNode != null) {
                            val childPath = "$parentPath/$fieldName[$i]"
                            // Determine child position based on parent position and index
                            val childPosition = when {
                                fieldName == "children" && parentPosition == TreePosition.ROOT && i == 0 -> TreePosition.FIRST_LEVEL_CHILD
                                fieldName == "children" && parentPosition == TreePosition.FIRST_LEVEL_CHILD && i == 0 -> TreePosition.RISK_MATRIX_LEVEL
                                else -> TreePosition.OTHER
                            }
                            traverseTreeNode(childNode, childPath, assetNodes, childPosition)
                        }
                    }
                }
                is JSONObject -> {
                    val childPath = "$parentPath/$fieldName"
                    traverseTreeNode(childValue, childPath, assetNodes, TreePosition.OTHER)
                }
            }
        }

        // Also check for any other object or array fields
        val keys = parentNode.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!childFieldNames.contains(key) && key != "file_id" && key != "name" && 
                key != "title" && key != "file_size" && key != "size" && key != "type") {
                when (val value = parentNode.opt(key)) {
                    is JSONObject -> {
                        val childPath = "$parentPath/$key"
                        traverseTreeNode(value, childPath, assetNodes, TreePosition.OTHER)
                    }
                    is JSONArray -> {
                        for (i in 0 until value.length()) {
                            val childNode = value.opt(i)
                            if (childNode != null) {
                                val childPath = "$parentPath/$key[$i]"
                                traverseTreeNode(childNode, childPath, assetNodes, TreePosition.OTHER)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Extract file type from node data.
     *
     * @param node JSON object representing the node
     * @return File type string or null
     */
    private fun extractFileType(node: JSONObject): String? {
        // Try different field names for file type
        val typeFields = listOf("file_type", "type", "extension", "format", "mime_type")
        
        for (field in typeFields) {
            val type = node.optString(field)
            if (type.isNotEmpty()) {
                return type.lowercase()
            }
        }

        // Try to extract from file name
        val fileName = node.optString("name").takeIf { it.isNotEmpty() }
            ?: node.optString("title").takeIf { it.isNotEmpty() }
            ?: node.optString("file_name").takeIf { it.isNotEmpty() }

        if (fileName != null) {
            val lastDotIndex = fileName.lastIndexOf('.')
            if (lastDotIndex > 0 && lastDotIndex < fileName.length - 1) {
                return fileName.substring(lastDotIndex + 1).lowercase()
            }
        }

        return null
    }
}