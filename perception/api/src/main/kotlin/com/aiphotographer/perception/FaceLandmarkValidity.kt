package com.aiphotographer.perception

import com.aiphotographer.model.BoundingBox
import com.aiphotographer.model.Landmark
import kotlin.math.max
import kotlin.math.min

object FaceLandmarkValidity {
    private fun valid(point: Landmark): Boolean = point.x.isFinite() && point.y.isFinite() &&
        (point.z?.isFinite() == true) &&
        (point.visibility?.let { it.isFinite() && it in 0.0..1.0 } ?: true) &&
        (point.presence?.let { it.isFinite() && it in 0.0..1.0 } ?: true)

    fun valid(points: List<Landmark>): Boolean = points.size == 478 && points.all(::valid)

    fun bounds(points: List<Landmark>): BoundingBox? {
        if (points.size != 478) return null
        var left = Double.POSITIVE_INFINITY
        var top = Double.POSITIVE_INFINITY
        var right = Double.NEGATIVE_INFINITY
        var bottom = Double.NEGATIVE_INFINITY
        for (point in points) {
            if (!valid(point)) return null
            left = min(left, point.x); top = min(top, point.y)
            right = max(right, point.x); bottom = max(bottom, point.y)
        }
        return BoundingBox(left, top, right, bottom)
    }
}
