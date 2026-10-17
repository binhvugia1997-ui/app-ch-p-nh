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
    @Test fun boundsMatchReferenceForAllLandmarksWithoutClampingOrMutation() {
        val random = java.util.Random(478)
        repeat(25) {
            val input = List(478) { index -> Landmark("face_$index", random.nextDouble() * 4 - 2,
                random.nextDouble() * 4 - 2, random.nextDouble(), null, null) }
            val original = input.toList()
            val actual = requireNotNull(FaceLandmarkValidity.bounds(input))
            assertEquals(input.minOf { it.x }, actual.left, 0.0)
            assertEquals(input.minOf { it.y }, actual.top, 0.0)
            assertEquals(input.maxOf { it.x }, actual.right, 0.0)
            assertEquals(input.maxOf { it.y }, actual.bottom, 0.0)
            assertEquals(original, input)
        }
    }

    @Test fun boundsRejectEveryInvalidLandmarkContract() {
        val invalid = listOf(points.dropLast(1), points + points[0]) +
            listOf(points[0].copy(x = Double.NaN), points[0].copy(y = Double.POSITIVE_INFINITY),
                points[0].copy(z = null), points[0].copy(z = Double.NEGATIVE_INFINITY),
                points[0].copy(visibility = 1.1), points[0].copy(presence = -0.1),
                points[0].copy(presence = Double.NaN)).map { bad ->
                    points.toMutableList().apply { this[477] = bad } }
        invalid.forEach { assertNull(FaceLandmarkValidity.bounds(it)); assertFalse(FaceLandmarkValidity.valid(it)) }
    }

    @Test fun boundsKeepDegenerateFacesAndOptionalConfidenceContract() {
        val boundary = points.map { it.copy(visibility = 0.0, presence = 1.0) }
        val actual = requireNotNull(FaceLandmarkValidity.bounds(boundary))
        assertEquals(0.5, actual.left, 0.0); assertEquals(0.5, actual.right, 0.0)
        assertEquals(0.5, actual.top, 0.0); assertEquals(0.5, actual.bottom, 0.0)
    }
}
