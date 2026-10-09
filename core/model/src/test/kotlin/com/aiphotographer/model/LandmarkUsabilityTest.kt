package com.aiphotographer.model

import org.junit.Assert.*
import org.junit.Test

class LandmarkUsabilityTest {
    @Test fun normativeBoundariesAndUnknownChannels() {
        val cases = listOf(Triple(1.0, 1.0, 1.0), Triple(.9, .9, .8), Triple(.8, .7, .4), Triple(.5, .5, 0.0),
            Triple(.2, 1.0, 0.0), Triple(.4, .3, 0.0), Triple(0.0, 0.0, 0.0), Triple(-.1, 1.2, 0.0))
        cases.forEach { (v, p, u) -> assertEquals(u, LandmarkUsability.evaluate(v, p).value, 1e-12) }
        assertEquals(UsabilityBand.UNUSABLE, LandmarkUsability.evaluate(.5, .5).band)
        assertEquals(UsabilityBand.MARGINAL, LandmarkUsability.evaluate(.8, .7).band)
        assertEquals(UsabilityBand.USABLE, LandmarkUsability.evaluate(.9, null).band)
        assertTrue(LandmarkUsability.evaluate(.9, null).singleChannel)
        assertEquals(.8, LandmarkUsability.evaluate(null, .9).value, 1e-12)
        assertEquals(.5, LandmarkUsability.evaluate(null, null).value, 0.0)
        assertTrue(LandmarkUsability.evaluate(null, null).unknown)
        assertFalse(LandmarkUsability.measurable(Landmark("nose", .5, .5)))
        assertFalse(LandmarkUsability.measurable(Landmark("nose", 1.1, .5, visibility = 1.0)))
        assertTrue(LandmarkUsability.evaluate(Double.NaN, null).unknown)
    }
}
