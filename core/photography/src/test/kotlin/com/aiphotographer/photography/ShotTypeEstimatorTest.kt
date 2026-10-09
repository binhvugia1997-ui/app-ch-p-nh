package com.aiphotographer.photography

import com.aiphotographer.model.*
import org.junit.Assert.*
import org.junit.Test

class ShotTypeEstimatorTest {
    private fun points(vararg parts: String) = (listOf("nose") + parts.flatMap { listOf("left_$it", "right_$it") })
        .map { Landmark(it, .5, .5, visibility = 1.0, presence = 1.0) }
    @Test fun extentBoundaryTable() {
        assertEquals(ShotType.UNKNOWN, ShotTypeEstimator.estimate(emptyList()).type)
        assertEquals(ShotType.UNKNOWN, ShotTypeEstimator.estimate(points("shoulder")).type)
        assertEquals(ShotType.HALF_BODY, ShotTypeEstimator.estimate(points("shoulder", "hip")).type)
        assertEquals(ShotType.THREE_QUARTER, ShotTypeEstimator.estimate(points("shoulder", "hip", "knee")).type)
        val full = points("shoulder", "hip", "knee", "ankle", "foot_index")
        assertEquals(ShotType.FULL_BODY, ShotTypeEstimator.estimate(full).type)
        assertEquals(0.0, ShotTypeEstimator.estimate(full).confidence, 0.0)
        assertEquals(ShotType.THREE_QUARTER, ShotTypeEstimator.estimate(full.map { if (it.id == "left_ankle") it.copy(visibility = .5) else it }).type)
        assertEquals(ShotType.UNKNOWN, ShotTypeEstimator.estimate(full.map { it.copy(visibility = null, presence = null) }).type)
        assertEquals(ShotType.UNKNOWN, ShotTypeEstimator.estimate(full.map { it.copy(x = 1.1) }).type)
    }
}
