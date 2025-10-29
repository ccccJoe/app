/**
 * File: StructuralDefectWizard.kt
 * Purpose: Structural Defect Details wizard component for event form
 * Author: SIMS-Android Development Team
 * 
 * Description:
 * - Provides a step-by-step wizard for collecting structural defect details
 * - Similar to RiskAssessmentWizard with navigation between steps
 * - Collects information about structure identification, defect description, connection, and environmental effects
 */

package com.simsapp.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 结构缺陷详情数据模型
 * 基于Vue3前端配置的完整表单数据结构
 */
data class StructuralDefectData(
    // Structure & Location Identification
    val typeOfStructure: String = "",
    val buildingMaterial: String = "",
    val defectComponent: String = "",
    val defectLocation: String = "",
    val defectOnComponent: String = "",
    val positionOfDefectComponent: String = "",
    val componentProfile: String = "",
    
    // Defect Description
    val defectType: String = "",
    val defectSeverity: String = "",
    val defectDescription: String = "",
    // 新增字段以匹配向导步骤2
    val structuralConcerns: String = "",
    val deformation: String = "",
    
    // Connection
    val supportArrangement: String = "",
    val fixingNature: String = "",
    val defectComponentFunction: String = "",
    
    // Operating Conditions & Environmental Effects
    val structureLocation: String = "",
    val defectComponentLoading: String = "",
    val impactDamage: String = "",
    val dynamicEffect: String = "",
    val windEffect: String = "",
    val chemicalEffect: String = "",
    
    // Other Issues
    val looseObject: String = "",
    val dustMaterialSpill: String = "",
    val accessToHazardArea: String = "",
    val personnelPresenceInArea: String = "",
    
    // Defect Extent (added to step 2)
    val defectExtentType: String = "",
    val defectQuantity: String = ""
)

/**
 * 结构缺陷详情表单步骤定义
 * 基于Vue3前端的五个主要步骤
 */
enum class StructuralDefectStep(val title: String, val description: String) {
    STRUCTURE_LOCATION("Structure & Location Identification", "Identify structure type, material and defect location"),
    DEFECT_DESCRIPTION("Defect Description", "Describe structural concerns, severity and extent"),
    CONNECTION("Connection", "Define support arrangement and fixing details"),
    OPERATING_CONDITIONS("Operating Conditions & Environmental Effects", "Specify environmental conditions"),
    OTHER_ISSUES("Other Issues", "Additional safety and operational concerns")
}

/**
 * 表单字段定义
 * 基于Vue3配置的完整字段结构
 */
data class StructuralDefectField(
    val key: String,
    val label: String,
    val type: String, // "select" or "input"
    val placeholder: String,
    val options: List<String> = emptyList(),
    val span: Int = 12 // 布局跨度，12为半宽，24为全宽
)

enum class FieldType {
    DROPDOWN,
    TEXT_INPUT,
    MULTILINE_TEXT
}

/**
 * 获取指定步骤的表单字段配置
 */
fun getFieldsForStep(step: StructuralDefectStep): List<List<StructuralDefectField>> {
    return when (step) {
        StructuralDefectStep.STRUCTURE_LOCATION -> listOf(
            // Row 1
            listOf(
                StructuralDefectField(
                    key = "typeOfStructure",
                    label = "Type of Structure",
                    type = "select",
                    placeholder = "Select type",
                    options = listOf("Building", "Pipe rack", "Platform")
                ),
                StructuralDefectField(
                    key = "buildingMaterial",
                    label = "Building Material",
                    type = "select",
                    placeholder = "Select material",
                    options = listOf("Structural steel", "Concrete")
                )
            ),
            // Row 2
            listOf(
                StructuralDefectField(
                    key = "defectComponent",
                    label = "Defect Component",
                    type = "select",
                    placeholder = "Select component",
                    options = listOf("Column", "Beam", "Plate", "Bolt", "Base connection")
                ),
                StructuralDefectField(
                    key = "defectLocation",
                    label = "Defect Location",
                    type = "select",
                    placeholder = "Select location",
                    options = listOf("Concrete top", "Concrete bottom", "Concrete face", "Concrete joint")
                )
            ),
            // Row 3
            listOf(
                StructuralDefectField(
                    key = "defectOnComponent",
                    label = "Defect on Component",
                    type = "select",
                    placeholder = "Select position",
                    options = listOf("Mid-span", "Edge", "End", "Connection")
                ),
                StructuralDefectField(
                    key = "positionOfDefectComponent",
                    label = "Position of Defect Component",
                    type = "select",
                    placeholder = "Select position",
                    options = listOf("Above ground >3m", "Above ground <3m", "Below ground")
                )
            ),
            // Row 4
            listOf(
                StructuralDefectField(
                    key = "componentProfile",
                    label = "Component Profile",
                    type = "input",
                    placeholder = "e.g., UB 406x178x60",
                    span = 24
                )
            )
        )
        
        StructuralDefectStep.DEFECT_DESCRIPTION -> listOf(
            // Row 1 - Structural Concerns and Defect Severity
            listOf(
                StructuralDefectField(
                    key = "structuralConcerns",
                    label = "Structural Concerns",
                    type = "select",
                    placeholder = "Select concern",
                    options = listOf(
                        "Non-compliant design",
                        "Inadequate section",
                        "Concrete cracks (shrinkage)",
                        "Steel corrosion"
                    )
                ),
                StructuralDefectField(
                    key = "defectSeverity",
                    label = "Defect Severity",
                    type = "select",
                    placeholder = "Select severity",
                    options = listOf("Class 1 (Minor)", "Class 2 (Moderate)", "Class 3 (Serious)", "Class 4 (Very Serious)", "Class 5 (Critical)")
                )
            ),
            // Row 2 - Deformation (span 12 in Vue)
            listOf(
                StructuralDefectField(
                    key = "deformation",
                    label = "Deformation",
                    type = "select",
                    placeholder = "Select deformation",
                    options = listOf(
                        "No",
                        "Yes - Steel member deformation"
                    ),
                    span = 12
                )
            ),
            // Row 3 - Type (Point/Linear/Area radio buttons)
            listOf(
                StructuralDefectField(
                    key = "defectType",
                    label = "Type",
                    type = "select",
                    placeholder = "Select type",
                    options = listOf("Point", "Linear", "Area"),
                    span = 12
                )
            ),
            // Row 4 - Quantity
            listOf(
                StructuralDefectField(
                    key = "defectQuantity",
                    label = "Quantity",
                    type = "input",
                    placeholder = "0.00",
                    span = 12
                )
            )
        )
        
        StructuralDefectStep.CONNECTION -> listOf(
            // Row 1
            listOf(
                StructuralDefectField(
                    key = "supportArrangement",
                    label = "Support Arrangement",
                    type = "select",
                    placeholder = "Select",
                    options = listOf("Simply Supported", "Fixed", "Pinned")
                ),
                StructuralDefectField(
                    key = "fixingNature",
                    label = "Fixing Nature",
                    type = "select",
                    placeholder = "Select",
                    options = listOf("Welded", "Bolted", "Riveted")
                )
            ),
            // Row 2
            listOf(
                StructuralDefectField(
                    key = "defectComponentFunction",
                    label = "Defect Component Function",
                    type = "select",
                    placeholder = "Select",
                    options = listOf("Primary Load Bearing", "Secondary", "Bracing")
                )
            )
        )
        
        StructuralDefectStep.OPERATING_CONDITIONS -> listOf(
            // Row 1
            listOf(
                StructuralDefectField(
                    key = "structureLocation",
                    label = "Structure Location",
                    type = "select",
                    placeholder = "Select",
                    options = listOf("Enclosed environment - dry", "Enclosed environment - humid", "Open environment")
                ),
                StructuralDefectField(
                    key = "defectComponentLoading",
                    label = "Defect Component Loading",
                    type = "select",
                    placeholder = "Select",
                    options = listOf("Dead Load", "Live Load", "Dynamic Load")
                )
            ),
            // Row 2
            listOf(
                StructuralDefectField(
                    key = "impactDamage",
                    label = "Impact Damage",
                    type = "select",
                    placeholder = "Select",
                    options = listOf("Yes", "No")
                ),
                StructuralDefectField(
                    key = "dynamicEffect",
                    label = "Dynamic Effect",
                    type = "select",
                    placeholder = "Select",
                    options = listOf("Yes - frequent vibration/fatigue", "No")
                )
            ),
            // Row 3
            listOf(
                StructuralDefectField(
                    key = "windEffect",
                    label = "Wind Effect",
                    type = "select",
                    placeholder = "Select",
                    options = listOf("Yes - high wind/suction effects", "No")
                ),
                StructuralDefectField(
                    key = "chemicalEffect",
                    label = "Chemical Effect",
                    type = "select",
                    placeholder = "Select",
                    options = listOf("Yes - acid/alkali/salt spray", "No")
                )
            )
        )
        
        StructuralDefectStep.OTHER_ISSUES -> listOf(
            // Row 1
            listOf(
                StructuralDefectField(
                    key = "looseObject",
                    label = "Loose Object",
                    type = "select",
                    placeholder = "Select",
                    options = listOf("Yes", "No")
                ),
                StructuralDefectField(
                    key = "dustMaterialSpill",
                    label = "Dust & Material Spill",
                    type = "select",
                    placeholder = "Select",
                    options = listOf("Yes", "No")
                )
            ),
            // Row 2
            listOf(
                StructuralDefectField(
                    key = "accessToHazardArea",
                    label = "Access to Hazard Area",
                    type = "select",
                    placeholder = "Select",
                    options = listOf("Easily Accessible", "Restricted", "Difficult")
                ),
                StructuralDefectField(
                    key = "personnelPresenceInArea",
                    label = "Personnel Presence in Area",
                    type = "select",
                    placeholder = "Select",
                    options = listOf("Constant", "Intermittent", "Rare")
                )
            )
        )
    }
}

/**
 * 结构缺陷详情向导对话框
 * 基于Vue3前端配置的完整表单实现
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StructuralDefectWizardDialog(
    onDismiss: (StructuralDefectData?) -> Unit,
    initialData: StructuralDefectData? = null
) {
    var currentStep by remember { mutableStateOf(StructuralDefectStep.STRUCTURE_LOCATION) }
    var formData by remember { mutableStateOf(initialData ?: StructuralDefectData()) }
    
    val steps = StructuralDefectStep.values().toList()
    val currentStepIndex = steps.indexOf(currentStep)

    Dialog(
        onDismissRequest = { onDismiss(null) },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Structural Defect Details",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1565C0)
                    )
                    IconButton(onClick = { onDismiss(null) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF546E7A)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 当前步骤内容
                Text(
                    text = currentStep.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1565C0)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 表单字段和总结文本
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val fieldsForStep = getFieldsForStep(currentStep)
                    fieldsForStep.forEach { rowFields ->
                        // Each field in its own row for better visibility
                        rowFields.forEach { field ->
                            item {
                                SingleFieldRow(
                                    field = field,
                                    value = getFieldValue(formData, field.key),
                                    onValueChange = { newValue ->
                                        formData = updateFormData(formData, field.key, newValue)
                                    }
                                )
                            }
                        }
                    }
                    
                    // 添加步骤总结文本到LazyColumn的底部
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF5F5F5)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Summary:",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1565C0)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = generateStepSummaryText(currentStep, formData),
                                    fontSize = 13.sp,
                                    color = Color(0xFF666666),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 导航按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (currentStepIndex > 0) {
                        OutlinedButton(
                            onClick = { currentStep = steps[currentStepIndex - 1] },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF1565C0)
                            )
                        ) {
                            Text("Previous")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = {
                            if (currentStepIndex < steps.size - 1) {
                                currentStep = steps[currentStepIndex + 1]
                            } else {
                                onDismiss(formData)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1565C0)
                        )
                    ) {
                        Text(if (currentStepIndex < steps.size - 1) "Next" else "Complete")
                    }
                }
            }
        }
    }
}

/**
 * 步骤指示器组件
 */
@Composable
private fun StepIndicator(
    currentStep: Int,
    totalSteps: Int,
    stepTitles: List<String>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val isActive = index <= currentStep
            val isCurrent = index == currentStep
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 步骤圆圈
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = if (isActive) Color(0xFF1565C0) else Color(0xFFE0E0E0),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        color = if (isActive) Color.White else Color(0xFF666666),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // 连接线（除了最后一个步骤）
                if (index < totalSteps - 1) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(
                                color = if (index < currentStep) Color(0xFF1565C0) else Color(0xFFE0E0E0)
                            )
                    )
                }
            }
        }
    }
}

/**
 * Single field row component for better layout control
 */
@Composable
private fun SingleFieldRow(
    field: StructuralDefectField,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        when (field.type) {
            "select" -> {
                DropdownField(
                    field = field,
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            "text", "input" -> {
                InputField(
                    field = field,
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            "number" -> {
                InputField(
                    field = field,
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Dropdown field component for select inputs
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    field: StructuralDefectField,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
             value = value,
             onValueChange = { },
             readOnly = true,
             placeholder = { Text(field.placeholder ?: "Select") },
             trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
             modifier = Modifier
                 .fillMaxWidth()
                 .menuAnchor(),
             colors = OutlinedTextFieldDefaults.colors(
                 focusedBorderColor = Color(0xFF1565C0),
                 unfocusedBorderColor = Color(0xFFE0E0E0)
             )
         )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            field.options?.forEach { option ->
                 DropdownMenuItem(
                     text = { Text(option) },
                     onClick = {
                         onValueChange(option)
                         expanded = false
                     }
                 )
             }
        }
    }
}

/**
 * Input field component for text inputs
 */
@Composable
private fun InputField(
    field: StructuralDefectField,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
         value = value,
         onValueChange = onValueChange,
         placeholder = { Text(field.placeholder ?: "") },
         modifier = modifier.fillMaxWidth(),
         keyboardOptions = if (field.type == "number") {
             KeyboardOptions(keyboardType = KeyboardType.Number)
         } else {
             KeyboardOptions.Default
         },
         colors = OutlinedTextFieldDefaults.colors(
             focusedBorderColor = Color(0xFF1565C0),
             unfocusedBorderColor = Color(0xFFE0E0E0)
         )
     )
}

/**
 * 获取表单字段值
 */
private fun getFieldValue(data: StructuralDefectData, key: String): String {
    return when (key) {
        "typeOfStructure", "type_of_structure" -> data.typeOfStructure
        "buildingMaterial", "building_material" -> data.buildingMaterial
        "defectComponent", "defect_component" -> data.defectComponent
        "defectLocation" -> data.defectLocation
        "defectOnComponent", "defect_on_component" -> data.defectOnComponent
        "positionOfDefectComponent", "position_of_defect_component" -> data.positionOfDefectComponent
        "componentProfile", "component_profile" -> data.componentProfile
        // 新增映射：向导第二步字段
        "structuralConcerns", "structural_concerns" -> data.structuralConcerns
        "deformation" -> data.deformation
        
        "defectType", "defect_type" -> data.defectType
        "defectSeverity", "defect_severity" -> data.defectSeverity
        "defectDescription", "defect_description" -> data.defectDescription
        "defectExtentType", "defect_extent_type" -> data.defectExtentType
        "defectQuantity", "defect_quantity", "quantity" -> data.defectQuantity
        "supportArrangement", "support_arrangement" -> data.supportArrangement
        "fixingNature", "fixing_nature" -> data.fixingNature
        "defectComponentFunction", "defect_component_function" -> data.defectComponentFunction
        "structureLocation", "structure_location" -> data.structureLocation
        "defectComponentLoading", "defect_component_loading" -> data.defectComponentLoading
        "impactDamage", "impact_damage" -> data.impactDamage
        "dynamicEffect", "dynamic_effect" -> data.dynamicEffect
        "windEffect", "wind_effect" -> data.windEffect
        "chemicalEffect", "chemical_effect" -> data.chemicalEffect
        "looseObject", "loose_object" -> data.looseObject
        "dustMaterialSpill", "dust_material_spill" -> data.dustMaterialSpill
        "accessToHazardArea", "access_to_hazard_area" -> data.accessToHazardArea
        "personnelPresenceInArea", "personnel_presence_in_area" -> data.personnelPresenceInArea
        else -> ""
    }
}

/**
 * 更新表单数据
 */
private fun updateFormData(data: StructuralDefectData, key: String, value: String): StructuralDefectData {
    return when (key) {
        "typeOfStructure", "type_of_structure" -> data.copy(typeOfStructure = value)
        "buildingMaterial", "building_material" -> data.copy(buildingMaterial = value)
        "defectComponent", "defect_component" -> data.copy(defectComponent = value)
        "defectLocation" -> data.copy(defectLocation = value)
        "defectOnComponent", "defect_on_component" -> data.copy(defectOnComponent = value)
        "positionOfDefectComponent", "position_of_defect_component" -> data.copy(positionOfDefectComponent = value)
        "componentProfile", "component_profile" -> data.copy(componentProfile = value)
        // 新增映射：向导第二步字段
        "structuralConcerns", "structural_concerns" -> data.copy(structuralConcerns = value)
        "deformation" -> data.copy(deformation = value)
        
        "defectType", "defect_type" -> data.copy(defectType = value)
        "defectSeverity", "defect_severity" -> data.copy(defectSeverity = value)
        "defectDescription", "defect_description" -> data.copy(defectDescription = value)
        "defectExtentType", "defect_extent_type" -> data.copy(defectExtentType = value)
        "defectQuantity", "defect_quantity", "quantity" -> data.copy(defectQuantity = value)
        "supportArrangement", "support_arrangement" -> data.copy(supportArrangement = value)
        "fixingNature", "fixing_nature" -> data.copy(fixingNature = value)
        "defectComponentFunction", "defect_component_function" -> data.copy(defectComponentFunction = value)
        "structureLocation", "structure_location" -> data.copy(structureLocation = value)
        "defectComponentLoading", "defect_component_loading" -> data.copy(defectComponentLoading = value)
        "impactDamage", "impact_damage" -> data.copy(impactDamage = value)
        "dynamicEffect", "dynamic_effect" -> data.copy(dynamicEffect = value)
        "windEffect", "wind_effect" -> data.copy(windEffect = value)
        "chemicalEffect", "chemical_effect" -> data.copy(chemicalEffect = value)
        "looseObject", "loose_object" -> data.copy(looseObject = value)
        "dustMaterialSpill", "dust_material_spill" -> data.copy(dustMaterialSpill = value)
        "accessToHazardArea", "access_to_hazard_area" -> data.copy(accessToHazardArea = value)
        "personnelPresenceInArea", "personnel_presence_in_area" -> data.copy(personnelPresenceInArea = value)
        else -> data
    }
}

/**
 * 生成步骤总结文本
 * 根据当前步骤和表单数据动态生成总结文本
 */
private fun generateStepSummaryText(step: StructuralDefectStep, data: StructuralDefectData): String {
    // Debug: Print data values for troubleshooting
    println("DEBUG: generateStepSummaryText called for step: $step")
    println("DEBUG: data.typeOfStructure = '${data.typeOfStructure}'")
    println("DEBUG: data.buildingMaterial = '${data.buildingMaterial}'")
    println("DEBUG: data.defectComponent = '${data.defectComponent}'")
    
    // Check if any fields have values for this step
    val hasAnyValues = when (step) {
        StructuralDefectStep.STRUCTURE_LOCATION -> {
            data.typeOfStructure.isNotEmpty() || data.buildingMaterial.isNotEmpty() || 
            data.defectComponent.isNotEmpty() || data.defectLocation.isNotEmpty() ||
            data.positionOfDefectComponent.isNotEmpty() || data.componentProfile.isNotEmpty()
        }
        StructuralDefectStep.DEFECT_DESCRIPTION -> {
            data.structuralConcerns.isNotEmpty() || data.defectSeverity.isNotEmpty() ||
            data.defectType.isNotEmpty() || data.defectQuantity.isNotEmpty() || data.deformation.isNotEmpty()
        }
        StructuralDefectStep.CONNECTION -> {
            data.supportArrangement.isNotEmpty() || data.fixingNature.isNotEmpty() || 
            data.defectComponentFunction.isNotEmpty()
        }
        StructuralDefectStep.OPERATING_CONDITIONS -> {
            data.structureLocation.isNotEmpty() || data.defectComponentLoading.isNotEmpty() ||
            data.impactDamage.isNotEmpty() || data.dynamicEffect.isNotEmpty() ||
            data.windEffect.isNotEmpty() || data.chemicalEffect.isNotEmpty()
        }
        StructuralDefectStep.OTHER_ISSUES -> {
            data.looseObject.isNotEmpty() || data.dustMaterialSpill.isNotEmpty() ||
            data.accessToHazardArea.isNotEmpty() || data.personnelPresenceInArea.isNotEmpty()
        }
    }
    
    if (!hasAnyValues) {
        return "Please fill in the fields above to see a summary of your selections."
    }
    
    return when (step) {
        StructuralDefectStep.STRUCTURE_LOCATION -> {
            val defectLocation = if (data.defectLocation.isNotEmpty()) data.defectLocation else "[...]"
            val typeOfStructure = if (data.typeOfStructure.isNotEmpty()) data.typeOfStructure else "[...]"
            val buildingMaterial = if (data.buildingMaterial.isNotEmpty()) data.buildingMaterial else "[...]"
            val defectComponent = if (data.defectComponent.isNotEmpty()) data.defectComponent else "[...]"
            val componentProfile = if (data.componentProfile.isNotEmpty()) data.componentProfile else "N/A"
            val positionOfDefectComponent = if (data.positionOfDefectComponent.isNotEmpty()) data.positionOfDefectComponent else "[...]"
            
            "The defect is on a $typeOfStructure of $buildingMaterial construction within a $defectLocation, located at the $defectComponent. The component's profile is $componentProfile and it is positioned $positionOfDefectComponent."
        }
        
        StructuralDefectStep.DEFECT_DESCRIPTION -> {
            val structuralConcerns = if (data.structuralConcerns.isNotEmpty()) data.structuralConcerns else "[...]"
            val defectSeverity = if (data.defectSeverity.isNotEmpty()) data.defectSeverity else "[...]"
            val defectType = if (data.defectType.isNotEmpty()) data.defectType else "[...]"
            val defectQuantity = if (data.defectQuantity.isNotEmpty()) data.defectQuantity else "[...]"
            val deformation = if (data.deformation.isNotEmpty()) data.deformation else "[...]"
            
            "The primary concern is $structuralConcerns with a severity of $defectSeverity. The defect extent is a $defectType of $defectQuantity, and $deformation was observed."
        }
        
        StructuralDefectStep.CONNECTION -> {
            val defectComponentFunction = if (data.defectComponentFunction.isNotEmpty()) data.defectComponentFunction else "[...]"
            val supportArrangement = if (data.supportArrangement.isNotEmpty()) data.supportArrangement else "[...]"
            val fixingNature = if (data.fixingNature.isNotEmpty()) data.fixingNature else "[...]"
            
            "The component serves as a $defectComponentFunction, with a $supportArrangement support arrangement and a $fixingNature fixing."
        }
        
        StructuralDefectStep.OPERATING_CONDITIONS -> {
            val structureLocation = if (data.structureLocation.isNotEmpty()) data.structureLocation else "[...]"
            val defectComponentLoading = if (data.defectComponentLoading.isNotEmpty()) data.defectComponentLoading else "[...]"
            val impactDamage = if (data.impactDamage.isNotEmpty()) data.impactDamage else "[...]"
            val dynamicEffect = if (data.dynamicEffect.isNotEmpty()) data.dynamicEffect else "[...]"
            val windEffect = if (data.windEffect.isNotEmpty()) data.windEffect else "[...]"
            val chemicalEffect = if (data.chemicalEffect.isNotEmpty()) data.chemicalEffect else "[...]"
            
            "Located in an $structureLocation environment, the component is subject to $defectComponentLoading. It has a $impactDamage risk of impact, $dynamicEffect dynamic effects, $windEffect wind effects, and $chemicalEffect chemical effects."
        }
        
        StructuralDefectStep.OTHER_ISSUES -> {
            val looseObject = if (data.looseObject.isNotEmpty()) data.looseObject else "[...]"
            val dustMaterialSpill = if (data.dustMaterialSpill.isNotEmpty()) data.dustMaterialSpill else "[...]"
            val accessToHazardArea = if (data.accessToHazardArea.isNotEmpty()) data.accessToHazardArea else "[...]"
            val personnelPresenceInArea = if (data.personnelPresenceInArea.isNotEmpty()) data.personnelPresenceInArea else "[...]"
            
            "There is a $looseObject risk of loose objects and $dustMaterialSpill for material spills. Access to the area is $accessToHazardArea with $personnelPresenceInArea personnel presence."
        }
    }
}