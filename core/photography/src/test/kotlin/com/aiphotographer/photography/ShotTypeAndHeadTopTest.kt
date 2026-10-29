package com.aiphotographer.photography

import com.aiphotographer.model.BoundingBox
import com.aiphotographer.model.ShotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShotTypeAndHeadTopTest {

    @Test fun faceLadderClassifiesHeadshotAndClosePortrait() {
        assertEquals(ShotType.HEADSHOT, ShotTypeEstimator.estimate(0.9, 0.32, true).type)
        assertEquals(ShotType.CLOSE_PORTRAIT, ShotTypeEstimator.estimate(0.9, 0.22, true).type)
    }

    @Test fun bodyLadderRequiresVisibleFeetForFullBody() {
        assertEquals(ShotType.FULL_BODY, ShotTypeEstimator.estimate(0.80, null, true).type)
        // Same height without feet cannot be a full body: the frame cuts the feet off.
        assertEquals(ShotType.THREE_QUARTER, ShotTypeEstimator.estimate(0.80, null, false).type)
        assertEquals(ShotType.THREE_QUARTER, ShotTypeEstimator.estimate(0.60, null, true).type)
        assertEquals(ShotType.HALF_BODY, ShotTypeEstimator.estimate(0.40, null, true).type)
        assertEquals(ShotType.UNKNOWN, ShotTypeEstimator.estimate(0.10, null, true).type)
    }

    @Test fun faceTakesPrecedenceOverBody() {
        // A close portrait still exposes hips; the face band must win over the body band.
        assertEquals(ShotType.CLOSE_PORTRAIT, ShotTypeEstimator.estimate(0.95, 0.25, true).type)
    }

    @Test fun phase2LandmarkBehaviourIsUnchanged() {
        val landmarks = Fixtures.subject().landmarks
        assertEquals(ShotType.FULL_BODY, ShotTypeEstimator.estimate(landmarks).type)
    }

    @Test fun headTopFollowsTheCircleThroughEyesAndEars() {
        val subject = Fixtures.subject(headTop = 0.20)
        val headTop = HeadTopEstimator.headTopY(subject.landmarks)
        assertNotNull(headTop)
        assertEquals(0.20, headTop!!, 0.005)
    }

    @Test fun headTopNeedsThreeVisibleHeadLandmarks() {
        val landmarks = Fixtures.subject().landmarks.filterNot { it.id == "left_ear" || it.id == "right_ear" }
        assertNull(HeadTopEstimator.headTopY(landmarks))
    }

    @Test fun headTopRefusesInvisibleHeadLandmarks() {
        val landmarks = Fixtures.subject(visibility = 0.2).landmarks
        assertNull(HeadTopEstimator.headTopY(landmarks))
    }

    @Test fun circleFitRefusesADegenerateSystem() {
        val duplicated = listOf(0.5 to 0.5, 0.5 to 0.5, 0.5 to 0.5)
        assertNull(HeadTopEstimator.fitCircle(duplicated))
    }

    @Test fun stepDistanceFollowsTheShotLadder() {
        assertEquals(1, ShotTypeEstimator.stepDistance(ShotType.FULL_BODY, ShotType.THREE_QUARTER))
        assertEquals(2, ShotTypeEstimator.stepDistance(ShotType.FULL_BODY, ShotType.HALF_BODY))
        assertEquals(4, ShotTypeEstimator.stepDistance(ShotType.HEADSHOT, ShotType.FULL_BODY))
    }

    @Test fun selectorUsesIntentForOneStepDisagreementOnly() {
        val oneStep = FramingStrategySelector.select(ShotType.FULL_BODY, ShotType.THREE_QUARTER)
        assertEquals(StrategyId.THREE_QUARTER_CENTERED, oneStep.strategy?.id)
        assertEquals(false, oneStep.intentMismatch)

        val twoSteps = FramingStrategySelector.select(ShotType.FULL_BODY, ShotType.HALF_BODY)
        assertEquals(StrategyId.FULL_BODY_CENTERED, twoSteps.strategy?.id)
        assertTrue(twoSteps.intentMismatch)
    }

    @Test fun selectorAbstainsForUnknownWithoutIntent() {
        val selection = FramingStrategySelector.select(ShotType.UNKNOWN, null)
        assertNull(selection.strategy)
    }

    @Test fun measurementsPreferTheMostProminentSubject() {
        val small = Fixtures.subject(BoundingBox(0.05, 0.05, 0.15, 0.20), visibleFraction = 0.9, trackId = 1)
        val large = Fixtures.subject(BoundingBox(0.20, 0.10, 0.60, 0.95), visibleFraction = 0.9, trackId = 2)
        val analysis = Fixtures.scene()
        assertEquals(2, FramingMeasurements.choose(listOf(small, large))?.trackId)
        // And the measured geometry really comes from that box: centre 0.40 → offset 0.20.
        val measured = FramingMeasurements.measure(analysis, large)
        assertEquals(0.20f, measured.centreOffset, 0.01f)
    }
}
