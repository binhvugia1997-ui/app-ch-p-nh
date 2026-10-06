package com.aiphotographer.perception

import com.aiphotographer.model.HeadPose
import kotlin.math.*

/** Column-major canonical-face transform, rotation Rz(roll)*Ry(yaw)*Rx(pitch); diagnostic only. */
object HeadPoseMatrix {
    fun decode(matrix: FloatArray): HeadPose? {
        if (matrix.size != 16 || matrix.any { !it.isFinite() }) return null
        fun columnScale(c: Int) = sqrt((0..2).sumOf { matrix[c * 4 + it].toDouble().pow(2) })
        val sx = columnScale(0); val sy = columnScale(1); val sz = columnScale(2)
        if (minOf(sx, sy, sz) <= 1e-9) return null
        val yaw = asin((-matrix[2] / sx).coerceIn(-1.0, 1.0))
        if (abs(cos(yaw)) < 1e-6) return null
        return HeadPose(Math.toDegrees(yaw), Math.toDegrees(atan2(matrix[6] / sy, matrix[10] / sz)),
            Math.toDegrees(atan2(matrix[1] / sx, matrix[0] / sx)))
    }
}
