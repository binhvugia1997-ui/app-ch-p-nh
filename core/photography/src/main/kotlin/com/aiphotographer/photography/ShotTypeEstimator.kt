package com.aiphotographer.photography

import com.aiphotographer.model.*

data class ShotEstimate(val type: ShotType, val confidence: Double = 0.0)

/** Diagnostic visible-body classification only; local capture calibration is still required. */
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
}
