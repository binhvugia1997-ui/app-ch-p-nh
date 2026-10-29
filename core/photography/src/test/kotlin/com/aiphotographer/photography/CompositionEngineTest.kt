package com.aiphotographer.photography

import com.aiphotographer.model.BoundingBox
import com.aiphotographer.model.FaceObservation
import com.aiphotographer.model.FrameAnalysis
import com.aiphotographer.model.HeadPose
import com.aiphotographer.model.ShotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionEngineTest {
    private val calibrated = EngineConfig(allowUncalibratedHardErrors = true)

    private fun run(
        frames: List<FrameAnalysis>,
        engine: CompositionEngine = CompositionEngine(),
        intent: ShotType? = null,
    ): CompositionResult {
        var state = engine.initialState()
        var last: CompositionResult? = null
        for (frame in frames) {
            last = engine.evaluate(frame, intent, state)
            state = last.state
        }
        return requireNotNull(last)
    }

    private fun resultOf(result: CompositionResult, id: RuleId) = result.results.first { it.ruleId == id }

    @Test fun aWellFramedFullBodyFiresNothing() {
        val result = run(listOf(Fixtures.scene(bbox = BoundingBox(0.35, 0.10, 0.65, 0.90), headTop = 0.115)))
        assertEquals(StrategyId.FULL_BODY_CENTERED, result.strategy?.id)
        assertTrue("expected no firing rule, got ${result.firing}", result.firing.isEmpty())
        assertEquals(Band.GOOD, result.summary?.overallBand)
    }

    @Test fun aSmallSubjectAsksForCloser() {
        val result = run(listOf(Fixtures.scene(bbox = BoundingBox(0.35, 0.10, 0.65, 0.83))))
        assertEquals(StrategyId.FULL_BODY_CENTERED, result.strategy?.id)
        val rule = resultOf(result, RuleId.FRAME_SUBJECT_TOO_SMALL)
        assertTrue(rule.fired)
        assertEquals(SemanticAction.MOVE_CLOSER, rule.suggestedAction)
        assertEquals(Actor.PHOTOGRAPHER, rule.actor)
    }

    @Test fun aSubjectFillingTheFrameAsksForFarther() {
        val result = run(listOf(Fixtures.scene(bbox = BoundingBox(0.20, 0.0, 0.80, 0.99))))
        assertEquals(StrategyId.FULL_BODY_CENTERED, result.strategy?.id)
        val rule = resultOf(result, RuleId.FRAME_SUBJECT_TOO_LARGE)
        assertTrue(rule.fired)
        assertEquals(SemanticAction.MOVE_FARTHER, rule.suggestedAction)
    }

    @Test fun hysteresisKeepsTheViolationUntilTheClearPoint() {
        val engine = CompositionEngine()
        var state = engine.initialState()
        val heights = listOf(0.74, 0.745, 0.76, 0.80)
        val fired = heights.mapIndexed { index, height ->
            val frame = Fixtures.scene(
                bbox = BoundingBox(0.35, 0.10, 0.65, 0.10 + height),
                timestampMs = index * 100L,
            )
            val result = engine.evaluate(frame, null, state)
            state = result.state
            resultOf(result, RuleId.FRAME_SUBJECT_TOO_SMALL).fired
        }
        // 0.74 fires; 0.745 still fires while latched; 0.76 is above the band minimum but below the clear
        // point 0.79, so it keeps firing; 0.80 finally clears.
        assertEquals(listOf(true, true, true, false), fired)
    }

    @Test fun headroomBandsUseTheHeadShotStrategy() {
        val insufficient = run(
            listOf(Fixtures.scene(bbox = BoundingBox(0.30, 0.10, 0.70, 0.95), faceHeight = 0.32, headTop = 0.02)),
        )
        assertEquals(StrategyId.HEADSHOT_HEADROOM_FIRST, insufficient.strategy?.id)
        assertTrue(resultOf(insufficient, RuleId.FRAME_HEADROOM_INSUFFICIENT).fired)
        assertEquals(
            SemanticAction.INCREASE_HEADROOM,
            resultOf(insufficient, RuleId.FRAME_HEADROOM_INSUFFICIENT).suggestedAction,
        )

        val excessive = run(
            listOf(Fixtures.scene(bbox = BoundingBox(0.30, 0.10, 0.70, 0.95), faceHeight = 0.32, headTop = 0.30)),
        )
        assertTrue(resultOf(excessive, RuleId.FRAME_HEADROOM_EXCESSIVE).fired)
        assertEquals(
            SemanticAction.DECREASE_HEADROOM,
            resultOf(excessive, RuleId.FRAME_HEADROOM_EXCESSIVE).suggestedAction,
        )
    }

    @Test fun headroomAbstainsWithoutEnoughHeadLandmarks() {
        val subject = Fixtures.subject(bbox = BoundingBox(0.35, 0.10, 0.65, 0.90))
            .let { it.copy(landmarks = it.landmarks.filterNot { landmark -> landmark.id.endsWith("ear") }) }
        val frame = Fixtures.scene().let { it.copy(subjects = listOf(subject)) }
        val result = run(listOf(frame))
        val rule = resultOf(result, RuleId.FRAME_HEADROOM_EXCESSIVE)
        assertFalse(rule.evaluated)
        assertEquals(AbstainReason.INSUFFICIENT_VISIBILITY, rule.abstainReason)
    }

    @Test fun noSubjectProducesTheGateOnly() {
        val result = run(List(3) { index -> Fixtures.scene(includeSubject = false, timestampMs = index * 100L) })
        assertNull(result.strategy)
        assertEquals(NeutralReason.NO_SUBJECT, result.neutral)
        val gate = resultOf(result, RuleId.FRAME_SUBJECT_PRESENT)
        assertFalse(gate.evaluated)
        assertEquals(AbstainReason.SUBJECT_ABSENT, gate.abstainReason)
        assertTrue(result.firing.isEmpty())
    }

    @Test fun aStalePoseCannotDriveFraming() {
        val result = run(listOf(Fixtures.scene(bbox = BoundingBox(0.35, 0.10, 0.65, 0.83), ageMs = 400)))
        assertEquals(NeutralReason.STALE, result.neutral)
        assertNull(result.strategy)
        assertEquals(AbstainReason.SOURCE_STALE, resultOf(result, RuleId.FRAME_SUBJECT_TOO_SMALL).abstainReason)
    }

    @Test fun aPartiallyVisibleSubjectOnlyFiresTheGate() {
        val result = run(listOf(Fixtures.scene(bbox = BoundingBox(0.35, 0.10, 0.65, 0.83), visibleFraction = 0.52)))
        assertTrue(resultOf(result, RuleId.FRAME_SUBJECT_PRESENT).fired)
        assertEquals(
            AbstainReason.INSUFFICIENT_VISIBILITY,
            resultOf(result, RuleId.FRAME_SUBJECT_TOO_SMALL).abstainReason,
        )
        assertNull(result.target)
    }

    @Test fun anUnknownShotTypeAbstains() {
        val result = run(listOf(Fixtures.scene(bbox = BoundingBox(0.45, 0.40, 0.55, 0.55))))
        assertEquals(NeutralReason.SHOT_TYPE_UNKNOWN, result.neutral)
        assertNull(result.strategy)
        assertNull(result.summary)
    }

    @Test fun aDegradedPipelineGoesNeutral() {
        val result = run(listOf(Fixtures.scene(degradationLevel = 5)))
        assertEquals(NeutralReason.DEGRADED, result.neutral)
        assertTrue(result.results.isEmpty())
        assertNull(result.target)
    }

    @Test fun theGateSeverityIsCappedWhenTheThresholdIsUncalibrated() {
        val frame = Fixtures.scene(bbox = BoundingBox(0.35, 0.10, 0.65, 0.83), visibleFraction = 0.52)
        val released = run(listOf(frame))
        assertEquals(Severity.NUDGE, resultOf(released, RuleId.FRAME_SUBJECT_PRESENT).severity)
        assertEquals(ThresholdStatus.CALIBRATION_REQUIRED, resultOf(released, RuleId.FRAME_SUBJECT_PRESENT).thresholdStatus)

        val developer = run(listOf(frame), CompositionEngine(calibrated))
        assertEquals(Severity.BLOCKING, resultOf(developer, RuleId.FRAME_SUBJECT_PRESENT).severity)
    }

    @Test fun noUncalibratedThresholdProducesAHardErrorInTheReleasedConfig() {
        val frames = listOf(
            Fixtures.scene(bbox = BoundingBox(0.35, 0.10, 0.65, 0.83)),
            Fixtures.scene(bbox = BoundingBox(0.20, 0.0, 0.80, 0.99)),
            Fixtures.scene(visibleFraction = 0.51),
            Fixtures.scene(roll = 4.0),
        )
        for (frame in frames) {
            val result = run(listOf(frame))
            for (rule in result.results) {
                if (rule.thresholdStatus == ThresholdStatus.CALIBRATION_REQUIRED) {
                    assertTrue("${rule.ruleId} escaped the cap", rule.severity.weight <= Severity.NUDGE.weight)
                }
            }
        }
    }

    @Test fun theTargetConvergesToTheBandMidpoint() {
        val engine = CompositionEngine()
        var state = engine.initialState()
        val heights = mutableListOf<Float>()
        for (index in 0 until 5) {
            val result = engine.evaluate(Fixtures.scene(timestampMs = index * 100L), null, state)
            state = result.state
            heights.add(requireNotNull(result.target).box.height)
        }
        val midpoint = (Thresholds.FULL_BODY_SIZE.low + Thresholds.FULL_BODY_SIZE.high) / 2f
        assertEquals(midpoint, heights.first(), 1e-4f)
        assertEquals(midpoint, heights.last(), 1e-3f)
        assertTrue("target should not overshoot", heights.all { kotlin.math.abs(it - midpoint) < 1e-3f })
    }

    @Test fun changingGeometryInvalidatesTheTarget() {
        val engine = CompositionEngine()
        val portrait = engine.evaluate(Fixtures.scene(), null, engine.initialState())
        assertNotNull(portrait.target)
        val landscape = Fixtures.scene(geometry = Fixtures.geometry(width = 480, height = 640, rotation = 90))
        val next = engine.evaluate(landscape, null, portrait.state)
        assertEquals(landscape.frame, requireNotNull(next.target).validFor)
        assertNotNull(next.summary)
    }

    @Test fun aNewTrackIdRestartsSmoothing() {
        val engine = CompositionEngine()
        var state = engine.initialState()
        for (index in 0 until 3) {
            state = engine.evaluate(
                Fixtures.scene(timestampMs = index * 100L, bbox = BoundingBox(0.35, 0.10, 0.65, 0.60)),
                null, state,
            ).state
        }
        val beforeSwitch = state.target?.box?.height
        val switched = engine.evaluate(
            Fixtures.scene(timestampMs = 400L, trackId = 9, bbox = BoundingBox(0.35, 0.10, 0.65, 0.90)),
            null, state,
        )
        assertNotNull(switched.target)
        assertEquals(9, switched.state.trackId)
        // A new subject starts from the raw band midpoint again, not from the previous EMA.
        val midpoint = (Thresholds.FULL_BODY_SIZE.low + Thresholds.FULL_BODY_SIZE.high) / 2f
        assertEquals(midpoint, requireNotNull(switched.target).box.height, 0.02f)
        if (beforeSwitch != null) assertTrue(beforeSwitch > 0f)
    }

    @Test fun theBandReportsNeedsWorkOnlyForRealSeverity() {
        val frame = Fixtures.scene(bbox = BoundingBox(0.35, 0.10, 0.65, 0.83))
        assertEquals(Band.ACCEPTABLE, run(listOf(frame)).summary?.overallBand)
        assertEquals(Band.NEEDS_WORK, run(listOf(frame), CompositionEngine(calibrated)).summary?.overallBand)
    }

    @Test fun aTiltedCameraAsksToLevelTheCamera() {
        val result = run(listOf(Fixtures.scene(roll = 3.0)))
        val rule = resultOf(result, RuleId.FRAME_CAMERA_TILT)
        assertTrue(rule.fired)
        assertEquals(SemanticAction.LEVEL_CAMERA, rule.suggestedAction)
        assertEquals(MeasurementKind.DEGREES, rule.measurement?.kind)
    }

    @Test fun aTouchingEdgeAsksToReframe() {
        val result = run(listOf(Fixtures.scene(bbox = BoundingBox(0.005, 0.30, 0.50, 0.65), headTop = 0.32)))
        assertEquals(StrategyId.HALF_BODY_CENTERED, result.strategy?.id)
        val rule = resultOf(result, RuleId.FRAME_EDGE_MARGIN)
        assertTrue(rule.fired)
        assertEquals(SemanticAction.REFRAME, rule.suggestedAction)
    }

    @Test fun aJointOnTheFrameEdgeAsksToReframeTheJoint() {
        val base = Fixtures.subject(bbox = BoundingBox(0.35, 0.10, 0.65, 0.90))
        val ankleOnEdge = base.copy(
            landmarks = base.landmarks.map { landmark ->
                if (landmark.id == "left_ankle") landmark.copy(y = 0.995) else landmark
            },
        )
        val frame = Fixtures.scene().let { it.copy(subjects = listOf(ankleOnEdge)) }
        val rule = resultOf(run(listOf(frame)), RuleId.FRAME_JOINT_CROP)
        assertTrue(rule.fired)
        assertEquals(SemanticAction.REFRAME_JOINT, rule.suggestedAction)
        assertEquals("guidance.frame.reframe_joint", rule.suggestedAction?.messageKey)
    }

    @Test fun tightCropStrategiesSuppressEdgeAndJointRules() {
        val base = Fixtures.subject(bbox = BoundingBox(0.30, 0.10, 0.70, 0.95), faceHeight = 0.32)
        val frame = Fixtures.scene().let { it.copy(subjects = listOf(base)) }
        val result = run(listOf(frame))
        assertEquals(StrategyId.HEADSHOT_HEADROOM_FIRST, result.strategy?.id)
        assertEquals(
            AbstainReason.NOT_APPLICABLE_SHOT_TYPE,
            resultOf(result, RuleId.FRAME_EDGE_MARGIN).abstainReason,
        )
        assertEquals(
            AbstainReason.NOT_APPLICABLE_SHOT_TYPE,
            resultOf(result, RuleId.FRAME_JOINT_CROP).abstainReason,
        )
    }

    @Test fun extremeOffCentreAsksToReframe() {
        val result = run(listOf(Fixtures.scene(bbox = BoundingBox(0.62, 0.10, 0.98, 0.90), headTop = 0.115)))
        val rule = resultOf(result, RuleId.FRAME_SUBJECT_OFF_CENTER_EXTREME)
        assertTrue(rule.fired)
        assertEquals(SemanticAction.REFRAME, rule.suggestedAction)
    }

    @Test fun negativeSpaceIsNotTreatedAsAnError() {
        val base = Fixtures.subject(
            bbox = BoundingBox(0.62, 0.10, 0.98, 0.90),
            faceHeight = 0.20,
        )
        val lookingAway = base.copy(
            face = base.face?.let { face ->
                FaceObservation(face.landmarks, face.bbox, HeadPose(yawDegrees = 45.0, pitchDegrees = 0.0, rollDegrees = 0.0))
            },
        )
        val frame = Fixtures.scene().let { it.copy(subjects = listOf(lookingAway)) }
        val rule = resultOf(run(listOf(frame)), RuleId.FRAME_SUBJECT_OFF_CENTER_EXTREME)
        assertFalse(rule.evaluated)
        assertEquals(AbstainReason.NOT_APPLICABLE_SHOT_TYPE, rule.abstainReason)
    }

    @Test fun evaluationIsDeterministic() {
        val frame = Fixtures.scene(bbox = BoundingBox(0.35, 0.10, 0.65, 0.83), roll = 2.0)
        val first = run(listOf(frame))
        val second = run(listOf(frame))
        assertEquals(first.results.map { it.ruleId to it.severity }, second.results.map { it.ruleId to it.severity })
        assertEquals(first.target?.box, second.target?.box)
        assertEquals(first.summary?.overallBand, second.summary?.overallBand)
    }
}
