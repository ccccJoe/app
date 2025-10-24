/*
 * File: QRScanViewModel.kt
 * Description: ViewModel for QR code scanning and authentication logic
 * Author: SIMS Team
 */
package com.simsapp.ui.auth

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simsapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * QRScanViewModel
 * 
 * 职责：
 * - 处理二维码扫描后的认证逻辑
 * - 管理绑定和登录流程
 * - 提供UI状态管理
 * 
 * 设计思路：
 * - 根据本地token状态决定执行绑定或登录
 * - 使用StateFlow管理UI状态
 * - 异步处理网络请求
 */
@HiltViewModel
class QRScanViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "QRScanViewModel"
    }
    
    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // 确认对话框显示状态
    private val _showConfirmDialog = MutableStateFlow(false)
    val showConfirmDialog: StateFlow<Boolean> = _showConfirmDialog.asStateFlow()
    
    // 认证结果
    private val _authResult = MutableLiveData<Pair<Boolean, String>?>()
    val authResult: LiveData<Pair<Boolean, String>?> = _authResult
    
    // 当前扫码值（用于确认登录时使用）
    private var currentQRValue: String? = null
    
    /**
     * 处理二维码扫描结果
     * 
     * @param qrValue 扫码获取的值
     */
    fun handleQRCode(qrValue: String) {
        Log.d(TAG, "Handling QR code: $qrValue")
        currentQRValue = qrValue
        
        if (authRepository.isDeviceBound()) {
            // 已绑定设备，显示登录确认对话框
            Log.d(TAG, "Device already bound, showing login confirmation")
            _showConfirmDialog.value = true
        } else {
            // 未绑定设备，执行绑定流程
            Log.d(TAG, "Device not bound, starting bind process")
            bindDevice(qrValue)
        }
    }
    
    /**
     * 绑定设备
     * 
     * @param qrValue 扫码值
     */
    private fun bindDevice(qrValue: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d(TAG, "Starting device bind")
                
                val (success, message) = authRepository.bindDevice(qrValue)
                
                Log.d(TAG, "Bind result: success=$success, message=$message")
                _authResult.value = success to message
                
            } catch (e: Exception) {
                Log.e(TAG, "Bind device failed", e)
                _authResult.value = false to "Bind failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 确认登录PC端
     */
    fun confirmLogin() {
        val qrValue = currentQRValue
        if (qrValue == null) {
            Log.e(TAG, "QR value is null when confirming login")
            _authResult.value = false to "QR value not found"
            return
        }
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _showConfirmDialog.value = false
                
                Log.d(TAG, "Starting login verify")
                
                val (success, message) = authRepository.verifyLogin(qrValue)
                
                Log.d(TAG, "Verify result: success=$success, message=$message")
                _authResult.value = success to message
                
            } catch (e: Exception) {
                Log.e(TAG, "Verify login failed", e)
                _authResult.value = false to "Verify failed: ${e.message}"
            } finally {
                _isLoading.value = false
                currentQRValue = null
            }
        }
    }
    
    /**
     * 取消登录确认对话框
     */
    fun dismissConfirmDialog() {
        _showConfirmDialog.value = false
        currentQRValue = null
        // 返回取消结果，让Activity重新开始扫描
        _authResult.value = false to "Login cancelled"
    }
    
    /**
     * 清除认证结果
     */
    fun clearAuthResult() {
        _authResult.value = null
    }
}