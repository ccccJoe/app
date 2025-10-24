/*
 * File: QRScanActivity.kt
 * Description: QR code scanning activity with camera preview and authentication logic
 * Author: SIMS Team
 */
package com.simsapp.ui.auth

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sims_android.ui.theme.SIMSAndroidTheme
import com.simsapp.utils.QRCodeScanner
import dagger.hilt.android.AndroidEntryPoint

/**
 * QRScanActivity
 * 
 * 职责：
 * - 提供二维码扫描界面
 * - 处理扫描结果和认证逻辑
 * - 管理相机预览和权限
 * 
 * 设计思路：
 * - 使用Compose UI构建界面
 * - 集成CameraX进行扫描
 * - 通过ViewModel处理业务逻辑
 */
@AndroidEntryPoint
class QRScanActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "QRScanActivity"
        const val EXTRA_QR_RESULT = "qr_result"
        
        fun createIntent(activity: Activity): Intent {
            return Intent(activity, QRScanActivity::class.java)
        }
    }
    
    private val viewModel: QRScanViewModel by viewModels()
    
    // 在Activity级别创建QRCodeScanner，确保在正确的生命周期时机注册
    private lateinit var qrCodeScanner: QRCodeScanner
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // 在onCreate中初始化QRCodeScanner
            initializeQRScanner()
            
            setContent {
                SIMSAndroidTheme {
                    QRScanScreen(
                        viewModel = viewModel,
                        onQRCodeScanned = { qrValue ->
                            handleQRCodeScanned(qrValue)
                        },
                        onBack = {
                            finish()
                        },
                        qrCodeScanner = qrCodeScanner
                    )
                }
            }
            
            // 观察ViewModel状态
            observeViewModelStates()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create QRScanActivity", e)
            Toast.makeText(this, "Failed to initialize scanner: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    /**
     * 初始化QR扫描器
     */
    private fun initializeQRScanner() {
        try {
            // 创建一个临时的PreviewView用于初始化
            val tempPreviewView = PreviewView(this)
            
            qrCodeScanner = QRCodeScanner(
                activity = this,
                previewView = tempPreviewView,
                onQRCodeScanned = { qrCode ->
                    handleQRCodeScanned(qrCode)
                },
                onError = { error ->
                    runOnUiThread {
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    }
                }
            )
            
            // 不在这里调用initialize()，而是在权限检查后调用
            // 如果已有权限，直接初始化；否则等待权限授予
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                qrCodeScanner.initialize()
            } else {
                // 权限未授予，QRCodeScanner的initialize()会自动请求权限
                qrCodeScanner.initialize()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing QR scanner", e)
            throw e
        }
    }
    
    /**
     * 处理扫码结果
     */
    private fun handleQRCodeScanned(qrValue: String) {
        Log.d(TAG, "QR Code scanned: $qrValue")
        
        // 停止扫描
        qrCodeScanner?.stopScanning()
        
        // 处理认证逻辑
        viewModel.handleQRCode(qrValue)
    }
    
    /**
     * 观察ViewModel状态变化
     */
    private fun observeViewModelStates() {
        // 观察认证结果
        viewModel.authResult.observe(this) { result ->
            result?.let { (success, message) ->
                if (success) {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    // 返回成功结果
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    // 重新开始扫描
                    qrCodeScanner?.initialize()
                }
            }
        }
    }
    
    /**
     * 更新PreviewView
     * 当Compose中的AndroidView创建后调用此方法
     */
    fun updatePreviewView(previewView: PreviewView) {
        if (::qrCodeScanner.isInitialized) {
            qrCodeScanner.updatePreviewView(previewView)
        }
    }

    /**
     * 处理权限请求结果
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            QRCodeScanner.CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 权限已授予，初始化扫描器
                    Log.d(TAG, "Camera permission granted, initializing scanner")
                    qrCodeScanner.initialize()
                } else {
                    // 权限被拒绝
                    Log.w(TAG, "Camera permission denied")
                    Toast.makeText(this, "Camera permission is required for QR scanning", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            qrCodeScanner?.stopScanning()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scanner in onDestroy", e)
        }
        super.onDestroy()
    }
}

/**
 * 二维码扫描界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScanScreen(
    viewModel: QRScanViewModel,
    onQRCodeScanned: (String) -> Unit,
    onBack: () -> Unit,
    qrCodeScanner: QRCodeScanner? = null
) {
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val showConfirmDialog by viewModel.showConfirmDialog.collectAsStateWithLifecycle()
    var isFlashlightOn by remember { mutableStateOf(false) }
    var previewView: PreviewView? by remember { mutableStateOf(null) }
    var scanner: QRCodeScanner? by remember { mutableStateOf(qrCodeScanner) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 相机预览
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { preview ->
                    previewView = preview
                    // 通知Activity更新PreviewView
                    (ctx as QRScanActivity).updatePreviewView(preview)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // 顶部工具栏
        TopAppBar(
            title = {
                Text(
                    text = "Scan QR Code",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            actions = {
                // 闪光灯切换按钮
                IconButton(
                    onClick = {
                        scanner?.toggleFlashlight()
                        isFlashlightOn = !isFlashlightOn
                    }
                ) {
                    Icon(
                        imageVector = if (isFlashlightOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Toggle Flashlight",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            ),
            modifier = Modifier.statusBarsPadding()
        )
        
        // 扫描框
        ScanningFrame(
            modifier = Modifier.align(Alignment.Center)
        )
        
        // 底部提示文字
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
            )
        ) {
            Text(
                text = "Align QR code within the frame to scan",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
        
        // 加载遮罩
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Processing...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        
        // 确认对话框
        if (showConfirmDialog) {
            LoginConfirmDialog(
                onConfirm = {
                    viewModel.confirmLogin()
                },
                onDismiss = {
                    viewModel.dismissConfirmDialog()
                }
            )
        }
    }
}

/**
 * 扫描框组件
 */
@Composable
fun ScanningFrame(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(250.dp)
    ) {
        // 四个角的边框
        val cornerLength = 30.dp
        val cornerWidth = 4.dp
        
        // 左上角
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(cornerLength, cornerWidth)
                .background(Color.White)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(cornerWidth, cornerLength)
                .background(Color.White)
        )
        
        // 右上角
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(cornerLength, cornerWidth)
                .background(Color.White)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(cornerWidth, cornerLength)
                .background(Color.White)
        )
        
        // 左下角
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(cornerLength, cornerWidth)
                .background(Color.White)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(cornerWidth, cornerLength)
                .background(Color.White)
        )
        
        // 右下角
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(cornerLength, cornerWidth)
                .background(Color.White)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(cornerWidth, cornerLength)
                .background(Color.White)
        )
    }
}

/**
 * 登录确认对话框
 */
@Composable
fun LoginConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Confirm Login",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text("Do you want to confirm login to PC?")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Yes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("No")
            }
        }
    )
}