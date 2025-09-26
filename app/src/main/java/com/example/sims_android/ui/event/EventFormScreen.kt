package com.simsapp.ui.event

import android.Manifest
import android.media.MediaRecorder
import android.os.Build
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.simsapp.ui.event.EventFormViewModel
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import android.widget.Toast
// 新增：录音 HUD 动画相关 import
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

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.abs
import android.util.Log

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
     onBack: () -> Unit,
     onOpenStorage: () -> Unit,
     selectedStorage: List<String> = emptyList()
 ) {
     // 通过 Hilt 获取 ViewModel，提供风险矩阵加载能力
     val viewModel: EventFormViewModel = hiltViewModel()
     val context = LocalContext.current
     val scope = rememberCoroutineScope()

     var location by remember { mutableStateOf("") }
     var description by remember { mutableStateOf("") }
     // 新增：维护 Room 中草稿事件的 eventId（便于后续根据 ID 继续编辑）
     var eventRoomId by remember { mutableStateOf<Long?>(null) }
     val photoFiles = remember { mutableStateListOf<File>() }
     val audioFiles = remember { mutableStateListOf<File>() }
     var isRecording by remember { mutableStateOf(false) }
     var isSaving by remember { mutableStateOf(false) }
     // 拍照进行中的临时文件，仅在成功回调后加入列表
     var pendingPhoto by remember { mutableStateOf<File?>(null) }
     // 大图预览选择的照片
     var largePhoto by remember { mutableStateOf<File?>(null) }
     // 录音进行中的临时文件，录制成功后再加入列表
     var pendingAudio by remember { mutableStateOf<File?>(null) }
     // 风险评估向导弹窗显示状态与结果缓存
     var showRiskDialog by remember { mutableStateOf(false) }
     var riskResult by remember { mutableStateOf<RiskAssessmentResult?>(null) }
     // 删除确认弹窗显示状态（仅编辑态）
    var showDeleteDialog by remember { mutableStateOf(false) }
    // 删除状态标记，用于阻止删除后的自动保存
    var isDeleted by remember { mutableStateOf(false) }

    // 新增：页面离开时自动保存逻辑
    DisposableEffect(Unit) {
        onDispose {
            // 页面离开时，如果有非空数据且未被删除则自动保存到数据库
            if (!isDeleted && (location.isNotBlank() || description.isNotBlank() || photoFiles.isNotEmpty() || audioFiles.isNotEmpty() || riskResult != null)) {
                // 使用runBlocking确保在页面销毁前完成保存
                runBlocking {
                    try {
                        // 判断是新建还是编辑模式
                        val isEditMode = eventId.isNotBlank()
                        val currentEventId = if (isEditMode) eventId.toLongOrNull() else eventRoomId
                        
                        val result = viewModel.saveEventToRoom(
                            projectName = projectName,
                            location = location,
                            description = description,
                            currentEventId = currentEventId,
                            isEditMode = isEditMode,
                            riskResult = riskResult,
                            photoFiles = photoFiles,
                            audioFiles = audioFiles
                        )
                        
                        result.onSuccess { savedEventId ->
                            Log.d("EventFormScreen", "Auto-saved event on page exit: projectName=$projectName, location=$location, descLen=${description.length}, isEditMode=$isEditMode, savedEventId=$savedEventId")
                        }.onFailure { e ->
                            Log.e("EventFormScreen", "Failed to auto-save event on page exit: ${e.message}", e)
                        }
                    } catch (e: Exception) {
                        Log.e("EventFormScreen", "Exception during auto-save on page exit: ${e.message}", e)
                    }
                }
            }
        }
    }

    // 进入时如果有 eventId，则从数据库加载事件数据并回显
    LaunchedEffect(eventId) {
        if (eventId.isNotBlank()) {
            // 首先尝试从数据库加载事件数据
            val eventIdLong = eventId.toLongOrNull()
            if (eventIdLong != null) {
                val result = viewModel.loadEventFromRoom(eventIdLong)
                result.onSuccess { eventEntity ->
                    if (eventEntity != null) {
                        // 从数据库加载的数据回显到UI
                        location = eventEntity.location ?: ""
                        description = eventEntity.content
                        eventRoomId = eventEntity.eventId
                        
                        // 回显风险评估结果
                        if (eventEntity.riskLevel != null && eventEntity.riskScore != null) {
                            val answers = eventEntity.riskAnswers?.let { answersJson ->
                                try {
                                    val gson = com.google.gson.Gson()
                                    val type = object : com.google.gson.reflect.TypeToken<List<RiskAnswer>>() {}.type
                                    gson.fromJson<List<RiskAnswer>>(answersJson, type)
                                } catch (e: Exception) {
                                    Log.e("EventFormScreen", "Failed to parse risk answers: ${e.message}", e)
                                    null
                                }
                            }
                            riskResult = RiskAssessmentResult(
                                level = eventEntity.riskLevel,
                                score = eventEntity.riskScore,
                                answers = answers
                            )
                        }
                        
                        // 回显图片文件
                        photoFiles.clear()
                        eventEntity.photoFiles.forEach { photoPath ->
                            val photoFile = File(photoPath)
                            if (photoFile.exists()) {
                                photoFiles.add(photoFile)
                            }
                        }
                        
                        // 回显音频文件
                        audioFiles.clear()
                        eventEntity.audioFiles.forEach { audioPath ->
                            val audioFile = File(audioPath)
                            if (audioFile.exists()) {
                                audioFiles.add(audioFile)
                            }
                        }
                        
                        Log.d("EventFormScreen", "Loaded complete event from database: eventId=$eventIdLong, location=$location, " +
                            "descLen=${description.length}, riskLevel=${eventEntity.riskLevel}, photoCount=${photoFiles.size}, audioCount=${audioFiles.size}")
                    }
                }.onFailure { e ->
                    Log.e("EventFormScreen", "Failed to load event from database: ${e.message}", e)
                }
            }
            
            // 然后尝试从本地文件系统加载额外数据（如风险评估、图片、音频等）
            // 注意：优先使用数据库中的数据，只有在数据库中没有时才从文件系统加载
            val baseDir = File(context.filesDir, "events")
            val eventDir = File(baseDir, eventId)
            val meta = File(eventDir, "meta.json")
            if (meta.exists()) {
                runCatching {
                    val obj = org.json.JSONObject(meta.readText())
                    // 如果数据库中没有location和description，则从meta.json中读取
                    if (location.isBlank()) location = obj.optString("location", "")
                    if (description.isBlank()) description = obj.optString("description", "")
                    
                    // 如果数据库中没有风险评估结果，则从meta.json中读取
                    if (riskResult == null) {
                        val riskObj = obj.optJSONObject("risk")
                        if (riskObj != null) {
                            val level = riskObj.optString("priority", "")
                            val score = riskObj.optDouble("score", 0.0)
                            // 解析 answers（如不存在则为 null）
                            val answersArr = riskObj.optJSONArray("answers")
                            val answers = if (answersArr != null) {
                                val list = mutableListOf<RiskAnswer>()
                                for (i in 0 until answersArr.length()) {
                                    val a = answersArr.optJSONObject(i) ?: continue
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
                                list
                            } else null
                            if (level.isNotBlank()) riskResult = RiskAssessmentResult(level = level, score = score, answers = answers)
                        }
                    }
                    
                    // 如果数据库中没有图片和音频文件，则从meta.json中读取
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
                }.onFailure { e ->
                    Log.e("EventFormScreen", "Failed to load event metadata from file: ${e.message}", e)
                }
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

    // 新增：权限快速判断（避免长按期间弹窗导致逻辑错乱）
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
    // 录音音量级别（0f..1f），用于驱动底部波浪动画
    var voiceLevel by remember { mutableStateOf(0f) }

    /**
     * 开始录音
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
            // 删除无效的 m4a 文件
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
            // 失败则丢弃临时文件
            try { pendingAudio?.delete() } catch (_: Exception) {}
            pendingAudio = null
        }
    }

    /**
     * 停止录音并释放资源
     */
    fun stopRecording() {
        Log.i("EventForm", "stopRecording: begin")
        val r = recorder
        if (r != null) {
            try {
                // 某些设备要求至少录制数百毫秒，否则 stop 可能抛异常或导致 native 崩溃
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
            // 将 0..32767 的幅度归一化到 0..1，并做指数平滑以避免跳变
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

    Scaffold {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFEAF2FF), Color(0xFFF7FAFF))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Next,
                        keyboardType = KeyboardType.Text
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Text
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 风险评估图标：点击打开风险矩阵评估
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE3F2FD), CircleShape)
                            .clickable { showRiskDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        // 自定义风险图标加大尺寸
                        RiskAssessmentIcon(size = 36.dp)
                    }
                
                    // 数据库图标：点击后进入“数据库文件展示区”的文件选择器
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE3F2FD), CircleShape)
                            .clickable { onOpenStorage() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Storage,
                            contentDescription = "Database",
                            tint = Color(0xFF1565C0),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                
                    // 拍照图标：点击触发拍照，仅图标不显示文字
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE3F2FD), CircleShape)
                            .clickable {
                                ensurePermissions(arrayOf(Manifest.permission.CAMERA)) { startTakePhoto() }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Take Photo",
                            tint = Color(0xFF1565C0),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                
                    // 录音图标：长按开始录音，松开停止；仅图标不显示文字
                    val micCircleModifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) Color(0xFFFFEBEE) else Color(0xFFE3F2FD), CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
                                        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                        return@detectTapGestures
                                    }
                                    startRecording()
                                    tryAwaitRelease()
                                    if (isRecording) stopRecording()
                                }
                            )
                        }
                    Box(micCircleModifier, contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Record",
                            tint = if (isRecording) Color(0xFFB00020) else Color(0xFF1565C0),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // 回显风险评估结果（保持不变）
                if (riskResult != null) {
                    Spacer(Modifier.height(8.dp))
                    RiskResultBar(result = riskResult!!, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                }

                Card(shape = RoundedCornerShape(10.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(text = "Data Results", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        // 原拍照与录音按钮（含英文文案）已移除，功能迁移到顶部图标区
                        // 这里保留媒体展示区（图片/录音）不变
                        Spacer(Modifier.height(12.dp))
                        Text(text = "拍照图片展示区", color = Color(0xFF546E7A), fontSize = 13.sp)
                        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                        if (photoFiles.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
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
                        }

                        Spacer(Modifier.height(16.dp))
                        Text(text = "录音文件展示区", color = Color(0xFF546E7A), fontSize = 13.sp)
                        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                        if (audioFiles.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                audioFiles.forEach { af ->
                                    AudioPlayerBar(file = af, onDelete = {
                                        try { af.delete() } catch (_: Exception) {}
                                        audioFiles.remove(af)
                                    })
                                }
                            }
                        }

                        // 新增：数据库文件展示区（模拟选择结果回显）
                        Spacer(Modifier.height(16.dp))
                        Text(text = "数据库文件展示区", color = Color(0xFF546E7A), fontSize = 13.sp)
                        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                        if (selectedStorage.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                selectedStorage.forEach { name ->
                                    Text(text = name, fontSize = 13.sp, color = Color(0xFF37474F))
                                }
                            }
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Text(text = "暂无选择", fontSize = 13.sp, color = Color(0xFF90A4AE))
                        }
                    }
                }

                if (largePhoto != null) {
                    LargePhotoDialog(file = largePhoto!!, onDismiss = { largePhoto = null })
                }

                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (isSaving) return@Button
                            isSaving = true
                            scope.launch {
                                // 按钮：仅上传到云，不做本地保存
                                val uid = eventId
                                if (uid.isBlank()) {
                                    Toast.makeText(context, "请先生成事件UID（保存本地后重试）", Toast.LENGTH_SHORT).show()
                                    isSaving = false
                                    return@launch
                                }
                                val (ok, msg) = viewModel.uploadEvent(uid)
                                isSaving = false
                                if (ok) {
                                    Toast.makeText(context, "上传成功", Toast.LENGTH_SHORT).show()
                                    onBack()
                                } else {
                                    Toast.makeText(context, "上传失败: $msg", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isSaving
                    ) { Text(if (isSaving) "Uploading..." else "同步到云") }

                    // 只有在编辑现有事件时才显示删除按钮
                    if (eventId.isNotBlank() && eventId != "0") {
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) { 
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Event",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete") 
                        }
                    }
                }
            }
        }
    }

    if (showRiskDialog) {
        RiskAssessmentWizardDialog(
            onDismiss = { r ->
                showRiskDialog = false
                if (r != null) riskResult = r
            },
            loader = viewModel.createRiskMatrixLoader(),
            initialAnswers = riskResult?.answers
        )
    }
    
    // 删除确认对话框
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
                                if (currentEventId != null) {
                                    // 使用eventId作为本地文件夹标识符
                                    val eventUid = currentEventId.toString()
                                    
                                    val result = viewModel.deleteEvent(currentEventId, eventUid)
                                    if (result.isSuccess) {
                                        isDeleted = true // 标记为已删除，阻止自动保存
                                        Toast.makeText(context, "Event deleted successfully", Toast.LENGTH_SHORT).show()
                                        onBack() // 删除成功后返回上一页
                                    } else {
                                        Toast.makeText(context, "Failed to delete event: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Invalid event ID", Toast.LENGTH_SHORT).show()
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
                // 优化：为 MediaPlayer 设置 AudioAttributes，改善音频焦点/路由
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

    // 时间格式化 mm:ss
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
     * 设计：使用 rememberInfiniteTransition 驱动相位滚动，以 Canvas 绘制多段柱状波形；采用 DP 插值与浅色渐变。
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


// 删除重复的底部上传按钮占位片段，已将主按钮改为“同步到云”并调用 viewModel.uploadEvent。
// 此处保留空白以维持文件结构与函数闭合。
// Button(
//     onClick = {
//         if (isSaving) return@Button
//         isSaving = true
//         scope.launch {
//             // TODO: 旧占位上传入口已移除
//             isSaving = false
//             Toast.makeText(context, "TODO removed", Toast.LENGTH_SHORT).show()
//         }
//     },
//     enabled = !isSaving
// ) { Text(if (isSaving) "Uploading..." else "同步到云") }