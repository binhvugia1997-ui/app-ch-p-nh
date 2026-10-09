package com.aiphotographer.app

import androidx.test.platform.app.InstrumentationRegistry
import com.aiphotographer.model.*
import com.aiphotographer.perception.SourceStatus
import com.aiphotographer.perception.mediapipe.MediaPipePipelineFactory
import org.junit.Assert.*
import org.junit.Test

/** Also run with -PinstrumentBuildType=profile to exercise the shrunk shipping runtime. */
class PerceptionInstrumentedTest {
    @Test fun packagedModelsInitializeAndReturnThroughProductionAdapter() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cap = CapabilityReport(DeviceTier.MEDIUM, totalRamBytes = 4L * 1024 * 1024 * 1024,
            glEsVersion = "test", gpuEligible = false, emulator = true)
        MediaPipePipelineFactory.create(context, cap).use { pipeline ->
            fun await(condition: () -> Boolean) {
                val limit = android.os.SystemClock.elapsedRealtime() + 30000
                while (!condition() && android.os.SystemClock.elapsedRealtime() < limit) Thread.sleep(10)
                assertTrue("Perception state timeout: ${pipeline.state.value}", condition())
            }
            await { pipeline.state.value.pose.status == SourceStatus.READY && pipeline.state.value.face.status == SourceStatus.READY }
            val frame = requireNotNull(pipeline.acquire(android.os.SystemClock.elapsedRealtime(),
                FrameGeometry(256, 256, 0, false, CameraFacing.BACK, AnalysisResolution.R480P), DeviceState(DeviceTier.MEDIUM)))
            frame.pixels.fill(0xff000000.toInt())
            pipeline.submit(frame)
            await { pipeline.state.value.metrics.poseCompleted == 1L }
            assertEquals(0L, pipeline.state.value.metrics.errors)
            assertTrue(pipeline.state.value.snapshot?.subjects.orEmpty().isEmpty())
        }
        assertTrue(context.databaseList().none { it.contains("datatransport") })
    }
}
