package com.aiphotographer.perception

import com.aiphotographer.model.Landmark
import org.junit.Assert.*
import org.junit.Test

class FaceLandmarkValidityTest {
    private val points = List(478) { Landmark("face_$it", 0.5, 0.5, 0.0, null, null) }
    @Test fun missingConfidenceIsValidAndOutOfFrameIsNotClamped() {
        assertTrue(FaceLandmarkValidity.valid(points))
        assertTrue(FaceLandmarkValidity.valid(points.toMutableList().apply { this[0] = this[0].copy(x = -0.1) }))
    }
    @Test fun rejectsWrongCountNonfiniteAndInvalidReportedConfidence() {
        assertFalse(FaceLandmarkValidity.valid(points.dropLast(1)))
        for (bad in listOf(points[0].copy(z = null), points[0].copy(x = Double.NaN), points[0].copy(z = Double.POSITIVE_INFINITY),
            points[0].copy(visibility = 1.1), points[0].copy(presence = -0.1))) {
            assertFalse(FaceLandmarkValidity.valid(points.toMutableList().apply { this[0] = bad }))
        }
    }
}
