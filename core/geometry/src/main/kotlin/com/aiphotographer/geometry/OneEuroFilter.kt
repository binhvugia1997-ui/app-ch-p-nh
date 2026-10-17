package com.aiphotographer.geometry

import kotlin.math.PI
import kotlin.math.abs

/** Engineering seed parameters; CALIBRATION_REQUIRED. Timestamp is supplied by the frame source. */
class OneEuroFilter(private val minCutoff: Double = 1.0, private val beta: Double = 0.007, private val derivativeCutoff: Double = 1.0) {
    private var previousTime: Long? = null
    private var previousRaw = 0.0
    private var filtered = 0.0
    private var derivative = 0.0
    init { require(minCutoff > 0 && beta >= 0 && derivativeCutoff > 0) }
    fun reset() { previousTime = null }
    fun filter(value: Double, timestampMs: Long): Double {
        require(value.isFinite())
        val old = previousTime
        if (old == null || timestampMs <= old) {
            previousTime = timestampMs; previousRaw = value; filtered = value; derivative = 0.0
            return value
        }
        val dt = (timestampMs - old) / 1000.0
        fun alpha(cutoff: Double) = 1.0 / (1.0 + 1.0 / (2 * PI * cutoff * dt))
        val ad = alpha(derivativeCutoff)
        derivative += ad * ((value - previousRaw) / dt - derivative)
        filtered += alpha(minCutoff + beta * abs(derivative)) * (value - filtered)
        previousTime = timestampMs; previousRaw = value
        return filtered
    }
}
