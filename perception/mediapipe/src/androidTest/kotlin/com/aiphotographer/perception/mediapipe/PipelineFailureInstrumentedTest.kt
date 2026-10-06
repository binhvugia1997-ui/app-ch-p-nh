package com.aiphotographer.perception.mediapipe

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiphotographer.model.*
import com.aiphotographer.perception.*
import com.google.mediapipe.framework.image.ByteBufferExtractor
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PipelineFailureInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val cap = CapabilityReport(DeviceTier.MEDIUM,totalRamBytes=4L shl 30,glEsVersion="test",gpuEligible=false,emulator=true)
    private val geometry = FrameGeometry(2,2,0,false,CameraFacing.BACK,AnalysisResolution.R480P)
    private class Pose : OwnedPoseSource {
        override val capability = SourceCapability(worldLandmarks=true)
        override val diagnostic = SourceDiagnostic(SourceStatus.READY,DelegateKind.CPU,"fake-test-source")
        var result: ((PoseFrameResult)->Unit)? = null
        var error: ((String)->Unit)? = null
        val submitted = CountDownLatch(1)
        val closed = CountDownLatch(1)
        var time = 0L
        override fun submit(frame: RgbFrame,result: (PoseFrameResult)->Unit,error: (String)->Unit) {
            this.result=result;this.error=error;time=frame.timestampMs;submitted.countDown()
        }
        override fun releaseImage() = Unit
        override fun close() { closed.countDown() }
    }
    private class Face : OwnedFaceSource {
        override val capability = SourceCapability()
        override val diagnostic = SourceDiagnostic(SourceStatus.READY,DelegateKind.CPU,"fake-test-source")
        override fun submit(frame: RgbFrame,result: (FaceFrameResult)->Unit,error: (String)->Unit) { result(FaceFrameResult(frame.timestampMs,null)) }
        override fun releaseImage() = Unit
        override fun close() = Unit
    }
    private suspend fun ready(pipeline: PerceptionPipeline) = withTimeout(10000) {
        pipeline.state.first { it.pose.status==SourceStatus.READY && it.face.status==SourceStatus.READY }
    }
    @Test fun initializationRecoveryBudgetStopsAndLifecycleAllowsRetry() = runBlocking {
        val attempts=AtomicInteger()
        MediaPipePipeline(context,cap,{ _,_ -> attempts.incrementAndGet();error("Injected initialization failure") },{ Face() },{ 10 }).use { pipeline ->
            withTimeout(10000) { while(attempts.get()<4) delay(10) }
            delay(150)
            assertEquals(4,attempts.get());assertEquals(SourceStatus.UNAVAILABLE,pipeline.state.value.pose.status)
            assertNull(pipeline.acquire(1,geometry,DeviceState(DeviceTier.MEDIUM)))
            pipeline.setActive(false);pipeline.setActive(true)
            withTimeout(10000) { while(attempts.get()<8) delay(10) }
            delay(150);assertEquals(8,attempts.get())
        }
    }
    @Test fun submittedCancellationCannotReleasePixelsAndOldEpochCannotPublish() = runBlocking {
        val poses=CopyOnWriteArrayList<Pose>()
        MediaPipePipeline(context,cap,{ _,_ -> Pose().also(poses::add) },{ Face() }).use { pipeline ->
            ready(pipeline)
            val lease= pipeline.acquire(100,geometry,DeviceState(DeviceTier.MEDIUM))!!
            pipeline.submit(lease)
            assertTrue(poses.last().submitted.await(5,TimeUnit.SECONDS))
            pipeline.cancel(lease)
            assertNull(pipeline.acquire(200,geometry,DeviceState(DeviceTier.MEDIUM)))
            val old=poses.last()
            pipeline.setActive(false)
            assertNull(pipeline.state.value.snapshot)
            old.result!!(PoseFrameResult(100,emptyList(),null))
            pipeline.setActive(true);ready(pipeline)
            assertTrue(old.closed.await(5,TimeUnit.SECONDS))
            assertNull(pipeline.state.value.snapshot)
            val next=pipeline.acquire(300,geometry,DeviceState(DeviceTier.MEDIUM))!!
            pipeline.submit(next);assertTrue(poses.last().submitted.await(5,TimeUnit.SECONDS))
            poses.last().result!!(PoseFrameResult(300,emptyList(),null))
            withTimeout(10000) { pipeline.state.first { it.snapshot?.timestampMs==300L } }
            assertEquals(0,pipeline.state.value.metrics.errors)
        }
    }
    @Test fun runtimeErrorIsRepresentedAndResourcesCloseBeforeRecovery() = runBlocking {
        val poses=CopyOnWriteArrayList<Pose>()
        MediaPipePipeline(context,cap,{ _,_ -> Pose().also(poses::add) },{ Face() },{ 100 }).use { pipeline ->
            ready(pipeline)
            val first=poses.last()
            val lease=pipeline.acquire(100,geometry,DeviceState(DeviceTier.MEDIUM))!!
            pipeline.submit(lease);assertTrue(first.submitted.await(5,TimeUnit.SECONDS))
            first.error!!("INJECTED_RUNTIME_FAILURE")
            withTimeout(10000) { pipeline.state.first { it.pose.status==SourceStatus.UNAVAILABLE } }
            assertEquals("INJECTED_RUNTIME_FAILURE",pipeline.state.value.pose.errorCode)
            assertNull(pipeline.state.value.snapshot)
            assertTrue(first.closed.await(5,TimeUnit.SECONDS))
            ready(pipeline);assertEquals(2,poses.size)
            assertEquals(1,pipeline.state.value.metrics.errors)
        }
    }
    @Test fun rapidLifecycleChangesCoalesceWhileInitializationIsBlocked() = runBlocking {
        val entered=CountDownLatch(1);val resume=CountDownLatch(1);val attempts=AtomicInteger()
        MediaPipePipeline(context,cap,{ _,_ ->
            if(attempts.incrementAndGet()==1) { entered.countDown();check(resume.await(5,TimeUnit.SECONDS)) }
            Pose()
        },{ Face() }).use { pipeline ->
            try {
                assertTrue(entered.await(5,TimeUnit.SECONDS))
                repeat(1000) { pipeline.setActive(false);pipeline.setActive(true) }
            } finally { resume.countDown() }
            ready(pipeline);assertTrue(attempts.get()<=2)
            assertNull(pipeline.state.value.snapshot)
        }
    }
    @Test fun reusableDirectInputPreservesRgbChannelsAcrossImageClosure() {
        val pixels=intArrayOf(0xff123456.toInt(),0xffabcdef.toInt(),0xff010203.toInt(),0xfffefdfc.toInt())
        TaskImage().use { storage ->
            repeat(3) {
                val image=storage.prepare(RgbFrame(it.toLong(),geometry,DeviceState(DeviceTier.MEDIUM),pixels))
                val buffer=ByteBufferExtractor.extract(image).duplicate()
                assertEquals(12,buffer.remaining())
                assertEquals(0x12,buffer.get().toInt() and 255);assertEquals(0x34,buffer.get().toInt() and 255)
                assertEquals(0x56,buffer.get().toInt() and 255)
                storage.release()
            }
        }
    }
    @Test fun mismatchedTimestampDoesNotBecomeAResult() = runBlocking {
        val source=Pose()
        MediaPipePipeline(context,cap,{ _,_ -> source },{ Face() },{ 1000 }).use { pipeline ->
            ready(pipeline)
            val frame=pipeline.acquire(100,geometry,DeviceState(DeviceTier.MEDIUM))!!
            pipeline.submit(frame);assertTrue(source.submitted.await(5,TimeUnit.SECONDS))
            source.result!!(PoseFrameResult(101,emptyList(),null))
            withTimeout(10000) { pipeline.state.first { it.pose.status==SourceStatus.UNAVAILABLE } }
            assertNull(pipeline.state.value.snapshot);assertEquals("POSE_TIMESTAMP_INVALID",pipeline.state.value.pose.errorCode)
        }
    }
}
