package com.aiphotographer.app

import com.aiphotographer.geometry.Coordinates
import com.aiphotographer.geometry.Point
import com.aiphotographer.model.ImageSnapshot
import com.aiphotographer.model.LandmarkUsability
import com.aiphotographer.perception.PerceptionFreshness
import com.aiphotographer.perception.PerceptionSnapshot

/**
 * Pure projection of the current analysis onto preview pixels.
 *
 * This lives in `main` on purpose: it is the single coordinate transform used by any overlay
 * (the debug diagnostic overlay today, media/guidance overlays later) and it is unit-tested
 * without Android. Drawing diagnostics is gated per build type; projection is not.
 */
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
