package com.aiphotographer.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.aiphotographer.model.*
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SessionMode { STARTING, FULL, ANALYSIS_ONLY, CAPTURE_ONLY, ERROR }
enum class CaptureStatus { IDLE, SAVING, SAVED, ERROR }
data class SessionState(
    val mode: SessionMode = SessionMode.STARTING, val capability: CapabilityReport,
    val fallbackAttempts: List<String> = emptyList(), val capture: CaptureStatus = CaptureStatus.IDLE,
    val savedLocation: String? = null,
)

@OptIn(ExperimentalCamera2Interop::class)
class CameraSession(
    private val context: Context,
    private val owner: LifecycleOwner,
    val previewView: PreviewView,
    val monitor: DeviceMonitor,
    private val facing: CameraFacing,
    private val resolution: AnalysisResolution,
    benchmarkRgb: Boolean,
    private val launchNs: Long,
) {
    val router = FrameRouter(facing, resolution, monitor::state, benchmarkRgb)
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "Phase1FrameRouter") }
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val mutableState = MutableStateFlow(SessionState(capability = monitor.capability))
    val state = mutableState.asStateFlow()
    private var provider: ProcessCameraProvider? = null
    private var boundSession: SessionConfig? = null
    private var analysis: ImageAnalysis? = null
    private var capture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraStateObserver: Observer<CameraState>? = null
    private var closed = false
    private var firstFrame = false
    private val selector = if (facing == CameraFacing.FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

    fun start() {
        if (closed) return
        monitor.start()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (closed) return@addListener
            try { provider = future.get(); bind() }
            catch (error: Exception) { fail(error) }
        }, mainExecutor)
    }
    private fun bind() {
        if (closed || previewView.width == 0 || previewView.height == 0) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            fail(SecurityException("Camera permission unavailable")); return
        }
        val provider = provider ?: return
        if (!provider.hasCamera(selector)) { fail(IllegalArgumentException("Camera unavailable")); return }
        analysis?.clearAnalyzer()
        cameraStateObserver?.let { camera?.cameraInfo?.cameraState?.removeObserver(it) }
        boundSession?.let(provider::unbind)
        val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
        val previewBuilder = Preview.Builder().setTargetRotation(rotation)
        Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                router.onPreviewCapture()
            }
        })
        val preview = previewBuilder.build().also { it.surfaceProvider = previewView.surfaceProvider }
        val imageCapture = ImageCapture.Builder().setTargetRotation(rotation).build()
        val attempts = mutableListOf<String>()
        val sizes = if (resolution == AnalysisResolution.R720P) listOf(Size(1280, 720), Size(640, 480), Size(320, 240))
            else listOf(Size(640, 480), Size(320, 240))
        val info = provider.getCameraInfo(selector)
        val camera2 = Camera2CameraInfo.from(info)
        val supported = camera2.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(ImageFormat.YUV_420_888)?.map { "${it.width}x${it.height}" }.orEmpty()
        var report = monitor.capability.copy(cameraId = camera2.cameraId, supportedYuvSizes = supported)
        fun tryBind(mode: SessionMode, imageAnalysis: ImageAnalysis?): Boolean {
            val uses = when (mode) {
                SessionMode.FULL -> listOf(preview, imageAnalysis!!, imageCapture)
                SessionMode.ANALYSIS_ONLY -> listOf(preview, imageAnalysis!!)
                else -> listOf(preview, imageCapture)
            }
            val config = SessionConfig(useCases = uses, viewPort = previewView.getViewPort(rotation))
            val supportedSession = try { info.isSessionConfigSupported(config) } catch (_: UnsupportedOperationException) { null }
            if (mode == SessionMode.FULL && report.threeUseCaseSupported == null) report = report.copy(threeUseCaseSupported = supportedSession)
            attempts.add("$mode ${imageAnalysis?.resolutionSelector?.resolutionStrategy?.boundSize} query=$supportedSession")
            if (supportedSession == false) return false
            return try {
                camera = provider.bindToLifecycle(owner, selector, config)
                boundSession = config
                analysis = imageAnalysis
                capture = if (mode == SessionMode.ANALYSIS_ONLY) null else imageCapture
                if (camera!!.cameraInfo.hasFlashUnit()) camera!!.cameraControl.enableTorch(false)
                mutableState.value = SessionState(mode, report, attempts.toList())
                cameraStateObserver = Observer<CameraState> { cameraState ->
                    if (!closed && cameraState.error != null) {
                        Log.e("Phase1Camera", "Camera state error: ${cameraState.error}")
                        mutableState.value = mutableState.value.copy(mode = SessionMode.ERROR)
                    } else if (!closed && cameraState.type == CameraState.Type.OPEN) {
                        mutableState.value = mutableState.value.copy(mode = mode)
                    }
                }.also { camera!!.cameraInfo.cameraState.observe(owner, it) }
                Log.i("Phase1Capability", mutableState.value.toString())
                true
            } catch (error: IllegalArgumentException) {
                Log.w("Phase1Capability", "Combination rejected", error); false
            }
        }
        for (size in sizes) {
            val analyzer = ImageAnalysis.Builder().setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(ResolutionSelector.Builder()
                    .setAspectRatioStrategy(if (size.width == 1280) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
                .build()
            analyzer.setAnalyzer(executor, router)
            if (tryBind(SessionMode.FULL, analyzer)) return
            analyzer.clearAnalyzer()
        }
        val analyzer = ImageAnalysis.Builder().setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                ResolutionStrategy(Size(320, 240), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build()).build()
        analyzer.setAnalyzer(executor, router)
        if (tryBind(SessionMode.ANALYSIS_ONLY, analyzer)) return
        analyzer.clearAnalyzer()
        if (tryBind(SessionMode.CAPTURE_ONLY, null)) return
        mutableState.value = SessionState(SessionMode.ERROR, report, attempts)
    }
    /** Serial capture in analysis-only fallback, then restore the analysis binding. */
    fun takePhoto() {
        if (closed || mutableState.value.capture == CaptureStatus.SAVING) return
        if (mutableState.value.mode == SessionMode.ANALYSIS_ONLY) {
            analysis?.clearAnalyzer()
            boundSession?.let { provider?.unbind(it) }
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val newCapture = ImageCapture.Builder().setTargetRotation(previewView.display?.rotation ?: android.view.Surface.ROTATION_0).build()
            val config = SessionConfig(useCases = listOf(preview, newCapture), viewPort = previewView.viewPort)
            try {
                camera = provider?.bindToLifecycle(owner, selector, config)
                boundSession = config
                capture = newCapture
            } catch (error: Exception) { fail(error); return }
        }
        val target = capture ?: return
        mutableState.value = mutableState.value.copy(capture = CaptureStatus.SAVING)
        val name = "AI_${System.currentTimeMillis()}.jpg"
        fun privateOptions(): ImageCapture.OutputFileOptions {
            val file = File(context.filesDir, "Pictures/AI Photographer/$name").also { it.parentFile?.mkdirs() }
            return ImageCapture.OutputFileOptions.Builder(file).build()
        }
        val options = if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AI Photographer")
            }
            ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
        } else privateOptions()
        val restoreAnalysis = mutableState.value.mode == SessionMode.ANALYSIS_ONLY
        fun save(output: ImageCapture.OutputFileOptions, allowPrivateFallback: Boolean) {
          target.takePicture(output, mainExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                if (closed) return
                val location = result.savedUri?.toString() ?: "${context.filesDir}/Pictures/AI Photographer/$name"
                if (restoreAnalysis) bind()
                mutableState.value = mutableState.value.copy(capture = CaptureStatus.SAVED, savedLocation = location)
                Log.i("Phase1Capture", location)
            }
            override fun onError(exception: ImageCaptureException) {
                if (closed) return
                Log.e("Phase1Capture", "Save failed", exception)
                if (allowPrivateFallback && Build.VERSION.SDK_INT >= 29) {
                    save(privateOptions(), false)
                    return
                }
                if (restoreAnalysis) bind()
                mutableState.value = mutableState.value.copy(capture = CaptureStatus.ERROR)
            }
          })
        }
        save(options, Build.VERSION.SDK_INT >= 29)
    }
    fun previewStreaming() {
        if (!firstFrame) {
            firstFrame = true
            Log.i("Phase1ColdStart", "launch_to_preview_streaming_ms=${(SystemClock.elapsedRealtimeNanos() - launchNs) / 1e6}")
        }
    }
    private fun fail(error: Exception) {
        Log.e("Phase1Camera", "Camera failure", error)
        mutableState.value = mutableState.value.copy(mode = SessionMode.ERROR)
    }
    fun close() {
        closed = true
        analysis?.clearAnalyzer()
        cameraStateObserver?.let { camera?.cameraInfo?.cameraState?.removeObserver(it) }
        boundSession?.let { provider?.unbind(it) }
        monitor.stop()
        executor.shutdown()
    }
}
