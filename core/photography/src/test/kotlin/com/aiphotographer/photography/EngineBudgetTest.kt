package com.aiphotographer.photography

import com.aiphotographer.model.BoundingBox
import com.aiphotographer.model.FrameAnalysis
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The JVM half of the Phase 5 gate: prove the engine cannot be the bottleneck before any device measurement
 * exists (`composition-engine.md` §9.3, `performance-strategy.md` §0).
 *
 * `composition-engine.md` §9.3 phrases the gate as "10 000 synthetic frames takes < 2 ms on the JVM". That
 * sentence cannot be read literally (10 000 frames in total under 2 ms is 200 ns/frame, below what a single
 * `HashMap` lookup costs), so this test enforces the only self-consistent reading: **< 2 ms per frame**, and
 * additionally asserts the whole 10 000-frame run finishes inside a generous 5 s wall-clock bound.
 *
 * This is a JVM/oracle measurement on the build machine. It is NOT a device measurement and says nothing
 * about G-P4 (≤ 35 ms per analysed frame on a MEDIUM device).
 */
class EngineBudgetTest {

    private fun frame(index: Int): FrameAnalysis = Fixtures.scene(
        bbox = BoundingBox(0.35, 0.10, 0.65, 0.10 + 0.72 + (index % 5) * 0.01),
        timestampMs = index * 33L,
    )

    @Test fun tenThousandFramesStayFarBelowThePerFrameBudget() {
        val engine = CompositionEngine()
        var state = engine.initialState()
        // Warm-up: let the JIT compile the hot paths before timing.
        repeat(2_000) { index -> state = engine.evaluate(frame(index), null, state).state }

        val count = 10_000
        val started = System.nanoTime()
        repeat(count) { index -> state = engine.evaluate(frame(index), null, state).state }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000.0
        val perFrameMs = elapsedMs / count

        assertTrue(
            "engine averaged %.4f ms/frame over %d frames (%.1f ms total)".format(perFrameMs, count, elapsedMs),
            perFrameMs < 2.0,
        )
        assertTrue("total run took %.1f ms for %d frames".format(elapsedMs, count), elapsedMs < 5_000.0)
    }
}
