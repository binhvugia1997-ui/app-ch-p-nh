package com.aiphotographer.photography

import com.aiphotographer.model.Landmark
import com.aiphotographer.model.LandmarkUsability
import kotlin.math.hypot

/**
 * Subject motion in torso lengths per second — the measurement `READY_SUBJECT_STABLE` asks for
 * (`specs/rules/mvp-rules.json`). EMA-smoothed so a single jittery frame cannot flip readiness.
 */
class MotionTracker(private val alpha: Double = 0.3) {
    private var previous: Map<String, Pair<Double, Double>>? = null
    private var previousMs = -1L
    private var ema: Double? = null

    fun reset() {
        previous = null
        previousMs = -1L
        ema = null
    }

    /** Null until two comparable frames exist. Never guesses a value for the first frame. */
    fun update(landmarks: List<Landmark>, nowMs: Long): Double? {
        val current = landmarks.filter(LandmarkUsability::measurable).associate { it.id to (it.x to it.y) }
        val torso = torsoLength(current)
        val prior = previous
        val priorMs = previousMs
        previous = current
        previousMs = nowMs
        if (torso == null || prior == null || priorMs < 0 || nowMs <= priorMs) return ema
        val shared = current.keys.intersect(prior.keys)
        if (shared.size < MIN_SHARED_LANDMARKS) return ema
        val meanDisplacement = shared.sumOf { id ->
            val (px, py) = prior.getValue(id)
            val (cx, cy) = current.getValue(id)
            hypot(cx - px, cy - py)
        } / shared.size
        val seconds = (nowMs - priorMs) / 1000.0
        val energy = (meanDisplacement / torso) / seconds
        ema = ema?.let { it + alpha * (energy - it) } ?: energy
        return ema
    }

    private fun torsoLength(points: Map<String, Pair<Double, Double>>): Double? {
        val ls = points["left_shoulder"] ?: return null
        val rs = points["right_shoulder"] ?: return null
        val lh = points["left_hip"]
        val rh = points["right_hip"]
        if (lh != null && rh != null) {
            val shoulderMid = (ls.first + rs.first) / 2 to (ls.second + rs.second) / 2
            val hipMid = (lh.first + rh.first) / 2 to (lh.second + rh.second) / 2
            val length = hypot(shoulderMid.first - hipMid.first, shoulderMid.second - hipMid.second)
            if (length > MIN_TORSO) return length
        }
        val shoulders = hypot(ls.first - rs.first, ls.second - rs.second)
        return shoulders.takeIf { it > MIN_TORSO }
    }

    private companion object {
        const val MIN_SHARED_LANDMARKS = 4
        const val MIN_TORSO = 1e-4
    }
}

enum class ReadinessCriterionId(val whyKey: String?) {
    SUBJECT_PRESENT(null),
    SUBJECT_IN_SIZE_BAND(RuleId.FRAME_SUBJECT_TOO_SMALL.whyKey),
    HEADROOM_IN_BAND(RuleId.FRAME_HEADROOM_EXCESSIVE.whyKey),
    EDGE_MARGIN(RuleId.FRAME_EDGE_MARGIN.whyKey),
    SUBJECT_STABLE("guidance.why.ready_subject_stable"),
    CAMERA_STABLE("guidance.why.ready_camera_stable"),
}

data class ReadinessCriterion(
    val id: ReadinessCriterionId,
    /** `false` = recommended only. A recommended criterion never gates auto-capture on its own. */
    val required: Boolean,
    val satisfied: Boolean,
)

data class ReadinessReport(
    val state: ReadinessState,
    val criteria: List<ReadinessCriterion>,
    val reasons: List<String>,
    val readySinceMs: Long?,
    /** Whether the composition layer contributed any criterion to this report (D8). */
    val compositionIncluded: Boolean,
) {
    /**
     * Constant, on purpose: no readiness result may lock the capture button
     * (`guidance-engine.md` §7, owner decision D3). It exists so a UI cannot invent the opposite.
     */
    val blocksManualCapture: Boolean get() = false
}

enum class ReadinessState { NOT_READY, NEAR_READY, READY }

data class ReadinessConfig(val readyHoldMs: Long = Thresholds.READY_HOLD_MS)

/**
 * Readiness as a *duration gate*, never a "partially true" value (`guidance-engine.md` §6.4).
 *
 * Owner decision D8 lives at the call site, not here: `SmartFramingSession` simply does not put
 * composition criteria into the list when Smart Framing is off, and resets this machine, so no stale
 * composition reason can survive a toggle.
 */
class ReadinessMachine(private val config: ReadinessConfig = ReadinessConfig()) {
    private var satisfiedSinceMs = -1L

    fun reset() {
        satisfiedSinceMs = -1L
    }

    fun evaluate(criteria: List<ReadinessCriterion>, nowMs: Long, compositionIncluded: Boolean): ReadinessReport {
        val required = criteria.filter { it.required }
        val satisfied = required.count { it.satisfied }
        val state: ReadinessState
        if (required.isEmpty()) {
            satisfiedSinceMs = -1L
            state = ReadinessState.NOT_READY
        } else if (satisfied == required.size) {
            if (satisfiedSinceMs < 0) satisfiedSinceMs = nowMs
            state = if (nowMs - satisfiedSinceMs >= config.readyHoldMs) ReadinessState.READY else ReadinessState.NEAR_READY
        } else {
            satisfiedSinceMs = -1L
            state = if (satisfied > 0) ReadinessState.NEAR_READY else ReadinessState.NOT_READY
        }
        val reasons = criteria.filterNot { it.satisfied }.mapNotNull { it.id.whyKey }.distinct()
        return ReadinessReport(
            state = state,
            criteria = criteria,
            reasons = reasons,
            readySinceMs = satisfiedSinceMs.takeIf { it >= 0 },
            compositionIncluded = compositionIncluded,
        )
    }
}
