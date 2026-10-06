package com.aiphotographer.perception

import com.aiphotographer.model.*
import org.junit.Assert.*
import org.junit.Test

class PerceptionContractTest {
    private val geometry = FrameGeometry(480, 640, 90, false, CameraFacing.BACK, AnalysisResolution.R480P)
    private fun pose(time: Long = 100) = PoseFrameResult(time, PoseLandmarks.ids.mapIndexed { i, id ->
        Landmark(id, .25 + i / 100.0, .25 + i / 100.0, visibility = .9, presence = .9)
    }, null)
    @Test fun singleLeaseAndLifecycleDiscard() {
        val gate = BatchGate()
        val epoch = gate.epoch()
        val first = gate.acquire()!!
        assertNull(gate.acquire())
        gate.setActive(false)
        assertFalse(gate.valid(first, epoch))
        gate.setActive(true)
        assertNull(gate.acquire()) // pixel storage remains owned until the old callback completes
        gate.release(first)
        val second = gate.acquire()!!
        gate.release(first)
        assertNull(gate.acquire())
        assertTrue(gate.valid(second, gate.epoch()))
    }
    @Test fun lossGeometryAndUnusableLandmarkReset() {
        val assembler = SubjectAssembler()
        val a = assembler.assemble(pose(), geometry, null).single()
        assertEquals(1.0, a.visibleFraction, 0.0)
        assertTrue(a.landmarks.all { it.smoothed })
        assertTrue(assembler.assemble(PoseFrameResult(200, emptyList(), null), geometry, null).isEmpty())
        val b = assembler.assemble(pose(300), geometry, null).single()
        assertNotEquals(a.trackId, b.trackId)
        val filter = LandmarkFilter()
        val raw = Landmark("nose", .1, .1, visibility = 1.0)
        filter.apply(listOf(raw), 100)
        assertFalse(filter.apply(listOf(raw.copy(visibility = .2)), 200).single().smoothed)
        assertEquals(.9, filter.apply(listOf(raw.copy(x = .9)), 300).single().x, 0.0)
        assertNotEquals(b.trackId, assembler.assemble(pose(400), geometry.copy(isMirrored = true), null).single().trackId)
    }
    @Test fun faceAssociationAndFreshnessAbstain() {
        val assembler = SubjectAssembler()
        val face = FaceObservation(emptyList(), BoundingBox(.1, .1, .4, .4))
        assertNotNull(assembler.assemble(pose(), geometry, FaceFrameResult(100, face)).single().face)
        assertNull(assembler.assemble(pose(701), geometry, FaceFrameResult(100, face)).single().face)
        assertNull(assembler.assemble(pose(800), geometry, FaceFrameResult(801, face)).single().face)
        assertNull(assembler.assemble(pose(900), geometry, FaceFrameResult(900, face.copy(bbox = BoundingBox(.8, .8, 1.0, 1.0)))).single().face)
        assertTrue(PerceptionFreshness.fresh(100, 600)); assertFalse(PerceptionFreshness.fresh(100, 601))
    }
}
