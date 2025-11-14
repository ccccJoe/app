/*
 * File: EventFormScreen.kt
 * Description: Compose screen for creating a new Event, including top app bar
 *              with back navigation and title, and a form for location,
 *              description, attachments, audio recording, and risk assessment.
 * Author: SIMS-Android Development Team
 */
package com.example.sims_android.ui.event

import android.app.Activity
import android.Manifest
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import coil.compose.AsyncImage
import com.simsapp.ui.event.components.DigitalAssetCategorizedDisplay
import com.simsapp.ui.common.SectionCard
import com.simsapp.ui.common.HeaderActionButton
import com.simsapp.ui.event.components.AssetPreviewDialog
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.text.style.TextOverflow
import com.simsapp.data.local.entity.DefectEntity
import com.simsapp.data.local.entity.EventEntity
import com.simsapp.ui.event.StructuralDefectWizardDialog
import com.simsapp.ui.event.StructuralDefectData
import com.example.sims_android.ui.event.EventFormViewModel
import com.example.sims_android.ui.event.DigitalAssetDetail
import com.simsapp.ui.common.RiskTagColors
import com.simsapp.ui.common.ProjectPickerBottomSheet
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import android.widget.Toast
// 新增：录音HUD 动画相关 import
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.lerp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import androidx.compose.foundation.Canvas
// 新增：图片预览和异步处理相关 import
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
// 新增：左滑删除功能相关 import
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.rememberSwipeToDismissBoxState

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.abs
import android.util.Log
// Gson 已在上方导入，不再重复导入

/**
 * Composable: EventFormScreen
 * Description:
 * - Collects location and description for a new Event
 * - Take photo via system camera and save into cache using FileProvider
 * - Long-press to start audio recording; release to stop, mimicking WeChat style
 * - Preview: show taken photo thumbnail and a simple audio player for recorded clip
 *
 * @param projectName The current project name from upstream navigation
 * @param onBack Callback to navigate back
 */
 @OptIn(ExperimentalMaterial3Api::class)
 @Composable
  fun EventFormScreen(
      projectName: String,
     eventId: String = "",
      defectId: String = "",
      onBack: () -> Unit,
      onOpenStorage: (String, List<String>, List<String>) -> Unit, // 传递projectUid、选中的节点ID与文件ID
      selectedStorageNodeIds: List<String> = emptyList(), // 新增：节点ID回显
      selectedStorageFileIds: List<String> = emptyList(), // 兼容：fileId回显
      onOpenDefect: ((DefectEntity) -> Unit)? = null
  ) {
     // 通过 Hilt 获取 ViewModel，提供风险矩阵加载能�?
     val viewModel: EventFormViewModel = hiltViewModel()
     val context = LocalContext.current
     val scope = rememberCoroutineScope()

     // 新增：获取projectUid状�?
     var projectUid by remember { mutableStateOf<String?>(null) }

     // 修改：使用 rememberSaveable 保留返回页面后的输入状态（避免导航往返清空）
     // 以 projectName 和 eventId 作为键，保证不同项目/事件的状态独立
     var location by rememberSaveable(projectName, eventId) { mutableStateOf("") }
     var description by rememberSaveable(projectName, eventId) { mutableStateOf("") }
     // 新增：维�?Room 中草稿事件的 eventId（便于后续根�?ID 继续编辑�?
    // 修改：eventRoomId使用rememberSaveable持久化，避免从子页面返回后丢失导致重复插入
    var eventRoomId by rememberSaveable(projectName, eventId) { mutableStateOf<Long?>(null) }
    // 替换：使用 rememberSaveable + listSaver 持久化照片与音频列表
    val photoFiles = rememberSaveable(saver = androidx.compose.runtime.saveable.listSaver(
        save = { list -> list.map { it.absolutePath } },
        restore = { paths ->
            val restored = mutableStateListOf<File>()
            paths.forEach { p ->
                val f = File(p)
                if (f.exists()) restored.add(f)
            }
            restored
        }
    )) { mutableStateListOf<File>() }
    val audioFiles = rememberSaveable(saver = androidx.compose.runtime.saveable.listSaver(
        save = { list -> list.map { it.absolutePath } },
        restore = { paths ->
            val restored = mutableStateListOf<File>()
            paths.forEach { p ->
                val f = File(p)
                if (f.exists()) restored.add(f)
            }
            restored
        }
    )) { mutableStateListOf<File>() }
     var isRecording by remember { mutableStateOf(false) }
     var isSaving by remember { mutableStateOf(false) }
     // 新增：自动保存互斥标记，避免多个触发点并发插入导致重复事件
     var isAutoSaving by remember { mutableStateOf(false) }
     // 拍照进行中的临时文件，仅在成功回调后加入列表
     var pendingPhoto by remember { mutableStateOf<File?>(null) }
     // 大图预览选择的照�?
     var largePhoto by remember { mutableStateOf<File?>(null) }
     // 录音进行中的临时文件，录制成功后再加入列�?
     var pendingAudio by remember { mutableStateOf<File?>(null) }
     // 风险评估向导弹窗显示状态与结果缓存
    var showRiskDialog by remember { mutableStateOf(false) }
    // 新增：riskResult 使用 JSON + rememberSaveable 持久化，避免返回后丢失
    var riskResultJson by rememberSaveable(projectName, eventId) { mutableStateOf<String?>(null) }
    var riskResult by remember { mutableStateOf<RiskAssessmentResult?>(null) }
    // 双向同步：riskResult -> riskResultJson
    // 修复：仅在 riskResult 非空时更新 JSON，避免页面往返导致 riskResult 暂为 null 时清空已保存的风险评估数据
    LaunchedEffect(riskResult) {
        if (riskResult != null) {
            try {
                riskResultJson = Gson().toJson(riskResult)
            } catch (_: Exception) {
                // 忽略序列化错误，保持已有 JSON 不变
            }
        }
    }
    // 双向同步：riskResultJson -> riskResult
    LaunchedEffect(riskResultJson) {
        if (riskResult == null && !riskResultJson.isNullOrBlank()) {
            try {
                riskResult = Gson().fromJson(riskResultJson, RiskAssessmentResult::class.java)
            } catch (_: Exception) {
                // ignore parse error
            }
        }
    }
    // 结构缺陷详情数据（对象与原始JSON双轨保存，原始JSON保留summary字段）
    // 文件级注释：结构缺陷详情状态
    // - 问题背景：在填写“Structural Defect Details”后，前往数字资产选择并返回，之前输入被清空。
    // - 根因分析：使用 remember 持有的状态在页面被临时移除（如跳转至选择页面）后可能丢失；返回后触发自动保存会用空值覆盖数据库。
    // - 修复策略：将 JSON 原始数据改为 rememberSaveable 以通过 SavedInstanceState 持久化；并在恢复时通过 JSON 解析得到结构化对象。
    var structuralDefectResult by remember { mutableStateOf<StructuralDefectData?>(null) }
    var structuralDefectJsonRaw by rememberSaveable { mutableStateOf<String?>(null) }
    
    // 新增：Structural Defect Activity Result Launcher
    val structuralDefectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val resultData = result.data?.getStringExtra(StructuralDefectActivity.EXTRA_RESULT_DATA)
            resultData?.let { dataJson ->
                structuralDefectJsonRaw = dataJson // 保留原始JSON，包含各步summary
                try {
                    val data = Gson().fromJson(dataJson, StructuralDefectData::class.java)
                    structuralDefectResult = data
                    Log.d("EventFormScreen", "Received structural defect data (raw len=${dataJson.length}): $data")
                } catch (e: Exception) {
                    Log.e("EventFormScreen", "Failed to parse structural defect result: ${e.message}", e)
                }
            }
        }
    }

    // 函数级注释：通过 JSON 恢复结构缺陷详情对象
    // 参数：无（依赖 structuralDefectJsonRaw 状态变化）
    // 返回：无（副作用更新 structuralDefectResult）
    // 逻辑：当 structuralDefectJsonRaw 非空时，解析为 StructuralDefectData；为空时清空对象，避免脏数据。
    LaunchedEffect(structuralDefectJsonRaw) {
        try {
            structuralDefectResult = structuralDefectJsonRaw?.let { json ->
                Gson().fromJson(json, StructuralDefectData::class.java)
            }
        } catch (e: Exception) {
            Log.e("EventFormScreen", "Failed to restore structural defect from JSON: ${e.message}", e)
            structuralDefectResult = null
        }
    }
     // 删除确认弹窗显示状态（仅编辑态）
    var showDeleteDialog by remember { mutableStateOf(false) }
    // 删除状态标记，用于阻止删除后的自动保存
    var isDeleted by remember { mutableStateOf(false) }
    
    // 新增：关联历史缺陷相关状�?
    val selectedDefects = remember { mutableStateListOf<DefectEntity>() }
    var showDefectSelectionDialog by remember { mutableStateOf(false) }
    
    // 重复声明清理：该段已在上文替换为 rememberSaveable 版本，移除此处重复声明

    // 新增：数字资产选择状态（fileId）
    var currentSelectedStorageFileIds by remember { mutableStateOf(selectedStorageFileIds) }
    // 新增：记录最后一次非空选择（fileId），用于页面退出兜底保存
    var lastNonEmptySelectedStorageFileIds by remember { mutableStateOf<List<String>>(emptyList()) }
    // 新增：数字资产选择状态（nodeId）
    var currentSelectedStorageNodeIds by remember { mutableStateOf(selectedStorageNodeIds) }
    // 新增：记录最后一次非空选择（nodeId），用于页面退出兜底保存
    var lastNonEmptySelectedStorageNodeIds by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // 新增：数字资产详细信息状�?
    var digitalAssetDetails by remember { mutableStateOf<List<DigitalAssetDetail>>(emptyList()) }
    
    // 新增：预览对话框状�?
    var showAssetPreview by remember { mutableStateOf(false) }
    var previewAsset by remember { mutableStateOf<DigitalAssetDetail?>(null) }

    // 新增：未完成项目列表与弹窗显示状态
    val notFinishedProjects by viewModel.getNotFinishedProjects().collectAsState(initial = emptyList())
    var showProjectPicker by remember { mutableStateOf(false) }
    
    // 修改：存储从数据库加载或首次保存后的事件UID，并使用rememberSaveable持久化
    var loadedEventUid by rememberSaveable(projectName, eventId) { mutableStateOf<String?>(null) }
    
    // 新增：数字资产文件名状态
    var digitalAssetFileNames by remember { mutableStateOf<List<String>>(emptyList()) }

    // 新增：监听数字资产节点ID选择变化，并映射为 fileId 后加载详情
    LaunchedEffect(selectedStorageNodeIds) {
        if (selectedStorageNodeIds.isNotEmpty()) {
            currentSelectedStorageNodeIds = selectedStorageNodeIds
            lastNonEmptySelectedStorageNodeIds = selectedStorageNodeIds
            Log.d("EventFormScreen", "LaunchedEffect triggered - selectedStorageNodeIds: ${selectedStorageNodeIds.joinToString()}")

            try {
                val mappedFileIds = viewModel.getFileIdsByNodeIds(selectedStorageNodeIds)
                if (mappedFileIds.isNotEmpty()) {
                    currentSelectedStorageFileIds = mappedFileIds
                    lastNonEmptySelectedStorageFileIds = mappedFileIds
                    digitalAssetFileNames = viewModel.getFileNamesByIds(mappedFileIds)
                    digitalAssetDetails = viewModel.getDigitalAssetDetailsByIds(mappedFileIds)
                    Log.d("EventFormScreen", "Mapped nodeIds to ${mappedFileIds.size} fileIds and loaded details")
                } else {
                    // Fallback：无法映射fileId时，直接根据nodeId加载基础详情，但不清空已有fileId选择
                    digitalAssetDetails = viewModel.getDigitalAssetDetailsByNodeIds(selectedStorageNodeIds)
                    digitalAssetFileNames = selectedStorageNodeIds
                    Log.w("EventFormScreen", "NodeIds mapping returned empty; loaded details by nodeIds as fallback")
                }
            } catch (e: Exception) {
                Log.e("EventFormScreen", "Error mapping nodeIds to fileIds: ${e.message}", e)
                // Fallback：构造基础详情，避免界面崩溃
                digitalAssetDetails = selectedStorageNodeIds.map { nodeId ->
                    DigitalAssetDetail(fileId = nodeId, fileName = nodeId, type = "file", localPath = null)
                }
            }
        } else {
            Log.d("EventFormScreen", "Ignore empty selectedStorageNodeIds update to preserve last non-empty selection")
        }
    }
    
    // 新增：监听数字资产选择变化
    LaunchedEffect(selectedStorageFileIds) {
        // 修复：避免外部导航重置导致资产在页面销毁前被清空
        if (selectedStorageFileIds.isNotEmpty()) {
            currentSelectedStorageFileIds = selectedStorageFileIds
            lastNonEmptySelectedStorageFileIds = selectedStorageFileIds
            android.util.Log.d("EventFormScreen", "LaunchedEffect triggered - selectedStorageFileIds: ${selectedStorageFileIds.joinToString()}")
            android.util.Log.d("EventFormScreen", "Updated currentSelectedStorageFileIds: ${currentSelectedStorageFileIds.joinToString()}")
            Log.d("EventFormScreen", "Digital assets updated: ${selectedStorageFileIds.size} files selected")
        } else {
            Log.d("EventFormScreen", "Ignore empty selectedStorageFileIds update to preserve last non-empty selection")
        }
        
        // 修复：在新建模式下，首次选择数字资产时同步更新初始状态
        // 避免触发不必要的自动保存
        // 注释掉这部分逻辑，因为initialDigitalAssetFileIds变量已被删除
        // if (eventId.isBlank() && initialDigitalAssetFileIds.isEmpty() && selectedStorageFileIds.isNotEmpty()) {
        //     initialDigitalAssetFileIds = selectedStorageFileIds
        //     Log.d("EventFormScreen", "Updated initialDigitalAssetFileIds for new event: ${initialDigitalAssetFileIds.joinToString()}")
        // }
        
        // 获取文件名和详细信息 - 在协程作用域中执行suspend函数
        try {
            if (currentSelectedStorageFileIds.isNotEmpty()) {
                digitalAssetFileNames = viewModel.getFileNamesByIds(currentSelectedStorageFileIds)
                digitalAssetDetails = viewModel.getDigitalAssetDetailsByIds(currentSelectedStorageFileIds)
                Log.d("EventFormScreen", "Successfully loaded ${digitalAssetFileNames.size} file names and ${digitalAssetDetails.size} asset details")
            } else {
                digitalAssetFileNames = emptyList()
                digitalAssetDetails = emptyList()
                Log.d("EventFormScreen", "Cleared digital asset data (empty selection)")
            }
        } catch (e: Exception) {
            Log.e("EventFormScreen", "Error loading digital asset details: ${e.message}", e)
            // 设置fallback数据，避免崩溃
            digitalAssetFileNames = currentSelectedStorageFileIds
            digitalAssetDetails = currentSelectedStorageFileIds.map { fileId ->
                DigitalAssetDetail(
                    fileId = fileId,
                    fileName = fileId,
                    type = "file",
                    localPath = null
                )
            }
        }
    }
    
    // 记录初始数据状态，用于检测数据是否发生变�?
    var initialLocation by remember { mutableStateOf("") }
    var initialDescription by remember { mutableStateOf("") }
    var initialPhotoFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var initialAudioFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var initialRiskResult by remember { mutableStateOf<RiskAssessmentResult?>(null) }
    var initialSelectedDefects by remember { mutableStateOf<List<DefectEntity>>(emptyList()) }
    var initialDigitalAssetFileIds by remember { mutableStateOf<List<String>>(emptyList()) } // 新增：数字资产初始状�?
    
    // 新增：监听风险矩阵评估结果、数字资产fileId与nodeId变化，触发自动保存
    LaunchedEffect(riskResult, currentSelectedStorageFileIds, currentSelectedStorageNodeIds) {
        // 避免并发保存：若已有自动保存在进行中，跳过本次触发
        if (isAutoSaving) {
            Log.d("EventFormScreen", "Skip auto-save: another save in progress")
            return@LaunchedEffect
        }
        // 检测风险矩阵评估结果或数字资产是否发生变化（基于有效 fileIds 映射）
        val riskResultChanged = riskResult != initialRiskResult
        // 计算有效的数字资产 fileIds：优先用当前 fileIds，其次用 nodeIds→fileIds 映射
        val effectiveCurrentFileIds: List<String> = when {
            currentSelectedStorageFileIds.isNotEmpty() -> currentSelectedStorageFileIds
            currentSelectedStorageNodeIds.isNotEmpty() -> try {
                viewModel.getFileIdsByNodeIds(currentSelectedStorageNodeIds)
            } catch (_: Exception) { emptyList() }
            else -> emptyList()
        }
        val digitalAssetsChanged = effectiveCurrentFileIds != initialDigitalAssetFileIds

        Log.d("EventFormScreen", "Auto-save check: riskChanged=$riskResultChanged, assetsChanged=$digitalAssetsChanged")
        Log.d("EventFormScreen", "Current assets(effective): ${effectiveCurrentFileIds.joinToString()}")
        Log.d("EventFormScreen", "Initial assets: ${initialDigitalAssetFileIds.joinToString()}")

        // 修复：只要有风险评估结果变化或数字资产变化就触发自动保存
        // 不再要求其他初始状态不为空，确保单独选择数字资产也能自动保存
        if (riskResultChanged || digitalAssetsChanged) {
            Log.d("EventFormScreen", "Auto-save triggered by risk result or digital assets change: riskChanged=$riskResultChanged, assetsChanged=$digitalAssetsChanged")

            // 跳过完全空白的初始化触发（所有状态都为初始值且没有实际内容）
            val hasAnyContent = location.isNotBlank() || description.isNotBlank() ||
                photoFiles.isNotEmpty() || audioFiles.isNotEmpty() ||
                riskResult != null || currentSelectedStorageFileIds.isNotEmpty() || currentSelectedStorageNodeIds.isNotEmpty()

            if (hasAnyContent) {
                // 触发自动保存
                try {
                    isAutoSaving = true
                    // 修复关键逻辑：优先通过事件UID解析已存在的Room ID，避免重复插入
                    // 修复：优先通过事件UID解析已存在的Room ID，支持导航参数UID或本地持久化的UID
                    val idFromUid = when {
                        eventId.isNotBlank() -> viewModel.getEventRoomIdByUid(eventId)
                        !loadedEventUid.isNullOrBlank() -> viewModel.getEventRoomIdByUid(loadedEventUid!!)
                        else -> null
                    }
                    val existingEventId = idFromUid ?: eventRoomId
                    val isEditMode = existingEventId != null && existingEventId > 0
                    val currentEventId = existingEventId

                    Log.d("EventFormScreen", "Executing auto-save: isEditMode=$isEditMode, currentEventId=$currentEventId")

                    val result = viewModel.saveEventToRoom(
                        projectName = projectName,
                        location = location,
                        description = description,
                        currentEventId = currentEventId,
                        isEditMode = isEditMode,
                        riskResult = riskResult,
                        photoFiles = photoFiles,
                        audioFiles = audioFiles,
                        selectedDefects = selectedDefects.toList(),
                        // 修复：数字资产参数采用有效 fileIds（nodeIds→fileIds 映射兜底）
                        digitalAssetFileIds = effectiveCurrentFileIds,
                        structuralDefectDetails = structuralDefectJsonRaw ?: structuralDefectResult?.let { Gson().toJson(it) }
                    )
                    val savedEventId = result.getOrNull()
                    if (savedEventId != null) {
                        // 更新eventRoomId以便后续编辑
                        if (!isEditMode && eventRoomId == null) {
                            eventRoomId = savedEventId
                        }
                        // 同步持久化事件UID，便于后续通过UID反查Room主键，避免重复插入
                        if (loadedEventUid.isNullOrBlank()) {
                            try {
                                val savedUid = viewModel.getEventUidById(savedEventId)
                                if (!savedUid.isNullOrBlank()) {
                                    loadedEventUid = savedUid
                                }
                            } catch (e: Exception) {
                                Log.w("EventFormScreen", "Failed to resolve UID for saved eventId=$savedEventId: ${e.message}")
                            }
                        }

                        // 更新初始状态，避免重复保存
                        if (riskResultChanged) {
                            initialRiskResult = riskResult
                        }
                        if (digitalAssetsChanged) {
                            initialDigitalAssetFileIds = effectiveCurrentFileIds
                        }

                        Log.d("EventFormScreen", "Auto-saved event due to risk/assets change: savedEventId=$savedEventId, riskLevel=${riskResult?.level}, assetsCount=${effectiveCurrentFileIds.size}")
                    } else {
                        val e = result.exceptionOrNull()
                        if (e != null) {
                            Log.e("EventFormScreen", "Failed to auto-save event due to risk/assets change: ${e.message}", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("EventFormScreen", "Exception during auto-save due to risk/assets change: ${e.message}", e)
                } finally {
                    isAutoSaving = false
                }
            } else {
                Log.d("EventFormScreen", "Skipped auto-save: no content to save (riskChanged=$riskResultChanged, assetsChanged=$digitalAssetsChanged)")
            }
        } else {
            Log.d("EventFormScreen", "Skipped auto-save: no changes detected (riskChanged=$riskResultChanged, assetsChanged=$digitalAssetsChanged)")
        }
    }
    
    // 新增：页面离开时自动保存逻辑
    // 函数级注释：
    // - 目的：确保即使仅数字资产发生变化（其他字段为空），也能在页面退出时自动保存。
    // - 关键修复：补充“数字资产非空”进入条件；并强化事件ID解析（支持数值ID与UID）。
    DisposableEffect(Unit) {
        onDispose {
            // 页面离开时，如果有非空数据且未被删除则检查是否需要自动保存
            // 修复：补充数字资产非空判断（currentSelectedStorageFileIds 或 lastNonEmptySelectedStorageFileIds）
            val hasAnyContentOnExit =
                location.isNotBlank() ||
                description.isNotBlank() ||
                photoFiles.isNotEmpty() ||
                audioFiles.isNotEmpty() ||
                riskResult != null ||
                currentSelectedStorageFileIds.isNotEmpty() ||
                lastNonEmptySelectedStorageFileIds.isNotEmpty() ||
                currentSelectedStorageNodeIds.isNotEmpty() ||
                lastNonEmptySelectedStorageNodeIds.isNotEmpty()

            if (!isDeleted && hasAnyContentOnExit) {
                // 检测数据是否发生变化
                // 修复：数字资产变更基于“有效 fileIds”检测（优先 current fileIds，其次 lastNonEmpty 映射结果）
                val effectiveComparisonAssetIds: List<String> = if (currentSelectedStorageFileIds.isNotEmpty()) {
                    currentSelectedStorageFileIds
                } else {
                    lastNonEmptySelectedStorageFileIds
                }
                val hasDataChanged = location != initialLocation ||
                    description != initialDescription ||
                    photoFiles.map { it.absolutePath } != initialPhotoFiles.map { it.absolutePath } ||
                    audioFiles.map { it.absolutePath } != initialAudioFiles.map { it.absolutePath } ||
                    riskResult != initialRiskResult ||
                    selectedDefects.toList() != initialSelectedDefects ||
                    effectiveComparisonAssetIds != initialDigitalAssetFileIds
                
                // 只有在数据确实发生变化时才进行自动保�?
                if (hasDataChanged) {
                    if (isAutoSaving) {
                        Log.d("EventFormScreen", "Skip page-exit auto-save: save already in progress")
                    } else {
                        isAutoSaving = true
                        // 使用runBlocking确保在页面销毁前完成保存
                        runBlocking {
                            try {
                                // 修复关键逻辑：
                                // 1) 优先解析传入的eventId为数值主键；若不是数值则按UID反查Room主键；
                                // 2) 回退到已加载的eventRoomId，确保编辑模式更新而非插入。
                                val resolvedEventId: Long? = eventId.toLongOrNull() ?: try {
                                    viewModel.getEventRoomIdByUid(eventId)
                                } catch (_: Exception) { null }
                                val fromLoadedUid: Long? = if (!loadedEventUid.isNullOrBlank()) try {
                                    viewModel.getEventRoomIdByUid(loadedEventUid!!)
                                } catch (_: Exception) { null } else null
                                val existingEventId = resolvedEventId ?: fromLoadedUid ?: eventRoomId
                                val isEditMode = existingEventId != null && existingEventId > 0
                                val currentEventId = existingEventId

                            // 计算页面退出时的有效资产 fileIds：优先 current fileIds，其次 nodeIds→fileIds 映射，再回退 lastNonEmpty
                            val effectiveAssetFileIds: List<String> = when {
                                currentSelectedStorageFileIds.isNotEmpty() -> currentSelectedStorageFileIds
                                currentSelectedStorageNodeIds.isNotEmpty() -> try {
                                    viewModel.getFileIdsByNodeIds(currentSelectedStorageNodeIds)
                                } catch (_: Exception) { lastNonEmptySelectedStorageFileIds }
                                lastNonEmptySelectedStorageFileIds.isNotEmpty() -> lastNonEmptySelectedStorageFileIds
                                lastNonEmptySelectedStorageNodeIds.isNotEmpty() -> try {
                                    viewModel.getFileIdsByNodeIds(lastNonEmptySelectedStorageNodeIds)
                                } catch (_: Exception) { emptyList() }
                                else -> emptyList()
                            }

                            val result = viewModel.saveEventToRoom(
                                projectName = projectName,
                                location = location,
                                description = description,
                                currentEventId = currentEventId,
                                isEditMode = isEditMode,
                                riskResult = riskResult,
                                photoFiles = photoFiles,
                                audioFiles = audioFiles,
                                selectedDefects = selectedDefects.toList(),
                                // 修复：页面退出时数字资产参数采用有效 fileIds
                                digitalAssetFileIds = effectiveAssetFileIds,
                                structuralDefectDetails = structuralDefectJsonRaw ?: structuralDefectResult?.let { Gson().toJson(it) }
                            )
                            
                            val savedEventId = result.getOrNull()
                            if (savedEventId != null) {
                                Log.d("EventFormScreen", "Auto-saved event on page exit (data changed): projectName=$projectName, location=$location, descLen=${description.length}, isEditMode=$isEditMode, savedEventId=$savedEventId")
                                if (!isEditMode && eventRoomId == null) {
                                    eventRoomId = savedEventId
                                }
                                // 同步持久化事件UID
                                if (loadedEventUid.isNullOrBlank()) {
                                    try {
                                        val savedUid = viewModel.getEventUidById(savedEventId)
                                        if (!savedUid.isNullOrBlank()) {
                                            loadedEventUid = savedUid
                                        }
                                    } catch (e: Exception) {
                                        Log.w("EventFormScreen", "Failed to resolve UID on page exit for saved eventId=$savedEventId: ${e.message}")
                                    }
                                }
                            } else {
                                val e = result.exceptionOrNull()
                                if (e != null) {
                                    Log.e("EventFormScreen", "Failed to auto-save event on page exit: ${e.message}", e)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("EventFormScreen", "Exception during auto-save on page exit: ${e.message}", e)
                        }
                        }
                        isAutoSaving = false
                    }
                } else {
                    Log.d(
                        "EventFormScreen",
                        "Skipped auto-save on page exit: no data changes detected (assetsCurrent=${currentSelectedStorageFileIds.size}, assetsLastNonEmpty=${lastNonEmptySelectedStorageFileIds.size})"
                    )
                }
            }
        }
    }

    // 新增：获取projectUid
    LaunchedEffect(projectName) {
        if (projectName.isNotBlank()) {
            try {
                val project = viewModel.projectDao.getByExactName(projectName)
                if (project != null) {
                    projectUid = project.projectUid
                    Log.d("EventFormScreen", "Loaded projectUid: $projectUid for project: $projectName")
                } else {
                    Log.w("EventFormScreen", "Project not found: $projectName")
                }
            } catch (e: Exception) {
                Log.e("EventFormScreen", "Failed to load project: ${e.message}", e)
            }
        }
    }

    // 处理defectId参数：如果传入了defectId，自动关联该缺陷
    LaunchedEffect(defectId) {
        if (defectId.isNotBlank() && projectName.isNotBlank()) {
            try {
                // 通过项目名称获取项目实体来获取projectUid
                val project = viewModel.projectDao.getByExactName(projectName)
                if (project != null) {
                    val projectUid = project.projectUid ?: ""
                    if (projectUid.isNotBlank()) {
                        // 通过projectUid和defectNo获取缺陷实体
                        val defect = viewModel.getDefectByProjectUidAndDefectNo(projectUid, defectId)
                        if (defect != null) {
                            // 检查是否已经关联了该缺�?
                            if (!selectedDefects.any { it.defectId == defect.defectId }) {
                                selectedDefects.add(defect)
                                Log.d("EventFormScreen", "Auto-associated defect: projectUid=$projectUid, defectNo=$defectId, defectId=${defect.defectId}")
                            }
                        } else {
                            Log.w("EventFormScreen", "Defect not found: projectUid=$projectUid, defectNo=$defectId")
                        }
                    } else {
                        Log.w("EventFormScreen", "Project UID is blank for project: $projectName")
                    }
                } else {
                    Log.w("EventFormScreen", "Project not found: $projectName")
                }
            } catch (e: Exception) {
                Log.e("EventFormScreen", "Failed to auto-associate defect: ${e.message}", e)
            }
        }
    }

    // 进入时如果有 eventId，则从数据库加载事件数据并回显
    // 函数级注释：
    // - 目的：修复仅携带事件UID（字符串）时无法从Room加载，导致数字资产不回显的问题。
    // - 策略：优先将UID解析为Room主键（event_id），若解析失败再回退到本地meta.json。
    // - 影响：确保更新后的 assets 字段从数据库正确回显，避免依赖可能未更新的 meta.json。
    LaunchedEffect(eventId) {
        if (eventId.isNotBlank()) {
            // 首先解析事件主键：优先用数值ID，其次用UID查询Room主键
            val resolvedEventId: Long? = eventId.toLongOrNull() ?: try {
                viewModel.getEventRoomIdByUid(eventId)
            } catch (_: Exception) { null }
            if (resolvedEventId != null) {
                val result = viewModel.loadEventFromRoom(resolvedEventId)
                val eventEntity: EventEntity? = result.getOrNull()
                if (eventEntity != null) {
                        // 从数据库加载的数据回显到UI
                        location = eventEntity.location ?: ""
                        description = eventEntity.content
                        eventRoomId = eventEntity.eventId
                        loadedEventUid = eventEntity.uid // 存储事件UID
                        
                        // 回显风险评估结果：不再依赖 riskLevel/riskScore 是否存在，只要 risk_answers 可解析即进行回显
                        // 文件级注释：该段逻辑用于将数据库中的 risk_answers 解析为弹窗所需的 initialAnswers/initialAssessmentData。
                        runCatching {
                            val gson = com.google.gson.Gson()
                            var parsedAnswers: List<RiskAnswer>? = null
                            var parsedAssessmentData: RiskAssessmentData? = null
                            eventEntity.riskAnswers?.let { answersJson ->
                                // 优先尝试解析为新的对象格式 RiskAssessmentData
                                parsedAssessmentData = try {
                                    gson.fromJson(answersJson, RiskAssessmentData::class.java)
                                } catch (_: Exception) {
                                    null
                                }
                                // 如果不是对象格式，则尝试解析为旧的数组格式 List<RiskAnswer>
                                if (parsedAssessmentData == null) {
                                    parsedAnswers = try {
                                        val type = object : com.google.gson.reflect.TypeToken<List<RiskAnswer>>() {}.type
                                        gson.fromJson<List<RiskAnswer>>(answersJson, type)
                                    } catch (e: Exception) {
                                        Log.e("EventFormScreen", "Failed to parse risk answers: ${e.message}", e)
                                        null
                                    }
                                }
                            }

                            // 若解析到任一格式，即构造 riskResult，level/score 若为空使用兜底值或对象内字段
                            if (parsedAnswers != null || parsedAssessmentData != null) {
                                val level = eventEntity.riskLevel
                                    ?: parsedAssessmentData?.risk_rating
                                    ?: ""
                                val score = eventEntity.riskScore ?: 0.0
                                riskResult = RiskAssessmentResult(
                                    level = level,
                                    score = score,
                                    answers = parsedAnswers,
                                    assessmentData = parsedAssessmentData
                                )
                            }
                        }
                        
                        // 回显图片文件（非破坏性合并）
                        // 函数级注释：
                        // - 目的：避免在选择数字资产返回后，已有本地拍照内容被清空。
                        // - 策略：不清空 photoFiles，而是合并数据库中已有的路径，保持已拍摄内容。
                        runCatching {
                            val existingPhotoPaths = photoFiles.map { it.absolutePath }.toMutableSet()
                            eventEntity.photoFiles.forEach { photoPath ->
                                val photoFile = File(photoPath)
                                if (photoFile.exists() && existingPhotoPaths.add(photoFile.absolutePath)) {
                                    photoFiles.add(photoFile)
                                }
                            }
                        }.onFailure { e ->
                            Log.w("EventFormScreen", "Failed to merge photo files: ${e.message}")
                        }

                        // 回显音频文件（非破坏性合并）
                        // 函数级注释：
                        // - 目的：避免在选择数字资产返回后，已有本地录音内容被清空。
                        // - 策略：不清空 audioFiles，而是合并数据库中已有的路径，保持已录音内容。
                        runCatching {
                            val existingAudioPaths = audioFiles.map { it.absolutePath }.toMutableSet()
                            eventEntity.audioFiles.forEach { audioPath ->
                                val audioFile = File(audioPath)
                                if (audioFile.exists() && existingAudioPaths.add(audioFile.absolutePath)) {
                                    audioFiles.add(audioFile)
                                }
                            }
                        }.onFailure { e ->
                            Log.w("EventFormScreen", "Failed to merge audio files: ${e.message}")
                        }
                        
                        // 回显 Structural Defect Details
                        if (!eventEntity.structuralDefectDetails.isNullOrBlank()) {
                            try {
                                val gson = Gson()
                                structuralDefectJsonRaw = eventEntity.structuralDefectDetails
                                structuralDefectResult = gson.fromJson(eventEntity.structuralDefectDetails, StructuralDefectData::class.java)
                                Log.d("EventFormScreen", "Restored structural defect details: ${structuralDefectResult}")
                            } catch (e: Exception) {
                                Log.e("EventFormScreen", "Failed to parse structural defect details: ${e.message}", e)
                            }
                        }
                        
                        // 回显数字资产选择 - 优先使用 nodeId，然后映射为 fileId 加载详情与文件名
                        val assetNodeIds = eventEntity.assets.mapNotNull { it.nodeId }
                        val assetFileNames = eventEntity.assets.map { it.fileName }
                        if (assetNodeIds.isNotEmpty()) {
                            android.util.Log.d("EventFormScreen", "Loading event assets - nodeIds: ${assetNodeIds.joinToString()}")
                            currentSelectedStorageNodeIds = assetNodeIds
                            lastNonEmptySelectedStorageNodeIds = assetNodeIds
                            // 将 nodeId 映射为 fileId 并加载详情
                            val mappedFileIds = try { viewModel.getFileIdsByNodeIds(assetNodeIds) } catch (_: Exception) { emptyList<String>() }
                            if (mappedFileIds.isNotEmpty()) {
                                currentSelectedStorageFileIds = mappedFileIds
                                lastNonEmptySelectedStorageFileIds = mappedFileIds
                                val assetDetails = viewModel.getDigitalAssetDetailsByIds(mappedFileIds)
                                digitalAssetDetails = assetDetails
                                digitalAssetFileNames = assetFileNames
                                initialDigitalAssetFileIds = mappedFileIds
                            } else {
                                // Fallback：无法映射fileId时直接用 nodeId 加载基础详情，保持原有fileId状态不变
                                digitalAssetDetails = viewModel.getDigitalAssetDetailsByNodeIds(assetNodeIds)
                                digitalAssetFileNames = assetNodeIds
                                initialDigitalAssetFileIds = currentSelectedStorageFileIds
                            }
                        } else {
                            // 兼容旧数据：无nodeId时使用 fileId 回显
                            val assetFileIds = eventEntity.assets.map { it.fileId }
                            android.util.Log.d("EventFormScreen", "Loading event assets - fileIds: ${assetFileIds.joinToString()}")
                            android.util.Log.d("EventFormScreen", "Loading event assets - fileNames: ${assetFileNames.joinToString()}")
                            currentSelectedStorageFileIds = assetFileIds
                            val assetDetails = viewModel.getDigitalAssetDetailsByIds(assetFileIds)
                            digitalAssetDetails = assetDetails
                            digitalAssetFileNames = assetFileNames
                            lastNonEmptySelectedStorageFileIds = assetFileIds
                            initialDigitalAssetFileIds = assetFileIds
                        }
                        Log.d("EventFormScreen", "Restored digital assets: ${eventEntity.assets.size} items (nodeIds=${assetNodeIds.size})")
                        
                        // 加载关联的缺陷信息（支持 defectIds / defectNos / defectUids）
                        if (eventEntity.defectIds.isNotEmpty() || eventEntity.defectNos.isNotEmpty() || eventEntity.defectUids.isNotEmpty()) {
                            // 在协程外部保存eventEntity的引�?
                            val defectIds = eventEntity.defectIds
                            val defectNos = eventEntity.defectNos
                            val defectUids = eventEntity.defectUids
                            val projectUid = eventEntity.projectUid
                            
                            scope.launch {
                                try {
                                    val defects = mutableListOf<DefectEntity>()

                                    // 通过 defectIds 加载缺陷（使用常规 for 循环以支持 suspend 调用）
                                    for (defectId: Long in defectIds) {
                                        val defect = viewModel.getDefectById(defectId)
                                        defect?.let { d: DefectEntity ->
                                            defects.add(d)
                                        }
                                    }

                                    // 通过 defectNos 加载缺陷（如果有 projectUid）
                                    if (projectUid.isNotBlank()) {
                                        for (defectNo: String in defectNos) {
                                            val defect = viewModel.getDefectByProjectUidAndDefectNo(projectUid, defectNo)
                                            defect?.let { d: DefectEntity ->
                                                if (!defects.any { existingDefect -> existingDefect.defectId == d.defectId }) {
                                                    defects.add(d)
                                                }
                                            }
                                        }
                                    }

                                    // 通过 defectUids 加载缺陷
                                    for (uid: String in defectUids) {
                                        val defect = if (projectUid.isNotBlank()) {
                                            viewModel.getDefectByProjectUidAndUid(projectUid, uid)
                                        } else {
                                            viewModel.getDefectByUid(uid)
                                        }
                                        defect?.let { d: DefectEntity ->
                                            if (!defects.any { existingDefect -> existingDefect.defectId == d.defectId }) {
                                                defects.add(d)
                                            }
                                        }
                                    }

                                    selectedDefects.clear()
                                    selectedDefects.addAll(defects)
                                } catch (e: Exception) {
                                    Log.e("EventFormScreen", "Failed to load associated defects: ${e.message}", e)
                                }
                            }
                        }
                        
                        Log.d("EventFormScreen", "Loaded complete event from database: eventId=$resolvedEventId, location=$location, " +
                            "descLen=${description.length}, riskLevel=${eventEntity.riskLevel}, photoCount=${photoFiles.size}, audioCount=${audioFiles.size}, " +
                            "digitalAssetCount=${eventEntity.assets.size}")
                        
                        // 设置初始状态，用于检测数据变�?
                        initialLocation = location
                        initialDescription = description
                        initialPhotoFiles = photoFiles.toList()
                        initialAudioFiles = audioFiles.toList()
                        initialRiskResult = riskResult
                        initialSelectedDefects = selectedDefects.toList()
                        initialDigitalAssetFileIds = eventEntity.assets.map { it.fileId } // 新增：设置数字资产初始状态
                } else {
                    val e = result.exceptionOrNull()
                    Log.e("EventFormScreen", "Failed to load event from database: ${e?.message}", e)
                }
            }
            
            // 然后尝试从本地文件系统加载额外数据（如风险评估、图片、音频等�?
            // 注意：优先使用数据库中的数据，只有在数据库中没有时才从文件系统加�?
            val baseDir = File(context.filesDir, "events")
            val eventDir = File(baseDir, eventId)
            val meta = File(eventDir, "meta.json")
            if (meta.exists()) {
                runCatching {
                    val obj = org.json.JSONObject(meta.readText())
                    // 如果数据库中没有location和description，则从meta.json中读�?
                    if (location.isBlank()) location = obj.optString("location", "")
                    if (description.isBlank()) description = obj.optString("description", "")
                    
                    // 如果数据库中没有风险评估结果，则从meta.json中读取，兼容对象与数组两种格式
                    if (riskResult == null) {
                        val riskObj = obj.optJSONObject("risk")
                        if (riskObj != null) {
                            // 兼容旧键名：优先读取level，若为空则回退priority
                            var level = riskObj.optString("level", "")
                            if (level.isBlank()) level = riskObj.optString("priority", "")
                            val score = riskObj.optDouble("score", 0.0)

                            var parsedAnswers: List<RiskAnswer>? = null
                            var parsedAssessmentData: RiskAssessmentData? = null

                            val answersAny = riskObj.opt("answers")
                            when (answersAny) {
                                is org.json.JSONObject -> {
                                    // 新对象格式：RiskAssessmentData
                                    parsedAssessmentData = try {
                                        Gson().fromJson(answersAny.toString(), RiskAssessmentData::class.java)
                                    } catch (_: Exception) { null }
                                }
                                is org.json.JSONArray -> {
                                    // 旧数组格式：List<RiskAnswer>
                                    val list = mutableListOf<RiskAnswer>()
                                    for (i in 0 until answersAny.length()) {
                                        val a = answersAny.optJSONObject(i) ?: continue
                                        list.add(
                                            RiskAnswer(
                                                stepIndex = a.optInt("stepIndex", i + 1),
                                                question = a.optString("question", ""),
                                                optionIndex = a.optInt("optionIndex", 0),
                                                optionText = a.optString("optionText", ""),
                                                value = a.optDouble("value", 0.0)
                                            )
                                        )
                                    }
                                    parsedAnswers = list
                                }
                                is String -> {
                                    // 若为字符串，尝试解析为对象或数组
                                    val s = answersAny as String
                                    parsedAssessmentData = runCatching { Gson().fromJson(s, RiskAssessmentData::class.java) }.getOrNull()
                                    if (parsedAssessmentData == null) {
                                        parsedAnswers = try {
                                            val type = object : com.google.gson.reflect.TypeToken<List<RiskAnswer>>() {}.type
                                            Gson().fromJson<List<RiskAnswer>>(s, type)
                                        } catch (_: Exception) { null }
                                    }
                                }
                            }

                            // 兼容：只要 answers 存在（对象或数组），即构建 riskResult 以便弹窗回显；level 可为空
                            if (parsedAnswers != null || parsedAssessmentData != null || level.isNotBlank()) {
                                riskResult = RiskAssessmentResult(
                                    level = level,
                                    score = score,
                                    answers = parsedAnswers,
                                    assessmentData = parsedAssessmentData
                                )
                            }
                        }
                    }
                    
                    // 如果数据库中没有图片和音频文件，则从meta.json中读�?
                    if (photoFiles.isEmpty()) {
                        val photos = obj.optJSONArray("photos")
                        if (photos != null) {
                            for (i in 0 until photos.length()) {
                                val filename = photos.optString(i)
                                if (filename.isNotBlank()) {
                                    val file = File(eventDir, filename)
                                    if (file.exists()) photoFiles.add(file)
                                }
                            }
                        }
                    }
                    
                    if (audioFiles.isEmpty()) {
                        val audios = obj.optJSONArray("audios")
                        if (audios != null) {
                            for (i in 0 until audios.length()) {
                                val filename = audios.optString(i)
                                if (filename.isNotBlank()) {
                                    val file = File(eventDir, filename)
                                    if (file.exists()) audioFiles.add(file)
                                }
                            }
                        }
                    }
                    
                    // 如果数据库中没有数字资产，则从meta.json中读取（兼容 assets 对象数组与 digitalAssets 字符串数组）
                    if (currentSelectedStorageFileIds.isEmpty()) {
                        val fileIds = mutableListOf<String>()
                        // 先尝试读取对象数组 assets：[{"fileId":"...","fileName":"..."}, ...]
                        val assetsArr = obj.optJSONArray("assets")
                        if (assetsArr != null) {
                            for (i in 0 until assetsArr.length()) {
                                val item = assetsArr.optJSONObject(i)
                                val fid = item?.optString("fileId") ?: ""
                                if (fid.isNotBlank()) fileIds.add(fid)
                            }
                        }
                        // 若未读取到，则回退读取 digitalAssets：["fid1","fid2",...]
                        if (fileIds.isEmpty()) {
                            val digitalAssets = obj.optJSONArray("digitalAssets")
                            if (digitalAssets != null) {
                                for (i in 0 until digitalAssets.length()) {
                                    val fileId = digitalAssets.optString(i)
                                    if (fileId.isNotBlank()) fileIds.add(fileId)
                                }
                            }
                        }
                        if (fileIds.isNotEmpty()) {
                            currentSelectedStorageFileIds = fileIds
                            lastNonEmptySelectedStorageFileIds = fileIds
                        }
                    }
                    
                    // 如果数据库中没有结构缺陷详情，则从meta.json中读取（兼容对象与旧字符串）
                    if (structuralDefectResult == null) {
                        val fieldAny = obj.opt("structuralDefectDetails")
                        when (fieldAny) {
                            is org.json.JSONObject -> {
                                try {
                                    val jsonStr = fieldAny.toString()
                                    structuralDefectResult = Gson().fromJson(jsonStr, StructuralDefectData::class.java)
                                    Log.d("EventFormScreen", "Loaded structural defect details (object) from meta.json")
                                } catch (e: Exception) {
                                    Log.e("EventFormScreen", "Failed to parse object structural defect details: ${e.message}", e)
                                }
                            }
                            is String -> {
                                val structuralDefectDetailsStr = fieldAny
                                if (structuralDefectDetailsStr.isNotBlank()) {
                                    try {
                                        structuralDefectResult = Gson().fromJson(structuralDefectDetailsStr, StructuralDefectData::class.java)
                                        Log.d("EventFormScreen", "Loaded structural defect details (string) from meta.json")
                                    } catch (e: Exception) {
                                        Log.e("EventFormScreen", "Failed to parse structural defect details from meta.json: ${e.message}", e)
                                    }
                                }
                            }
                            else -> { /* ignore */ }
                        }
                    }
                }.onFailure { e ->
                    Log.e("EventFormScreen", "Failed to load event metadata from file: ${e.message}", e)
                }
            }
            
            // 数据加载完成后，更新初始状态用于后续变化检测
            initialLocation = location
            initialDescription = description
            initialPhotoFiles = photoFiles.toList()
            initialAudioFiles = audioFiles.toList()
            initialRiskResult = riskResult
            initialSelectedDefects = selectedDefects.toList()
            // 修复：不要用“当前选择”覆盖初始资产，否则资产选择后被误判为无变化
            // 仅当初始资产尚未设置（例如新建态且数据库未加载资产）时，保持为空或沿用更早在数据库加载逻辑处设置的值
            // 在上方事件读取（eventEntity）逻辑中，若已存在资产，会将 initialDigitalAssetFileIds 设置为数据库中的资产文件ID
            // 这里不再覆盖，避免影响变更检测（确保仅资产变更也能触发保存）
            if (initialDigitalAssetFileIds.isEmpty()) {
                // 保持为空，等待后续选择触发变更保存；不主动用 currentSelectedStorageFileIds 覆盖
            }
            // 同步最后一次非空选择（兜底保存）
            if (lastNonEmptySelectedStorageFileIds.isEmpty() && currentSelectedStorageFileIds.isNotEmpty()) {
                lastNonEmptySelectedStorageFileIds = currentSelectedStorageFileIds
            }
        }
    }

    // -- Permission helper: request multiple permissions and then run pending action --
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        if (allGranted) pendingAction?.invoke()
        pendingAction = null
    }

    fun ensurePermissions(perms: Array<String>, action: () -> Unit) {
        val notGranted = perms.any {
            ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (notGranted) {
            pendingAction = action
            permissionLauncher.launch(perms)
        } else action()
    }

    // 新增：权限快速判断（避免长按期间弹窗导致逻辑错乱�?
    fun hasPermission(perm: String): Boolean {
        return ContextCompat.checkSelfPermission(context, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * 创建临时文件（缓存目录）
     */
    fun createTempFile(prefix: String, suffix: String): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        return File.createTempFile("${prefix}_${ts}", suffix, context.cacheDir)
    }

    // Photo: TakePicture launcher（修复：仅在成功后加入列表）
    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingPhoto?.let { photoFiles.add(it) }
        } else {
            try { pendingPhoto?.delete() } catch (_: Exception) {}
        }
        pendingPhoto = null
    }

    /**
     * 启动拍照流程（不要提前设置到最终列表）
     */
    fun startTakePhoto() {
        val file = createTempFile("IMG", ".jpg")
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
        pendingPhoto = file
        takePictureLauncher.launch(uri)
    }

    // Recorder instance retained in state
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    // 录音音量级别�?f..1f），用于驱动底部波浪动画
    var voiceLevel by remember { mutableStateOf(0f) }
    // 底部录音弹窗显示状态
    var showAudioRecordSheet by remember { mutableStateOf(false) }

    /**
     * 开始录�?
     */
    var recordStartTime by remember { mutableStateOf(0L) }
    fun startRecording() {
        Log.i("EventForm", "startRecording: begin, perms MIC=${hasPermission(android.Manifest.permission.RECORD_AUDIO)}")
        // 首选方案：AAC + MPEG_4
        var f = createTempFile("AUDIO", ".m4a")
        pendingAudio = f
        val r = MediaRecorder()
        try {
            // 配置音源（Android 12+ 优先 VOICE_RECOGNITION 提升语音清晰度）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                r.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            } else {
                r.setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            isRecording = true
            recordStartTime = System.currentTimeMillis()
            Log.i("EventForm", "startRecording: AAC/MPEG_4 started at ${f.absolutePath}")
            return
        } catch (e: Exception) {
            Log.w("EventForm", "startRecording: AAC/MPEG_4 failed: ${e.javaClass.simpleName} ${e.message}")
            try { r.reset(); r.release() } catch (_: Exception) {}
            recorder = null
            isRecording = false
        }
    
        // 回退方案：AMR_NB + THREE_GPP（兼容性更好，但音质较低）
        try {
            // 删除无效�?m4a 文件
            try { f.delete() } catch (_: Exception) {}
            f = createTempFile("AUDIO", ".3gp")
            pendingAudio = f
            val r2 = MediaRecorder()
            r2.setAudioSource(MediaRecorder.AudioSource.MIC)
            r2.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            r2.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            r2.setOutputFile(f.absolutePath)
            r2.prepare()
            r2.start()
            recorder = r2
            isRecording = true
            recordStartTime = System.currentTimeMillis()
            Log.i("EventForm", "startRecording: fallback AMR_NB/3GP started at ${f.absolutePath}")
        } catch (e2: Exception) {
            Log.e("EventForm", "startRecording: fallback failed: ${e2.javaClass.simpleName} ${e2.message}")
            try { recorder?.reset(); recorder?.release() } catch (_: Exception) {}
            recorder = null
            isRecording = false
            // 失败则丢弃临时文�?
            try { pendingAudio?.delete() } catch (_: Exception) {}
            pendingAudio = null
        }
    }

    /**
     * 停止录音并释放资�?
     */
    fun stopRecording() {
        Log.i("EventForm", "stopRecording: begin")
        val r = recorder
        if (r != null) {
            try {
                // 某些设备要求至少录制数百毫秒，否�?stop 可能抛异常或导致 native 崩溃
                val elapsed = System.currentTimeMillis() - recordStartTime
                if (elapsed < 400) {
                    try { Thread.sleep(400 - elapsed) } catch (_: Exception) {}
                }
                r.stop()
                Log.i("EventForm", "stopRecording: recorder stopped")
            } catch (e: Exception) {
                Log.w("EventForm", "stopRecording: stop failed: ${e.javaClass.simpleName} ${e.message}")
            } finally {
                try { r.reset(); r.release() } catch (_: Exception) {}
            }
        }
        recorder = null
        isRecording = false
        val f = pendingAudio
        if (f != null && f.exists() && f.length() > 0L) {
            audioFiles.add(f)
            Log.i("EventForm", "stopRecording: audio accepted len=${f.length()} path=${f.absolutePath}")
        } else {
            try { f?.delete() } catch (_: Exception) {}
            Log.w("EventForm", "stopRecording: audio discarded (empty/invalid)")
        }
        pendingAudio = null
    }

    // 在录音时轮询当前音量幅度并平滑更新到 voiceLevel（用于驱动波形动画）
    LaunchedEffect(isRecording) {
        while (isRecording) {
            val amp = try { recorder?.getMaxAmplitude() ?: 0 } catch (_: Exception) { 0 }
            // �?0..32767 的幅度归一化到 0..1，并做指数平滑以避免跳变
            val normalized = (amp / 4000f).coerceIn(0f, 1f)
            voiceLevel = (voiceLevel * 0.7f + normalized * 0.3f).coerceIn(0f, 1f)
            kotlinx.coroutines.delay(60)
        }
        // 录音结束后逐渐回落
        if (!isRecording) {
            repeat(10) {
                voiceLevel = (voiceLevel * 0.6f).coerceAtLeast(0f)
                kotlinx.coroutines.delay(30)
            }
            voiceLevel = 0f
        }
    }

    val title = remember(projectName) { if (projectName.isNotBlank()) "New Event - $projectName" else "New Event" }
    
    // 新增：离开页面确认弹窗状�?
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    
    // 新增：检查是否需要显示离开确认弹窗的函�?
    val shouldShowExitConfirm = {
        // 检查是否没有关联任何defect且有其他内容
        selectedDefects.isEmpty() && (location.isNotBlank() || description.isNotBlank() || photoFiles.isNotEmpty() || audioFiles.isNotEmpty() || riskResult != null)
    }
    
    // 新增：处理返回按钮点击的函数
    val handleBackPress = {
        if (shouldShowExitConfirm()) {
            showExitConfirmDialog = true
        } else {
            onBack()
        }
    }

    // 添加BackHandler来处理系统返回按�?
    BackHandler {
        handleBackPress()
    }

    Scaffold(
        topBar = {
            com.simsapp.ui.common.AppTopBar(
                title = "Event",
                onBack = { handleBackPress() },
                containerColor = Color.White,
                titleColor = Color(0xFF1C1C1E),
                navigationIconColor = Color(0xFF1C1C1E)
            )
        },
        bottomBar = {
            androidx.compose.material3.Surface(
                color = Color.White,
                tonalElevation = 0.dp,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Delete（左侧）：仅编辑模式显示
                    if (eventId.isNotBlank() && eventId != "0") {
                        TextButton(
                            onClick = { showDeleteDialog = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Delete")
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    // Sync（中间）：保持原逻辑，文本按钮样式
                    TextButton(
                        onClick = {
                            if (isSaving) return@TextButton
                            if (notFinishedProjects.isNotEmpty()) {
                                showProjectPicker = true
                            } else {
                                scope.launch {
                                    // 1) 计算当前 UID（可能为空）
                                    var finalUid: String = run {
                                        if (eventId.isNotBlank()) {
                                            val asLong = eventId.toLongOrNull()
                                            if (asLong != null) {
                                                viewModel.getEventUidById(asLong) ?: (loadedEventUid ?: "")
                                            } else {
                                                eventId
                                            }
                                        } else {
                                            loadedEventUid ?: ""
                                        }
                                    }

                                    // 2) 上传前，先进行本地保存（生成 UID/RoomId，并落地最新数据）
                                    isSaving = true
                                    // 2.1 计算有效的数字资产 fileIds（优先已选 fileIds，其次 nodeIds 映射，否则空）
                                    val effectiveAssetFileIds: List<String> = when {
                                        currentSelectedStorageFileIds.isNotEmpty() -> currentSelectedStorageFileIds
                                        currentSelectedStorageNodeIds.isNotEmpty() -> {
                                            try {
                                                viewModel.getFileIdsByNodeIds(currentSelectedStorageNodeIds)
                                            } catch (_: Exception) { emptyList() }
                                        }
                                        else -> emptyList()
                                    }

                                    // 2.2 保存逻辑：若没有 UID，则当作新建保存；否则按编辑更新保存
                                    val saveResult: Result<Long> = run {
                                        if (finalUid.isBlank()) {
                                            viewModel.saveEventToRoom(
                                                projectName = projectName,
                                                location = location,
                                                description = description,
                                                riskResult = riskResult,
                                                photoFiles = photoFiles,
                                                audioFiles = audioFiles,
                                                selectedDefects = selectedDefects,
                                                digitalAssetFileIds = effectiveAssetFileIds,
                                                isEditMode = false,
                                                currentEventId = null,
                                                structuralDefectDetails = structuralDefectJsonRaw ?: structuralDefectResult?.let { Gson().toJson(it) }
                                            )
                                        } else {
                                            val existingId = viewModel.getEventRoomIdByUid(finalUid) ?: eventRoomId
                                            viewModel.saveEventToRoom(
                                                projectName = projectName,
                                                location = location,
                                                description = description,
                                                riskResult = riskResult,
                                                photoFiles = photoFiles,
                                                audioFiles = audioFiles,
                                                selectedDefects = selectedDefects,
                                                digitalAssetFileIds = effectiveAssetFileIds,
                                                isEditMode = existingId != null,
                                                currentEventId = existingId,
                                                structuralDefectDetails = structuralDefectJsonRaw ?: structuralDefectResult?.let { Gson().toJson(it) }
                                            )
                                        }
                                    }

                                    if (saveResult.isFailure) {
                                        isSaving = false
                                        Toast.makeText(context, saveResult.exceptionOrNull()?.message ?: "Local save failed", Toast.LENGTH_LONG).show()
                                        return@launch
                                    }

                                    // 2.3 若之前没有 UID，则从刚保存的 RoomId 反查并缓存 UID
                                    if (finalUid.isBlank()) {
                                        // 更新本地缓存的 eventRoomId 与 loadedEventUid
                                        val savedId = saveResult.getOrNull() ?: -1L
                                        if (savedId > 0L) {
                                            eventRoomId = savedId
                                            val uid = viewModel.getEventUidById(savedId) ?: ""
                                            if (uid.isNotBlank()) {
                                                finalUid = uid
                                                loadedEventUid = uid
                                            }
                                        }
                                        if (finalUid.isBlank()) {
                                            isSaving = false
                                            Toast.makeText(context, "Failed to resolve UID after save", Toast.LENGTH_LONG).show()
                                            return@launch
                                        }
                                    }

                                    // 3) 执行同步上传（已确保本地保存完成）
                                    val (ok, msg) = viewModel.uploadEventWithSyncRetry(
                                        eventUid = finalUid,
                                        projectName = projectName,
                                        location = location,
                                        description = description,
                                        riskResult = riskResult,
                                        photoFiles = photoFiles,
                                        audioFiles = audioFiles,
                                        selectedDefects = selectedDefects,
                                        digitalAssetFileIds = effectiveAssetFileIds,
                                        structuralDefectDetails = structuralDefectJsonRaw ?: structuralDefectResult?.let { Gson().toJson(it) },
                                        maxRetries = 5,
                                        delayMs = 3000
                                    )
                                    isSaving = false
                                    if (ok) {
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        onBack()
                                    } else {
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = !isSaving
                    ) {
                        Text(if (isSaving) "Uploading..." else "Sync")
                    }

                    // Save（右侧）：执行本地保存/更新后再提示
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    // 计算有效的数字资产 fileIds（优先当前fileIds，其次nodeIds→fileIds映射）
                                    val effectiveFileIds: List<String> = when {
                                        currentSelectedStorageFileIds.isNotEmpty() -> currentSelectedStorageFileIds
                                        currentSelectedStorageNodeIds.isNotEmpty() -> try {
                                            viewModel.getFileIdsByNodeIds(currentSelectedStorageNodeIds)
                                        } catch (_: Exception) { emptyList() }
                                        else -> emptyList()
                                    }

                                    // 解析已存在的事件ID（优先UID→RoomID，其次已缓存的eventRoomId）
                                    val idFromUid = when {
                                        eventId.isNotBlank() -> viewModel.getEventRoomIdByUid(eventId)
                                        !loadedEventUid.isNullOrBlank() -> viewModel.getEventRoomIdByUid(loadedEventUid!!)
                                        else -> null
                                    }
                                    val existingEventId = idFromUid ?: eventRoomId
                                    val isEditMode = existingEventId != null && existingEventId > 0
                                    val currentEventId = existingEventId

                                    val result = viewModel.saveEventToRoom(
                                        projectName = projectName,
                                        location = location,
                                        description = description,
                                        riskResult = riskResult,
                                        photoFiles = photoFiles,
                                        audioFiles = audioFiles,
                                        selectedDefects = selectedDefects.toList(),
                                        digitalAssetFileIds = effectiveFileIds,
                                        isEditMode = isEditMode,
                                        currentEventId = currentEventId,
                                        structuralDefectDetails = structuralDefectJsonRaw ?: structuralDefectResult?.let { Gson().toJson(it) }
                                    )

                                    if (result.isSuccess) {
                                        val savedEventId = result.getOrNull() ?: -1L
                                        // 首次保存时，缓存Room主键ID以便后续更新
                                        if (!isEditMode && eventRoomId == null && savedEventId > 0L) {
                                            eventRoomId = savedEventId
                                        }
                                        // 同步持久化事件UID，避免重复插入
                                        if (loadedEventUid.isNullOrBlank() && savedEventId > 0L) {
                                            try {
                                                val savedUid = viewModel.getEventUidById(savedEventId)
                                                if (!savedUid.isNullOrBlank()) {
                                                    loadedEventUid = savedUid
                                                }
                                            } catch (e: Exception) {
                                                Log.w("EventFormScreen", "Resolve UID failed for saved eventId=$savedEventId: ${e.message}")
                                            }
                                        }

                                        Toast.makeText(context, "Saved successfully", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val err = result.exceptionOrNull()?.message ?: "Unknown error"
                                        Toast.makeText(context, "Save failed: ${err}", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF153E8B)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Save", color = Color.White)
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF7F8FA))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 输入信息区：去掉“Event”标题，采用白色卡片+浅阴影，内置两行输入
                androidx.compose.material3.Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = androidx.compose.material3.CardDefaults.cardElevation(
                        defaultElevation = 2.dp
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Event Description",
                            fontSize = 12.sp,
                            color = Color(0xFF90A4AE)
                        )
                        androidx.compose.material3.TextField(
                            value = description,
                            onValueChange = { description = it },
                            placeholder = { Text("Type here") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedPlaceholderColor = Color(0xFFB0BEC5),
                                unfocusedPlaceholderColor = Color(0xFFB0BEC5)
                            ),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default.copy(
                                imeAction = ImeAction.Next,
                                keyboardType = KeyboardType.Text
                            )
                        )
                        Spacer(Modifier.height(10.dp))
                        androidx.compose.material3.HorizontalDivider(color = Color(0xFFE8EAED))
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Location",
                            fontSize = 12.sp,
                            color = Color(0xFF90A4AE)
                        )
                        androidx.compose.material3.TextField(
                            value = location,
                            onValueChange = { location = it },
                            placeholder = { Text("Type here") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedPlaceholderColor = Color(0xFFB0BEC5),
                                unfocusedPlaceholderColor = Color(0xFFB0BEC5)
                            ),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default.copy(
                                imeAction = ImeAction.Done,
                                keyboardType = KeyboardType.Text
                            )
                        )
                        Spacer(Modifier.height(10.dp))
                        androidx.compose.material3.HorizontalDivider(color = Color(0xFFE8EAED))
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 结构缺陷详情区块（统一 SectionCard 样式，右侧箭头进入）
                SectionCard(
                    title = "Structural Defect Details",
                    trailing = {
                        HeaderActionButton(
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open Structural Defect Details",
                            tint = Color(0xFF666666)
                        ) {
                            val intent = StructuralDefectActivity.createIntent(context, structuralDefectResult)
                            structuralDefectLauncher.launch(intent)
                        }
                    }
                ) {
                    if (structuralDefectResult != null) {
                        Text(text = "Details completed", fontSize = 12.sp, color = Color(0xFF4CAF50))
                    } else {
                        Text(text = "Click to add structural defect details", fontSize = 12.sp, color = Color(0xFF666666))
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 统一采用各区块右侧动作按钮，不再使用顶部图标行

                // 风险分析区块：始终显示卡片，右侧为进入评估的箭头
                SectionCard(
                    title = "Risk Analysis",
                    trailing = {
                        HeaderActionButton(
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open Risk Analysis",
                            tint = Color(0xFF666666)
                        ) { showRiskDialog = true }
                    }
                ) {
                    if (riskResult != null) {
                        // 使用绿色圆角胶囊样式展示评估结果
                        RiskResultPill(result = riskResult!!, modifier = Modifier.fillMaxWidth())
                    } else {
                        Text(text = "Tap to assess risk", fontSize = 13.sp, color = Color(0xFF90A4AE))
                    }
                }
                Spacer(Modifier.height(12.dp))

                // 数据库文件展示区卡片（右侧 + 打开选择器）
                SectionCard(
                    title = "Database Files",
                    trailing = {
                        HeaderActionButton(
                            icon = Icons.Default.Add,
                            contentDescription = "Add Database Files"
                        ) {
                            projectUid?.let { uid ->
                                android.util.Log.d("EventFormScreen", "打开数字资产选择页面，传递fileIds: ${currentSelectedStorageFileIds.joinToString()}")
                                onOpenStorage(uid, currentSelectedStorageNodeIds, currentSelectedStorageFileIds)
                            } ?: run {
                                Toast.makeText(context, "ProjectUid not available", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    if (digitalAssetDetails.isNotEmpty()) {
                        DigitalAssetCategorizedDisplay(
                            assets = digitalAssetDetails,
                            onAssetClick = { asset ->
                                if (asset.type != "RISK_MATRIX") {
                                    previewAsset = asset
                                    showAssetPreview = true
                                }
                            },
                            onAssetDelete = { asset ->
                                // Function: removeSelectedAsset
                                // Purpose: Remove a selected digital asset from current event while preserving preview feature
                                // Params: asset (DigitalAssetDetail)
                                // Returns: none; updates state (fileIds/nodeIds/details)
                                scope.launch {
                                    try {
                                        val updatedFileIds = currentSelectedStorageFileIds.filterNot { it == asset.fileId }
                                        currentSelectedStorageFileIds = updatedFileIds
                                        lastNonEmptySelectedStorageFileIds = updatedFileIds

                                        // 精确移除对应 nodeId，避免误清空其他选择
                                        val nodeIdToRemove: String? = try {
                                            viewModel.projectDigitalAssetDao.getByFileId(asset.fileId)?.nodeId
                                        } catch (_: Exception) { null }
                                        if (!nodeIdToRemove.isNullOrBlank()) {
                                            currentSelectedStorageNodeIds = currentSelectedStorageNodeIds.filterNot { it == nodeIdToRemove }
                                            lastNonEmptySelectedStorageNodeIds = lastNonEmptySelectedStorageNodeIds.filterNot { it == nodeIdToRemove }
                                        }

                                        // Refresh details and filenames
                                        if (updatedFileIds.isNotEmpty()) {
                                            digitalAssetDetails = viewModel.getDigitalAssetDetailsByIds(updatedFileIds)
                                            digitalAssetFileNames = viewModel.getFileNamesByIds(updatedFileIds)
                                        } else {
                                            digitalAssetDetails = emptyList()
                                            digitalAssetFileNames = emptyList()
                                        }
                                        Log.d("EventFormScreen", "Removed asset: fileId=${asset.fileId}; remaining=${updatedFileIds.size}")

                                        // 新增：立即持久化保存，修复删除后本地缓存未更新问题
                                        if (!isAutoSaving) {
                                            isAutoSaving = true
                                            try {
                                                // 优先通过事件UID解析已存在的Room ID，确保更新而非插入
                                                val idFromUid = when {
                                                    eventId.isNotBlank() -> viewModel.getEventRoomIdByUid(eventId)
                                                    !loadedEventUid.isNullOrBlank() -> viewModel.getEventRoomIdByUid(loadedEventUid!!)
                                                    else -> null
                                                }
                                                val existingEventId = idFromUid ?: eventRoomId
                                                val isEditMode = existingEventId != null && existingEventId > 0
                                                val currentEventId = existingEventId

                                                val result = viewModel.saveEventToRoom(
                                                    projectName = projectName,
                                                    location = location,
                                                    description = description,
                                                    currentEventId = currentEventId,
                                                    isEditMode = isEditMode,
                                                    riskResult = riskResult,
                                                    photoFiles = photoFiles,
                                                    audioFiles = audioFiles,
                                                    selectedDefects = selectedDefects.toList(),
                                                    digitalAssetFileIds = updatedFileIds,
                                                    structuralDefectDetails = structuralDefectJsonRaw ?: structuralDefectResult?.let { Gson().toJson(it) },
                                                    // 关键：当删除后列表为空时，明确要求清空资产
                                                    forceClearAssets = updatedFileIds.isEmpty()
                                                )

                                                val savedEventId = result.getOrNull()
                                                if (savedEventId != null) {
                                                    Log.d("EventFormScreen", "Auto-saved after asset delete: savedEventId=$savedEventId, remainingAssets=${updatedFileIds.size}")
                                                    if (!isEditMode && eventRoomId == null) {
                                                        eventRoomId = savedEventId
                                                    }
                                                    // 同步持久化事件UID
                                                    if (loadedEventUid.isNullOrBlank()) {
                                                        try {
                                                            val savedUid = viewModel.getEventUidById(savedEventId)
                                                            if (!savedUid.isNullOrBlank()) {
                                                                loadedEventUid = savedUid
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.w("EventFormScreen", "Resolve UID after asset delete failed: ${e.message}")
                                                        }
                                                    }
                                                } else {
                                                    val e = result.exceptionOrNull()
                                                    if (e != null) Log.e("EventFormScreen", "Auto-save after asset delete failed: ${e.message}", e)
                                                }
                                            } catch (e: Exception) {
                                                Log.e("EventFormScreen", "Exception during auto-save after asset delete: ${e.message}", e)
                                            } finally {
                                                isAutoSaving = false
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("EventFormScreen", "Failed to remove asset ${asset.fileId}: ${e.message}", e)
                                    }
                                }
                            }
                        )
                    } else {
                        Text(text = "No files selected", fontSize = 13.sp, color = Color(0xFF90A4AE))
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 拍照片展示区卡片（右侧 + 触发拍照）
                SectionCard(
                    title = "Photo Gallery",
                    trailing = {
                        HeaderActionButton(
                            icon = Icons.Default.Add,
                            contentDescription = "Add Photo"
                        ) {
                            ensurePermissions(arrayOf(Manifest.permission.CAMERA)) { startTakePhoto() }
                        }
                    }
                ) {
                    if (photoFiles.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(photoFiles) { pf ->
                                PhotoThumb(
                                    file = pf,
                                    onClick = { largePhoto = pf },
                                    onDelete = {
                                        try { pf.delete() } catch (_: Exception) {}
                                        photoFiles.remove(pf)
                                    }
                                )
                            }
                        }
                    } else {
                        Text(text = "No photos taken", fontSize = 13.sp, color = Color(0xFF90A4AE))
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 录音文件展示区卡片（右侧 + 弹窗按住录音）
                SectionCard(
                    title = "Audio Files",
                    trailing = {
                        HeaderActionButton(
                            icon = Icons.Default.Add,
                            contentDescription = "Add Audio"
                        ) {
                            ensurePermissions(arrayOf(Manifest.permission.RECORD_AUDIO)) {
                                showAudioRecordSheet = true
                            }
                        }
                    }
                ) {
                    if (audioFiles.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            audioFiles.forEach { af ->
                                AudioPlayerBar(file = af, onDelete = {
                                    try { af.delete() } catch (_: Exception) {}
                                    audioFiles.remove(af)
                                })
                            }
                        }
                    } else {
                        Text(text = "No audio recorded", fontSize = 13.sp, color = Color(0xFF90A4AE))
                    }
                }

                if (largePhoto != null) {
                    LargePhotoDialog(file = largePhoto!!, onDismiss = { largePhoto = null })
                }

                // 底部录音弹窗：按住开始，松开结束，自动保存文件
                if (showAudioRecordSheet) {
                    AudioRecordBottomSheet(
                        isRecording = isRecording,
                        level = voiceLevel,
                        onStart = { startRecording() },
                        onStopAndDismiss = {
                            try { stopRecording() } catch (_: Exception) {}
                            showAudioRecordSheet = false
                        },
                        onDismiss = { showAudioRecordSheet = false }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 关联历史缺陷区块（右侧 + 打开选择对话框）
                SectionCard(
                    title = "Associated Historical Defects",
                    trailing = {
                        HeaderActionButton(
                            icon = Icons.Default.Add,
                            contentDescription = "Add Defect"
                        ) { showDefectSelectionDialog = true }
                    }
                ) {
                    if (selectedDefects.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            selectedDefects.forEach { defect ->
                                DefectInfoCard(
                                    defect = defect,
                                    onDefectClick = { defectEntity -> onOpenDefect?.invoke(defectEntity) },
                                    // 优化：不显示图片，右侧提供删除按钮
                                    showImages = false,
                                    onDeleteClick = {
                                        // 从已选列表移除，并触发持久化保存
                                        selectedDefects.removeAll { it.defectId == defect.defectId }
                                        scope.launch {
                                            if (isAutoSaving) return@launch
                                            isAutoSaving = true
                                            try {
                                                val idFromUid = when {
                                                    eventId.isNotBlank() -> viewModel.getEventRoomIdByUid(eventId)
                                                    !loadedEventUid.isNullOrBlank() -> viewModel.getEventRoomIdByUid(loadedEventUid!!)
                                                    else -> null
                                                }
                                                val existingEventId = idFromUid ?: eventRoomId
                                                val isEditMode = existingEventId != null && existingEventId > 0
                                                val currentEventId = existingEventId

                                                // 计算有效的数字资产 fileIds：优先 current，其次 nodeIds→fileIds 映射，最后回退 lastNonEmpty
                                                val effectiveAssetFileIds: List<String> = when {
                                                    currentSelectedStorageFileIds.isNotEmpty() -> currentSelectedStorageFileIds
                                                    currentSelectedStorageNodeIds.isNotEmpty() -> try {
                                                        viewModel.getFileIdsByNodeIds(currentSelectedStorageNodeIds)
                                                    } catch (_: Exception) { lastNonEmptySelectedStorageFileIds }
                                                    lastNonEmptySelectedStorageFileIds.isNotEmpty() -> lastNonEmptySelectedStorageFileIds
                                                    lastNonEmptySelectedStorageNodeIds.isNotEmpty() -> try {
                                                        viewModel.getFileIdsByNodeIds(lastNonEmptySelectedStorageNodeIds)
                                                    } catch (_: Exception) { emptyList() }
                                                    else -> emptyList()
                                                }

                                                val result = viewModel.saveEventToRoom(
                                                    projectName = projectName,
                                                    location = location,
                                                    description = description,
                                                    currentEventId = currentEventId,
                                                    isEditMode = isEditMode,
                                                    riskResult = riskResult,
                                                    photoFiles = photoFiles,
                                                    audioFiles = audioFiles,
                                                    selectedDefects = selectedDefects.toList(),
                                                    digitalAssetFileIds = effectiveAssetFileIds,
                                                    structuralDefectDetails = structuralDefectJsonRaw ?: structuralDefectResult?.let { Gson().toJson(it) },
                                                    forceClearAssets = effectiveAssetFileIds.isEmpty() && digitalAssetDetails.isEmpty()
                                                )
                                                val savedEventId = result.getOrNull()
                                                if (savedEventId != null) {
                                                    Log.d("EventFormScreen", "Auto-saved after defect delete: savedEventId=$savedEventId, defectCount=${selectedDefects.size}")
                                                    if (!isEditMode && eventRoomId == null) eventRoomId = savedEventId
                                                    if (loadedEventUid.isNullOrBlank()) {
                                                        try {
                                                            val savedUid = viewModel.getEventUidById(savedEventId)
                                                            if (!savedUid.isNullOrBlank()) loadedEventUid = savedUid
                                                        } catch (e: Exception) {
                                                            Log.w("EventFormScreen", "Resolve UID after defect delete failed: ${e.message}")
                                                        }
                                                    }
                                                } else {
                                                    val e = result.exceptionOrNull()
                                                    if (e != null) Log.e("EventFormScreen", "Auto-save after defect delete failed: ${e.message}", e)
                                                }
                                            } catch (e: Exception) {
                                                Log.e("EventFormScreen", "Exception during auto-save after defect delete: ${e.message}", e)
                                            } finally {
                                                isAutoSaving = false
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        Text(text = "No associated defects", fontSize = 13.sp, color = Color(0xFF90A4AE))
                    }
                }

                // 为固定底栏留出空间，避免滚动内容与底部遮挡
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    if (showRiskDialog) {
        RiskAssessmentWizardDialog(
            onDismiss = { r ->
                showRiskDialog = false
                if (r != null) riskResult = r
            },
            loader = projectUid?.let { viewModel.createRiskMatrixLoader(it) } 
                ?: viewModel.createRiskMatrixLoaderByName(projectName),
            initialAnswers = riskResult?.answers,
            // 传入对象格式以支持新缓存回显
            initialAssessmentData = riskResult?.assessmentData,
            projectUid = projectUid,
            projectDigitalAssetDao = viewModel.projectDigitalAssetDao
        )
    }
    
    // 删除确认对话�?
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Event") },
            text = { Text("Are you sure you want to delete this event? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            try {
                                val currentEventId = eventId.toLongOrNull()
                                if (currentEventId != null && loadedEventUid != null) {
                                    // 使用从数据库加载的真实UID
                                    val result = viewModel.deleteEvent(currentEventId, loadedEventUid!!)
                                    if (result.isSuccess) {
                                        isDeleted = true // 标记为已删除，阻止自动保�?
                                        Toast.makeText(context, "Event deleted successfully", Toast.LENGTH_SHORT).show()
                                        onBack() // 删除成功后返回上一�?
                                    } else {
                                        Toast.makeText(context, "Failed to delete event: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Invalid event ID or UID", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error deleting event: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // 关联历史缺陷选择对话�?
    if (showDefectSelectionDialog) {
        DefectSelectionDialog(
            projectName = projectName,
            selectedDefects = selectedDefects.toList(),
            onDismiss = { showDefectSelectionDialog = false },
            onConfirm = { newSelectedDefects ->
                selectedDefects.clear()
                selectedDefects.addAll(newSelectedDefects)
                showDefectSelectionDialog = false
                
                // 立即触发自动保存，确保defect关联变化被保存并更新event_count
                scope.launch {
                    if (isAutoSaving) {
                        Log.d("EventFormScreen", "Skip auto-save after defect selection: save in progress")
                        return@launch
                    }
                    try {
                        isAutoSaving = true
                        // 修复关键逻辑：优先通过事件UID解析已存在的Room ID，确保更新而非插入
                        val idFromUid = when {
                            eventId.isNotBlank() -> viewModel.getEventRoomIdByUid(eventId)
                            !loadedEventUid.isNullOrBlank() -> viewModel.getEventRoomIdByUid(loadedEventUid!!)
                            else -> null
                        }
                        val existingEventId = idFromUid ?: eventRoomId
                        val isEditMode = existingEventId != null && existingEventId > 0
                        val currentEventId = existingEventId

                        // 计算有效的数字资产 fileIds：优先 current fileIds，其次 nodeIds→fileIds 映射，最后回退 lastNonEmpty
                        val effectiveAssetFileIds: List<String> = when {
                            currentSelectedStorageFileIds.isNotEmpty() -> currentSelectedStorageFileIds
                            currentSelectedStorageNodeIds.isNotEmpty() -> try {
                                viewModel.getFileIdsByNodeIds(currentSelectedStorageNodeIds)
                            } catch (_: Exception) { lastNonEmptySelectedStorageFileIds }
                            lastNonEmptySelectedStorageFileIds.isNotEmpty() -> lastNonEmptySelectedStorageFileIds
                            lastNonEmptySelectedStorageNodeIds.isNotEmpty() -> try {
                                viewModel.getFileIdsByNodeIds(lastNonEmptySelectedStorageNodeIds)
                            } catch (_: Exception) { emptyList() }
                            else -> emptyList()
                        }

                        val result = viewModel.saveEventToRoom(
                            projectName = projectName,
                            location = location,
                            description = description,
                            currentEventId = currentEventId,
                            isEditMode = isEditMode,
                            riskResult = riskResult,
                            photoFiles = photoFiles,
                            audioFiles = audioFiles,
                            selectedDefects = selectedDefects.toList(),
                            // 修复：数字资产参数采用有效 fileIds（nodeIds→fileIds 映射兜底）
                            digitalAssetFileIds = effectiveAssetFileIds,
                            structuralDefectDetails = structuralDefectJsonRaw ?: structuralDefectResult?.let { Gson().toJson(it) }
                        )
                        
                        val savedEventId = result.getOrNull()
                        if (savedEventId != null) {
                            Log.d("EventFormScreen", "Auto-saved event after defect selection change: projectName=$projectName, isEditMode=$isEditMode, savedEventId=$savedEventId, defectCount=${selectedDefects.size}")
                            if (!isEditMode && eventRoomId == null) {
                                eventRoomId = savedEventId
                            }
                            // 同步持久化事件UID
                            if (loadedEventUid.isNullOrBlank()) {
                                try {
                                    val savedUid = viewModel.getEventUidById(savedEventId)
                                    if (!savedUid.isNullOrBlank()) {
                                        loadedEventUid = savedUid
                                    }
                                } catch (e: Exception) {
                                    Log.w("EventFormScreen", "Failed to resolve UID after defect change for eventId=$savedEventId: ${e.message}")
                                }
                            }
                        } else {
                            val e = result.exceptionOrNull()
                            if (e != null) {
                                Log.e("EventFormScreen", "Failed to auto-save event after defect selection change: ${e.message}", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("EventFormScreen", "Exception during auto-save after defect selection change: ${e.message}", e)
                    } finally {
                        isAutoSaving = false
                    }
                }
            }
        )
    }
    
    // 新增：离开页面确认弹窗
    if (showExitConfirmDialog) {
        ExitConfirmationDialog(
            onConfirm = {
                showExitConfirmDialog = false
                onBack()
            },
            onDismiss = {
                showExitConfirmDialog = false
            }
        )
    }
    
    // 数字资产预览对话框
    if (showAssetPreview && previewAsset != null) {
        AssetPreviewDialog(
            asset = previewAsset!!,
            onDismiss = {
                showAssetPreview = false
                previewAsset = null
            }
        )
    }
    // 项目选择底部弹窗：用于在同步前选择目标项目
    if (showProjectPicker) {
        ProjectPickerBottomSheet(
            projects = notFinishedProjects,
            defaultProjectUid = projectUid,
            onConfirm = { targetProject ->
                showProjectPicker = false
                scope.launch {
                    val finalUid: String = run {
                        if (eventId.isNotBlank()) {
                            val asLong = eventId.toLongOrNull()
                            if (asLong != null) {
                                viewModel.getEventUidById(asLong) ?: (loadedEventUid ?: "")
                            } else {
                                eventId
                            }
                        } else {
                            loadedEventUid ?: ""
                        }
                    }
                    if (finalUid.isBlank()) {
                        Toast.makeText(context, "Please generate event UID first (save locally then retry)", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    isSaving = true
                    // 计算上传时的有效数字资产 fileIds：优先 current，其次 nodeIds→fileIds，最后回退 lastNonEmpty
                    val uploadEffectiveFileIds: List<String> = when {
                        currentSelectedStorageFileIds.isNotEmpty() -> currentSelectedStorageFileIds
                        currentSelectedStorageNodeIds.isNotEmpty() -> try {
                            viewModel.getFileIdsByNodeIds(currentSelectedStorageNodeIds)
                        } catch (_: Exception) { lastNonEmptySelectedStorageFileIds }
                        lastNonEmptySelectedStorageFileIds.isNotEmpty() -> lastNonEmptySelectedStorageFileIds
                        lastNonEmptySelectedStorageNodeIds.isNotEmpty() -> try {
                            viewModel.getFileIdsByNodeIds(lastNonEmptySelectedStorageNodeIds)
                        } catch (_: Exception) { emptyList() }
                        else -> emptyList()
                    }
                    val (ok, msg) = viewModel.uploadEventWithSyncRetry(
                        eventUid = finalUid,
                        projectName = projectName,
                        location = location,
                        description = description,
                        riskResult = riskResult,
                        photoFiles = photoFiles,
                        audioFiles = audioFiles,
                        selectedDefects = selectedDefects,
                        digitalAssetFileIds = uploadEffectiveFileIds,
                        structuralDefectDetails = structuralDefectJsonRaw ?: structuralDefectResult?.let { Gson().toJson(it) },
                        overrideTargetProjectUid = targetProject.projectUid,
                        maxRetries = 5,
                        delayMs = 3000
                    )
                    isSaving = false
                    if (ok) {
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        onBack()
                    } else {
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { showProjectPicker = false }
        )
    }
}

/**
 * 离开页面确认弹窗组件
 * 当用户在没有关联defect的情况下尝试离开页面时显�?
 * 提供全英文的提示信息
 */
@Composable
private fun ExitConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(
                text = "This event has no historical defect linked.",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Sure",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}

/**
 * 缺陷信息卡片组件
 * 参照历史缺陷列表样式，显示已关联的缺陷信息，包括缺陷编号、风险等级标签、图片缩略图
 * 支持点击跳转到缺陷详情页面，支持图片预览功能
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DefectInfoCard(
    defect: DefectEntity,
    onDefectClick: (DefectEntity) -> Unit,
    modifier: Modifier = Modifier,
    /** 是否显示图片缩略图（默认显示） */
    showImages: Boolean = true,
    /** 右侧删除按钮点击回调（为空则不显示删除按钮） */
    onDeleteClick: (() -> Unit)? = null
) {
    // State: 当前被预览的大图路径
    var largePhotoPath by remember { mutableStateOf<String?>(null) }
    
    // 获取Context
    val context = LocalContext.current
    
    // 获取缺陷图片路径
    val defectImages = remember(defect.defectNo, defect.projectUid) {
        val projectUid = defect.projectUid ?: ""
        val defectNo = defect.defectNo
        if (projectUid.isNotBlank() && defectNo.isNotBlank()) {
            val dir = File(context.filesDir, "history_defects/${projectUid}/${sanitize(defectNo)}")
            if (dir.exists()) {
                dir.listFiles()
                    ?.sortedBy { it.name }
                    ?.map { it.absolutePath }
                    // 移除 .take(3) 限制，支持显示所有图片并左右滑动查看
                    ?: emptyList()
            } else emptyList()
        } else emptyList()
    }
    
    // 主要内容卡片
    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDefectClick(defect) },
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)), // 淡灰色背�?
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // 标题布局：缺陷编号和风险等级标签
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 缺陷编号文本
                    Text(
                        text = "No.${defect.defectNo}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF222222),
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // 风险等级标签
                    if (defect.riskRating.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        RiskLevelTag(riskLevel = defect.riskRating)
                    }

                    // 删除按钮（如果提供删除回调）
                    if (onDeleteClick != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color(0xFFE0E0E0), CircleShape)
                                .clickable { onDeleteClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove defect",
                                tint = Color(0xFF757575),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                
                // 图片缩略图展示区域（可切换显示/隐藏）
                if (showImages && defectImages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DefectThumbnailRow(
                        images = defectImages,
                        onPhotoClick = { path -> largePhotoPath = path }
                    )
                }
            }
        }
    }

    // （已移至 EventFormScreen 顶层）
    
    // 全屏图片预览对话�?
    if (largePhotoPath != null) {
        DefectLargePhotoDialog(
            path = largePhotoPath!!,
            onDismiss = { largePhotoPath = null }
        )
    }
}

/**
 * 缺陷图片缩略图横向列�?
 * 参照历史缺陷列表的ThumbnailRow实现，支持横向滑动和点击预览
 */
@Composable
private fun DefectThumbnailRow(images: List<String>, onPhotoClick: (String) -> Unit) {
    if (images.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(images) { path ->
            DefectFileThumbnail(
                path = path,
                size = 72.dp,
                onClick = { onPhotoClick(path) }
            )
        }
    }
}

/**
 * 单个缺陷文件缩略图组�?
 * 参照历史缺陷列表的FileThumbnail实现，异步加载图片并支持点击预览
 */
@Composable
private fun DefectFileThumbnail(path: String, size: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bitmapState = remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            val bmp = runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
            withContext(Dispatchers.Main) { bitmapState.value = bmp }
        }
    }
    val bmp = bitmapState.value
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "defect thumbnail",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onClick() }
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF3F4F7))
                .clickable { onClick() }
        ) { /* placeholder */ }
    }
}

/**
 * 缺陷大图预览对话�?
 * 参照历史缺陷列表的LargePhotoDialogForPath实现，支持双指缩放和拖拽
 */
@Composable
private fun DefectLargePhotoDialog(path: String, onDismiss: () -> Unit) {
    val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (bitmap != null) {
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                    scale = (scale * zoomChange).coerceIn(1f, 5f)
                    offset = offset + panChange
                }
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Defect Photo Preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .transformable(transformState),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * 生成安全的文件名，移除特殊字�?
 */
private fun sanitize(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

/**
 * 缺陷选择项组�?
 * 按照历史缺陷列表样式展示，包含编号、风险等级标签和图片缩略�?
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DefectSelectionItem(
    defect: DefectEntity,
    isSelected: Boolean,
    onSelectionChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectionChanged(!isSelected) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE3F2FD) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = if (isSelected) BorderStroke(2.dp, Color(0xFF1565C0)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 选择�?
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectionChanged,
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF1565C0),
                    uncheckedColor = Color(0xFF90A4AE)
                )
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 缺陷信息区域
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 标题行：缺陷编号和风险等级标�?
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 缺陷编号文本
                    Text(
                        text = "No.${defect.defectNo}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF222222),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    // 风险等级标签
                    if (!defect.riskRating.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        RiskLevelTag(riskLevel = defect.riskRating!!)
                    }
                }
                
                // 图片缩略图（如果有的话）
                val images = defect.images // images 已经�?List<String> 类型
                if (images.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(images.take(3)) { imagePath -> // 最多显�?张图�?
                            DefectThumbnail(
                                path = imagePath,
                                size = 48.dp
                            )
                        }
                        val imageCount = images.size
                        if (imageCount > 3) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            Color(0xFFF5F5F5),
                                            RoundedCornerShape(6.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+${imageCount - 3}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 风险等级标签组件
 * 
 * 统一的风险等级标签UI组件，具有圆角背景、内边距和颜色配置�?
 * 用于在各个界面中显示风险等级，保持一致的视觉效果�?
 * 
 * @param riskLevel 风险等级字符串（如P1、P2、P3、P4等）
 * @param modifier 修饰�?
 */
@Composable
private fun RiskLevelTag(
    riskLevel: String,
    modifier: Modifier = Modifier
) {
    val colorPair = RiskTagColors.getColorPair(riskLevel)
    
    Box(
        modifier = modifier
            .background(
                color = colorPair.backgroundColor,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = riskLevel.trim().uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = colorPair.textColor
        )
    }
}

/**
 * 风险等级标签组件（旧版本，保持兼容性）
 * 使用统一的颜色配置，与历史缺陷列表保持一致的样式
 */
@Composable
private fun RiskTag(risk: String) {
    val colorPair = RiskTagColors.getColorPair(risk)
    
    Box(
        modifier = Modifier
            .background(colorPair.backgroundColor, RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = risk.trim().uppercase(),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = colorPair.textColor
        )
    }
}

/**
 * 缺陷图片缩略图组�?
 * 用于在选择对话框中显示缺陷的图片预�?
 */
@Composable
private fun DefectThumbnail(path: String, size: Dp) {
    val bitmap = remember(path) { 
        runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }
    
    Box(
        modifier = Modifier
            .size(size)
            .background(Color(0xFFF5F5F5), RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Defect image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // 占位图标
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = "Image placeholder",
                modifier = Modifier
                    .size(size * 0.4f)
                    .align(Alignment.Center),
                tint = Color(0xFFBDBDBD)
            )
        }
    }
}

/**
 * 缺陷选择对话�?
 * 显示当前项目下的历史缺陷列表，支持多�?
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefectSelectionDialog(
    projectName: String,
    selectedDefects: List<DefectEntity>,
    onDismiss: () -> Unit,
    onConfirm: (List<DefectEntity>) -> Unit
) {
    val viewModel: EventFormViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    
    // 获取当前项目的UID（从projectName推导�?
    var projectUid by remember { mutableStateOf("") }
    var availableDefects by remember { mutableStateOf<List<DefectEntity>>(emptyList()) }
    var tempSelectedDefects by remember { mutableStateOf(selectedDefects.toMutableList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // 加载项目UID和缺陷列�?
    LaunchedEffect(projectName) {
        scope.launch {
            try {
                // 通过项目名称获取项目实体来获取projectUid
                val project = viewModel.projectDao.getByExactName(projectName)
                if (project != null) {
                    projectUid = project.projectUid ?: ""
                    if (projectUid.isNotBlank()) {
                        // 订阅缺陷列表
                        launch {
                            viewModel.getDefectsByProjectUid(projectUid).collect { defects: List<DefectEntity> ->
                                // 过滤掉duty of care类型的缺�?
                                availableDefects = defects.filter { defect ->
                                    val type = defect.type.uppercase()
                                    // 过滤掉包含DUTY和CARE关键词的类型
                                    !(type.contains("DUTY") && type.contains("CARE"))
                                }
                                isLoading = false
                            }
                        }
                    } else {
                        isLoading = false
                    }
                } else {
                    isLoading = false
                }
            } catch (e: Exception) {
                Log.e("DefectSelectionDialog", "Failed to load defects: ${e.message}", e)
                isLoading = false
            }
        }
    }
    
    // 底部弹窗样式的对话框
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .clickable(enabled = false) { /* ��ֹ�����͸ */ },
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // 标题栏
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Select Associated Defects",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1565C0)
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF546E7A)
                                )
                            }
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    item {
                        HorizontalDivider(color = Color(0xFFE0E0E0))
                    }
                    
                    // 内容区域
                    if (isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(400.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFF1565C0))
                            }
                        }
                    } else if (availableDefects.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(400.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No historical defect data available",
                                    fontSize = 16.sp,
                                    color = Color(0xFF90A4AE)
                                )
                            }
                        }
                    } else {
                        // 缺陷列表
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(availableDefects) { defect ->
                            DefectSelectionItem(
                                defect = defect,
                                isSelected = tempSelectedDefects.any { it.defectId == defect.defectId },
                                onSelectionChanged = { isSelected ->
                                    tempSelectedDefects = if (isSelected) {
                                        if (!tempSelectedDefects.any { it.defectId == defect.defectId }) {
                                            (tempSelectedDefects + defect).toMutableList()
                                        } else {
                                            tempSelectedDefects
                                        }
                                    } else {
                                        tempSelectedDefects.filter { it.defectId != defect.defectId }.toMutableList()
                                    }
                                }
                            )
                        }
                    }
                    
                    item {
                        HorizontalDivider(color = Color(0xFFE0E0E0))
                    }
                    
                    // 底部按钮
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF546E7A)
                                )
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = { onConfirm(tempSelectedDefects) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1565C0)
                                )
                            ) {
                                Text("Confirm (${tempSelectedDefects.size})")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoThumb(file: File, onClick: () -> Unit, onDelete: () -> Unit) {
    val bitmap = remember(file.absolutePath) { BitmapFactory.decodeFile(file.absolutePath) }
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(Color(0xFFF3F3F3), RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        // 删除按钮（右上角顶部，紧贴角落的小圆形覆盖按钮）
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(20.dp)
                .background(Color(0xAA000000), CircleShape)
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "remove",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun LargePhotoDialog(file: File, onDismiss: () -> Unit) {
    val bitmap = remember(file.absolutePath) { BitmapFactory.decodeFile(file.absolutePath) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (bitmap != null) {
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                    scale = (scale * zoomChange).coerceIn(1f, 5f)
                    offset = offset + panChange
                }
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Photo Preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .transformable(transformState),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            } else {
                Text(
                    text = "Photo decode failed",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Close") }
            }
        }
    }
}

@Composable
private fun AudioPlayerBar(file: File, onDelete: () -> Unit) {
    var mediaPlayer by remember(file.absolutePath) { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember(file.absolutePath) { mutableStateOf(false) }
    var duration by remember(file.absolutePath) { mutableStateOf(0) }
    var position by remember(file.absolutePath) { mutableStateOf(0) }
    var isUserSeeking by remember { mutableStateOf(false) }

    // 预读取媒体时长，提升 UI 首帧体验
    LaunchedEffect(file.absolutePath) {
        duration = try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(file.absolutePath)
            val d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0
            mmr.release()
            d
        } catch (_: Exception) { 0 }
        position = 0
        isPlaying = false
    }

    // 在文件变化或组件销毁时释放资源
    DisposableEffect(file.absolutePath) {
        onDispose {
            try { mediaPlayer?.stop() } catch (_: Exception) {}
            try { mediaPlayer?.release() } catch (_: Exception) {}
            mediaPlayer = null
            isPlaying = false
        }
    }

    // 播放进度刷新（仅在播放时刷新，降低功耗）
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            position = if (!isUserSeeking) (mediaPlayer?.currentPosition ?: 0) else position
            kotlinx.coroutines.delay(500)
        }
    }

    fun play() {
        try {
            if (mediaPlayer == null) {
                val mp = MediaPlayer()
                // 优化：为 MediaPlayer 设置 AudioAttributes，改善音频焦�?路由
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    mp.setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                }
                mp.setDataSource(file.absolutePath)
                mp.prepare()
                if (duration <= 0) duration = mp.duration
                mp.setOnCompletionListener {
                    isPlaying = false
                    position = 0
                }
                mediaPlayer = mp
            }
            mediaPlayer?.start()
            isPlaying = true
        } catch (_: Exception) {
            isPlaying = false
        }
    }

    fun pause() {
        try { mediaPlayer?.pause() } catch (_: Exception) {}
        isPlaying = false
    }

    fun seekTo(targetMs: Int) {
        try { mediaPlayer?.seekTo(targetMs.coerceIn(0, duration)) } catch (_: Exception) {}
        position = targetMs.coerceIn(0, duration)
    }

    // 时间格式�?mm:ss
    fun fmt(ms: Int): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format("%02d:%02d", m, s)
    }

    // 标题改为录制时间（基于文件修改时间）
    val timeTitle = remember(file) { SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(file.lastModified())) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF5F5F5),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                // 文件名过长时单行省略，避免将右侧按钮挤出可视区域
                Text(
                    text = timeTitle,
                    fontSize = 12.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (isPlaying) {
                    IconButton(onClick = { pause() }) { Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = Color(0xFF1F1F1F), modifier = Modifier.size(22.dp)) }
                } else {
                    IconButton(onClick = { play() }) { Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color(0xFF1F1F1F), modifier = Modifier.size(22.dp)) }
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFB00020), modifier = Modifier.size(20.dp)) }
            }
            // 进度条与时间
            if (duration > 0) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(text = fmt(position), fontSize = 11.sp, color = Color(0xFF999999))
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = position.coerceIn(0, duration).toFloat(),
                        onValueChange = {
                            isUserSeeking = true
                            position = it.toInt()
                        },
                        valueRange = 0f..duration.toFloat(),
                        onValueChangeFinished = {
                            seekTo(position)
                            isUserSeeking = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = fmt(duration), fontSize = 11.sp, color = Color(0xFF999999))
                }
            }
        }
    }
}

@Composable
private fun VoiceRecordingOverlay(level: Float, modifier: Modifier = Modifier) {
    /**
     * 文件级注释：录音波形覆盖层
     * 职责：在页面底部显示类似微信语音的动态波浪效果；根据录音实时音量 level(0..1) 调整波形振幅。
     * 设计：使用 rememberInfiniteTransition 驱动相位滚动，以 Canvas 绘制多条柱状波形；采用 DP 插值与浅色渐变。
     * 参数：
     * - level: 当前归一化音量（0f..1f），由 MediaRecorder.getMaxAmplitude() 平滑得到
     * - modifier: 容器修饰符，用于定位到底部居中等
     */
    val phase by rememberInfiniteTransition(label = "voice_wave").animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI.toFloat()),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Recording",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(text = "Recording...", color = Color.White, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            // 动态波浪：多段柱形条，随相位滚动并叠加音量级别
            val barCount = 28
            val minH = 6.dp
            val maxH = 26.dp
            val barWidth = 4.dp
            val gap = 2.dp
            Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                val w = size.width
                val h = size.height
                val totalGapPx = (barCount - 1) * gap.toPx()
                val barWidthPx = barWidth.toPx()
                val usableWidth = w - totalGapPx
                val step = usableWidth / barCount
                for (i in 0 until barCount) {
                    val t = i.toFloat() / (barCount - 1)
                    // 计算滚动正弦波，加入音量权重，避免完全静音时无反馈
                    val wave = abs(sin(t * (2f * PI).toFloat() + phase))
                    val weight = (0.35f + 0.65f * level).coerceIn(0.35f, 1f)
                    val hDp = lerp(minH, maxH, (wave * weight).coerceIn(0f, 1f))
                    val barH = hDp.toPx()
                    val x = i * (step + gap.toPx())
                    val y = (h - barH) / 2f
                    drawRoundRect(
                        color = Color(0xFF64B5F6),
                        topLeft = androidx.compose.ui.geometry.Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(barWidthPx, barH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioRecordBottomSheet(
    isRecording: Boolean,
    level: Float,
    onStart: () -> Unit,
    onStopAndDismiss: () -> Unit,
    onDismiss: () -> Unit
) {
    /**
     * 函数级注释：AudioRecordBottomSheet
     * 用途：显示底部弹窗提供“按住录音，松开结束”的交互；在录音过程中展示波形动画。
     * 参数：
     * - isRecording: 当前录音状态（用于处理外部关闭时的安全停止）
     * - level: 录音音量归一化值（0..1），驱动波形幅度
     * - onStart: 开始录音回调（按下触发）
     * - onStopAndDismiss: 结束录音并关闭弹窗的回调（松开触发或外部关闭触发）
     * - onDismiss: 关闭弹窗回调（未录音时直接关闭）
     * 返回：无
     * 逻辑：
     * - 采用 Material3 ModalBottomSheet 实现底部弹窗；
     * - 大圆形按钮使用 detectTapGestures(onPress) 捕获按下与释放；
     * - 录音过程通过 VoiceRecordingOverlay 展示动态波形效果。
     */
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = {
            if (isRecording) onStopAndDismiss() else onDismiss()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Press and hold to record",
                fontSize = 14.sp,
                color = Color(0xFF546E7A)
            )
            // 录音波浪效果移至麦克风按钮上方
            if (isRecording) {
                VoiceRecordingOverlay(
                    level = level,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1565C0))
                    .pointerInput(Unit) {
                        // 优化：兼容较低版本 Compose，移除 awaitFirstDown，改为按压检测循环
                        awaitPointerEventScope {
                            // 等待任意触点进入按压状态即开始录音
                            var started = false
                            while (!started) {
                                val event = awaitPointerEvent()
                                val anyPressed = event.changes.any { it.pressed }
                                if (anyPressed) {
                                    runCatching { onStart() }
                                    event.changes.forEach { it.consume() }
                                    started = true
                                }
                            }
                            // 等待所有触点抬起或取消后结束录音并关闭弹窗
                            var released = false
                            while (!released) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                                val anyPressed = event.changes.any { it.pressed }
                                if (!anyPressed) {
                                    released = true
                                }
                            }
                            runCatching { onStopAndDismiss() }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Record",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}