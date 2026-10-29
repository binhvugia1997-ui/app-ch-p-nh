package com.aiphotographer.photography

import com.aiphotographer.model.BoundingBox
import com.aiphotographer.model.FrameAnalysis
import com.aiphotographer.model.ShotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessAndSessionTest {

    private fun criteria(vararg pairs: Pair<ReadinessCriterionId, Boolean>, required: Boolean = true) =
        pairs.map { (id, satisfied) -> ReadinessCriterion(id, required, satisfied) }

    @Test fun readinessRequiresTheHoldDuration() {
        val machine = ReadinessMachine()
        val satisfied = criteria(
            ReadinessCriterionId.SUBJECT_PRESENT to true,
            ReadinessCriterionId.CAMERA_STABLE to true,
        )
        assertEquals(ReadinessState.NEAR_READY, machine.evaluate(satisfied, 0L, true).state)
        assertEquals(ReadinessState.NEAR_READY, machine.evaluate(satisfied, 200L, true).state)
        assertEquals(ReadinessState.READY, machine.evaluate(satisfied, 400L, true).state)
    }

    @Test fun losingACriterionRestartsTheHold() {
        val machine = ReadinessMachine()
        val satisfied = criteria(ReadinessCriterionId.SUBJECT_PRESENT to true)
        val unsatisfied = criteria(ReadinessCriterionId.SUBJECT_PRESENT to false)
        assertEquals(ReadinessState.NEAR_READY, machine.evaluate(satisfied, 0L, true).state)
        assertEquals(ReadinessState.READY, machine.evaluate(satisfied, 500L, true).state)
        assertEquals(ReadinessState.NOT_READY, machine.evaluate(unsatisfied, 600L, true).state)
        assertEquals(ReadinessState.NEAR_READY, machine.evaluate(satisfied, 700L, true).state)
        assertEquals(ReadinessState.READY, machine.evaluate(satisfied, 1_100L, true).state)
    }

    @Test fun reasonsListOnlyUnsatisfiedCriteria() {
        val machine = ReadinessMachine()
        val report = machine.evaluate(
            criteria(
                ReadinessCriterionId.SUBJECT_PRESENT to true,
                ReadinessCriterionId.SUBJECT_STABLE to false,
            ),
            0L,
            true,
        )
        assertEquals(listOf("guidance.why.ready_subject_stable"), report.reasons)
    }

    @Test fun readinessNeverBlocksManualCapture() {
        val machine = ReadinessMachine()
        val report = machine.evaluate(criteria(ReadinessCriterionId.SUBJECT_PRESENT to false), 0L, true)
        assertFalse(report.blocksManualCapture)
    }

    // ---- session: enable/disable (D2, D3, D8) ----------------------------------------------------

    private fun wellFramed(timestamp: Long): FrameAnalysis =
        Fixtures.scene(bbox = BoundingBox(0.35, 0.10, 0.65, 0.90), timestampMs = timestamp)

    @Test fun smartFramingIsOnByDefault() {
        assertTrue(SmartFramingSession().enabled)
    }

    @Test fun disablingStopsTheEngineCompletely() {
        val session = SmartFramingSession()
        session.setEnabled(false)
        val output = session.update(wellFramed(0L))
        assertFalse(output.enabled)
        assertNull(output.composition)
        assertNull(output.target)
        assertNull(output.summary)
        assertNull(output.strategyId)
        assertNull(output.subjectBox)
        assertEquals(NeutralReason.DISABLED, output.neutral)
        assertEquals(NeutralReason.DISABLED, output.guidance.neutral)
        assertNull(output.guidance.primary)
    }

    @Test fun disabledReadinessDropsCompositionCriteria() {
        val session = SmartFramingSession()
        session.setEnabled(false)
        val output = session.update(wellFramed(0L))
        val ids = output.readiness.criteria.map { it.id }.toSet()
        assertEquals(
            setOf(ReadinessCriterionId.SUBJECT_PRESENT, ReadinessCriterionId.SUBJECT_STABLE, ReadinessCriterionId.CAMERA_STABLE),
            ids,
        )
        assertFalse(output.readiness.compositionIncluded)
        val compositionReasons = setOf(
            RuleId.FRAME_SUBJECT_TOO_SMALL.whyKey,
            RuleId.FRAME_HEADROOM_EXCESSIVE.whyKey,
            RuleId.FRAME_EDGE_MARGIN.whyKey,
        )
        assertTrue(output.readiness.reasons.none { it in compositionReasons })
    }

    @Test fun subjectPresenceStaysRequiredWhenDisabled() {
        val session = SmartFramingSession()
        session.setEnabled(false)
        val output = session.update(Fixtures.scene(includeSubject = false))
        val present = output.readiness.criteria.first { it.id == ReadinessCriterionId.SUBJECT_PRESENT }
        assertTrue(present.required)
        assertFalse(present.satisfied)
    }

    @Test fun togglingOffRecomputesReadinessFromScratch() {
        val session = SmartFramingSession()
        var output = session.update(wellFramed(0L))
        for (index in 1..8) output = session.update(wellFramed(index * 100L))
        assertEquals(ReadinessState.READY, output.readiness.state)

        session.setEnabled(false)
        val disabled = session.update(wellFramed(900L))
        assertEquals(
            "a stale READY must not survive the toggle",
            ReadinessState.NEAR_READY,
            disabled.readiness.state,
        )
    }

    @Test fun togglingBackOnStartsWithoutAStaleTargetOrInstruction() {
        val session = SmartFramingSession()
        session.update(Fixtures.scene(bbox = BoundingBox(0.35, 0.10, 0.65, 0.83), timestampMs = 0L))
        assertNotNull(session.update(wellFramed(100L)).composition)
        session.setEnabled(false)
        assertNull(session.update(wellFramed(200L)).target)
        session.setEnabled(true)
        // A fresh composition state: the previous latch cannot leak into the first enabled frame.
        val output = session.update(wellFramed(300L))
        assertNotNull(output.composition)
        assertEquals(0, requireNotNull(output.composition).state.latches.values.firstOrNull()?.persistenceFrames ?: 0)
    }

    @Test fun setEnabledReportsWhetherStateWasReset() {
        val session = SmartFramingSession()
        assertTrue(session.setEnabled(false))
        assertFalse(session.setEnabled(false))
        assertTrue(session.setEnabled(true))
    }

    @Test fun enabledReadinessIncludesCompositionCriteria() {
        val session = SmartFramingSession()
        val output = session.update(wellFramed(0L))
        assertTrue(output.readiness.compositionIncluded)
        val ids = output.readiness.criteria.map { it.id }
        assertTrue(ReadinessCriterionId.SUBJECT_IN_SIZE_BAND in ids)
        assertTrue(ReadinessCriterionId.EDGE_MARGIN in ids)
    }

    @Test fun headroomIsAdvisoryAndCannotBlockReadiness() {
        val session = SmartFramingSession()
        val frame = Fixtures.scene(
            bbox = BoundingBox(0.30, 0.10, 0.70, 0.95),
            faceHeight = 0.32,
            headTop = 0.30,
            timestampMs = 0L,
        )
        var output = session.update(frame)
        for (index in 1..8) output = session.update(frame.copy(timestampMs = index * 100L))
        val headroom = output.readiness.criteria.first { it.id == ReadinessCriterionId.HEADROOM_IN_BAND }
        assertFalse("the fixture should violate the headroom band", headroom.satisfied)
        assertFalse("headroom must stay a recommendation", headroom.required)
        assertEquals(ReadinessState.READY, output.readiness.state)
        assertTrue(output.readiness.reasons.contains(RuleId.FRAME_HEADROOM_EXCESSIVE.whyKey))
    }

    @Test fun aShotIntentOverridesADetectedTypeByOneStep() {
        val session = SmartFramingSession()
        val output = session.update(wellFramed(0L), intent = ShotType.THREE_QUARTER)
        assertEquals(StrategyId.THREE_QUARTER_CENTERED, output.strategyId)
    }

    @Test fun aFramingInstructionCanBeProducedEndToEnd() {
        val session = SmartFramingSession()
        var output = session.update(Fixtures.scene(bbox = BoundingBox(0.35, 0.10, 0.65, 0.83), timestampMs = 0L))
        output = session.update(Fixtures.scene(bbox = BoundingBox(0.35, 0.10, 0.65, 0.83), timestampMs = 100L))
        assertEquals("guidance.frame.move_closer", output.primary?.messageId)
        assertEquals("MOVE_CLOSER", output.primary?.id)
        assertNotNull(output.explanationFor())
    }

    private fun SmartFramingOutput.explanationFor(): String? =
        primary?.ruleIds?.firstOrNull()?.whyKey
}
