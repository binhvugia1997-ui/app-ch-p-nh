package com.aiphotographer.perception

import com.aiphotographer.model.BoundingBox
import org.junit.Assert.assertEquals
import org.junit.Test

class FaceContinuityProbeTest {
    private fun box(left: Double, right: Double) = BoundingBox(left, 0.1, right, 0.5)

    @Test fun overlappingAndTouchingBoxesAreDistinguished() {
        val probe = FaceContinuityProbe()
        probe.observe(box(0.1, 0.3))
        probe.observe(box(0.2, 0.4))
        probe.observe(box(0.4, 0.6))
        assertEquals(2, probe.comparablePairs)
        assertEquals(1, probe.disjointPairs)
        assertEquals(0, probe.reacquisitions)
    }

    @Test fun aMissBreaksComparisonAndCountsReacquisition() {
        val probe = FaceContinuityProbe()
        probe.observe(null)
        probe.observe(box(0.1, 0.3))
        probe.observe(null)
        probe.observe(null)
        probe.observe(box(0.7, 0.9))
        assertEquals(0, probe.comparablePairs)
        assertEquals(0, probe.disjointPairs)
        assertEquals(1, probe.reacquisitions)
        probe.observe(box(0.7, 0.9))
        probe.reset()
        probe.observe(box(0.1, 0.3))
        assertEquals(0, probe.comparablePairs)
        assertEquals(0, probe.disjointPairs)
        assertEquals(0, probe.reacquisitions)
    }

    @Test fun degenerateGeometryDoesNotCreateSwitchCandidate() {
        val probe = FaceContinuityProbe()
        probe.observe(box(0.1, 0.1))
        probe.observe(box(0.7, 0.9))
        probe.observe(box(0.8, 0.8))
        assertEquals(0, probe.comparablePairs)
        assertEquals(0, probe.disjointPairs)
    }
}
