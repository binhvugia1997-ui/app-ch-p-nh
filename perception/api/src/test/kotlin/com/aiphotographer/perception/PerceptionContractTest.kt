package com.aiphotographer.perception

import com.aiphotographer.model.*
import org.junit.Assert.*
import org.junit.Test

class PerceptionContractTest {
    @Test fun unusablePersonLossResetsSessionAndScoresRemainRaw() {
        val assembler = SubjectAssembler()
        val initial = assembler.assemble(pose(),geometry,null).single()
        val unusable = pose(200).copy(landmarks=pose().landmarks.map { it.copy(visibility=.2,presence=1.0) })
        assertTrue(assembler.assemble(unusable,geometry,null).isEmpty())
        val resumed = assembler.assemble(pose(300),geometry,null).single()
        assertNotEquals(initial.trackId,resumed.trackId)
        assertTrue(resumed.landmarks.all { it.visibility==.9 && it.presence==.9 })
        assertEquals(33,PoseLandmarks.ids.size); assertEquals(33,PoseLandmarks.ids.toSet().size)
        val unknown = pose(400).copy(landmarks=pose().landmarks.map { it.copy(visibility=null,presence=null) })
        assertTrue(assembler.assemble(unknown,geometry,null).isEmpty())
    }
    @Test fun freshnessRejectsFutureNegativeAndOverflowAndRecoveryIsBounded() {
        assertFalse(PerceptionFreshness.fresh(-1,100))
        assertFalse(PerceptionFreshness.fresh(Long.MIN_VALUE,Long.MAX_VALUE))
        assertFalse(PerceptionFreshness.fresh(101,100))
        assertEquals(Long.MAX_VALUE,PerceptionFreshness.advance(SourceQuality(Long.MAX_VALUE),100).ageMs)
        assertTrue(PerceptionFreshness.advance(SourceQuality(Long.MAX_VALUE),100).stale)
        assertFalse(PerceptionFreshness.advance(SourceQuality(0),500).stale)
        assertTrue(PerceptionFreshness.advance(SourceQuality(0),501).stale)
        val budget=RecoveryBudget()
        assertEquals(1000L,budget.nextDelayMs()); assertEquals(2000L,budget.nextDelayMs()); assertEquals(4000L,budget.nextDelayMs())
        repeat(10) { assertNull(budget.nextDelayMs()) }
        budget.reset(); assertEquals(1000L,budget.nextDelayMs())
    }
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
