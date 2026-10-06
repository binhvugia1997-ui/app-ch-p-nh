package com.aiphotographer.perception

import com.aiphotographer.geometry.OneEuroFilter
import com.aiphotographer.model.*
import com.aiphotographer.photography.ShotTypeEstimator

class LandmarkFilter {
    private val filters = mutableMapOf<String, Array<OneEuroFilter>>()
    fun reset() { filters.clear() }
    fun apply(points: List<Landmark>, timestampMs: Long): List<Landmark> = points.map { point ->
        if (!LandmarkUsability.measurable(point)) {
            filters.remove(point.id)
            point
        } else {
            val filter = filters.getOrPut(point.id) { Array(3) { OneEuroFilter() } }
            point.copy(x = filter[0].filter(point.x, timestampMs), y = filter[1].filter(point.y, timestampMs),
                z = point.z?.takeIf { it.isFinite() }?.let { filter[2].filter(it, timestampMs) }, smoothed = true)
        }
    }
}

object PerceptionFreshness {
    const val MAX_AGE_MS = 500L // CALIBRATION_REQUIRED, diagnostic timeout; not a validated accuracy bound.
    fun fresh(sourceTimestamp: Long, frameTimestamp: Long) = frameTimestamp >= sourceTimestamp && frameTimestamp - sourceTimestamp <= MAX_AGE_MS
    fun sameGeometry(a: FrameGeometry, b: FrameGeometry) = a == b
}

class SubjectAssembler {
    private val filter = LandmarkFilter()
    private var geometry: FrameGeometry? = null
    private var trackId = 0
    private var present = false
    fun reset() { filter.reset(); geometry = null; present = false }
    fun assemble(pose: PoseFrameResult, frame: FrameGeometry, face: FaceFrameResult?): List<Subject> {
        if (geometry != frame) { reset(); geometry = frame }
        if (pose.landmarks.isEmpty()) { filter.reset(); present = false; return emptyList() }
        if (!present) { trackId++; present = true; filter.reset() }
        val landmarks = filter.apply(pose.landmarks, pose.timestampMs)
        val measurable = landmarks.filter(LandmarkUsability::measurable)
        if (measurable.isEmpty()) return emptyList()
        val box = BoundingBox(measurable.minOf { it.x }, measurable.minOf { it.y }, measurable.maxOf { it.x }, measurable.maxOf { it.y })
        val visible = measurable.count { it.id in PoseLandmarks.coreIds }.toDouble() / PoseLandmarks.coreIds.size
        val nose = measurable.firstOrNull { it.id == "nose" }
        val associated = face?.takeIf { PerceptionFreshness.fresh(it.timestampMs, pose.timestampMs) }?.face?.takeIf { observation ->
            nose != null && nose.x in observation.bbox.left..observation.bbox.right && nose.y in observation.bbox.top..observation.bbox.bottom
        }
        val shot = ShotTypeEstimator.estimate(landmarks)
        return listOf(Subject(trackId, landmarks, box, visible, shot.type, pose.worldLandmarks, associated, shot.confidence))
    }
}
