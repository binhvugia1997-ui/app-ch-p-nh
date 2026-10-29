package com.aiphotographer.photography

import com.aiphotographer.model.AnalysisQuality
import com.aiphotographer.model.DeviceState
import com.aiphotographer.model.FrameAnalysis
import com.aiphotographer.model.Landmark
import com.aiphotographer.model.LandmarkUsability
import com.aiphotographer.model.Subject
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Everything the framing rules need from one frame, computed once per frame so that eight rules do not
 * recompute the same geometry.
 */
data class FramingMeasurement(
    val subject: Subject,
    val shot: ShotEstimate,
    val bodyHeight: Float,
    val faceHeight: Float?,
    val headroom: Float?,
    val eyeLine: Float?,
    val minEdgeMargin: Float,
    val centreOffset: Float,
    val visibleFraction: Float,
    val rollDegrees: Float?,
    val yawDegrees: Double?,
    val sourceAgeMs: Long,
) {
    /** Why the measurement cannot support a framing instruction, or null when it can. */
    fun abstainReason(): AbstainReason? = when {
        visibleFraction < Thresholds.V_MIN -> AbstainReason.INSUFFICIENT_VISIBILITY
        sourceAgeMs > Thresholds.RULE_FRESHNESS_MS -> AbstainReason.SOURCE_STALE
        else -> null
    }
}

object FramingMeasurements {
    /** Prominence as specified by `FRAME_SUBJECT_PRESENT`: visibleFraction · √area. */
    fun choose(subjects: List<Subject>): Subject? = subjects.maxByOrNull { subject ->
        val area = (subject.bbox.right - subject.bbox.left) * (subject.bbox.bottom - subject.bbox.top)
        subject.visibleFraction * sqrt(area.coerceAtLeast(0.0))
    }

    fun measure(analysis: FrameAnalysis, subject: Subject): FramingMeasurement {
        val box = subject.bbox
        val left = box.left.toFloat()
        val top = box.top.toFloat()
        val right = box.right.toFloat()
        val bottom = box.bottom.toFloat()
        val margin = minOf(left, top, 1f - right, 1f - bottom)
        val offset = abs(((left + right) / 2f) - 0.5f) / 0.5f
        val faceBox = subject.face?.bbox
        return FramingMeasurement(
            subject = subject,
            shot = ShotTypeEstimator.estimate(subject),
            bodyHeight = (bottom - top).coerceAtLeast(0f),
            faceHeight = faceBox?.let { ((it.bottom - it.top).toFloat()).takeIf { height -> height > 0f } },
            headroom = HeadTopEstimator.headTopY(subject.landmarks)?.toFloat(),
            eyeLine = eyeLine(subject.landmarks),
            minEdgeMargin = margin,
            centreOffset = offset,
            visibleFraction = subject.visibleFraction.toFloat(),
            rollDegrees = analysis.device.rollDegrees?.toFloat(),
            yawDegrees = subject.face?.headPose?.yawDegrees,
            sourceAgeMs = sourceAge(analysis.quality),
        )
    }

    fun eyeLine(landmarks: List<Landmark>): Float? {
        val eyes = landmarks.filter { it.id == "left_eye" || it.id == "right_eye" }
            .filter(LandmarkUsability::measurable)
        if (eyes.isEmpty()) return null
        return eyes.map { it.y.toFloat() }.average().toFloat()
    }

    /**
     * Pose freshness from the perception layer. Falls back to "fresh" when the source is not published,
     * because a missing key must not silently suppress every rule.
     */
    private fun sourceAge(quality: AnalysisQuality): Long =
        quality.sources["pose"]?.ageMs?.takeIf { it >= 0 } ?: 0L

    /** Convenience for tests and callers that only have a `DeviceState`. */
    fun rollOf(device: DeviceState): Float? = device.rollDegrees?.toFloat()
}
