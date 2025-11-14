/*
 * File: DigitalAssetItem.kt
 * Description: Data class representing a digital asset item with file ID and name.
 * Author: SIMS Team
 */
package com.simsapp.data.local.entity

/**
 * DigitalAssetItem
 *
 * Represents a digital asset with its identifiers and display name.
 * Used in EventEntity to store selected digital assets information.
 *
 * @property fileId The unique file identifier for the digital asset
 * @property fileName The display name of the digital asset file
 * @property nodeId The unique node identifier in the asset tree (optional for backward compatibility)
 */
data class DigitalAssetItem(
    /** Unique file identifier for the digital asset. */
    val fileId: String,
    /** Display name of the digital asset file. */
    val fileName: String,
    /** Unique node identifier in the asset tree; used for precise selection recall. */
    val nodeId: String? = null
)