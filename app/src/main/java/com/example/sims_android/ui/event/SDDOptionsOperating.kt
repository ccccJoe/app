/**
 * File: SDDOptionsOperating.kt
 * Purpose: Provide cascader (tree) options for operating conditions and other issues
 * Author: SIMS-Android Development Team
 *
 * Description:
 * - Converts options from sddOptions.js operatingFormRows and otherIssuesFormRows into Kotlin OptionNode lists.
 * - Flat lists are represented as leaf-only cascader lists to keep UI consistent.
 */
package com.simsapp.ui.event

/**
 * Object: SDDOptionsOperating
 * Description: Cascader options for operating conditions and other issues sections.
 */
object SDDOptionsOperating {
    /** Impact Damage options (leaf-only) */
    val impactDamage: List<OptionNode> = listOf(
        OptionNode("Regular impact"),
        OptionNode("Occasion impact"),
        OptionNode("No external impact")
    )

    /** Dynamic Effect options (leaf-only) */
    val dynamicEffect: List<OptionNode> = listOf(
        OptionNode("High vibration"),
        OptionNode("Mild vibration"),
        OptionNode("Low vibration"),
        OptionNode("No dynamic effect")
    )

    /** Wind Effect options (leaf-only) */
    val windEffect: List<OptionNode> = listOf(
        OptionNode("High wind"),
        OptionNode("Mild wind"),
        OptionNode("Low wind"),
        OptionNode("No wind effect")
    )

    /** Chemical Effect options (leaf-only) */
    val chemicalEffect: List<OptionNode> = listOf(
        OptionNode("Chemical splash attack"),
        OptionNode("Chemical constant attack"),
        OptionNode("No chemical effect")
    )

    /** Loose Object options (leaf-only) */
    val looseObject: List<OptionNode> = listOf(
        OptionNode("Expected loose object"),
        OptionNode("Unexpected loose object"),
        OptionNode("No loose object")
    )

    /** Dust & Material Spill options (leaf-only) */
    val dustMaterialSpill: List<OptionNode> = listOf(
        OptionNode("Heavy dust/spillage"),
        OptionNode("Mild dust/spillage"),
        OptionNode("Low dust/spillage"),
        OptionNode("No spillage")
    )

    /** Access to Hazard Area options (leaf-only) */
    val accessToHazardArea: List<OptionNode> = listOf(
        OptionNode("No restricted access"),
        OptionNode("Area barricaded"),
        OptionNode("Area access with permit"),
        OptionNode("No access to area")
    )

    /** Personnel Presence in Area options (leaf-only) */
    val personnelPresenceInArea: List<OptionNode> = listOf(
        OptionNode("Regular personnel present"),
        OptionNode("Occasion personnel present"),
        OptionNode("Limited personnel present"),
        OptionNode("No personnel present")
    )
}