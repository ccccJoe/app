/**
 * 文件用途：公钥显示弹窗组件，用于显示本地缓存的公钥并支持复制功能
 * 作者：SIMS 团队
 * 设计说明：遵循 Material Design 规范，提供清晰的公钥展示和便捷的复制操作
 */

package com.example.sims_android.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.simsapp.data.local.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel

/**
 * 公钥弹窗ViewModel
 * 负责获取本地缓存的公钥数据
 */
@HiltViewModel
class PublicKeyDialogViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {
    
    /**
     * 获取本地缓存的公钥Base64字符串
     * 
     * @return 公钥Base64字符串，如果不存在则返回null
     */
    fun getPublicKeyBase64(): String? {
        return authManager.getPublicKeyBase64()
    }
}

/**
 * 公钥显示弹窗
 * 
 * @param onDismiss 关闭弹窗的回调
 * @param viewModel 公钥弹窗ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicKeyDialog(
    onDismiss: () -> Unit,
    viewModel: PublicKeyDialogViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val publicKeyBase64 = remember { viewModel.getPublicKeyBase64() }
    var showCopySuccess by remember { mutableStateOf(false) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Public Key",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF666666)
                        )
                    }
                }
                
                // 公钥内容区域
                if (publicKeyBase64 != null) {
                    // 公钥显示区域
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = publicKeyBase64,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF333333),
                                lineHeight = 16.sp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                    
                    // 复制按钮
                    Button(
                        onClick = {
                            copyToClipboard(context, publicKeyBase64)
                            showCopySuccess = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF153E75)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Copy Public Key",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    // 无公钥提示
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No Public Key Found",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF666666),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Please bind device first to generate key pair",
                                fontSize = 14.sp,
                                color = Color(0xFF999999),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
    
    // 复制成功提示
    if (showCopySuccess) {
        LaunchedEffect(showCopySuccess) {
            kotlinx.coroutines.delay(2000)
            showCopySuccess = false
        }
        
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier.padding(16.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = "Public key copied to clipboard",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * 复制文本到剪贴板
 * 
 * @param context Android上下文
 * @param text 要复制的文本
 */
private fun copyToClipboard(context: Context, text: String) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText("Public Key", text)
    clipboardManager.setPrimaryClip(clipData)
}