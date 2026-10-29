package com.aiphotographer.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiphotographer.camera.CameraSession
import com.aiphotographer.geometry.Point
import com.aiphotographer.model.PoseLandmarks

/**
 * Debug-only diagnostic overlay: draws the raw Pose skeleton and Face landmark dots.
 *
 * This is a development instrument, never guidance. It must not exist in `profile` or `release`
 * (see the no-op declarations in `src/profile` and `src/release`). The always-on product overlay
 * for Smart Framing is a separate composable that never draws raw landmarks.
 */
@Composable internal fun DiagnosticLandmarkOverlay(camera: CameraSession) {
    val image by camera.router.snapshot.collectAsStateWithLifecycle()
    val diagnostic = camera.perception?.state?.collectAsStateWithLifecycle()?.value?.snapshot
    Canvas(Modifier.fillMaxSize()) {
        val projected = PerceptionOverlayProjection.project(image, diagnostic, size.width.toInt(), size.height.toInt())
        fun offset(point: Point) = Offset(point.x.toFloat(), point.y.toFloat())
        PoseLandmarks.bones.forEach { (start, end) ->
            val from = projected.pose[PoseLandmarks.ids[start]]
            val to = projected.pose[PoseLandmarks.ids[end]]
            if (from != null && to != null) drawLine(Color.Green, offset(from), offset(to), strokeWidth = 2.dp.toPx())
        }
        projected.pose.values.forEach { drawCircle(Color.Yellow, 3.dp.toPx(), offset(it)) }
        projected.face.forEach { drawCircle(Color.Cyan, 1.dp.toPx(), offset(it)) }
    }
}
