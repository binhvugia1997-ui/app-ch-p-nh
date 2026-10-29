package com.aiphotographer.photography

import com.aiphotographer.model.*

data class ShotEstimate(val type: ShotType, val confidence: Double = 0.0)

/**
 * Diagnostic visible-body classification only; local capture calibration is still required.
 *
 * Two entry points, deliberately separate:
 *  * [estimate] for a landmark list — the behaviour Phase 2 shipped and `SubjectAssembler` calls. It can
 *    only classify body-based shot types and returns `UNKNOWN` for headshot/close portrait.
 *  * [estimate] for a `Subject` — implements the full ladder of `composition-engine.md` §4, which needs
 *    the face bbox. The engine calls this; the perception module is not modified.
 *
 * All cut-offs are `CALIBRATION_REQUIRED` proposals (`Thresholds.T_*`). The confidence value is a
 * deterministic heuristic, not a calibrated probability: it only has to cross the arbiter's 0.5 floor for
 * a well-measured subject and stay below it when the evidence is thin.
 */
object ShotTypeEstimator {
    fun estimate(landmarks: List<Landmark>): ShotEstimate {
        val usable = landmarks.filter(LandmarkUsability::measurable).associateBy { it.id }
        fun both(part: String) = "left_$part" in usable && "right_$part" in usable
        if ("nose" !in usable || !both("shoulder")) return ShotEstimate(ShotType.UNKNOWN)
        val type = when {
            both("hip") && both("knee") && both("ankle") && both("foot_index") -> ShotType.FULL_BODY
            both("hip") && both("knee") -> ShotType.THREE_QUARTER
            both("hip") -> ShotType.HALF_BODY
            else -> ShotType.UNKNOWN
        }
        // Unknown is deliberate: no calibrated boundary distinguishes headshot from close portrait yet.
        return ShotEstimate(type)
    }

    /** The full ladder. `bboxHeight`/`faceHeight` are fractions of the frame height, in 0..1. */
    fun estimate(bboxHeight: Double, faceHeight: Double?, feetVisible: Boolean): ShotEstimate {
        val face = faceHeight?.takeIf { it.isFinite() && it > 0.0 }
        val body = bboxHeight.takeIf { it.isFinite() && it > 0.0 } ?: return ShotEstimate(ShotType.UNKNOWN)
        if (face != null) {
            if (face >= Thresholds.T_HEADSHOT) return ShotEstimate(ShotType.HEADSHOT, 0.9)
            if (face >= Thresholds.T_CLOSE) return ShotEstimate(ShotType.CLOSE_PORTRAIT, 0.9)
        }
        return when {
            body >= Thresholds.T_FULL && feetVisible -> ShotEstimate(ShotType.FULL_BODY, 0.8)
            body >= Thresholds.T_FULL -> ShotEstimate(ShotType.THREE_QUARTER, 0.7) // feet cut: 3/4 at best
            body >= Thresholds.T_THREE_QUARTER -> ShotEstimate(ShotType.THREE_QUARTER, 0.7)
            body >= Thresholds.T_HALF -> ShotEstimate(ShotType.HALF_BODY, 0.7)
            else -> ShotEstimate(ShotType.UNKNOWN)
        }
    }

    fun estimate(subject: Subject): ShotEstimate {
        val usable = subject.landmarks.filter(LandmarkUsability::measurable).associateBy { it.id }
        val feetVisible = "left_foot_index" in usable && "right_foot_index" in usable
        val faceHeight = subject.face?.bbox?.let { it.bottom - it.top }
        return estimate(subject.bbox.bottom - subject.bbox.top, faceHeight, feetVisible)
    }

    /** Intent/detected distance on the ladder, used by the one-step override rule (§4.4). */
    fun stepDistance(a: ShotType, b: ShotType): Int = ladder(a)?.let { left -> ladder(b)?.let { right -> kotlin.math.abs(left - right) } } ?: Int.MAX_VALUE

    private fun ladder(type: ShotType): Int? = when (type) {
        ShotType.HEADSHOT -> 0
        ShotType.CLOSE_PORTRAIT -> 1
        ShotType.HALF_BODY -> 2
        ShotType.THREE_QUARTER -> 3
        ShotType.FULL_BODY -> 4
        ShotType.UNKNOWN -> null
    }
}
