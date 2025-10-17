/*
 * File: DigitalAssetTreeParserTest.kt
 * Description: Unit tests for DigitalAssetTreeParser to verify digital asset tree parsing functionality.
 * Author: SIMS Team
 */
package com.simsapp.utils

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * DigitalAssetTreeParserTest
 *
 * Tests for parsing project_digital_asset_tree from various JSON structures.
 */
@RunWith(RobolectricTestRunner::class)
class DigitalAssetTreeParserTest {

    /**
     * Test parsing when project_digital_asset_tree is at root level.
     */
    @Test
    fun testParseDigitalAssetTree_RootLevel() {
        val json = """
        {
            "project_digital_asset_tree": {
                "name": "root",
                "children": [
                    {
                        "file_id": "file123",
                        "name": "document.pdf",
                        "file_type": "pdf",
                        "file_size": 1024
                    },
                    {
                        "name": "folder1",
                        "children": [
                            {
                                "file_id": "file456",
                                "name": "image.jpg",
                                "file_type": "jpg",
                                "file_size": 2048
                            }
                        ]
                    }
                ]
            }
        }
        """.trimIndent()

        val result = DigitalAssetTreeParser.parseDigitalAssetTree(json)
        
        assertEquals(2, result.size)
        
        val firstAsset = result.find { it.fileId == "file123" }
        assertNotNull(firstAsset)
        assertEquals("document.pdf", firstAsset?.nodeName)
        assertEquals("pdf", firstAsset?.fileType)
        assertEquals(1024L, firstAsset?.fileSize)
        
        val secondAsset = result.find { it.fileId == "file456" }
        assertNotNull(secondAsset)
        assertEquals("image.jpg", secondAsset?.nodeName)
        assertEquals("jpg", secondAsset?.fileType)
        assertEquals(2048L, secondAsset?.fileSize)
    }

    /**
     * Test parsing when project_digital_asset_tree is inside data object.
     */
    @Test
    fun testParseDigitalAssetTree_InsideDataObject() {
        val json = """
        {
            "data": {
                "project_uid": "1234567890",
                "project_name": "Test Project",
                "project_digital_asset_tree": {
                    "name": "assets",
                    "files": [
                        {
                            "file_id": "asset001",
                            "title": "Risk Matrix",
                            "type": "json",
                            "size": 512
                        },
                        {
                            "file_id": "asset002",
                            "title": "Report Template",
                            "type": "pdf",
                            "size": 4096
                        }
                    ]
                }
            }
        }
        """.trimIndent()

        val result = DigitalAssetTreeParser.parseDigitalAssetTree(json)
        
        assertEquals(2, result.size)
        
        val riskMatrix = result.find { it.fileId == "asset001" }
        assertNotNull(riskMatrix)
        assertEquals("Risk Matrix", riskMatrix?.nodeName)
        assertEquals("json", riskMatrix?.fileType)
        assertEquals(512L, riskMatrix?.fileSize)
        
        val reportTemplate = result.find { it.fileId == "asset002" }
        assertNotNull(reportTemplate)
        assertEquals("Report Template", reportTemplate?.nodeName)
        assertEquals("pdf", reportTemplate?.fileType)
        assertEquals(4096L, reportTemplate?.fileSize)
    }

    /**
     * Test parsing when project_digital_asset_tree is an array at root level.
     */
    @Test
    fun testParseDigitalAssetTree_ArrayAtRoot() {
        val json = """
        {
            "project_digital_asset_tree": [
                {
                    "file_id": "direct001",
                    "name": "direct_file.txt",
                    "file_type": "txt"
                },
                {
                    "file_id": "direct002",
                    "name": "another_file.png",
                    "file_type": "png"
                }
            ]
        }
        """.trimIndent()

        val result = DigitalAssetTreeParser.parseDigitalAssetTree(json)
        
        assertEquals(2, result.size)
        assertTrue(result.any { it.fileId == "direct001" && it.nodeName == "direct_file.txt" })
        assertTrue(result.any { it.fileId == "direct002" && it.nodeName == "another_file.png" })
    }

    /**
     * Test parsing when no project_digital_asset_tree exists.
     */
    @Test
    fun testParseDigitalAssetTree_NoAssetTree() {
        val json = """
        {
            "data": {
                "project_uid": "1234567890",
                "project_name": "Test Project",
                "project_status": "ACTIVE"
            }
        }
        """.trimIndent()

        val result = DigitalAssetTreeParser.parseDigitalAssetTree(json)
        
        assertTrue(result.isEmpty())
    }

    /**
     * Test parsing with nested folder structure.
     */
    @Test
    fun testParseDigitalAssetTree_NestedStructure() {
        val json = """
        {
            "data": {
                "project_digital_asset_tree": {
                    "name": "project_assets",
                    "items": [
                        {
                            "name": "documents",
                            "content": [
                                {
                                    "file_id": "doc001",
                                    "name": "specification.docx",
                                    "file_type": "docx"
                                }
                            ]
                        },
                        {
                            "name": "media",
                            "assets": [
                                {
                                    "file_id": "media001",
                                    "name": "video.mp4",
                                    "file_type": "mp4",
                                    "file_size": 10485760
                                }
                            ]
                        }
                    ]
                }
            }
        }
        """.trimIndent()

        val result = DigitalAssetTreeParser.parseDigitalAssetTree(json)
        
        assertEquals(2, result.size)
        
        val docAsset = result.find { it.fileId == "doc001" }
        assertNotNull(docAsset)
        assertEquals("specification.docx", docAsset?.nodeName)
        assertEquals("docx", docAsset?.fileType)
        
        val mediaAsset = result.find { it.fileId == "media001" }
        assertNotNull(mediaAsset)
        assertEquals("video.mp4", mediaAsset?.nodeName)
        assertEquals("mp4", mediaAsset?.fileType)
        assertEquals(10485760L, mediaAsset?.fileSize)
    }

    /**
     * Test parsing with malformed JSON.
     */
    @Test
    fun testParseDigitalAssetTree_MalformedJson() {
        val json = """
        {
            "data": {
                "project_uid": "1234567890"
                "project_name": "Test Project"  // Missing comma
            }
        }
        """.trimIndent()

        val result = DigitalAssetTreeParser.parseDigitalAssetTree(json)
        
        assertTrue(result.isEmpty())
    }

    /**
     * Test parsing with empty asset tree.
     */
    @Test
    fun testParseDigitalAssetTree_EmptyAssetTree() {
        val json = """
        {
            "data": {
                "project_digital_asset_tree": {
                    "name": "empty_tree"
                }
            }
        }
        """.trimIndent()

        val result = DigitalAssetTreeParser.parseDigitalAssetTree(json)
        
        assertTrue(result.isEmpty())
    }

    /**
     * Test parsing with risk matrix and folder structure.
     */
    @Test
    fun testParseDigitalAssetTree_WithRiskMatrixAndFolders() {
        val jsonWithRiskMatrix = """
        {
            "data": {
                "project_digital_asset_tree": {
                    "children": [
                        {
                            "children": [
                                {
                                    "file_id": "risk_matrix_001",
                                    "node_name": "Risk Matrix Config",
                                    "tree_node_type": "File",
                                    "file_type": "JSON"
                                },
                                {
                                    "file_id": "doc_001",
                                    "node_name": "Document 1",
                                    "tree_node_type": "File",
                                    "file_type": "PDF"
                                }
                            ]
                        },
                        {
                            "tree_node_type": "Folder",
                            "node_name": "Documents",
                            "children": [
                                {
                                    "file_id": "doc_002",
                                    "node_name": "Document 2",
                                    "tree_node_type": "File",
                                    "file_type": "PDF"
                                }
                            ]
                        },
                        {
                            "file_id": null,
                            "node_name": "Empty Node",
                            "tree_node_type": "File"
                        },
                        {
                            "file_id": "img_001",
                            "node_name": "Image 1",
                            "tree_node_type": "File",
                            "file_type": "JPG"
                        }
                    ]
                }
            }
        }
        """.trimIndent()

        val result = DigitalAssetTreeParser.parseDigitalAssetTree(jsonWithRiskMatrix)

        // Should have 4 nodes (risk matrix + documents + image, excluding folders and null file_id)
        assertEquals("Should have exactly 4 nodes", 4, result.size)

        // Check that risk matrix node exists and is correctly identified
        val riskMatrixNode = result.find { it.fileId == "risk_matrix_001" }
        assertNotNull("Risk matrix node should exist", riskMatrixNode)
        assertEquals("Risk Matrix Config", riskMatrixNode!!.nodeName)
        assertEquals("json", riskMatrixNode.fileType)

        // Check other document nodes exist
        val docNode1 = result.find { it.fileId == "doc_001" }
        assertNotNull("Document 1 should exist", docNode1)
        assertEquals("Document 1", docNode1!!.nodeName)
        assertEquals("pdf", docNode1.fileType)

        val docNode2 = result.find { it.fileId == "doc_002" }
        assertNotNull("Document 2 should exist", docNode2)
        assertEquals("Document 2", docNode2!!.nodeName)
        assertEquals("pdf", docNode2.fileType)

        val imgNode = result.find { it.fileId == "img_001" }
        assertNotNull("Image node should exist", imgNode)
        assertEquals("Image 1", imgNode!!.nodeName)
        assertEquals("jpg", imgNode.fileType)

        // Should not contain folder or null file_id nodes
        assertFalse("Should not contain Documents folder", result.any { it.nodeName == "Documents" })
        assertFalse("Should not contain Empty Node", result.any { it.nodeName == "Empty Node" })
     }

    /**
     * Test file type extraction from file name.
     */
    @Test
    fun testExtractFileTypeFromName() {
        // Since extractFileTypeFromName is private, we test it through parseDigitalAssetTree
        val json = """
        {
            "project_digital_asset_tree": {
                "children": [
                    {
                        "file_id": "test001",
                        "name": "document.pdf"
                    },
                    {
                        "file_id": "test002",
                        "name": "image.jpg"
                    },
                    {
                        "file_id": "test003",
                        "name": "photo.PNG"
                    },
                    {
                        "file_id": "test004",
                        "name": "audio.mp3"
                    },
                    {
                        "file_id": "test005",
                        "name": "file"
                    }
                ]
            }
        }
        """.trimIndent()

        val result = DigitalAssetTreeParser.parseDigitalAssetTree(json)
        
        assertEquals(5, result.size)
        assertEquals("pdf", result.find { it.fileId == "test001" }?.fileType)
        assertEquals("jpg", result.find { it.fileId == "test002" }?.fileType)
        assertEquals("png", result.find { it.fileId == "test003" }?.fileType)
        assertEquals("mp3", result.find { it.fileId == "test004" }?.fileType)
        assertEquals(null, result.find { it.fileId == "test005" }?.fileType) // No extension
    }

    /**
     * Test parsing with file type extraction from name.
     */
    @Test
    fun testParseDigitalAssetTree_FileTypeFromName() {
        val json = """
        {
            "project_digital_asset_tree": {
                "children": [
                    {
                        "file_id": "test001",
                        "name": "report.pdf"
                    },
                    {
                        "file_id": "test002",
                        "title": "image.JPG"
                    }
                ]
            }
        }
        """.trimIndent()

        val result = DigitalAssetTreeParser.parseDigitalAssetTree(json)
        
        assertEquals(2, result.size)
        
        val pdfAsset = result.find { it.fileId == "test001" }
        assertEquals("pdf", pdfAsset?.fileType)
        
        val jpgAsset = result.find { it.fileId == "test002" }
        assertEquals("jpg", jpgAsset?.fileType)
    }
}