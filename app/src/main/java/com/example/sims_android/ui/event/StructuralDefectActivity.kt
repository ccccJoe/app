/**
 * 结构缺陷详情Activity
 * 用于全屏显示结构缺陷表单，替代原有的弹窗模式
 * 
 * @author SIMS Team
 * @since 2024
 */
package com.example.sims_android.ui.event

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sims_android.ui.theme.SIMSAndroidTheme
import com.google.gson.Gson
import com.simsapp.ui.event.StructuralDefectData
import com.simsapp.ui.event.StructuralDefectStep
import com.simsapp.ui.event.StructuralDefectField
import com.simsapp.ui.event.getFieldsForStep

/**
 * 结构缺陷详情Activity
 * 提供全屏的结构缺陷表单编辑界面
 */
@OptIn(ExperimentalMaterial3Api::class)
class StructuralDefectActivity : ComponentActivity() {
    
    companion object {
        private const val EXTRA_INITIAL_DATA = "initial_data"
        const val EXTRA_RESULT_DATA = "result_data"
        const val REQUEST_CODE_STRUCTURAL_DEFECT = 1001
        
        /**
         * 启动结构缺陷详情Activity
         * @param context 上下文
         * @param initialData 初始数据（可选）
         */
        fun start(context: Context, initialData: StructuralDefectData? = null) {
            val intent = Intent(context, StructuralDefectActivity::class.java)
            initialData?.let {
                intent.putExtra(EXTRA_INITIAL_DATA, Gson().toJson(it))
            }
            context.startActivity(intent)
        }
        
        /**
         * 创建启动Intent
         * @param context 上下文
         * @param initialData 初始数据（可选）
         * @return Intent
         */
        fun createIntent(context: Context, initialData: StructuralDefectData? = null): Intent {
            val intent = Intent(context, StructuralDefectActivity::class.java)
            initialData?.let {
                intent.putExtra(EXTRA_INITIAL_DATA, Gson().toJson(it))
            }
            return intent
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 获取初始数据
        val initialDataJson = intent.getStringExtra(EXTRA_INITIAL_DATA)
        val initialData = initialDataJson?.let { 
            try {
                Gson().fromJson(it, StructuralDefectData::class.java)
            } catch (e: Exception) {
                null
            }
        }
        
        setContent {
            SIMSAndroidTheme {
                StructuralDefectScreen(
                    initialData = initialData,
                    onSave = { data ->
                        // 保存数据并返回结果
                        val resultJson = buildResultJsonWithSummaries(data)
                        val resultIntent = Intent().apply {
                            putExtra(EXTRA_RESULT_DATA, resultJson)
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    },
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }
}

/**
 * 生成步骤总结文本
 * 根据当前步骤和表单数据动态生成总结文本（与向导组件保持一致）
 */
private fun generateStepSummaryText(step: StructuralDefectStep, data: StructuralDefectData): String {
    // 调试：打印关键字段，便于排查
    println("DEBUG: [Activity] generateStepSummaryText called for step: $step")
    println("DEBUG: [Activity] data.typeOfStructure='${data.typeOfStructure}', buildingMaterial='${data.buildingMaterial}', defectComponent='${data.defectComponent}'")

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

            "Operating conditions: located at $structureLocation with loading $defectComponentLoading. Observed impacts: impact=$impactDamage, dynamic=$dynamicEffect, wind=$windEffect, chemical=$chemicalEffect."
        }

        StructuralDefectStep.OTHER_ISSUES -> {
            val looseObject = if (data.looseObject.isNotEmpty()) data.looseObject else "[...]"
            val dustMaterialSpill = if (data.dustMaterialSpill.isNotEmpty()) data.dustMaterialSpill else "[...]"
            val accessToHazardArea = if (data.accessToHazardArea.isNotEmpty()) data.accessToHazardArea else "[...]"
            val personnelPresenceInArea = if (data.personnelPresenceInArea.isNotEmpty()) data.personnelPresenceInArea else "[...]"

            "Other issues: loose object=$looseObject, dust/material spill=$dustMaterialSpill, access to hazard area=$accessToHazardArea, personnel presence=$personnelPresenceInArea."
        }
    }
}

/**
 * 构建包含每步Summary的结果JSON
 * 在原始表单数据JSON基础上，附加五个summary字段：
 * - structure_location_summary
 * - defect_description_summary
 * - connection_summary
 * - operating_conditions_summary
 * - other_issues_summary
 */
private fun buildResultJsonWithSummaries(data: StructuralDefectData): String {
    val gson = Gson()
    val obj = gson.toJsonTree(data).asJsonObject

    obj.addProperty(
        "structure_location_summary",
        generateStepSummaryText(StructuralDefectStep.STRUCTURE_LOCATION, data)
    )
    obj.addProperty(
        "defect_description_summary",
        generateStepSummaryText(StructuralDefectStep.DEFECT_DESCRIPTION, data)
    )
    obj.addProperty(
        "connection_summary",
        generateStepSummaryText(StructuralDefectStep.CONNECTION, data)
    )
    obj.addProperty(
        "operating_conditions_summary",
        generateStepSummaryText(StructuralDefectStep.OPERATING_CONDITIONS, data)
    )
    obj.addProperty(
        "other_issues_summary",
        generateStepSummaryText(StructuralDefectStep.OTHER_ISSUES, data)
    )

    return gson.toJson(obj)
}

/**
 * 单个表单字段行组件
 * @param field 字段定义
 * @param value 当前值
 * @param onValueChange 值变化回调
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF5F6B7A),
            modifier = Modifier.padding(bottom = 8.dp)
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

        // 非下拉字段，统一在字段下方保留灰色分割线
        if (field.type != "select") {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = 1.dp, color = Color(0xFFEAEFF4))
        }
    }
}

/**
 * 下拉选择字段组件
 * @param field 字段定义
 * @param value 当前值
 * @param onValueChange 值变化回调
 * @param modifier 修饰符
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
    // 是否为树形下拉
    val isTree = field.treeOptions.isNotEmpty()
    // 当前导航层级路径（用于树形下拉）
    var path by remember { mutableStateOf(listOf<com.simsapp.ui.event.OptionNode>()) }
    
    // 调试日志
    LaunchedEffect(value) {
        println("DropdownField [${field.key}]: Current value = '$value'")
    }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        // 将“选择框 + 底部分割线”作为菜单锚点，保证弹层出现时不遮挡分割线
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        ) {
            TextField(
                value = value,
                onValueChange = { },
                readOnly = true,
                placeholder = { Text(field.placeholder ?: "Select here", color = Color(0xFF9CA3AF)) },
                trailingIcon = {
                    CompositionLocalProvider(LocalContentColor provides Color(0xFF6B7280)) {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    cursorColor = Color(0xFF1565C0),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )
            // 分割线位置上移，紧贴选择框底部；展开时改为蓝色
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(
                thickness = 1.dp,
                color = if (expanded) Color(0xFF0F4C81) else Color(0xFFEAEFF4)
            )
        }
        // 取消外层蓝线，改为在下拉菜单内部顶部显示
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 8.dp,
            tonalElevation = 0.dp,
            modifier = Modifier
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
        ) {
            if (!isTree) {
                // 普通下拉：使用扁平 options
                field.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = Color(0xFF111827)) },
                        onClick = {
                            println("DropdownField [${field.key}]: Selected option = '$option'")
                            onValueChange(option)
                            expanded = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
            } else {
                // 树形下拉：根据 path 决定当前显示的节点列表
                val currentList = if (path.isEmpty()) field.treeOptions else path.last().children

                // 当在子级时，显示“返回”头部
                if (path.isNotEmpty()) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF546E7A)) },
                        text = { Text(path.last().label, color = Color(0xFF1565C0)) },
                        onClick = {
                            path = path.dropLast(1)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }

                // 列表项：有 children 的显示右侧箭头并进入下一级；叶子节点直接选中
                currentList.forEach { node ->
                    val hasChildren = node.children.isNotEmpty()
                    DropdownMenuItem(
                        text = { Text(node.label, color = Color(0xFF111827)) },
                        trailingIcon = if (hasChildren) {
                            { Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF9CA3AF)) }
                        } else null,
                        onClick = {
                            if (hasChildren) {
                                path = path + node
                            } else {
                                println("DropdownField [${field.key}]: Selected node = '${node.value}'")
                                onValueChange(node.value)
                                expanded = false
                                path = emptyList()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

/**
 * 输入字段组件
 * @param field 字段定义
 * @param value 当前值
 * @param onValueChange 值变化回调
 * @param modifier 修饰符
 */
@Composable
private fun InputField(
    field: StructuralDefectField,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 针对数量等数字类型字段，拦截非数字输入，仅允许数字和单个小数点
    // 同时将区域键盘中的逗号自动转换为小数点，确保数值格式统一
    TextField(
        value = value,
        onValueChange = { newValue ->
            // 判断是否为数字字段：类型为 number，或 key/label 包含 quantity
            val isNumericField = field.type == "number" ||
                    field.key.contains("quantity", ignoreCase = true) ||
                    field.label.contains("quantity", ignoreCase = true)

            if (!isNumericField) {
                onValueChange(newValue)
            } else {
                // 去除空格，逗号统一转为点
                val sanitized = newValue.replace(" ", "").replace(',', '.')
                // 只允许数字 + 单个小数点（可为空字符串以支持清空）
                val numericRegex = Regex("^\\d*(?:\\.\\d*)?$")
                if (sanitized.isEmpty() || numericRegex.matches(sanitized)) {
                    onValueChange(sanitized)
                }
                // 非法输入直接忽略，从而避免出现例如 7,55,85-3 这类非规范内容
            }
        },
        placeholder = { Text(field.placeholder ?: "", color = Color(0xFF9CA3AF)) },
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = if (field.type == "number") {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            cursorColor = Color(0xFF1565C0),
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        )
    )
}

/**
 * 获取表单字段值
 * @param data 表单数据
 * @param key 字段键
 * @return 字段值
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
        "defectQuantity", "defect_quantity" -> data.defectQuantity
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
 * @param data 当前表单数据
 * @param key 字段键
 * @param value 新值
 * @return 更新后的表单数据
 */
private fun updateFormData(data: StructuralDefectData, key: String, value: String): StructuralDefectData {
    println("updateFormData: key='$key', value='$value'")
    val updatedData = when (key) {
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
        "defectQuantity", "defect_quantity" -> data.copy(defectQuantity = value)
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
        else -> {
            println("updateFormData: Unknown key '$key', returning original data")
            data
        }
    }
    println("updateFormData: Updated data for key '$key': ${getFieldValue(updatedData, key)}")
    return updatedData
}

/**
 * 结构缺陷表单屏幕组件
 * @param initialData 初始数据
 * @param onSave 保存回调
 * @param onCancel 取消回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StructuralDefectScreen(
    initialData: StructuralDefectData? = null,
    onSave: (StructuralDefectData) -> Unit,
    onCancel: () -> Unit
) {
    var currentStep by remember { mutableStateOf(StructuralDefectStep.STRUCTURE_LOCATION) }
    var formData by remember { mutableStateOf(initialData ?: StructuralDefectData()) }
    
    val steps = StructuralDefectStep.values().toList()
    val currentStepIndex = steps.indexOf(currentStep)
    val isLastStep = currentStepIndex == steps.size - 1
    
    Scaffold(
        containerColor = Color.White,
        topBar = {
            // 顶部栏样式：采用浅色背景与深色文字，贴近图二风格（居中标题 + 标准返回图标）
            com.simsapp.ui.common.AppTopBar(
                title = "Structural Defect Details",
                onBack = onCancel,
                containerColor = Color.White,
                titleColor = MaterialTheme.colorScheme.onSurface,
                navigationIconColor = MaterialTheme.colorScheme.onSurface
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = Color.White,
                contentColor = Color(0xFF1565C0)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 上一步按钮
                    if (currentStepIndex > 0) {
                        OutlinedButton(
                            onClick = {
                                currentStep = steps[currentStepIndex - 1]
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF0F4C81),
                                containerColor = Color.White
                            ),
                            border = null,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .width(140.dp)
                        ) {
                            Text("Previous")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(140.dp))
                    }
                    
                    // 下一步/完成按钮
                    Button(
                        onClick = {
                            if (isLastStep) {
                                onSave(formData)
                            } else {
                                currentStep = steps[currentStepIndex + 1]
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0F4C81),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .width(140.dp)
                    ) {
                        if (isLastStep) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Complete")
                        } else {
                            Text("Next")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 当前步骤标题
            // 文件级注释：此处展示图二样式的步骤指示（x/5 + 标题）
            // 类级注释：页面头部采用更明确的步骤进度文案，满足新样式需求
            // 函数级注释：在每一步顶部显示“当前步/总步数 + 步骤标题”，不影响原有流程
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val totalSteps = steps.size
                Text(
                    text = "${currentStepIndex + 1}/$totalSteps ${currentStep.title}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            // 在步骤标题下方加入浅色分隔线与灰色间隔模块，增强模块化层级
            HorizontalDivider(thickness = 1.dp, color = Color(0xFFE5E7EB), modifier = Modifier.padding(horizontal = 16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Color(0xFFF3F4F6))
            )
            
            // 表单内容
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)
            ) {
                val fieldsForStep = getFieldsForStep(currentStep)
                
                // 将二维列表展平，让每个字段单独占一行
                val allFields = fieldsForStep.flatten()
                
                items(allFields) { field ->
                    SingleFieldRow(
                        field = field,
                        value = getFieldValue(formData, field.key),
                        onValueChange = { newValue ->
                            println("StructuralDefectScreen: Updating field '${field.key}' with value '$newValue'")
                            val updatedData = updateFormData(formData, field.key, newValue)
                            formData = updatedData
                            println("StructuralDefectScreen: FormData updated, new value for '${field.key}': '${getFieldValue(formData, field.key)}'")
                        }
                    )
                }

                // 底部总结模块（去卡片样式，改为纯白模块）
                item {
                    // 模块间灰色背景间隔，贴近设计图的分区效果
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(Color(0xFFF3F4F6))
                    )
                    // 去掉 Summary 顶部多余分隔线，保持纯白模块
                    // 纯白背景模块，与表单一致的左右留白
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                            .background(Color.White)
                    ) {
                        Column(modifier = Modifier.padding(vertical = 16.dp)) {
                            Text(
                                text = "Summary",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = generateStepSummaryText(currentStep, formData),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}