package com.aiphotographer.photography

import com.aiphotographer.model.Landmark
import kotlin.math.sqrt

/**
 * Head-top estimator from `FRAME_HEADROOM_*` (`docs/photography-rules.md`, `headTopK`).
 *
 * Fits a circle through the eyes and ears and extrapolates the crown:
 * `headTopY = centreY − K · radius`, with K = `Thresholds.HEAD_TOP_K` (1.45, `CALIBRATION_REQUIRED`).
 *
 * Why a least-squares (Kåsa) fit rather than three-point circumcircle: with eyes and ears roughly
 * collinear, a circumcircle is numerically unstable — a millimetre of landmark jitter moves the centre
 * far enough to flip the headroom band. The algebraic fit uses all four points and returns `null` instead
 * of a wild estimate when the system is degenerate, which is the abstention behaviour the rules need.
 */
object HeadTopEstimator {
    private val IDS = listOf("left_eye", "right_eye", "left_ear", "right_ear")

    /** min(visibility, presence) when both exist, else the single channel; null when unknown. */
    private fun channel(landmark: Landmark): Double? {
        val v = landmark.visibility?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)
        val p = landmark.presence?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)
        return if (v != null && p != null) minOf(v, p) else v ?: p
    }

    /** Normalized y of the estimated crown, or null when it cannot be estimated honestly. */
    fun headTopY(landmarks: List<Landmark>): Double? {
        val byId = landmarks.associateBy { it.id }
        val points = IDS.mapNotNull { id ->
            val landmark = byId[id] ?: return@mapNotNull null
            if (!LandmarkUsabilityMeasurable.isMeasurable(landmark)) return@mapNotNull null
            if ((channel(landmark) ?: 0.0) < Thresholds.HEAD_LANDMARK_MIN_VISIBILITY) return@mapNotNull null
            landmark.x to landmark.y
        }
        if (points.size < 3) return null
        val circle = fitCircle(points) ?: return null
        val (cx, cy, r) = circle
        if (cx.isNaN() || cy.isNaN() || r.isNaN() || r <= 0.0) return null
        return cy - Thresholds.HEAD_TOP_K * r
    }

    /** Kåsa algebraic circle fit. Returns (centreX, centreY, radius) or null when degenerate. */
    fun fitCircle(points: List<Pair<Double, Double>>): Triple<Double, Double, Double>? {
        if (points.size < 3) return null
        val n = points.size.toDouble()
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        var sz = 0.0; var sxz = 0.0; var syz = 0.0
        for ((x, y) in points) {
            val z = x * x + y * y
            sx += x; sy += y; sxx += x * x; syy += y * y; sxy += x * y
            sz += z; sxz += x * z; syz += y * z
        }
        // [sxx sxy sx][D]   [-sxz]
        // [sxy syy sy][E] = [-syz]
        // [sx  sy  n ][F]   [-sz ]
        val a11 = sxx; val a12 = sxy; val a13 = sx
        val a21 = sxy; val a22 = syy; val a23 = sy
        val a31 = sx; val a32 = sy; val a33 = n
        val det = determinant(a11, a12, a13, a21, a22, a23, a31, a32, a33)
        if (kotlin.math.abs(det) < 1e-12) return null
        val b1 = -sxz; val b2 = -syz; val b3 = -sz
        val d = determinant(b1, a12, a13, b2, a22, a23, b3, a32, a33) / det
        val e = determinant(a11, b1, a13, a21, b2, a23, a31, b3, a33) / det
        val f = determinant(a11, a12, b1, a21, a22, b2, a31, a32, b3) / det
        val cx = -d / 2.0
        val cy = -e / 2.0
        val squared = cx * cx + cy * cy - f
        if (squared <= 0.0) return null
        return Triple(cx, cy, sqrt(squared))
    }

    private fun determinant(
        a: Double, b: Double, c: Double,
        d: Double, e: Double, f: Double,
        g: Double, h: Double, i: Double,
    ): Double = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
}

/** Bridges `LandmarkUsability.measurable` without importing it at every call site. */
internal object LandmarkUsabilityMeasurable {
    fun isMeasurable(landmark: Landmark) = com.aiphotographer.model.LandmarkUsability.measurable(landmark)
}
