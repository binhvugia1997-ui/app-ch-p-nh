package com.aiphotographer.perception.mediapipe

import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiphotographer.model.*
import com.aiphotographer.perception.*
import com.google.mediapipe.tasks.core.logging.TasksStatsLoggerFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PipelineInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun localRuntimeOwnsOneFrameAndRestartsAcrossLifecycleAndGeometry() = runBlocking {
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission("android.permission.INTERNET"))
        assertEquals("TasksStatsDummyLogger", TasksStatsLoggerFactory.create(context, "PoseLandmarker", "LIVE_STREAM").javaClass.simpleName)
        assertTrue(runCatching { Class.forName("com.google.android.datatransport.runtime.TransportRuntime") }.isFailure)
        val cap = CapabilityReport(DeviceTier.MEDIUM, totalRamBytes = 4L * 1024 * 1024 * 1024,
            glEsVersion = "test", gpuEligible = false, emulator = true)
        MediaPipePipelineFactory.create(context, cap).use { pipeline ->
            suspend fun ready() = withTimeout(30000) { pipeline.state.first { it.pose.status == SourceStatus.READY && it.face.status == SourceStatus.READY } }
            ready()
            var time = SystemClock.elapsedRealtime()
            suspend fun frame(geometry: FrameGeometry) {
                var lease: RgbFrame? = null
                withTimeout(30000) {
                    while (lease == null) { time += 200; lease = pipeline.acquire(time, geometry, DeviceState(DeviceTier.MEDIUM)); if (lease == null) kotlinx.coroutines.delay(10) }
                }
                val owned = requireNotNull(lease)
                val before = pipeline.state.value.metrics.poseCompleted
                assertNull(pipeline.acquire(time + 1, geometry, DeviceState(DeviceTier.MEDIUM)))
                assertNull(pipeline.acquire(time + 100, geometry, DeviceState(DeviceTier.MEDIUM)))
                owned.pixels.fill(0xff000000.toInt())
                pipeline.submit(owned)
                withTimeout(30000) { pipeline.state.first { it.metrics.poseCompleted > before } }
                assertEquals(0, pipeline.state.value.metrics.errors)
                assertTrue(pipeline.state.value.metrics.cadenceSkipped > 0)
                assertEquals(geometry, pipeline.state.value.snapshot?.frame)
                assertTrue(pipeline.state.value.snapshot?.subjects.orEmpty().isEmpty())
            }
            val portrait = FrameGeometry(256, 320, 90, false, CameraFacing.BACK, AnalysisResolution.R480P)
            frame(portrait)
            pipeline.setActive(false)
            assertNull(pipeline.acquire(++time, portrait, DeviceState(DeviceTier.MEDIUM)))
            assertNull(pipeline.state.value.snapshot)
            pipeline.setActive(true)
            ready()
            frame(portrait.copy(width = 320, height = 256, rotationDegrees = 0, isMirrored = true, primaryCameraFacing = CameraFacing.FRONT))
            frame(portrait)
        }
        assertTrue(context.databaseList().none { it.contains("datatransport") })
    }
}
