package com.aiphotographer.geometry

import org.junit.Assert.*
import org.junit.Test

class OneEuroFilterTest {
    @Test fun constantInputAndMonotoneStepWithoutOvershoot() {
        val filter = OneEuroFilter()
        repeat(100) { assertEquals(.25, filter.filter(.25, it * 33L), 1e-12) }
        var last = .25
        repeat(100) {
            val value = filter.filter(1.0, (it + 100) * 33L)
            assertTrue(value >= last && value <= 1.0); last = value
        }
        assertTrue(last > .999)
        filter.reset()
        assertEquals(0.0, filter.filter(0.0, 0), 0.0)
    }
    @Test fun timestampReversalResetsWithoutInfiniteDerivative() {
        val filter = OneEuroFilter()
        filter.filter(1.0, 100)
        assertEquals(2.0, filter.filter(2.0, 99), 0.0)
        assertTrue(filter.filter(3.0, 100).isFinite())
    }
}
