package com.aiphotographer.perception.mediapipe

import android.content.Context
import android.os.SystemClock
import android.os.Trace
import android.os.Build
import android.util.Log
import com.aiphotographer.model.*
import com.aiphotographer.perception.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ScheduledFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object MediaPipePipelineFactory {
    fun create(context: Context, capability: CapabilityReport): PerceptionPipeline = MediaPipePipeline(context.applicationContext, capability)
}

private class MediaPipePipeline(private val context: Context, private val capability: CapabilityReport) : PerceptionPipeline {
    private val worker = java.util.concurrent.ScheduledThreadPoolExecutor(1) { Thread(it, "Phase2Perception") }
        .apply { removeOnCancelPolicy = true }
    private val dispatcher = worker.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val completions = Channel<() -> Unit>(2)
    private val gate = BatchGate()
    private val closed = AtomicBoolean(false)
    private val active = AtomicBoolean(true)
    private val mutableState = MutableStateFlow(PerceptionState())
    override val state = mutableState.asStateFlow()
    private var pose: MediaPipePoseSource? = null
    private var face: MediaPipeFaceSource? = null
    private val assembler = SubjectAssembler()
    private var lastFace: FaceFrameResult? = null
    private var pixels = IntArray(0)
    private data class Batch(val frame: RgbFrame, val token: Long, val epoch: Long, val startNs: Long,
        var outstanding: Int = 0, var poseResult: PoseFrameResult? = null, var failed: Boolean = false,
        var watchdog: ScheduledFuture<*>? = null)
    @Volatile private var batch: Batch? = null
    private var lastPoseMs = -1L
    private var lastFaceMs = -1L
    private var lastTimestampMs = -1L
    private var startedNs = 0L
    private var scheduleOffered = 0L
    private var scheduleBusy = 0L
    private var scheduleWindowMs = -1L
    private var recentBusyRatio = 0.0
    private var metrics = PerceptionMetrics()
    private val windows = mapOf("pose" to LatencyWindow(), "face" to LatencyWindow(), "batch" to LatencyWindow())
    private val schedule = PerceptionSchedule(capability.tier)
    private var geometry: FrameGeometry? = null
    private val level get() = schedule.level
    private fun beginTrace(name: String, token: Long) { if (Build.VERSION.SDK_INT >= 29) Trace.beginAsyncSection(name, token.toInt()) }
    private fun endTrace(name: String, token: Long) { if (Build.VERSION.SDK_INT >= 29) Trace.endAsyncSection(name, token.toInt()) }
    init {
        scope.launch { for (completion in completions) completion() }
        worker.execute { if (gate.epoch() == 0L) initialize() }
    }
    private fun initialize() {
        if (closed.get() || !active.get()) return
        val model = if (schedule.useLite) "pose_landmarker_lite" else "pose_landmarker_full"
        fun delegates() = if (capability.gpuEligible && !capability.emulator) listOf(DelegateKind.GPU, DelegateKind.CPU) else listOf(DelegateKind.CPU)
        for (delegate in delegates()) {
            try { pose = MediaPipePoseSource(context, delegate, model); break }
            catch (error: Throwable) {
                if (error is VirtualMachineError || error is ThreadDeath) throw error
                Log.w("Phase2Perception", "pose_init_failure delegate=$delegate type=${error.javaClass.simpleName}", error)
            }
        }
        if (closed.get() || !active.get()) { closeSources(); return }
        for (delegate in delegates()) {
            try { face = MediaPipeFaceSource(context, delegate); break }
            catch (error: Throwable) {
                if (error is VirtualMachineError || error is ThreadDeath) throw error
                Log.w("Phase2Perception", "face_init_failure delegate=$delegate type=${error.javaClass.simpleName}", error)
            }
        }
        if (closed.get() || !active.get()) { closeSources(); return }
        mutableState.value = mutableState.value.copy(
            pose = pose?.diagnostic ?: SourceDiagnostic(SourceStatus.UNAVAILABLE, model = model, errorCode = "POSE_INIT_FAILED"),
            face = face?.diagnostic ?: SourceDiagnostic(SourceStatus.UNAVAILABLE, model = "face_landmarker", errorCode = "FACE_INIT_FAILED"))
        Log.i("Phase2Perception", "configured pose=${mutableState.value.pose} face=${mutableState.value.face}; runtime_delegate_evidence=NOT_MEASURED")
    }
    @Synchronized override fun acquire(timestampMs: Long, geometry: FrameGeometry, device: DeviceState): RgbFrame? {
        if (closed.get() || !active.get() || mutableState.value.pose.status != SourceStatus.READY) return null
        metrics = metrics.copy(offered = metrics.offered + 1)
        if (startedNs == 0L) startedNs = SystemClock.elapsedRealtimeNanos()
        if (timestampMs <= lastTimestampMs || (lastPoseMs >= 0 && timestampMs - lastPoseMs < schedule.posePeriodMs)) {
            metrics = metrics.copy(cadenceSkipped = metrics.cadenceSkipped + 1); return null
        }
        val token = gate.acquire()
        if (token == null) { metrics = metrics.copy(busySkipped = metrics.busySkipped + 1); return null }
        lastPoseMs = timestampMs; lastTimestampMs = timestampMs
        if (pixels.size != geometry.width * geometry.height) pixels = IntArray(geometry.width * geometry.height)
        val frame = RgbFrame(timestampMs, geometry, device, pixels)
        batch = Batch(frame, token, gate.epoch(), SystemClock.elapsedRealtimeNanos())
        metrics = metrics.copy(accepted = metrics.accepted + 1)
        return frame
    }
    override fun submit(frame: RgbFrame) {
        val current = batch?.takeIf { it.frame === frame } ?: return
        if (closed.get()) { gate.release(current.token); return }
        worker.execute {
            if (!gate.valid(current.token, current.epoch) || closed.get()) { release(current); return@execute }
            if (geometry != null && geometry != frame.geometry) {
                closeSources(); assembler.reset(); lastFace = null; schedule.resetMotion(); initialize()
            }
            geometry = frame.geometry
            val poseSource = pose ?: run { release(current); return@execute }
            val faceSource = face
            val runFace = face != null && mutableState.value.snapshot?.subjects?.isNotEmpty() == true &&
                (lastFaceMs < 0 || frame.timestampMs - lastFaceMs >= schedule.facePeriodMs)
            current.outstanding = if (runFace) 2 else 1
            val poseStart = SystemClock.elapsedRealtimeNanos()
            beginTrace("phase2_pose", current.token)
            poseSource.submit(frame, { result -> enqueue {
                endTrace("phase2_pose", current.token)
                if (batch !== current) return@enqueue
                windows.getValue("pose").record(SystemClock.elapsedRealtimeNanos() - poseStart)
                poseSource.releaseImage(); current.poseResult = result
                synchronized(this) { metrics = metrics.copy(poseCompleted = metrics.poseCompleted + 1, poseDetected = metrics.poseDetected + if (result.landmarks.isEmpty()) 0 else 1) }
                completed(current)
            } }, { code -> enqueue { endTrace("phase2_pose", current.token); if (batch === current) { poseSource.releaseImage(); failed(current, code) } } })
            if (runFace) {
                lastFaceMs = frame.timestampMs
                val faceStart = SystemClock.elapsedRealtimeNanos()
                beginTrace("phase2_face", current.token)
                faceSource?.submit(frame, { result -> enqueue {
                    endTrace("phase2_face", current.token)
                    if (batch !== current) return@enqueue
                    windows.getValue("face").record(SystemClock.elapsedRealtimeNanos() - faceStart)
                    faceSource.releaseImage()
                    if (gate.valid(current.token, current.epoch)) lastFace = result
                    synchronized(this) { metrics = metrics.copy(faceCompleted = metrics.faceCompleted + 1, faceDetected = metrics.faceDetected + if (result.face == null) 0 else 1) }
                    completed(current)
                } }, { code -> enqueue { endTrace("phase2_face", current.token); if (batch === current) { faceSource.releaseImage(); failed(current, code) } } })
            }
            // One watchdog per admitted batch; timeout closes tasks before permitting any buffer reuse.
            current.watchdog = worker.schedule({ if (batch === current && current.outstanding > 0 && !closed.get()) {
                Log.e("Phase2Perception", "INFERENCE_TIMEOUT")
                synchronized(this) { metrics = metrics.copy(errors = metrics.errors + 1) }
                closeSources()
                assembler.reset(); lastFace = null
                mutableState.value = mutableState.value.copy(pose = mutableState.value.pose.copy(status = SourceStatus.UNAVAILABLE, errorCode = "INFERENCE_TIMEOUT"),
                    face = mutableState.value.face.copy(status = SourceStatus.UNAVAILABLE, errorCode = "INFERENCE_TIMEOUT"), snapshot = null)
                release(current)
            } }, 5, TimeUnit.SECONDS)
        }
    }
    private fun enqueue(completion: () -> Unit) {
        if (!closed.get() && completions.trySend(completion).isFailure) Log.e("Phase2Perception", "COMPLETION_CHANNEL_FULL")
    }
    private fun failed(current: Batch, code: String) {
        Log.e("Phase2Perception", code)
        synchronized(this) { metrics = metrics.copy(errors = metrics.errors + 1) }
        current.failed = true
        completed(current)
    }
    private fun completed(current: Batch) {
        current.outstanding--
        if (current.outstanding > 0 || batch !== current) return
        val now = SystemClock.elapsedRealtimeNanos()
        windows.getValue("batch").record(now - current.startNs)
        if (gate.valid(current.token, current.epoch)) {
            val result = current.poseResult
            val subjects = if (result == null || current.failed) emptyList() else assembler.assemble(result, current.frame.geometry, lastFace)
            val sourceAge = lastFace?.let { (current.frame.timestampMs - it.timestampMs).coerceAtLeast(0) } ?: Long.MAX_VALUE
            val quality = AnalysisQuality(mapOf(
                "pose" to SourceQuality(0, windows.getValue("pose").summary().p50Ms, result == null || current.failed),
                "face" to SourceQuality(sourceAge, windows.getValue("face").summary().p50Ms, sourceAge > PerceptionFreshness.MAX_AGE_MS)),
                synchronized(this) { if (metrics.offered == 0L) 0.0 else metrics.busySkipped.toDouble() / metrics.offered }, level)
            val snapshot = PerceptionSnapshot(current.frame.timestampMs, current.frame.geometry, current.frame.device, subjects, quality,
                lastFace?.takeIf { PerceptionFreshness.fresh(it.timestampMs, current.frame.timestampMs) }?.face)
            synchronized(this) { metrics = metrics.copy(elapsedSeconds = (now - startedNs) / 1e9, latencies = windows.mapValues { it.value.summary() }, degradationLevel = level) }
            mutableState.value = mutableState.value.copy(snapshot = snapshot, metrics = synchronized(this) { metrics })
            Log.i("Phase2Baseline", "${mutableState.value.metrics}; pose=${mutableState.value.pose.configuredDelegate}; face=${mutableState.value.face.configuredDelegate}")
            val oldLite = schedule.useLite
            synchronized(this) {
                if (scheduleWindowMs < 0 || current.frame.timestampMs - scheduleWindowMs >= 1000) {
                    val offered = metrics.offered - scheduleOffered
                    recentBusyRatio = if (offered > 0) (metrics.busySkipped - scheduleBusy).toDouble() / offered else 0.0
                    scheduleOffered = metrics.offered; scheduleBusy = metrics.busySkipped; scheduleWindowMs = current.frame.timestampMs
                }
                schedule.observe(current.frame.timestampMs, current.frame.device.thermalStatus,
                    recentBusyRatio, windows.getValue("pose").summary().p95Ms, result?.landmarks)
            }
            if (oldLite != schedule.useLite) { closeSources(); assembler.reset(); lastFace = null; initialize() }
        }
        release(current)
    }
    @Synchronized private fun release(current: Batch) { current.watchdog?.cancel(false); if (batch === current) batch = null; gate.release(current.token) }
    override fun cancel(frame: RgbFrame) { batch?.takeIf { it.frame === frame }?.let(::release) }
    override fun setActive(active: Boolean) {
        if (closed.get()) return
        if (this.active.getAndSet(active) == active) return
        gate.setActive(active)
        val epoch = gate.epoch()
        mutableState.value = mutableState.value.copy(snapshot = null,
            pose = mutableState.value.pose.copy(status = if (active) SourceStatus.INITIALIZING else SourceStatus.STOPPED),
            face = mutableState.value.face.copy(status = if (active) SourceStatus.INITIALIZING else SourceStatus.STOPPED))
        worker.execute {
            if (gate.epoch() != epoch || closed.get()) return@execute
            closeSources(); batch?.let(::release); assembler.reset(); lastFace = null; geometry = null
            synchronized(this) {
                lastPoseMs = -1; lastFaceMs = -1; lastTimestampMs = -1; schedule.resetMotion()
                scheduleOffered = metrics.offered; scheduleBusy = metrics.busySkipped; scheduleWindowMs = -1; recentBusyRatio = 0.0
            }
            if (this.active.get()) initialize()
        }
    }
    private fun closeSources() { pose?.close(); face?.close(); pose = null; face = null }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        gate.setActive(false)
        mutableState.value = mutableState.value.copy(snapshot = null, pose = mutableState.value.pose.copy(status = SourceStatus.STOPPED), face = mutableState.value.face.copy(status = SourceStatus.STOPPED))
        worker.execute {
            closeSources(); batch?.let(::release)
            completions.close(); scope.cancel(); worker.shutdown(); dispatcher.close()
        }
    }
}
