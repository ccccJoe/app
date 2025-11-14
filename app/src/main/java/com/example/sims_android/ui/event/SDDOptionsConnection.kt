/**
 * File: SDDOptionsConnection.kt
 * Purpose: Provide cascader (tree) options for connection-related fields
 * Author: SIMS-Android Development Team
 *
 * Description:
 * - Converts options from sddOptions.js connectionFormRows into Kotlin OptionNode lists.
 * - Even flat lists are converted to tree format (leaf-only) to unify dropdown behavior.
 */
package com.simsapp.ui.event

/**
 * Object: SDDOptionsConnection
 * Description: Cascader options for connection section.
 */
object SDDOptionsConnection {
    /**
     * Support Arrangement - Converted from sddOptions.js connectionFormRows.support_arrangement
     * @return List<OptionNode> leaf-only nodes
     */
    val supportArrangement: List<OptionNode> = listOf(
        OptionNode("Single span"),
        OptionNode("2-way span"),
        OptionNode("Continuous span"),
        OptionNode("Cantilever"),
        OptionNode("Overhang"),
        OptionNode("Suspended"),
        OptionNode("Other support arrangement")
    )

    /**
     * Fixing Nature - Converted from sddOptions.js connectionFormRows.fixing_nature
     * @return List<OptionNode> leaf-only nodes
     */
    val fixingNature: List<OptionNode> = listOf(
        OptionNode("Single-bolt"),
        OptionNode("Bolts group (moment)"),
        OptionNode("Bolts group (shear)"),
        OptionNode("Welded (fully)"),
        OptionNode("Welded (partial)"),
        OptionNode("Clamp"),
        OptionNode("Beam-to-slab"),
        OptionNode("Beam-to-wall"),
        OptionNode("Concrete moment joint"),
        OptionNode("Isolation joint"),
        OptionNode("Construction joint"),
        OptionNode("Sliding joint"),
        OptionNode("Expansion joint"),
        OptionNode("Other fixing")
    )

    /**
     * Defect Component Function - Converted from sddOptions.js connectionFormRows.defect_component_function
     * @return List<OptionNode> leaf-only nodes
     */
    val defectComponentFunction: List<OptionNode> = listOf(
        OptionNode("Load transfer"),
        OptionNode("Fall Protection"),
        OptionNode("Barrier and risk segregation"),
        OptionNode("Equipment support"),
        OptionNode("Tie and restraint to adjacent member"),
        OptionNode("Reinforcing"),
        OptionNode("Personnel access"),
        OptionNode("Mobile equipment access"),
        OptionNode("Maintenance work platform"),
        OptionNode("Lifting or Moving"),
        OptionNode("Containment"),
        OptionNode("Ventilation"),
        OptionNode("Redundant"),
        OptionNode("Other function")
    )
}