package com.aiphotographer.perception.mediapipe

import android.content.Context
import android.os.SystemClock
import android.os.Trace
import android.os.Build
import android.util.Log
import com.aiphotographer.model.*
import com.aiphotographer.perception.*
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

internal class MediaPipePipeline(
    context: Context, private val capability: CapabilityReport,
    private val makePose: (DelegateKind, String) -> OwnedPoseSource = { delegate, model -> MediaPipePoseSource(context, delegate, model) },
    private val makeFace: (DelegateKind) -> OwnedFaceSource = { delegate -> MediaPipeFaceSource(context, delegate) },
    private val retryDelay: (Long) -> Long = { it },
) : PerceptionPipeline {
    private companion object { val sessions = java.util.concurrent.atomic.AtomicLong() }
    private val worker = java.util.concurrent.ScheduledThreadPoolExecutor(1) { Thread(it, "Phase2Perception") }
        .apply { removeOnCancelPolicy = true }
    private val dispatcher = worker.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val completions = Channel<() -> Unit>(2)
    private val gate = BatchGate()
    private val closed = AtomicBoolean(false)
    private val active = AtomicBoolean(true)
    private val lifecycleQueued = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(PerceptionState())
    override val state = mutableState.asStateFlow()
    private var pose: OwnedPoseSource? = null
    private var face: OwnedFaceSource? = null
    private val assembler = SubjectAssembler()
    private val recovery = RecoveryBudget()
    private var retry: ScheduledFuture<*>? = null
    private var cpuOnly = false
    private var lastFace: FaceFrameResult? = null
    private var pixels = IntArray(0)
    private data class Batch(val frame: RgbFrame, val token: Long, val epoch: Long, val startNs: Long,
        val submitted: AtomicBoolean = AtomicBoolean(false), var outstanding: Int = 0,
        var poseResult: PoseFrameResult? = null, var failed: Boolean = false,
        var poseTrace: Boolean = false, var faceTrace: Boolean = false,
        var watchdog: ScheduledFuture<*>? = null)
    @Volatile private var batch: Batch? = null
    private var lastPoseMs = -1L
    private var lastFaceMs = -1L
    private var lastTimestampMs = -1L
    private var startedNs = 0L
    private var summariesNs = 0L
    private var scheduleOffered = 0L
    private var scheduleBusy = 0L
    private var scheduleWindowMs = -1L
    private var recentBusyRatio = 0.0
    private var metrics = PerceptionMetrics()
    private val windows = mapOf("pose" to LatencyWindow(), "face" to LatencyWindow(), "batch" to LatencyWindow())
    private var summaries = emptyMap<String, LatencySummary>()
    private val schedule = PerceptionSchedule(capability.tier)
    private var geometry: FrameGeometry? = null
    private val level get() = schedule.level
    private fun trace(current: Batch, source: String, begin: Boolean) {
        if (Build.VERSION.SDK_INT < 29) return
        if (begin) {
            if (source == "pose") current.poseTrace = true else current.faceTrace = true
            Trace.beginAsyncSection("phase2_$source", current.token.toInt())
        } else if (if (source == "pose") current.poseTrace else current.faceTrace) {
            Trace.endAsyncSection("phase2_$source", current.token.toInt())
            if (source == "pose") current.poseTrace = false else current.faceTrace = false
        }
    }
    init {
        scope.launch { for (completion in completions) completion() }
        requestLifecycleRefresh()
    }
    private fun eligible(epoch: Long) = !closed.get() && active.get() && gate.epoch() == epoch
    private fun initialize(epoch: Long) {
        if (!eligible(epoch)) return
        val model = if (schedule.useLite) "pose_landmarker_lite" else "pose_landmarker_full"
        val delegates = if (!cpuOnly && capability.gpuEligible && !capability.emulator) listOf(DelegateKind.GPU, DelegateKind.CPU) else listOf(DelegateKind.CPU)
        if (pose == null) for (delegate in delegates) {
            try { pose = makePose(delegate, model); break }
            catch (error: Throwable) { logNonfatal("pose_init_failure delegate=$delegate", error) }
        }
        if (!eligible(epoch)) { closeSources(); return }
        if (face == null) for (delegate in delegates) {
            try { face = makeFace(delegate); break }
            catch (error: Throwable) { logNonfatal("face_init_failure delegate=$delegate", error) }
        }
        val published = synchronized(this) {
            if (!eligible(epoch)) false else {
                mutableState.value = mutableState.value.copy(
                pose = pose?.diagnostic ?: SourceDiagnostic(SourceStatus.UNAVAILABLE, model = model, errorCode = "POSE_INIT_FAILED"),
                face = face?.diagnostic ?: SourceDiagnostic(SourceStatus.UNAVAILABLE, model = "face_landmarker", errorCode = "FACE_INIT_FAILED"))
                true
            }
        }
        if (!published) { closeSources(); return }
        Log.i("Phase2Perception", "configured pose=${mutableState.value.pose} face=${mutableState.value.face}; runtime_delegate_evidence=NOT_MEASURED")
        if (pose == null || face == null) scheduleRecovery(epoch)
    }
    private fun logNonfatal(message: String, error: Throwable) {
        if (error is VirtualMachineError || error is ThreadDeath) throw error
        Log.w("Phase2Perception", message, error)
    }
    private fun scheduleRecovery(epoch: Long) {
        if (!eligible(epoch) || retry?.isDone == false) return
        val delay = recovery.nextDelayMs() ?: return
        retry = worker.schedule({ retry = null; if (eligible(epoch)) initialize(epoch) }, retryDelay(delay), TimeUnit.MILLISECONDS)
    }
    @Synchronized override fun acquire(timestampMs: Long, geometry: FrameGeometry, device: DeviceState): RgbFrame? {
        if (closed.get() || !active.get() || mutableState.value.pose.status != SourceStatus.READY) return null
        require(timestampMs >= 0 && geometry.width > 0 && geometry.height > 0)
        metrics = metrics.copy(offered = metrics.offered + 1)
        if (startedNs == 0L) startedNs = SystemClock.elapsedRealtimeNanos()
        if (timestampMs <= lastTimestampMs || (lastPoseMs >= 0 && timestampMs - lastPoseMs < schedule.posePeriodMs)) {
            metrics = metrics.copy(cadenceSkipped = metrics.cadenceSkipped + 1); return null
        }
        val size = Math.multiplyExact(geometry.width, geometry.height)
        val token = gate.acquire()
        if (token == null) { metrics = metrics.copy(busySkipped = metrics.busySkipped + 1); return null }
        lastPoseMs = timestampMs; lastTimestampMs = timestampMs
        if (pixels.size != size) pixels = IntArray(size)
        val frame = RgbFrame(timestampMs, geometry, device, pixels)
        batch = Batch(frame, token, gate.epoch(), SystemClock.elapsedRealtimeNanos())
        metrics = metrics.copy(accepted = metrics.accepted + 1)
        return frame
    }
    override fun submit(frame: RgbFrame) {
        synchronized(this) {
            val current = batch?.takeIf { it.frame === frame } ?: return
            if (closed.get() || !current.submitted.compareAndSet(false, true)) return
            worker.execute { submitOwned(current) }
        }
    }
    private fun submitOwned(current: Batch) {
        if (!gate.valid(current.token, current.epoch) || closed.get()) { release(current); return }
        val frame = current.frame
        if (geometry != null && geometry != frame.geometry) {
            retry?.cancel(false); retry = null
            closeSources(); clearResults(); schedule.resetObservations(); initialize(current.epoch)
        }
        geometry = frame.geometry
        val poseSource = pose ?: run { release(current); return }
        val faceSource = face
        val runFace = faceSource != null && mutableState.value.snapshot?.subjects?.isNotEmpty() == true &&
            (lastFaceMs < 0 || frame.timestampMs - lastFaceMs >= schedule.facePeriodMs)
        current.outstanding = if (runFace) 2 else 1
        val poseStart = SystemClock.elapsedRealtimeNanos()
        trace(current, "pose", true)
        poseSource.submit(frame, { result -> enqueue {
            trace(current, "pose", false)
            if (!valid(current)) return@enqueue
            poseSource.releaseImage()
            if (result.timestampMs != frame.timestampMs) { failed(current, "POSE_TIMESTAMP_INVALID"); return@enqueue }
            windows.getValue("pose").record(SystemClock.elapsedRealtimeNanos() - poseStart)
            current.poseResult = result
            synchronized(this) { metrics = metrics.copy(poseCompleted = metrics.poseCompleted + 1, poseDetected = metrics.poseDetected + if (result.landmarks.isEmpty()) 0 else 1) }
            completed(current)
        } }, { code -> enqueue { trace(current, "pose", false); if (valid(current)) { poseSource.releaseImage(); failed(current, code) } } })
        if (runFace) {
            lastFaceMs = frame.timestampMs
            val faceStart = SystemClock.elapsedRealtimeNanos()
            trace(current, "face", true)
            faceSource!!.submit(frame, { result -> enqueue {
                trace(current, "face", false)
                if (!valid(current)) return@enqueue
                faceSource.releaseImage()
                if (result.timestampMs != frame.timestampMs) { failed(current, "FACE_TIMESTAMP_INVALID"); return@enqueue }
                windows.getValue("face").record(SystemClock.elapsedRealtimeNanos() - faceStart)
                lastFace = result
                synchronized(this) { metrics = metrics.copy(faceCompleted = metrics.faceCompleted + 1, faceDetected = metrics.faceDetected + if (result.face == null) 0 else 1) }
                completed(current)
            } }, { code -> enqueue { trace(current, "face", false); if (valid(current)) { faceSource.releaseImage(); failed(current, code) } } })
        }
        current.watchdog = worker.schedule({ if (valid(current) && current.outstanding > 0) {
            if (failedState(current, "INFERENCE_TIMEOUT")) recover(current)
        } }, 5, TimeUnit.SECONDS)
    }
    private fun valid(current: Batch) = batch === current && gate.valid(current.token, current.epoch) && !closed.get()
    private fun enqueue(completion: () -> Unit) {
        if (!closed.get() && completions.trySend(completion).isFailure) Log.e("Phase2Perception", "COMPLETION_CHANNEL_FULL")
    }
    private fun failedState(current: Batch, code: String): Boolean {
        Log.e("Phase2Perception", code)
        synchronized(this) {
            if (!valid(current)) return false
            metrics = metrics.copy(errors = metrics.errors + 1)
            mutableState.value = mutableState.value.copy(snapshot = null, metrics = metrics,
                pose = mutableState.value.pose.copy(status = SourceStatus.UNAVAILABLE, errorCode = code),
                face = mutableState.value.face.copy(status = SourceStatus.UNAVAILABLE, errorCode = code))
        }
        return true
    }
    private fun failed(current: Batch, code: String) {
        if (!failedState(current, code)) return
        current.failed = true; completed(current)
    }
    private fun clearResults() {
        assembler.reset(); lastFace = null; lastFaceMs = -1; windows.values.forEach { it.clear() }; summaries = emptyMap(); summariesNs = 0
        synchronized(this) { mutableState.value = mutableState.value.copy(snapshot = null) }
    }
    private fun recover(current: Batch) {
        retry?.cancel(false); retry = null
        cpuOnly = true
        closeSources(); clearResults(); release(current)
        scheduleRecovery(current.epoch)
    }
    private fun completed(current: Batch) {
        current.outstanding--
        if (current.outstanding > 0 || batch !== current) return
        if (current.failed) { recover(current); return }
        val now = SystemClock.elapsedRealtimeNanos()
        windows.getValue("batch").record(now - current.startNs)
        val refresh = summariesNs == 0L || now - summariesNs >= 1_000_000_000L
        if (refresh) { summaries = windows.mapValues { it.value.summary() }; summariesNs = now }
        var modelChanged = false
        synchronized(this) {
            if (!valid(current)) { release(current); return }
            val result = current.poseResult
            val subjects = if (result == null) emptyList() else assembler.assemble(result, current.frame.geometry, lastFace)
            val sourceAge = lastFace?.let { (current.frame.timestampMs - it.timestampMs).coerceAtLeast(0) } ?: Long.MAX_VALUE
            val faceFresh = lastFace?.let { PerceptionFreshness.fresh(it.timestampMs, current.frame.timestampMs) } == true
            val quality = AnalysisQuality(mapOf(
                "pose" to SourceQuality(0, summaries["pose"]?.p50Ms, result == null),
                "face" to SourceQuality(sourceAge, summaries["face"]?.p50Ms, !faceFresh)),
                if (metrics.offered == 0L) 0.0 else metrics.busySkipped.toDouble() / metrics.offered, level)
            val snapshot = PerceptionSnapshot(current.frame.timestampMs, current.frame.geometry, current.frame.device, subjects, quality,
                lastFace?.takeIf { faceFresh }?.face)
            metrics = metrics.copy(elapsedSeconds = (now - startedNs) / 1e9, latencies = summaries, degradationLevel = level)
            mutableState.value = mutableState.value.copy(snapshot = snapshot, metrics = metrics)
            if (refresh) Log.i("Phase2Baseline", "$metrics; pose=${mutableState.value.pose.configuredDelegate}; face=${mutableState.value.face.configuredDelegate}")
            val oldLite = schedule.useLite
            if (scheduleWindowMs < 0 || current.frame.timestampMs - scheduleWindowMs >= 1000) {
                val offered = metrics.offered - scheduleOffered
                recentBusyRatio = if (offered > 0) (metrics.busySkipped - scheduleBusy).toDouble() / offered else 0.0
                scheduleOffered = metrics.offered; scheduleBusy = metrics.busySkipped; scheduleWindowMs = current.frame.timestampMs
            }
            schedule.observe(current.frame.timestampMs, current.frame.device.thermalStatus,
                recentBusyRatio, summaries["pose"]?.p95Ms, result?.landmarks)
            modelChanged = oldLite != schedule.useLite
        }
        if (modelChanged && eligible(current.epoch)) { closeSources(); clearResults(); initialize(current.epoch) }
        release(current)
    }
    @Synchronized private fun release(current: Batch) {
        current.watchdog?.cancel(false)
        trace(current, "pose", false); trace(current, "face", false)
        if (batch === current) batch = null
        gate.release(current.token)
    }
    @Synchronized override fun cancel(frame: RgbFrame) {
        batch?.takeIf { it.frame === frame && !it.submitted.get() }?.let(::release)
    }
    @Synchronized override fun setActive(active: Boolean) {
        if (closed.get() || this.active.getAndSet(active) == active) return
        gate.setActive(active)
        mutableState.value = mutableState.value.copy(snapshot = null,
            pose = mutableState.value.pose.copy(status = if (active) SourceStatus.INITIALIZING else SourceStatus.STOPPED),
            face = mutableState.value.face.copy(status = if (active) SourceStatus.INITIALIZING else SourceStatus.STOPPED))
        requestLifecycleRefresh()
    }
    private fun requestLifecycleRefresh() {
        if (closed.get() || !lifecycleQueued.compareAndSet(false, true)) return
        worker.execute {
            var epoch = -1L
            try {
                do {
                    epoch = gate.epoch()
                    retry?.cancel(false); retry = null
                    closeSources(); batch?.let(::release); clearResults(); geometry = null
                    recovery.reset(); cpuOnly = false
                    synchronized(this) {
                        lastPoseMs = -1; lastFaceMs = -1; lastTimestampMs = -1; schedule.resetObservations()
                        startedNs = 0; metrics = PerceptionMetrics(sessionId = sessions.incrementAndGet())
                        mutableState.value = mutableState.value.copy(metrics = metrics)
                        scheduleOffered = 0; scheduleBusy = 0; scheduleWindowMs = -1; recentBusyRatio = 0.0
                    }
                    if (eligible(epoch)) initialize(epoch)
                } while (!closed.get() && epoch != gate.epoch())
            } finally {
                lifecycleQueued.set(false)
                if (!closed.get() && epoch != gate.epoch()) requestLifecycleRefresh()
            }
        }
    }
    private fun closeSources() {
        val sources = listOfNotNull(pose, face); pose = null; face = null
        sources.forEach { source -> try { source.close() } catch (error: Throwable) { logNonfatal("source_close_failure", error) } }
    }
    @Synchronized override fun close() {
        if (!closed.compareAndSet(false, true)) return
        gate.setActive(false)
        mutableState.value = mutableState.value.copy(snapshot = null, pose = mutableState.value.pose.copy(status = SourceStatus.STOPPED), face = mutableState.value.face.copy(status = SourceStatus.STOPPED))
        worker.execute {
            retry?.cancel(false); retry = null
            closeSources(); batch?.let(::release)
            completions.close(); scope.cancel(); worker.shutdown(); dispatcher.close()
        }
    }
}
