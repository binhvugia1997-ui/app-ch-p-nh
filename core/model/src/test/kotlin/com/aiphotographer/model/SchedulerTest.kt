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
    @Test fun summarizingDoesNotReorderLiveRingAndClearDiscardsOldValues() {
        val window = LatencyWindow(4)
        listOf(9L, 1L, 8L, 2L).forEach { window.record(it * 1_000_000) }
        assertEquals(window.summary(), window.summary())
        window.record(3_000_000)
        assertEquals(LatencySummary(4, 2.0, 8.0), window.summary())
        window.clear()
        assertEquals(LatencySummary(0, null, null), window.summary())
        window.record(-100)
        assertEquals(LatencySummary(1, 0.0, 0.0), window.summary())
    }

    @Test fun latencySummariesMatchNearestRankReferenceAcrossCapacitiesAndWraps() {
        val random = java.util.Random(4096)
        for (capacity in listOf(1, 4, 19, 4096)) {
            val window = LatencyWindow(capacity)
            val history = mutableListOf<Long>()
            repeat(capacity * 2 + 3) { index ->
                val duration = random.nextInt(100_000_000).toLong() - 10_000
                window.record(duration)
                history.add(duration.coerceAtLeast(0))
                if (index % 17 == 0 || index == capacity * 2 + 2) {
                    val expected = history.takeLast(capacity).sorted()
                    fun percentile(p: Double) = expected[(kotlin.math.ceil(expected.size * p).toInt() - 1)] / 1e6
                    assertEquals(LatencySummary(expected.size, percentile(.5), percentile(.95)), window.summary())
                }
            }
        }
    }
}
