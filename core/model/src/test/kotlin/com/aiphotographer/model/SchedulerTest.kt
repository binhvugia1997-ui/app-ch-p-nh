package com.aiphotographer.model

import org.junit.Assert.*
import org.junit.Test

class SchedulerTest {
    @Test fun noQueueAndIndependentCadence() {
        val scheduler = StageScheduler(mapOf(Stage.LUMA to 500L, Stage.RGB to 1000L))
        assertTrue(scheduler.tryStart(Stage.LUMA, 0))
        assertFalse(scheduler.tryStart(Stage.LUMA, 999))
        assertTrue(scheduler.tryStart(Stage.RGB, 0))
        scheduler.finish(Stage.LUMA)
        assertFalse(scheduler.tryStart(Stage.LUMA, 499))
        assertTrue(scheduler.tryStart(Stage.LUMA, 500))
        assertFalse(scheduler.tryStart(Stage.POSE, 10000))
    }
    @Test fun boundedLatencyPercentilesAndEmptyWindow() {
        val window = LatencyWindow(4)
        assertNull(window.summary().p50Ms)
        for (n in 1..5) window.record(n * 1_000_000L)
        assertEquals(3.0, window.summary().p50Ms!!, 0.0)
        assertEquals(5.0, window.summary().p95Ms!!, 0.0)
        assertEquals(4, window.summary().count)
    }
}
