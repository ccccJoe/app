/**
 * 文件名: AssetPreviewDialog.kt
 * 作者: SIMS Android Team
 * 描述: 数字资产预览对话框组件，支持图片、PDF、音频等多种类型文件的预览
 * 创建时间: 2024
 */

package com.simsapp.ui.event.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import com.example.sims_android.ui.event.DigitalAssetDetail
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 数字资产预览对话框
 * 
 * @param asset 要预览的数字资产详情
 * @param onDismiss 关闭对话框的回调
 */
@Composable
fun AssetPreviewDialog(
    asset: DigitalAssetDetail,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = asset.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }
                
                Divider()
                
                // 预览内容
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (asset.type.uppercase()) {
                        "PIC", "IMAGE", "JPG", "JPEG", "PNG", "GIF", "BMP", "WEBP" -> {
                            ImagePreview(asset = asset, context = context)
                        }
                        "PDF", "DOC", "DOCUMENT" -> {
                            PdfPreview(asset = asset, context = context)
                        }
                        "REC", "RECORDING", "MP3", "WAV", "M4A", "AAC" -> {
                            AudioPreview(asset = asset, context = context)
                        }
                        else -> {
                            UnsupportedPreview(asset = asset)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 图片预览组件
 */
@Composable
private fun ImagePreview(
    asset: DigitalAssetDetail,
    context: Context
) {
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    
    val imageFile = remember(asset) {
        findAssetLocalFile(context, asset)
    }
    
    if (imageFile != null && imageFile.exists()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageFile)
                .crossfade(true)
                .build(),
            contentDescription = asset.fileName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            onLoading = { isLoading = true },
            onSuccess = { 
                isLoading = false
                hasError = false
            },
            onError = { 
                isLoading = false
                hasError = true
            }
        )
    } else {
        // 显示文件不存在的提示
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.ImageNotSupported,
                contentDescription = "Image not found",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Image file not found",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "File may be stored in cloud",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
    
    // 加载指示器
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
    
    // 错误提示
    if (hasError) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error loading image",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Failed to load image",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * PDF预览组件
 */
@Composable
private fun PdfPreview(
    asset: DigitalAssetDetail,
    context: Context
) {
    var pdfPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    val pdfFile = remember(asset) {
        findAssetLocalFile(context, asset)
    }
    
    LaunchedEffect(pdfFile) {
        if (pdfFile != null && pdfFile.exists()) {
            try {
                isLoading = true
                hasError = false
                pdfPages = loadPdfPages(pdfFile)
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
                hasError = true
                errorMessage = e.message ?: "Unknown error"
                android.util.Log.e("AssetPreviewDialog", "Error loading PDF: ${e.message}", e)
            }
        } else {
            isLoading = false
            hasError = true
            errorMessage = "PDF file not found"
        }
    }
    
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading PDF...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        hasError -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = "PDF Error",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Failed to load PDF",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        
        pdfPages.isNotEmpty() -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(pdfPages.withIndex().toList()) { (index, bitmap) ->
                    Column {
                        Text(
                            text = "Page ${index + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "PDF Page ${index + 1}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
            }
        }
        
        else -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = "Empty PDF",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "PDF is empty",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 音频预览组件
 */
@Composable
private fun AudioPreview(
    asset: DigitalAssetDetail,
    context: Context
) {
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    val audioFile = remember(asset) {
        findAssetLocalFile(context, asset)
    }
    
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AudioFile,
            contentDescription = "Audio File",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = asset.fileName,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (audioFile != null && audioFile.exists()) {
            Button(
                onClick = {
                    try {
                        if (isPlaying) {
                            mediaPlayer?.pause()
                            isPlaying = false
                        } else {
                            if (mediaPlayer == null) {
                                mediaPlayer = MediaPlayer().apply {
                                    setDataSource(audioFile.absolutePath)
                                    prepare()
                                    setOnCompletionListener {
                                        isPlaying = false
                                    }
                                }
                            }
                            mediaPlayer?.start()
                            isPlaying = true
                        }
                        hasError = false
                    } catch (e: Exception) {
                        hasError = true
                        errorMessage = e.message ?: "Unknown error"
                        android.util.Log.e("AssetPreviewDialog", "Error playing audio: ${e.message}", e)
                    }
                },
                modifier = Modifier.size(64.dp),
                shape = androidx.compose.foundation.shape.CircleShape
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp)
                )
            }
        } else {
            Text(
                text = "Audio file not found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        
        if (hasError) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Error: $errorMessage",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

/**
 * 不支持预览的内容
 */
@Composable
private fun UnsupportedPreview(
    asset: DigitalAssetDetail
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.InsertDriveFile,
            contentDescription = "Unsupported file type",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Preview not supported",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "File type: ${asset.type}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * 加载PDF页面为Bitmap列表
 */
private suspend fun loadPdfPages(pdfFile: File): List<Bitmap> = withContext(Dispatchers.IO) {
    val pages = mutableListOf<Bitmap>()
    
    try {
        val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val pdfRenderer = PdfRenderer(fileDescriptor)
        
        for (i in 0 until pdfRenderer.pageCount) {
            val page = pdfRenderer.openPage(i)
            val bitmap = Bitmap.createBitmap(
                page.width * 2, // 提高分辨率
                page.height * 2,
                Bitmap.Config.ARGB_8888
            )
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pages.add(bitmap)
            page.close()
        }
        
        pdfRenderer.close()
        fileDescriptor.close()
    } catch (e: Exception) {
        throw e
    }
    
    pages
}

/**
 * 查找资产的本地文件
 * 
 * 优先使用数据库中存储的localPath，如果不存在则尝试在标准目录中查找
 */
private fun findAssetLocalFile(context: Context, asset: DigitalAssetDetail): File? {
    android.util.Log.d("AssetPreviewDialog", "Looking for local file: fileId=${asset.fileId}, fileName=${asset.fileName}, localPath=${asset.localPath}")
    
    // 1. 优先使用数据库中存储的localPath
    if (!asset.localPath.isNullOrEmpty()) {
        val file = File(asset.localPath)
        if (file.exists()) {
            android.util.Log.d("AssetPreviewDialog", "Found file using localPath: ${file.absolutePath}")
            return file
        } else {
            android.util.Log.w("AssetPreviewDialog", "File not found at localPath: ${asset.localPath}")
        }
    }
    
    // 2. 如果localPath不存在或文件不存在，尝试在数字资产目录中查找
    val digitalAssetsDir = File(context.filesDir, "digital_assets")
    
    // 尝试不同的文件名格式
    val possibleFileNames = listOf(
        "${asset.fileId}.${getFileExtensionFromType(asset.type)}", // fileId + 扩展名
        "${asset.fileId}.pdf", // 默认PDF扩展名
        "${asset.fileId}.jpg", // 默认图片扩展名
        "${asset.fileId}.png", // PNG图片
        "${asset.fileId}.mp3", // 音频文件
        asset.fileId, // 仅fileId
        asset.fileName // 原始文件名
    )
    
    for (fileName in possibleFileNames) {
        val file = File(digitalAssetsDir, fileName)
        if (file.exists()) {
            android.util.Log.d("AssetPreviewDialog", "Found file using fallback search: ${file.absolutePath}")
            return file
        }
    }
    
    android.util.Log.w("AssetPreviewDialog", "File not found for asset: fileId=${asset.fileId}, fileName=${asset.fileName}")
    return null
}

/**
 * 根据文件类型获取文件扩展名
 */
private fun getFileExtensionFromType(type: String): String {
    return when (type.uppercase()) {
        "PDF", "DOC", "DOCUMENT" -> "pdf"
        "PIC", "IMAGE", "JPG", "JPEG" -> "jpg"
        "PNG" -> "png"
        "GIF" -> "gif"
        "BMP" -> "bmp"
        "WEBP" -> "webp"
        "REC", "RECORDING", "MP3" -> "mp3"
        "WAV" -> "wav"
        "M4A" -> "m4a"
        "AAC" -> "aac"
        "MP4" -> "mp4"
        else -> "dat" // 默认扩展名
    }
}