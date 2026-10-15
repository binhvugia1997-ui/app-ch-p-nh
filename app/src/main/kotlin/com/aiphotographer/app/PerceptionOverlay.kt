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
import com.aiphotographer.geometry.Coordinates
import com.aiphotographer.geometry.Point
import com.aiphotographer.model.ImageSnapshot
import com.aiphotographer.model.LandmarkUsability
import com.aiphotographer.model.PoseLandmarks
import com.aiphotographer.perception.PerceptionFreshness
import com.aiphotographer.perception.PerceptionSnapshot

internal data class ProjectedPerception(val pose: Map<String, Point>, val face: List<Point>)

internal object PerceptionOverlayProjection {
    fun project(image: ImageSnapshot?, diagnostic: PerceptionSnapshot?, width: Int, height: Int): ProjectedPerception {
        if (image == null || width <= 0 || height <= 0) return ProjectedPerception(emptyMap(), emptyList())
        val analysis = image.analysis
        val frame = analysis.frame
        fun point(x: Double, y: Double): Point? = Coordinates.analysisToPreview(
            Point(x, y), frame.width, frame.height, width, height, frame.isMirrored)

        val subject = analysis.subjects.firstOrNull().takeIf {
            analysis.quality.sources["pose"]?.stale == false
        }
        val pose = subject?.landmarks.orEmpty().asSequence()
            .filter(LandmarkUsability::measurable)
            .mapNotNull { landmark -> point(landmark.x, landmark.y)?.let { landmark.id to it } }
            .toMap()
        val face = if (analysis.quality.sources["face"]?.stale == false) {
            val diagnosticFace = if (analysis.subjects.isEmpty() && analysis.quality.sources["pose"]?.stale == true) {
                diagnostic?.takeIf { it.frame == frame && PerceptionFreshness.fresh(it.timestampMs, analysis.timestampMs) }
                    ?.faceDiagnostic
            } else null
            val observed = subject?.face ?: diagnosticFace
            observed?.landmarks.orEmpty().mapNotNull { point(it.x, it.y) }
        } else emptyList()
        return ProjectedPerception(pose, face)
    }
}

@Composable internal fun PerceptionOverlay(camera: CameraSession) {
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
