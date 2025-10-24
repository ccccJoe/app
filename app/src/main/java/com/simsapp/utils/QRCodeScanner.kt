/*
 * File: QRCodeScanner.kt
 * Description: QR code scanner utility using CameraX and ML Kit
 * Author: SIMS Team
 */
package com.simsapp.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * QRCodeScanner
 * 
 * 职责：
 * - 使用CameraX和ML Kit实现二维码扫描
 * - 处理相机权限请求
 * - 提供扫描结果回调
 * 
 * 设计思路：
 * - 封装CameraX的复杂配置
 * - 使用ML Kit进行条码识别
 * - 支持生命周期管理
 */
class QRCodeScanner(
    private val activity: ComponentActivity,
    private var previewView: PreviewView,
    private val onQRCodeScanned: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "QRCodeScanner"
        const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService
    private val barcodeScanner = BarcodeScanning.getClient()

    /**
     * 初始化扫描器
     */
    fun initialize() {
        try {
            cameraExecutor = Executors.newSingleThreadExecutor()
            
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                requestPermissions()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize scanner", e)
            onError("Scanner initialization failed: ${e.message}")
        }
    }

    /**
     * 检查是否已授予所有必要权限
     */
    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        activity, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * 请求相机权限
     */
    private fun requestPermissions() {
        activity.requestPermissions(
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    /**
     * 更新PreviewView
     * 当Compose中的AndroidView创建后调用此方法
     */
    fun updatePreviewView(newPreviewView: PreviewView) {
        previewView = newPreviewView
        // 如果相机已经启动，重新绑定到新的PreviewView
        if (cameraProvider != null && ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }

    /**
     * 启动相机
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)

        cameraProviderFuture.addListener({
            try {
                // 获取相机提供者
                cameraProvider = cameraProviderFuture.get()

                // 设置预览
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // 设置图像分析
                imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, QRCodeAnalyzer { qrCode ->
                            Log.d(TAG, "QR Code detected: $qrCode")
                            onQRCodeScanned(qrCode)
                        })
                    }

                // 选择后置摄像头
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // 绑定用例到生命周期
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    activity as LifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )

                Log.d(TAG, "Camera started successfully")
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                onError("Failed to start camera: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    /**
     * 停止扫描
     */
    fun stopScanning() {
        try {
            cameraProvider?.unbindAll()
            if (::cameraExecutor.isInitialized && !cameraExecutor.isShutdown) {
                cameraExecutor.shutdown()
            }
            barcodeScanner.close()
            Log.d(TAG, "Scanner stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scanner", e)
        }
    }

    /**
     * 开启/关闭闪光灯
     */
    fun toggleFlashlight() {
        camera?.let { camera ->
            if (camera.cameraInfo.hasFlashUnit()) {
                val currentTorchState = camera.cameraInfo.torchState.value
                camera.cameraControl.enableTorch(currentTorchState != TorchState.ON)
            }
        }
    }

    /**
     * QR码分析器
     */
    private inner class QRCodeAnalyzer(
        private val onQRCodeDetected: (String) -> Unit
    ) : ImageAnalysis.Analyzer {

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                
                barcodeScanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            when (barcode.valueType) {
                                Barcode.TYPE_TEXT,
                                Barcode.TYPE_URL -> {
                                    barcode.rawValue?.let { value ->
                                        onQRCodeDetected(value)
                                        return@addOnSuccessListener
                                    }
                                }
                            }
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Barcode scanning failed", exception)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
}