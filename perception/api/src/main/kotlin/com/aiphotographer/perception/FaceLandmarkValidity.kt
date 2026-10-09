package com.aiphotographer.perception

import com.aiphotographer.model.Landmark

object FaceLandmarkValidity {
    fun valid(points: List<Landmark>): Boolean = points.size == 478 && points.all {
        it.x.isFinite() && it.y.isFinite() && (it.z?.isFinite() == true) &&
            (it.visibility?.let { v -> v.isFinite() && v in 0.0..1.0 } ?: true) &&
            (it.presence?.let { p -> p.isFinite() && p in 0.0..1.0 } ?: true)
    }
}
