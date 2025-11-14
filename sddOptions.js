// Structural options
const structuralOptions = {
  // Type of Structure - Cascader options
  type_of_structure: [
    { label: "Building", value: "Building" },
    { label: "Pipe rack", value: "Pipe rack" },
    { label: "Platform", value: "Platform" },
    { label: "Walkway", value: "Walkway" },
    { label: "Stair", value: "Stair" },
    { label: "Ladder", value: "Ladder" },
    {
      label: "Services structural support",
      value: "Services structural support",
    },
    { label: "Cable tray", value: "Cable tray" },
    { label: "Equipment support", value: "Equipment support" },
    { label: "Temporary support", value: "Temporary support" },
    {
      label: "Other",
      value: "Other",
      children: [
        { label: "Monorail", value: "Monorail" },
        { label: "OHTC crane", value: "OHTC crane" },
        { label: "Bund", value: "Bund" },
        { label: "Sump", value: "Sump" },
        { label: "Road", value: "Road" },
        { label: "Bridge", value: "Bridge" },
        { label: "Culvert", value: "Culvert" },
        { label: "Fixed light tower", value: "Fixed light tower" },
      ],
    },
  ],
  // Building Material - Cascader options
  building_material: [
    { label: "Structural steel", value: "Structural steel" },
    { label: "Concrete", value: "Concrete" },
    {
      label: "Fibre Reinforced Plastic (FRP)",
      value: "Fibre Reinforced Plastic (FRP)",
    },
    { label: "Timber", value: "Timber" },
    {
      label: "Other",
      value: "Other",
      children: [
        {
          label: "Polyvinyl Chloride (PVC)",
          value: "Polyvinyl Chloride (PVC)",
        },
        {
          label: "High-Density Polyethylene (HDPE)",
          value: "High-Density Polyethylene (HDPE)",
        },
        { label: "Grout", value: "Grout" },
        {
          label: "Blockwork (concrete masonry)",
          value: "Blockwork (concrete masonry)",
        },
        { label: "Brick", value: "Brick" },
        { label: "Protection coating", value: "Protection coating" },
        { label: "Fireproofing", value: "Fireproofing" },
        { label: "Insulation", value: "Insulation" },
        { label: "Rubber", value: "Rubber" },
        { label: "Bitumen", value: "Bitumen" },
        { label: "Asphalt", value: "Asphalt" },
      ],
    },
  ],
  // Defect Location - Cascader options
  // Defect Location - Merged options (independent of building material)
  defect_location: [
    {
      label: "Steel section",
      value: "Steel section",
      children: [
        { label: "Steel flange top", value: "Steel flange top" },
        { label: "Steel flange bottom", value: "Steel flange bottom" },
        { label: "Steel wed", value: "Steel wed" },
      ],
    },
    {
      label: "Concrete",
      value: "Concrete",
      children: [
        { label: "Concrete top", value: "Concrete top" },
        { label: "Concrete bottom", value: "Concrete bottom" },
        { label: "Concrete face", value: "Concrete face" },
        { label: "Concrete Joint", value: "Concrete Joint" },
      ],
    },
  ],
  // Defect Component - Cascader options
  defect_component: [
    {
      label: "Column",
      value: "Column",
      children: [
        { label: "Column (single)", value: "Column (single)" },
        { label: "Column (internal)", value: "Column (internal)" },
        { label: "Column (external)", value: "Column (external)" },
      ],
    },
    {
      label: "Beam",
      value: "Beam",
      children: [
        { label: "Beam (single)", value: "Beam (single)" },
        { label: "Beam (internal)", value: "Beam (internal)" },
        { label: "Beam (edge)", value: "Beam (edge)" },
        {
          label: "Purlin or girt (internal)",
          value: "Purlin or girt (internal)",
        },
        { label: "Purlin or girt (end)", value: "Purlin or girt (end)" },
      ],
    },
    {
      label: "Plate",
      value: "Plate",
      children: [
        { label: "Wed side plate", value: "Wed side plate" },
        { label: "Doubler plate", value: "Doubler plate" },
        { label: "End plate", value: "End plate" },
        { label: "Stiffener plate", value: "Stiffener plate" },
        { label: "Reinforcing plate", value: "Reinforcing plate" },
        { label: "Cap plate", value: "Cap plate" },
      ],
    },
    {
      label: "Crane",
      value: "Crane",
      children: [
        { label: "Monorail", value: "Monorail" },
        { label: "Crane rail", value: "Crane rail" },
        { label: "Crane runway", value: "Crane runway" },
      ],
    },
    {
      label: "Bracing",
      value: "Bracing",
      children: [
        { label: "Vertical bracing", value: "Vertical bracing" },
        { label: "Horizontal bracing", value: "Horizontal bracing" },
      ],
    },
    {
      label: "Wall",
      value: "Wall",
      children: [
        { label: "Wall (internal)", value: "Wall (internal)" },
        { label: "Wall (end)", value: "Wall (end)" },
      ],
    },
    { label: "Pedestals/plinth", value: "Pedestals/plinth" },
    { label: "Footing", value: "Footing" },
    {
      label: "Ladder",
      value: "Ladder",
      children: [
        { label: "Ladder stile", value: "Ladder stile" },
        { label: "Ladder rung", value: "Ladder rung" },
        { label: "Ladder cage", value: "Ladder cage" },
      ],
    },
    {
      label: "Flooring",
      value: "Flooring",
      children: [
        { label: "Grating", value: "Grating" },
        { label: "Stair tread", value: "Stair tread" },
        { label: "Floorplate", value: "Floorplate" },
        { label: "Suspended slab", value: "Suspended slab" },
        { label: "Ground slab (floating)", value: "Ground slab (floating)" },
      ],
    },
    { label: "Stair stringer", value: "Stair stringer" },
    { label: "Suspended rod", value: "Suspended rod" },
    {
      label: "Connection",
      value: "Connection",
      children: [
        { label: "Lifting or anchor lug", value: "Lifting or anchor lug" },
        { label: "Bolts and nut", value: "Bolts and nut" },
        { label: "Baseplate", value: "Baseplate" },
        {
          label: "Anchor or hold-down bolt",
          value: "Anchor or hold-down bolt",
        },
        { label: "Screw", value: "Screw" },
        { label: "Bracket", value: "Bracket" },
      ],
    },
    {
      label: "Protection & Safety",
      value: "Protection & Safety",
      children: [
        { label: "Bollard or barrier", value: "Bollard or barrier" },
        { label: "Handrail", value: "Handrail" },
        { label: "Guardrail", value: "Guardrail" },
        { label: "Guardmesh", value: "Guardmesh" },
        { label: "Kickplate", value: "Kickplate" },
        { label: "Steel roof sheet", value: "Steel roof sheet" },
        { label: "Steel wall cladding", value: "Steel wall cladding" },
        { label: "Doorway or gate", value: "Doorway or gate" },
      ],
    },
    { label: "Louvers", value: "Louvers" },
  ],

  // Structure Location - Cascader options
  structure_location: [
    {
      label: "Enclosed environment",
      value: "Enclosed environment",
      children: [
        { label: "Enclosed dry", value: "Enclosed dry" },
        { label: "Enclosed moisture", value: "Enclosed moisture" },
      ],
    },
    {
      label: "Outdoor environment",
      value: "Outdoor environment",
      children: [
        { label: "Outdoor dry", value: "Outdoor dry" },
        { label: "Outdoor wet", value: "Outdoor wet" },
      ],
    },
    { label: "Underground", value: "Underground" },
    { label: "Marine", value: "Marine" },
    { label: "Underwater (fresh)", value: "Underwater (fresh)" },
    { label: "Underwater (salt)", value: "Underwater (salt)" },
  ],
  // Defect on Component - Cascader options
  defect_on_component: [
    {
      label: "Member",
      value: "Member",
      children: [
        { label: "End (load-path)", value: "End (load-path)" },
        { label: "End (free)", value: "End (free)" },
        { label: "Midspan", value: "Midspan" },
        { label: "Connection", value: "Connection" },
      ],
    },
    {
      label: "Panel",
      value: "Panel",
      children: [
        { label: "Midspan", value: "Midspan" },
        { label: "Bearing end", value: "Bearing end" },
        { label: "Connection", value: "Connection" },
      ],
    },
    {
      label: "Handrail",
      value: "Handrail",
      children: [
        { label: "Top rail", value: "Top rail" },
        { label: "Knee rail", value: "Knee rail" },
        { label: "Stanchion (top ball)", value: "Stanchion (top ball)" },
        { label: "Stanchion (knee ball)", value: "Stanchion (knee ball)" },
        { label: "Stanchion base", value: "Stanchion base" },
        { label: "Kickplate", value: "Kickplate" },
        { label: "Kickplate joint", value: "Kickplate joint" },
      ],
    },
    {
      label: "Grating",
      value: "Grating",
      children: [
        { label: "Load bar (bearing end)", value: "Load bar (bearing end)" },
        { label: "Load bar (span)", value: "Load bar (span)" },
        { label: "Spread bar", value: "Spread bar" },
        { label: "Fixing", value: "Fixing" },
      ],
    },
    {
      label: "Other",
      value: "Other",
      children: [
        { label: "Defect on Component", value: "Defect on Component" },
      ],
    },
  ],
  // Position of Defect Component - Cascader options (multiple)
  position_of_defect_component: [
    {
      label: "Above ground",
      value: "Above ground",
      children: [
        { label: "< 3m above ground", value: "< 3m above ground" },
        { label: "> 3m above ground", value: "> 3m above ground" },
      ],
    },
    {
      label: "Below ground",
      value: "Below ground",
      children: [
        { label: "< 2m below ground", value: "< 2m below ground" },
        { label: "> 2m below ground", value: "> 2m below ground" },
      ],
    },
    { label: "Near edge", value: "Near edge" },
    { label: "Away from edge", value: "Away from edge" },
  ],
  // Structural Concerns - Cascader options
  structural_concerns: [
    { label: "Non-compliant", value: "Non-compliant" },
    { label: "Inadequate", value: "Inadequate" },
    { label: "Overloaded", value: "Overloaded" },
    { label: "Impact damage", value: "Impact damage" },
    {
      label: "Concrete",
      value: "Concrete",
      children: [
        {
          label: "Concrete cracks (shrinkage)",
          value: "Concrete cracks (shrinkage)",
        },
        {
          label: "Concrete cracks (movement)",
          value: "Concrete cracks (movement)",
        },
        { label: "Concrete spalling", value: "Concrete spalling" },
        { label: "Concrete delamination", value: "Concrete delamination" },
        { label: "Concrete erosion", value: "Concrete erosion" },
        { label: "Concrete cover loss", value: "Concrete cover loss" },
        { label: "Sealant damage", value: "Sealant damage" },
      ],
    },
    {
      label: "Steel",
      value: "Steel",
      children: [
        { label: "Steel corrosion", value: "Steel corrosion" },
        { label: "Steel deformation", value: "Steel deformation" },
        { label: "Steel missing", value: "Steel missing" },
        {
          label: "Steel detached from support",
          value: "Steel detached from support",
        },
        { label: "Steel cut-out", value: "Steel cut-out" },
        { label: "Steel fatigue crack", value: "Steel fatigue crack" },
        { label: "Steel misalignment", value: "Steel misalignment" },
      ],
    },
    {
      label: "Steel bolt & nut",
      value: "Steel bolt & nut",
      children: [
        { label: "Bolt/nut corrosion", value: "Bolt/nut corrosion" },
        { label: "Bolt/nut loose", value: "Bolt/nut loose" },
        { label: "Missing bolt", value: "Missing bolt" },
        { label: "Missing nut", value: "Missing nut" },
      ],
    },
    {
      label: "Weld",
      value: "Weld",
      children: [
        { label: "Weld crack", value: "Weld crack" },
        { label: "Weld corroded", value: "Weld corroded" },
      ],
    },
    { label: "Grout missing or damaged", value: "Grout missing or damaged" },
  ],
  // Defect Component Loading - Cascader options
  defect_component_loading: [
    {
      label: "Load bearing",
      value: "Load bearing",
      children: [
        {
          label: "High load bearing (static)",
          value: "High load bearing (static)",
        },
        {
          label: "Moderate load bearing (static)",
          value: "Moderate load bearing (static)",
        },
        {
          label: "Minor load bearing (static)",
          value: "Minor load bearing (static)",
        },
      ],
    },
    {
      label: "Pipe rack",
      value: "Pipe rack",
      children: [
        { label: "Full width pipes loaing", value: "Full width pipes loaing" },
        { label: "Half width pipes loaing", value: "Half width pipes loaing" },
        {
          label: "Minor width pipes loaing",
          value: "Minor width pipes loaing",
        },
      ],
    },
    {
      label: "Cable rack",
      value: "Cable rack",
      children: [
        {
          label: "Full width cables loaing",
          value: "Full width cables loaing",
        },
        {
          label: "Half width cables loaing",
          value: "Half width cables loaing",
        },
        {
          label: "Minor width cables loaing",
          value: "Minor width cables loaing",
        },
      ],
    },
    { label: "No load on component", value: "No load on component" },
  ],
  // Deformation - Cascader options
  deformation: [
    {
      label: "Steel member deformation",
      value: "Steel member deformation",
      children: [
        {
          label: "minor deformed (local of)",
          value: "minor deformed (local of)",
        },
        { label: "medium deformed (1% v)", value: "medium deformed (1% v)" },
        { label: "extreme deformed (>1%)", value: "extreme deformed (>1%)" },
      ],
    },
  ],
  defect_extent_type: ["Point", "Linear", "Area"],
};

// Defect Severity options - always show all options regardless of Structural Concerns
const defectSeverityOptions = computed(() => [
  {
    label: "Concrete cracks",
    value: "Concrete cracks",
    children: [
      {
        label: "Concrete crack <0.03 mm (as new)",
        value: "Concrete crack <0.03 mm (as new)",
      },
      { label: "0.03 to 0.3 mm (good)", value: "0.03 to 0.3 mm (good)" },
      { label: "0.3 to 1.5 mm (fair)", value: "0.3 to 1.5 mm (fair)" },
      { label: "1.5 to 3 mm (poor)", value: "1.5 to 3 mm (poor)" },
      { label: "> 3mm (very bad)", value: "> 3mm (very bad)" },
    ],
  },
  {
    label: "Concrete spalling",
    value: "Concrete spalling",
    children: [
      {
        label: "<5% component surface (fair)",
        value: "<5% component surface (fair)",
      },
      {
        label: "5 - 20% component surface (poor)",
        value: "5 - 20% component surface (poor)",
      },
      {
        label: "> 20% component surface (very bad)",
        value: "> 20% component surface (very bad)",
      },
    ],
  },
  {
    label: "Concrete delamination",
    value: "Concrete delamination",
    children: [
      {
        label: "<10% component surface (good)",
        value: "<10% component surface (good)",
      },
      {
        label: "10 - 20% component surface (fair)",
        value: "10 - 20% component surface (fair)",
      },
      {
        label: "20 - 50% component surface (poor)",
        value: "20 - 50% component surface (poor)",
      },
      {
        label: "> 50% component surface (very bad)",
        value: "> 50% component surface (very bad)",
      },
    ],
  },
  {
    label: "Concrete erosion",
    value: "Concrete erosion",
    children: [
      { label: "<5 mm depth (fair)", value: "<5 mm depth (fair)" },
      { label: "5 - 20 mm depth (poor)", value: "5 - 20 mm depth (poor)" },
      { label: "> 20 mm deep (very bad)", value: "> 20 mm deep (very bad)" },
    ],
  },
  {
    label: "Concrete cover loss / Exposed Reo",
    value: "Concrete cover loss / Exposed Reo",
    children: [
      {
        label: "cover loss <20mm, no exposed Reo (good)",
        value: "cover loss <20mm, no exposed Reo (good)",
      },
      {
        label: "exposed Reo with no steel loss (fair)",
        value: "exposed Reo with no steel loss (fair)",
      },
      {
        label: "exposed Reo with minor steel loss (poor)",
        value: "exposed Reo with minor steel loss (poor)",
      },
      {
        label: "exposed Reo with significant steel loss (very bad)",
        value: "exposed Reo with significant steel loss (very bad)",
      },
    ],
  },
  {
    label: "Steel corrosion",
    value: "Steel corrosion",
    children: [
      {
        label: "C1 (loss < 0.1 mm thk 或 < 5% cross cover)",
        value: "C1 (loss < 0.1 mm thk 或 < 5% cross cover)",
      },
      {
        label: "C2 (loss 0.1 - 0.5 mm thk 或 5 - 10% cross cover)",
        value: "C2 (loss 0.1 - 0.5 mm thk 或 5 - 10% cross cover)",
      },
      {
        label: "C3 (loss 0.5 - 2 mm thk 或 10 - 50% cross cover)",
        value: "C3 (loss 0.5 - 2 mm thk 或 10 - 50% cross cover)",
      },
      {
        label: "C4 (loss 2 - 5 mm thk 或 50 - 100% cross cover)",
        value: "C4 (loss 2 - 5 mm thk 或 50 - 100% cross cover)",
      },
      {
        label: "C5 (> 5 mm thk loss 或 penetrate)",
        value: "C5 (> 5 mm thk loss 或 penetrate)",
      },
    ],
  },
  {
    label: "Steel cut-out",
    value: "Steel cut-out",
    children: [
      {
        label: "excessive (> 30% section section)",
        value: "excessive (> 30% section section)",
      },
      {
        label: "large (15 - 30% section area)",
        value: "large (15 - 30% section area)",
      },
      {
        label: "mild (up to 15% section area)",
        value: "mild (up to 15% section area)",
      },
    ],
  },
  {
    label: "Bolt corrosion",
    value: "Bolt corrosion",
    children: [
      { label: "C1 (surface rust)", value: "C1 (surface rust)" },
      {
        label: "C2 (minor steel reduction)",
        value: "C2 (minor steel reduction)",
      },
      {
        label: "C3 (moderate steel reduction and rust flakes)",
        value: "C3 (moderate steel reduction and rust flakes)",
      },
      {
        label: "C4 (high steel reduction and threads worn out)",
        value: "C4 (high steel reduction and threads worn out)",
      },
      {
        label: "C5 (significant size reduction and loss function)",
        value: "C5 (significant size reduction and loss function)",
      },
    ],
  },
  {
    label: "Sealant",
    value: "Sealant",
    children: [
      { label: "missing (throughout)", value: "missing (throughout)" },
      {
        label: "moderate damage (partial damaged > 10% of width)",
        value: "moderate damage (partial damaged > 10% of width)",
      },
      {
        label: "minor (crack or peeling ~ 10% of width)",
        value: "minor (crack or peeling ~ 10% of width)",
      },
    ],
  },
]);

const connectionFormRows = [
  {
    fields: [
      {
        key: "support_arrangement",
        label: "Support Arrangement",
        type: "select",
        placeholder: "Select",
        options: [
          "Single span",
          "2-way span",
          "Continuous span",
          "Cantilever",
          "Overhang",
          "Suspended",
          "Other support arrangement",
        ],
      },
      {
        key: "fixing_nature",
        label: "Fixing Nature",
        type: "select",
        placeholder: "Select",
        options: [
          "Single-bolt",
          "Bolts group (moment)",
          "Bolts group (shear)",
          "Welded (fully)",
          "Welded (partial)",
          "Clamp",
          "Beam-to-slab",
          "Beam-to-wall",
          "Concrete moment joint",
          "Isolation joint",
          "Construction joint",
          "Sliding joint",
          "Expansion joint",
          "Other fixing",
        ],
      },
    ],
  },
  {
    fields: [
      {
        key: "defect_component_function",
        label: "Defect Component Function",
        type: "select",
        placeholder: "Select",
        options: [
          "Load transfer",
          "Fall Protection",
          "Barrier and risk segregation",
          "Equipment support",
          "Tie and restraint to adjacent member",
          "Reinforcing",
          "Personnel access",
          "Mobile equipment access",
          "Maintenance work platform",
          "Lifting or Moving",
          "Containment",
          "Ventilation",
          "Redundant",
          "Other function",
        ],
      },
    ],
  },
];

const operatingFormRows = [
  {
    fields: [
      {
        key: "structure_location",
        label: "Structure Location",
        type: "cascader",
        placeholder: "Select",
        options: structuralOptions.structure_location,
      },
      {
        key: "defect_component_loading",
        label: "Defect Component Loading",
        type: "cascader",
        placeholder: "Select",
        options: structuralOptions.defect_component_loading,
      },
    ],
  },
  {
    fields: [
      {
        key: "impact_damage",
        label: "Impact Damage",
        type: "select",
        placeholder: "Select",
        options: ["Regular impact", "Occasion impact", "No external impact"],
      },
      {
        key: "dynamic_effect",
        label: "Dynamic Effect",
        type: "select",
        placeholder: "Select",
        options: [
          "High vibration",
          "Mild vibration",
          "Low vibration",
          "No dynamic effect",
        ],
      },
    ],
  },
  {
    fields: [
      {
        key: "wind_effect",
        label: "Wind Effect",
        type: "select",
        placeholder: "Select",
        options: ["High wind", "Mild wind", "Low wind", "No wind effect"],
      },
      {
        key: "chemical_effect",
        label: "Chemical Effect",
        type: "select",
        placeholder: "Select",
        options: [
          "Chemical splash attack",
          "Chemical constant attack",
          "No chemical effect",
        ],
      },
    ],
  },
];

// Other Issues
const otherIssuesFormRows = [
  {
    fields: [
      {
        key: "loose_object",
        label: "Loose Object",
        type: "select",
        placeholder: "Select",
        options: [
          "Expected loose object",
          "Unexpected loose object",
          "No loose object",
        ],
      },
      {
        key: "dust_material_spill",
        label: "Dust & Material Spill",
        type: "select",
        placeholder: "Select",
        options: [
          "Heavy dust/spillage",
          "Mild dust/spillage",
          "Low dust/spillage",
          "No spillage",
        ],
      },
    ],
  },
  {
    fields: [
      {
        key: "access_to_hazard_area",
        label: "Access to Hazard Area",
        type: "select",
        placeholder: "Select",
        options: [
          "No restricted access",
          "Area barricaded",
          "Area access with permit",
          "No access to area",
        ],
      },
      {
        key: "personnel_presence_in_area",
        label: "Personnel Presence in Area",
        type: "select",
        placeholder: "Select",
        options: [
          "Regular personnel present",
          "Occasion personnel present",
          "Limited personnel present",
          "No personnel present",
        ],
      },
    ],
  },
];
