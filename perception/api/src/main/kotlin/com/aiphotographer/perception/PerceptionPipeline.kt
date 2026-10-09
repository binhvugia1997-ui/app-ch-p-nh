package com.aiphotographer.perception

import com.aiphotographer.model.*
import kotlinx.coroutines.flow.StateFlow

data class SourceCapability(val maxSubjects: Int = 1, val worldLandmarks: Boolean = false)
enum class SourceStatus { INITIALIZING, READY, UNAVAILABLE, STOPPED }
enum class DelegateKind { CPU, GPU, UNKNOWN }
data class SourceDiagnostic(val status: SourceStatus = SourceStatus.INITIALIZING, val configuredDelegate: DelegateKind = DelegateKind.UNKNOWN,
    val model: String = "", val errorCode: String? = null)
data class PerceptionMetrics(
    val sessionId: Long = 0,
    val offered: Long = 0, val accepted: Long = 0, val busySkipped: Long = 0, val cadenceSkipped: Long = 0,
    val poseCompleted: Long = 0, val poseDetected: Long = 0, val faceCompleted: Long = 0, val faceDetected: Long = 0,
    val faceValid478: Long = 0,
    val errors: Long = 0, val elapsedSeconds: Double = 0.0, val latencies: Map<String, LatencySummary> = emptyMap(),
    val degradationLevel: Int = 0,
)
data class PerceptionSnapshot(val timestampMs: Long, val frame: FrameGeometry, val device: DeviceState,
    val subjects: List<Subject>, val quality: AnalysisQuality, val faceDiagnostic: FaceObservation? = null)
data class PerceptionState(val pose: SourceDiagnostic = SourceDiagnostic(), val face: SourceDiagnostic = SourceDiagnostic(),
    val snapshot: PerceptionSnapshot? = null, val metrics: PerceptionMetrics = PerceptionMetrics())

/** Upright crop-local ANALYSIS pixels, never mirrored. Lease owner must not mutate after submit. */
data class RgbFrame(val timestampMs: Long, val geometry: FrameGeometry, val device: DeviceState, val pixels: IntArray)
data class PoseFrameResult(val timestampMs: Long, val landmarks: List<Landmark>, val worldLandmarks: List<Landmark>?)
data class FaceFrameResult(val timestampMs: Long, val face: FaceObservation?)

/** Scores retain model semantics; use core:model LandmarkUsability (pose-system §4.1.1), not probabilities. */
interface PoseSource : AutoCloseable {
    val capability: SourceCapability
    val diagnostic: SourceDiagnostic
    fun submit(frame: RgbFrame, result: (PoseFrameResult) -> Unit, error: (String) -> Unit)
}
/** Face confidence channels may be absent. Missing scores are unknown, never invented as 1.0. */
interface FaceSource : AutoCloseable {
    val capability: SourceCapability
    val diagnostic: SourceDiagnostic
    fun submit(frame: RgbFrame, result: (FaceFrameResult) -> Unit, error: (String) -> Unit)
}

interface PerceptionPipeline : AutoCloseable {
    val state: StateFlow<PerceptionState>
    fun acquire(timestampMs: Long, geometry: FrameGeometry, device: DeviceState): RgbFrame?
    fun submit(frame: RgbFrame)
    fun cancel(frame: RgbFrame)
    fun setActive(active: Boolean)
}

/** Pure one-batch ownership guard, including lifecycle epochs. Never releases a newer lease. */
class BatchGate {
    private var epoch = 0L
    private var sequence = 0L
    private var held: Long? = null
    private var active = true
    @Synchronized fun acquire(): Long? = if (!active || held != null) null else (++sequence).also { held = it }
    @Synchronized fun valid(token: Long, submittedEpoch: Long) = active && held == token && epoch == submittedEpoch
    @Synchronized fun epoch() = epoch
    @Synchronized fun release(token: Long) { if (held == token) held = null }
    @Synchronized fun setActive(value: Boolean) { active = value; epoch++ }
}
