package com.aiphotographer.model

enum class UsabilityBand { USABLE, MARGINAL, UNUSABLE }
data class Usability(val value: Double, val band: UsabilityBand, val unknown: Boolean, val singleChannel: Boolean)

/** Normative mapping: docs/pose-system.md §4.1.1. All constants remain CALIBRATION_REQUIRED. */
object LandmarkUsability {
    const val FLOOR = 0.50
    private fun channel(value: Double?) = value?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)
    fun evaluate(visibility: Double?, presence: Double?): Usability {
        val v = channel(visibility)
        val p = channel(presence)
        val unknown = v == null && p == null
        val g = if (v != null && p != null) minOf(v, p) else v ?: p
        val u = if (g == null) 0.50 else ((g - FLOOR) / (1 - FLOOR)).coerceIn(0.0, 1.0)
        val band = if (u >= 0.60) UsabilityBand.USABLE else if (u >= 0.25) UsabilityBand.MARGINAL else UsabilityBand.UNUSABLE
        return Usability(u, band, unknown, (v == null) != (p == null))
    }
    fun evaluate(landmark: Landmark) = evaluate(landmark.visibility, landmark.presence)
    fun measurable(landmark: Landmark) = landmark.x.isFinite() && landmark.y.isFinite() &&
        landmark.x in 0.0..1.0 && landmark.y in 0.0..1.0 && evaluate(landmark).let { it.value > 0 && !it.unknown }
}

object PoseLandmarks {
    val ids = listOf("nose", "left_eye_inner", "left_eye", "left_eye_outer", "right_eye_inner", "right_eye", "right_eye_outer",
        "left_ear", "right_ear", "mouth_left", "mouth_right", "left_shoulder", "right_shoulder", "left_elbow", "right_elbow",
        "left_wrist", "right_wrist", "left_pinky", "right_pinky", "left_index", "right_index", "left_thumb", "right_thumb",
        "left_hip", "right_hip", "left_knee", "right_knee", "left_ankle", "right_ankle", "left_heel", "right_heel",
        "left_foot_index", "right_foot_index")
    val coreIds = setOf("nose", "left_eye", "right_eye", "left_ear", "right_ear", "left_shoulder", "right_shoulder",
        "left_hip", "right_hip", "left_elbow", "right_elbow", "left_wrist", "right_wrist", "left_knee", "right_knee",
        "left_ankle", "right_ankle", "left_heel", "right_heel", "left_foot_index", "right_foot_index")
    val bones = listOf(11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16, 11 to 23, 12 to 24, 23 to 24,
        23 to 25, 25 to 27, 27 to 29, 29 to 31, 24 to 26, 26 to 28, 28 to 30, 30 to 32)
}
