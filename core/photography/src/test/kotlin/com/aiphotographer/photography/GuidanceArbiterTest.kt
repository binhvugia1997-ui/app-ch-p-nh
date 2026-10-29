package com.aiphotographer.photography

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GuidanceArbiterTest {

    @Test fun onlyOnePrimaryIsShown() {
        val arbiter = GuidanceArbiter()
        val state = arbiter.update(
            listOf(
                Fixtures.candidate(SemanticAction.MOVE_CLOSER, score = 100f),
                Fixtures.candidate(SemanticAction.LEVEL_CAMERA, RuleId.FRAME_CAMERA_TILT, score = 95f),
            ),
            0L,
        )
        assertEquals(SemanticAction.MOVE_CLOSER.messageKey, state.primary?.messageId)
        assertNull("both candidates are photographer actions, so no secondary", state.secondary)
    }

    @Test fun secondaryNeedsADifferentActorAndACompatibleScore() {
        val arbiter = GuidanceArbiter()
        val state = arbiter.update(
            listOf(
                Fixtures.candidate(SemanticAction.MOVE_CLOSER, score = 100f),
                Fixtures.candidate(SemanticAction.SUBJECT_MOVE_INWARD, RuleId.FRAME_EDGE_MARGIN, score = 85f),
            ),
            0L,
        )
        assertNotNull(state.secondary)
        assertEquals(Actor.SUBJECT, state.secondary?.actor)

        val tooWeak = GuidanceArbiter().update(
            listOf(
                Fixtures.candidate(SemanticAction.MOVE_CLOSER, score = 100f),
                Fixtures.candidate(SemanticAction.SUBJECT_MOVE_INWARD, RuleId.FRAME_EDGE_MARGIN, score = 70f),
            ),
            0L,
        )
        assertNull(tooWeak.secondary)
    }

    @Test fun dwellKeepsTheCurrentInstructionBeforeASwap() {
        val arbiter = GuidanceArbiter()
        val closer = Fixtures.candidate(SemanticAction.MOVE_CLOSER, score = 100f)
        val tilt = Fixtures.candidate(SemanticAction.LEVEL_CAMERA, RuleId.FRAME_CAMERA_TILT, score = 200f)

        assertEquals(SemanticAction.MOVE_CLOSER.messageKey, arbiter.update(listOf(closer), 0L).primary?.messageId)
        // A stronger candidate appears 300 ms later: too early to replace what is on screen.
        assertEquals(
            SemanticAction.MOVE_CLOSER.messageKey,
            arbiter.update(listOf(closer, tilt), 300L).primary?.messageId,
        )
        // After minDwellMs the stronger one wins.
        assertEquals(SemanticAction.LEVEL_CAMERA.messageKey, arbiter.update(listOf(closer, tilt), 1_000L).primary?.messageId)
    }

    @Test fun aResolvedRuleStaysQuietForTheCooldownAndThenTheRepeatWindow() {
        val arbiter = GuidanceArbiter()
        val closer = Fixtures.candidate(SemanticAction.MOVE_CLOSER, score = 100f)

        assertNotNull(arbiter.update(listOf(closer), 0L).primary)
        // Cause vanished at 100 ms → resolved.
        assertNull(arbiter.update(emptyList(), 100L).primary)
        // Inside cooldownMs (2 500 ms).
        assertNull(arbiter.update(listOf(closer), 2_000L).primary)
        // Past cooldownMs but inside repeatCooldownMs (4 000 ms).
        assertNull(arbiter.update(listOf(closer), 3_000L).primary)
        // Past both windows: the instruction may be shown again.
        assertNotNull(arbiter.update(listOf(closer), 4_200L).primary)
    }

    @Test fun theFlipGuardPreventsLeftRightOscillation() {
        val arbiter = GuidanceArbiter()
        val closer = Fixtures.candidate(SemanticAction.MOVE_CLOSER, score = 100f)
        val farther = Fixtures.candidate(SemanticAction.MOVE_FARTHER, RuleId.FRAME_SUBJECT_TOO_LARGE, score = 100f)

        assertNotNull(arbiter.update(listOf(closer), 0L).primary)
        // Cause vanishes, the opposite direction appears 200 ms later: suppressed, not shown.
        assertNull(arbiter.update(listOf(farther), 200L).primary)
    }

    @Test fun blockingInstructionsIgnoreTheFlipGuard() {
        val arbiter = GuidanceArbiter()
        val closer = Fixtures.candidate(SemanticAction.MOVE_CLOSER, score = 100f)
        val farther = Fixtures.candidate(
            SemanticAction.MOVE_FARTHER, RuleId.FRAME_SUBJECT_TOO_LARGE,
            severity = Severity.BLOCKING, score = 100f,
        )
        assertNotNull(arbiter.update(listOf(closer), 0L).primary)
        assertNotNull(arbiter.update(listOf(farther), 200L).primary)
    }

    @Test fun closerPlusHeadroomIsMergedIntoOneSentence() {
        val arbiter = GuidanceArbiter()
        val state = arbiter.update(
            listOf(
                Fixtures.candidate(SemanticAction.MOVE_CLOSER, score = 100f),
                Fixtures.candidate(SemanticAction.INCREASE_HEADROOM, RuleId.FRAME_HEADROOM_INSUFFICIENT, score = 95f),
            ),
            0L,
        )
        assertEquals("guidance.frame.move_closer_lower_camera", state.primary?.messageId)
        assertEquals("COMPOSITE", state.primary?.id)
        assertEquals(2, state.primary?.ruleIds?.size)
    }

    @Test fun lowConfidenceCandidatesAreDropped() {
        val arbiter = GuidanceArbiter()
        val weak = Fixtures.candidate(SemanticAction.MOVE_CLOSER, confidence = 0.3f, score = 100f)
        assertNull(arbiter.update(listOf(weak), 0L).primary)
    }

    @Test fun lowScoreCandidatesAreDropped() {
        val arbiter = GuidanceArbiter()
        val weak = Fixtures.candidate(SemanticAction.MOVE_CLOSER, score = 0.2f)
        assertNull(arbiter.update(listOf(weak), 0L).primary)
    }

    @Test fun resetForgetsTheDisplayedInstruction() {
        val arbiter = GuidanceArbiter()
        val closer = Fixtures.candidate(SemanticAction.MOVE_CLOSER, score = 100f)
        assertNotNull(arbiter.update(listOf(closer), 0L).primary)
        arbiter.reset()
        // The same cause is never in cooldown after a reset (toggle off → on starts clean).
        assertNotNull(arbiter.update(listOf(closer), 10L).primary)
    }
}
