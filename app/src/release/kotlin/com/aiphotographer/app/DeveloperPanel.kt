package com.aiphotographer.app

import androidx.compose.runtime.Composable
import com.aiphotographer.camera.CameraSession
import com.aiphotographer.model.AnalysisResolution

@Composable internal fun DeveloperOverlay(camera: CameraSession) = Unit
@Composable internal fun DeveloperControls(resolution: AnalysisResolution, onResolution: (AnalysisResolution) -> Unit, aspect: Float, onAspect: (Float) -> Unit, rgb: Boolean, onRgb: (Boolean) -> Unit) = Unit
