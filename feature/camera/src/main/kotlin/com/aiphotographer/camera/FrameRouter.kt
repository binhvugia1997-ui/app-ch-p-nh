package com.aiphotographer.camera

import android.os.SystemClock
import android.os.Trace
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.aiphotographer.geometry.Coordinates
import com.aiphotographer.model.*
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.aiphotographer.perception.*

data class PipelineMetrics(
    val delivered: Long = 0, val cadenceSkipped: Long = 0, val processed: Long = 0,
    val errors: Long = 0, val elapsedSeconds: Double = 0.0,
    val previewCaptureFps: Double = 0.0, val analysisFps: Double = 0.0,
    val lumaFps: Double = 0.0, val routerDropRatio: Double = 0.0,
    val latencies: Map<String, LatencySummary> = emptyMap(), val lastResolution: String = "",
)

class FrameRouter(
    private val facing: CameraFacing,
    private val resolution: AnalysisResolution,
    private val device: () -> DeviceState,
    benchmarkRgb: Boolean,
    private val perception: PerceptionPipeline? = null,
) : ImageAnalysis.Analyzer {
    private val scheduler = StageScheduler(buildMap {
        put(Stage.LUMA, 500_000_000L)
        if (benchmarkRgb) put(Stage.RGB, 1_000_000_000L)
    })
    private val windows = mapOf("luma" to LatencyWindow(), "yuv_rgb_rotation" to LatencyWindow(), "router" to LatencyWindow())
    private val mutableSnapshot = MutableStateFlow<ImageSnapshot?>(null)
    val snapshot = mutableSnapshot.asStateFlow()
    private val mutableMetrics = MutableStateFlow(PipelineMetrics())
    val metrics = mutableMetrics.asStateFlow()
    private val previewFrames = AtomicLong()
    private var startNs = 0L
    private var publishedNs = 0L
    private var delivered = 0L
    private var skipped = 0L
    private var processed = 0L
    private var errors = 0L
    private var rgb = IntArray(0)
    private var lastLuma: LumaGrid? = null
    private var lastPerception: PerceptionSnapshot? = null
    private var currentGeometry: FrameGeometry? = null
    @Synchronized fun perceptionResult(result: PerceptionSnapshot?) {
        if (result != null && currentGeometry != null && !PerceptionFreshness.sameGeometry(result.frame, currentGeometry!!)) return
        lastPerception = result
        if (result == null) {
            mutableSnapshot.value = mutableSnapshot.value?.let { it.copy(analysis = it.analysis.copy(subjects = emptyList())) }
            return
        }
        val sources = result.quality.sources + (mutableSnapshot.value?.analysis?.quality?.sources?.filterKeys { it == "luma" }.orEmpty())
        mutableSnapshot.value = ImageSnapshot(FrameAnalysis(result.timestampMs, result.frame, result.device, result.subjects,
            result.quality.copy(sources = sources)), lastLuma)
    }
    fun onPreviewCapture() { previewFrames.incrementAndGet() }
    @Synchronized fun resetSessionWindow() {
        startNs = 0L
        publishedNs = 0L
        delivered = 0
        skipped = 0
        processed = 0
        errors = 0
        scheduler.reset()
        lastLuma = null
        lastPerception = null
        currentGeometry = null
        mutableSnapshot.value = null
        mutableMetrics.value = PipelineMetrics()
        previewFrames.set(0)
        windows.values.forEach { it.clear() }
    }

    private inline fun <T> measured(name: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtimeNanos()
        try {
            Trace.beginSection("phase1_$name")
            return block()
        } finally {
            Trace.endSection()
            windows.getValue(name).record(SystemClock.elapsedRealtimeNanos() - start)
        }
    }
    @Synchronized override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtimeNanos()
        if (startNs == 0L) startNs = now
        delivered++
        try {
            measured("router") {
                val rect = image.cropRect
                val crop = Crop(rect.left, rect.top, rect.width(), rect.height())
                val rotation = image.imageInfo.rotationDegrees
                val (w, h) = Coordinates.uprightSize(crop.width, crop.height, rotation)
                val planes = image.planes.map { Plane(it.buffer, it.rowStride, it.pixelStride) }
                val timestamp = image.imageInfo.timestamp / 1_000_000
                val geometry = FrameGeometry(w, h, rotation, facing == CameraFacing.FRONT, facing, resolution)
                currentGeometry = geometry
                val lease = perception?.acquire(timestamp, geometry, device())
                if (lease != null) {
                    try {
                        measured("yuv_rgb_rotation") { ImagePlanes.rgb(planes[0], planes[1], planes[2], crop, rotation, lease.pixels) }
                        perception?.submit(lease)
                    } catch (error: Exception) { perception?.cancel(lease); throw error }
                }
                if (scheduler.tryStart(Stage.LUMA, now)) {
                    try {
                        lastLuma = LumaGrid(64, 64, measured("luma") { ImagePlanes.luma(planes[0], crop, rotation) }, image.imageInfo.timestamp / 1_000_000)
                        processed++
                    } finally { scheduler.finish(Stage.LUMA) }
                } else skipped++
                if (lease == null && scheduler.tryStart(Stage.RGB, now)) {
                    try {
                        if (rgb.size != w * h) rgb = IntArray(w * h)
                        measured("yuv_rgb_rotation") { ImagePlanes.rgb(planes[0], planes[1], planes[2], crop, rotation, rgb) }
                    } finally { scheduler.finish(Stage.RGB) }
                }
                // UI snapshots are emitted at the luma cadence, not on every camera frame.
                if (now - publishedNs >= 500_000_000L) {
                    val age = lastLuma?.let { (timestamp - it.timestampMs).coerceAtLeast(0) } ?: 0L
                    val result = lastPerception?.takeIf { PerceptionFreshness.sameGeometry(it.frame, geometry) && PerceptionFreshness.fresh(it.timestampMs, timestamp) }
                    val sourceQuality = result?.quality?.sources?.mapValues { (_, source) -> source.copy(ageMs = source.ageMs + timestamp - result.timestampMs,
                        stale = source.stale || source.ageMs + timestamp - result.timestampMs > PerceptionFreshness.MAX_AGE_MS) }.orEmpty()
                    mutableSnapshot.value = ImageSnapshot(FrameAnalysis(timestamp,
                        geometry, device(), subjects = result?.subjects.orEmpty(), quality = AnalysisQuality(sourceQuality +
                            mapOf("luma" to SourceQuality(age, windows.getValue("luma").summary().p50Ms, age > 500)),
                            result?.quality?.droppedFrameRatio ?: (skipped.toDouble() / delivered), result?.quality?.degradationLevel ?: 0)), lastLuma)
                    val seconds = (now - startNs) / 1e9
                    val metrics = PipelineMetrics(delivered, skipped, processed, errors, seconds,
                        if (seconds > 0) previewFrames.get() / seconds else 0.0,
                        if (seconds > 0) delivered / seconds else 0.0,
                        if (seconds > 0) processed / seconds else 0.0,
                        skipped.toDouble() / delivered, windows.mapValues { it.value.summary() }, "${image.width}x${image.height}; crop ${w}x$h")
                    mutableMetrics.value = metrics
                    Log.i("Phase1Baseline", metrics.toString())
                    publishedNs = now
                }
            }
        } catch (error: Exception) {
            errors++
            Log.e("Phase1Baseline", "Analyzer failed", error)
        } finally { image.close() }
    }
}
