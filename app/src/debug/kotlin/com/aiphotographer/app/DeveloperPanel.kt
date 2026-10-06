package com.aiphotographer.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiphotographer.camera.CameraSession
import com.aiphotographer.model.AnalysisResolution
import kotlinx.coroutines.delay
import java.util.Locale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.Offset
import com.aiphotographer.geometry.Coordinates
import com.aiphotographer.model.LandmarkUsability
import com.aiphotographer.model.PoseLandmarks

@Composable internal fun DeveloperOverlay(camera: CameraSession) {
    val snapshot by camera.router.snapshot.collectAsStateWithLifecycle()
    val metrics by camera.router.metrics.collectAsStateWithLifecycle()
    val state by camera.state.collectAsStateWithLifecycle()
    val perception = camera.perception?.state?.collectAsStateWithLifecycle()?.value
    var rss by remember { mutableStateOf<String?>(null) }
    var thermal by remember { mutableStateOf(camera.monitor.thermal()) }
    LaunchedEffect(camera) {
        while (true) {
            rss = runCatching { java.io.File("/proc/self/status").useLines { lines -> lines.firstOrNull { it.startsWith("VmRSS:") } } }.getOrNull()
            thermal = camera.monitor.thermal()
            delay(1000)
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        snapshot?.analysis?.frame?.let { frame ->
            drawIntoCanvas { CrosshairRenderer.draw(it.nativeCanvas, size.width.toInt(), size.height.toInt(), frame) }
            if (snapshot?.analysis?.quality?.sources?.get("pose")?.stale != true) {
                snapshot?.analysis?.subjects?.firstOrNull()?.let { subject ->
                    val points = subject.landmarks.associateBy { it.id }
                    fun point(id: String): Offset? = points[id]?.takeIf(LandmarkUsability::measurable)?.let { l ->
                        Coordinates.analysisToPreview(com.aiphotographer.geometry.Point(l.x, l.y), frame.width, frame.height,
                            size.width.toInt(), size.height.toInt(), frame.isMirrored)?.let { Offset(it.x.toFloat(), it.y.toFloat()) }
                    }
                    PoseLandmarks.bones.forEach { (a, b) ->
                        val from = point(PoseLandmarks.ids[a]); val to = point(PoseLandmarks.ids[b])
                        if (from != null && to != null) drawLine(Color.Green, from, to, strokeWidth = 2.dp.toPx())
                    }
                    points.keys.forEach { point(it)?.let { p -> drawCircle(Color.Yellow, 3.dp.toPx(), p) } }
                    subject.face?.takeIf { snapshot?.analysis?.quality?.sources?.get("face")?.stale != true }?.landmarks?.forEach { l ->
                        Coordinates.analysisToPreview(com.aiphotographer.geometry.Point(l.x, l.y), frame.width, frame.height,
                            size.width.toInt(), size.height.toInt(), frame.isMirrored)?.let { p ->
                            drawCircle(Color.Cyan, 1.dp.toPx(), Offset(p.x.toFloat(), p.y.toFloat()))
                        }
                    }
                }
            }
            val diagnostic = perception?.snapshot
            if (diagnostic?.frame == frame && diagnostic.quality.sources["face"]?.stale == false &&
                snapshot?.analysis?.subjects?.firstOrNull()?.face == null) {
                diagnostic.faceDiagnostic?.landmarks?.forEach { l ->
                    Coordinates.analysisToPreview(com.aiphotographer.geometry.Point(l.x, l.y), frame.width, frame.height,
                        size.width.toInt(), size.height.toInt(), frame.isMirrored)?.let { p ->
                        drawCircle(Color.Cyan, 1.dp.toPx(), Offset(p.x.toFloat(), p.y.toFloat()))
                    }
                }
            }
        }
    }
    var visible by remember { mutableStateOf(false) }
    Column(Modifier.padding(8.dp).background(Color.Black.copy(alpha = .7f)).widthIn(max = 340.dp)) {
        TextButton(onClick = { visible = !visible }) { Text(stringResource(R.string.phase2_diagnostics)) }
        if (visible) {
            fun Double.f() = String.format(Locale.ROOT, "%.2f", this)
            val cap = state.capability
            val delegateLine = stringResource(R.string.perception_delegate_diagnostic, perception?.pose.toString(), perception?.face.toString())
            val perceptionLine = stringResource(R.string.perception_metrics_diagnostic, perception?.metrics.toString())
            val subjectLine = stringResource(R.string.perception_subject_diagnostic, snapshot?.analysis?.subjects?.size ?: 0,
                snapshot?.analysis?.subjects?.firstOrNull()?.shotType.toString())
            Text(buildString {
                appendLine("Preview capture FPS (not display): ${metrics.previewCaptureFps.f()}")
                appendLine("Analysis delivery FPS: ${metrics.analysisFps.f()}; luma ${metrics.lumaFps.f()} Hz")
                appendLine("Received=${metrics.delivered} processed=${metrics.processed} skipped=${metrics.cadenceSkipped} errors=${metrics.errors}")
                appendLine("Router skip ratio=${metrics.routerDropRatio.f()}; CameraX drops UNKNOWN")
                appendLine("${metrics.elapsedSeconds.f()} s; actual ${metrics.lastResolution}")
                metrics.latencies.forEach { (stage, summary) -> appendLine("$stage n=${summary.count} p50=${summary.p50Ms?.f()} p95=${summary.p95Ms?.f()} ms") }
                appendLine("$rss; thermal=$thermal")
                appendLine("${cap.tier} heuristic/CALIBRATION_REQUIRED; GL=${cap.glEsVersion}")
                appendLine("GPU eligible=${cap.gpuEligible}; emulator=${cap.emulator}")
                appendLine(delegateLine)
                appendLine(perceptionLine)
                appendLine(subjectLine)
                appendLine("Camera ${cap.cameraId}; 3-use-case=${cap.threeUseCaseSupported}; ${state.mode}")
                appendLine("YUV resolutions=${cap.supportedYuvSizes}")
                appendLine("Fallback=${state.fallbackAttempts}")
                appendLine("IMU roll=${snapshot?.analysis?.device?.rollDegrees}; stability=UNKNOWN; gravity=${camera.monitor.gravity}")
                append("Marker ANALYSIS=(0.25,0.5); no guidance")
            }, color = Color.White, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(8.dp))
        }
        if (visible) Text(stringResource(R.string.single_person_diagnostic), color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
@Composable internal fun DeveloperControls(resolution: AnalysisResolution, onResolution: (AnalysisResolution) -> Unit, aspect: Float, onAspect: (Float) -> Unit, rgb: Boolean, onRgb: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TextButton(onClick = { onResolution(if (resolution == AnalysisResolution.R480P) AnalysisResolution.R720P else AnalysisResolution.R480P) }) { Text(resolution.name) }
        TextButton(onClick = { onAspect(if (aspect == 3f / 4f) 9f / 16f else 3f / 4f) }) { Text("4:3 / 16:9") }
        TextButton(onClick = { onRgb(!rgb) }) { Text("RGB benchmark: $rgb") }
    }
}
