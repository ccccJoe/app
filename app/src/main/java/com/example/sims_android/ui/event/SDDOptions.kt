/**
 * File: SDDOptions.kt
 * Purpose: Provide cascader (tree) options for Structural Defect Details based on sddOptions.js.
 * Author: SIMS-Android Development Team
 *
 * Description:
 * - Mirrors the structure of d:\Project\SIMS-Android\sddOptions.js in Kotlin.
 * - Supplies OptionNode lists for dropdown tree components across the Structural Defect form.
 */
package com.simsapp.ui.event

/**
 * Object: SDDOptions
 * Description: Static cascader options translated from sddOptions.js.
 * These are consumed by form field definitions via `treeOptions`.
 */
object SDDOptions {
    // Type of Structure - Cascader options
    val typeOfStructure: List<OptionNode> = listOf(
        OptionNode("Building"),
        OptionNode("Pipe rack"),
        OptionNode("Platform"),
        OptionNode("Walkway"),
        OptionNode("Stair"),
        OptionNode("Ladder"),
        OptionNode("Services structural support"),
        OptionNode("Cable tray"),
        OptionNode("Equipment support"),
        OptionNode("Temporary support"),
        OptionNode(
            label = "Other",
            children = listOf(
                OptionNode("Monorail"),
                OptionNode("OHTC crane"),
                OptionNode("Bund"),
                OptionNode("Sump"),
                OptionNode("Road"),
                OptionNode("Bridge"),
                OptionNode("Culvert"),
                OptionNode("Fixed light tower")
            )
        )
    )

    // Building Material - Cascader options
    val buildingMaterial: List<OptionNode> = listOf(
        OptionNode("Structural steel"),
        OptionNode("Concrete"),
        OptionNode("Fibre Reinforced Plastic (FRP)"),
        OptionNode("Timber"),
        OptionNode(
            label = "Other",
            children = listOf(
                OptionNode("Polyvinyl Chloride (PVC)"),
                OptionNode("High-Density Polyethylene (HDPE)"),
                OptionNode("Grout"),
                OptionNode("Blockwork (concrete masonry)"),
                OptionNode("Brick"),
                OptionNode("Protection coating"),
                OptionNode("Fireproofing"),
                OptionNode("Insulation"),
                OptionNode("Rubber"),
                OptionNode("Bitumen"),
                OptionNode("Asphalt")
            )
        )
    )

    // Defect Location - Cascader options
    val defectLocation: List<OptionNode> = listOf(
        OptionNode(
            label = "Steel section",
            children = listOf(
                OptionNode("Steel flange top"),
                OptionNode("Steel flange bottom"),
                OptionNode("Steel wed")
            )
        ),
        OptionNode(
            label = "Concrete",
            children = listOf(
                OptionNode("Concrete top"),
                OptionNode("Concrete bottom"),
                OptionNode("Concrete face"),
                OptionNode("Concrete Joint")
            )
        )
    )

    // Defect Component - Cascader options
    val defectComponent: List<OptionNode> = listOf(
        OptionNode(
            label = "Column",
            children = listOf(
                OptionNode("Column (single)"),
                OptionNode("Column (internal)"),
                OptionNode("Column (external)")
            )
        ),
        OptionNode(
            label = "Beam",
            children = listOf(
                OptionNode("Beam (single)"),
                OptionNode("Beam (internal)"),
                OptionNode("Beam (edge)"),
                OptionNode("Purlin or girt (internal)"),
                OptionNode("Purlin or girt (end)")
            )
        ),
        OptionNode(
            label = "Plate",
            children = listOf(
                OptionNode("Wed side plate"),
                OptionNode("Doubler plate"),
                OptionNode("End plate"),
                OptionNode("Stiffener plate"),
                OptionNode("Reinforcing plate"),
                OptionNode("Cap plate")
            )
        ),
        OptionNode(
            label = "Crane",
            children = listOf(
                OptionNode("Monorail"),
                OptionNode("Crane rail"),
                OptionNode("Crane runway")
            )
        ),
        OptionNode(
            label = "Bracing",
            children = listOf(
                OptionNode("Vertical bracing"),
                OptionNode("Horizontal bracing")
            )
        ),
        OptionNode(
            label = "Wall",
            children = listOf(
                OptionNode("Wall (internal)"),
                OptionNode("Wall (end)")
            )
        ),
        OptionNode("Pedestals/plinth"),
        OptionNode("Footing"),
        OptionNode(
            label = "Ladder",
            children = listOf(
                OptionNode("Ladder stile"),
                OptionNode("Ladder rung"),
                OptionNode("Ladder cage")
            )
        ),
        OptionNode(
            label = "Flooring",
            children = listOf(
                OptionNode("Grating"),
                OptionNode("Stair tread"),
                OptionNode("Floorplate"),
                OptionNode("Suspended slab"),
                OptionNode("Ground slab (floating)")
            )
        ),
        OptionNode("Stair stringer"),
        OptionNode("Suspended rod"),
        OptionNode(
            label = "Connection",
            children = listOf(
                OptionNode("Lifting or anchor lug"),
                OptionNode("Bolts and nut"),
                OptionNode("Baseplate"),
                OptionNode("Anchor or hold-down bolt"),
                OptionNode("Screw"),
                OptionNode("Bracket")
            )
        ),
        OptionNode(
            label = "Protection & Safety",
            children = listOf(
                OptionNode("Bollard or barrier"),
                OptionNode("Handrail"),
                OptionNode("Guardrail"),
                OptionNode("Guardmesh"),
                OptionNode("Kickplate"),
                OptionNode("Steel roof sheet"),
                OptionNode("Steel wall cladding"),
                OptionNode("Doorway or gate")
            )
        ),
        OptionNode("Louvers")
    )

    // Structure Location - Cascader options
    val structureLocation: List<OptionNode> = listOf(
        OptionNode(
            label = "Enclosed environment",
            children = listOf(
                OptionNode("Enclosed dry"),
                OptionNode("Enclosed moisture")
            )
        ),
        OptionNode(
            label = "Outdoor environment",
            children = listOf(
                OptionNode("Outdoor dry"),
                OptionNode("Outdoor wet")
            )
        ),
        OptionNode("Underground"),
        OptionNode("Marine"),
        OptionNode("Underwater (fresh)"),
        OptionNode("Underwater (salt)")
    )

    // Defect on Component - Cascader options
    val defectOnComponent: List<OptionNode> = listOf(
        OptionNode(
            label = "Member",
            children = listOf(
                OptionNode("End (load-path)"),
                OptionNode("End (free)"),
                OptionNode("Midspan"),
                OptionNode("Connection")
            )
        ),
        OptionNode(
            label = "Panel",
            children = listOf(
                OptionNode("Midspan"),
                OptionNode("Bearing end"),
                OptionNode("Connection")
            )
        ),
        OptionNode(
            label = "Handrail",
            children = listOf(
                OptionNode("Top rail"),
                OptionNode("Knee rail"),
                OptionNode("Stanchion (top ball)"),
                OptionNode("Stanchion (knee ball)"),
                OptionNode("Stanchion base"),
                OptionNode("Kickplate"),
                OptionNode("Kickplate joint")
            )
        ),
        OptionNode(
            label = "Grating",
            children = listOf(
                OptionNode("Load bar (bearing end)"),
                OptionNode("Load bar (span)"),
                OptionNode("Spread bar"),
                OptionNode("Fixing")
            )
        ),
        OptionNode(
            label = "Other",
            children = listOf(
                OptionNode("Defect on Component")
            )
        )
    )

    // Position of Defect Component - Cascader options
    val positionOfDefectComponent: List<OptionNode> = listOf(
        OptionNode(
            label = "Above ground",
            children = listOf(
                OptionNode("< 3m above ground"),
                OptionNode("> 3m above ground")
            )
        ),
        OptionNode(
            label = "Below ground",
            children = listOf(
                OptionNode("< 2m below ground"),
                OptionNode("> 2m below ground")
            )
        ),
        OptionNode("Near edge"),
        OptionNode("Away from edge")
    )

    // Structural Concerns - Cascader options
    val structuralConcerns: List<OptionNode> = listOf(
        OptionNode("Non-compliant"),
        OptionNode("Inadequate"),
        OptionNode("Overloaded"),
        OptionNode("Impact damage"),
        OptionNode(
            label = "Concrete",
            children = listOf(
                OptionNode("Concrete cracks (shrinkage)"),
                OptionNode("Concrete cracks (movement)"),
                OptionNode("Concrete spalling"),
                OptionNode("Concrete delamination"),
                OptionNode("Concrete erosion"),
                OptionNode("Concrete cover loss"),
                OptionNode("Sealant damage")
            )
        ),
        OptionNode(
            label = "Steel",
            children = listOf(
                OptionNode("Steel corrosion"),
                OptionNode("Steel deformation"),
                OptionNode("Steel missing"),
                OptionNode("Steel detached from support"),
                OptionNode("Steel cut-out"),
                OptionNode("Steel fatigue crack"),
                OptionNode("Steel misalignment")
            )
        ),
        OptionNode(
            label = "Steel bolt & nut",
            children = listOf(
                OptionNode("Bolt/nut corrosion"),
                OptionNode("Bolt/nut loose"),
                OptionNode("Missing bolt"),
                OptionNode("Missing nut")
            )
        ),
        OptionNode(
            label = "Weld",
            children = listOf(
                OptionNode("Weld crack"),
                OptionNode("Weld corroded")
            )
        ),
        OptionNode("Grout missing or damaged")
    )

    // Defect Component Loading - Cascader options
    val defectComponentLoading: List<OptionNode> = listOf(
        OptionNode(
            label = "Load bearing",
            children = listOf(
                OptionNode("High load bearing (static)"),
                OptionNode("Moderate load bearing (static)"),
                OptionNode("Minor load bearing (static)")
            )
        ),
        OptionNode(
            label = "Pipe rack",
            children = listOf(
                OptionNode("Full width pipes loaing"),
                OptionNode("Half width pipes loaing"),
                OptionNode("Minor width pipes loaing")
            )
        ),
        OptionNode(
            label = "Cable rack",
            children = listOf(
                OptionNode("Full width cables loaing"),
                OptionNode("Half width cables loaing"),
                OptionNode("Minor width cables loaing")
            )
        ),
        OptionNode("No load on component")
    )

    // Deformation - Cascader options
    val deformation: List<OptionNode> = listOf(
        OptionNode(
            label = "Steel member deformation",
            children = listOf(
                OptionNode("minor deformed (local of)"),
                OptionNode("medium deformed (1% v)"),
                OptionNode("extreme deformed (>1%)")
            )
        )
    )

    // Defect Severity - Cascader options (always show all)
    val defectSeverity: List<OptionNode> = listOf(
        OptionNode(
            label = "Concrete cracks",
            children = listOf(
                OptionNode("Concrete crack <0.03 mm (as new)"),
                OptionNode("0.03 to 0.3 mm (good)"),
                OptionNode("0.3 to 1.5 mm (fair)"),
                OptionNode("1.5 to 3 mm (poor)"),
                OptionNode("> 3mm (very bad)")
            )
        ),
        OptionNode(
            label = "Concrete spalling",
            children = listOf(
                OptionNode("<5% component surface (fair)"),
                OptionNode("5 - 20% component surface (poor)"),
                OptionNode("> 20% component surface (very bad)")
            )
        ),
        OptionNode(
            label = "Concrete delamination",
            children = listOf(
                OptionNode("<10% component surface (good)"),
                OptionNode("10 - 20% component surface (fair)"),
                OptionNode("20 - 50% component surface (poor)"),
                OptionNode("> 50% component surface (very bad)")
            )
        ),
        OptionNode(
            label = "Concrete erosion",
            children = listOf(
                OptionNode("<5 mm depth (fair)"),
                OptionNode("5 - 20 mm depth (poor)"),
                OptionNode("> 20 mm deep (very bad)")
            )
        ),
        OptionNode(
            label = "Concrete cover loss / Exposed Reo",
            children = listOf(
                OptionNode("cover loss <20mm, no exposed Reo (good)"),
                OptionNode("exposed Reo with no steel loss (fair)"),
                OptionNode("exposed Reo with minor steel loss (poor)"),
                OptionNode("exposed Reo with significant steel loss (very bad)")
            )
        ),
        OptionNode(
            label = "Steel corrosion",
            children = listOf(
                OptionNode("C1 (loss < 0.1 mm thk 或 < 5% cross cover)"),
                OptionNode("C2 (loss 0.1 - 0.5 mm thk 或 5 - 10% cross cover)"),
                OptionNode("C3 (loss 0.5 - 2 mm thk 或 10 - 50% cross cover)"),
                OptionNode("C4 (loss 2 - 5 mm thk 或 50 - 100% cross cover)"),
                OptionNode("C5 (> 5 mm thk loss 或 penetrate)")
            )
        ),
        OptionNode(
            label = "Steel cut-out",
            children = listOf(
                OptionNode("excessive (> 30% section section)"),
                OptionNode("large (15 - 30% section area)"),
                OptionNode("mild (up to 15% section area)")
            )
        ),
        OptionNode(
            label = "Bolt corrosion",
            children = listOf(
                OptionNode("C1 (surface rust)"),
                OptionNode("C2 (minor steel reduction)"),
                OptionNode("C3 (moderate steel reduction and rust flakes)"),
                OptionNode("C4 (high steel reduction and threads worn out)"),
                OptionNode("C5 (significant size reduction and loss function)")
            )
        ),
        OptionNode(
            label = "Sealant",
            children = listOf(
                OptionNode("missing (throughout)"),
                OptionNode("moderate damage (partial damaged > 10% of width)"),
                OptionNode("minor (crack or peeling ~ 10% of width)")
            )
        )
    )
}