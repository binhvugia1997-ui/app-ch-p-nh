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
    @Test fun liteModelRepeatedBlankFramesPreserveInputAndReportNoPerson() {
        val model = mappedModel(context,"models/pose_landmarker_lite.task")
        assertTrue(model.isDirect);assertTrue(model.isReadOnly)
        assertThrows(java.nio.ReadOnlyBufferException::class.java) { model.put(0,0.toByte()) }
        val geometry = FrameGeometry(256,256,0,false,CameraFacing.BACK,AnalysisResolution.R480P)
        MediaPipePoseSource(context,DelegateKind.CPU,"pose_landmarker_lite").use { source ->
            repeat(3) { index ->
                val pixels=IntArray(256*256) { 0xff000000.toInt() }
                val done=java.util.concurrent.CountDownLatch(1)
                var result: PoseFrameResult? = null
                var error: String? = null
                source.submit(RgbFrame((index+1)*200L,geometry,DeviceState(DeviceTier.MEDIUM),pixels),
                    { result=it;done.countDown() },{ error=it;done.countDown() })
                assertTrue(done.await(30,java.util.concurrent.TimeUnit.SECONDS))
                assertNull(error);assertEquals((index+1)*200L,result!!.timestampMs)
                assertTrue(result!!.landmarks.isEmpty());assertTrue(pixels.all { it==0xff000000.toInt() })
                source.releaseImage()
            }
        }
    }
    @Test fun faceRepeatedBlankFramesReportNoFaceWithCorrectTimestamps() {
        val geometry = FrameGeometry(256,256,0,false,CameraFacing.BACK,AnalysisResolution.R480P)
        MediaPipeFaceSource(context,DelegateKind.CPU).use { source ->
            repeat(3) { index ->
                val done=java.util.concurrent.CountDownLatch(1)
                var result: FaceFrameResult? = null
                var error: String? = null
                source.submit(RgbFrame((index+1)*200L,geometry,DeviceState(DeviceTier.MEDIUM),IntArray(256*256) { 0xff000000.toInt() }),
                    { result=it;done.countDown() },{ error=it;done.countDown() })
                assertTrue(done.await(30,java.util.concurrent.TimeUnit.SECONDS))
                assertNull(error);assertEquals((index+1)*200L,result!!.timestampMs);assertNull(result!!.face)
                source.releaseImage()
            }
        }
    }
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
            frame(portrait)
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
