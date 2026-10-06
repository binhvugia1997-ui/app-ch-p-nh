package com.aiphotographer.perception.mediapipe

import android.content.Context
import android.util.Log
import com.aiphotographer.model.*
import com.aiphotographer.perception.*
import com.google.mediapipe.framework.image.ByteBufferImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

internal fun mappedModel(context: Context, path: String): ByteBuffer = context.assets.openFd(path).use { asset ->
    asset.createInputStream().use { stream ->
        stream.channel.map(FileChannel.MapMode.READ_ONLY, asset.startOffset, asset.declaredLength)
    }
}

private fun NormalizedLandmark.domain(id: String): Landmark {
    val v = visibility().orElse(null)?.toDouble()
    val p = presence().orElse(null)?.toDouble()
    require(x().isFinite() && y().isFinite() && z().isFinite())
    require(v == null || (v.isFinite() && v in 0.0..1.0))
    require(p == null || (p.isFinite() && p in 0.0..1.0))
    return Landmark(id, x().toDouble(), y().toDouble(), z().toDouble(), v, p)
}

/** Each task owns RGB storage until its native LIVE_STREAM callback completes. */
internal class TaskImage : AutoCloseable {
    private var buffer: ByteBuffer? = null
    private var image: MPImage? = null
    fun prepare(frame: RgbFrame): MPImage {
        release()
        val w = frame.geometry.width; val h = frame.geometry.height
        val size = Math.multiplyExact(Math.multiplyExact(w, h), 3)
        require(w > 0 && h > 0 && frame.pixels.size == size / 3)
        if (buffer?.capacity() != size) buffer = ByteBuffer.allocateDirect(size)
        val owned = requireNotNull(buffer).apply { clear() }
        frame.pixels.forEach { pixel -> owned.put((pixel shr 16).toByte()); owned.put((pixel shr 8).toByte()); owned.put(pixel.toByte()) }
        owned.rewind()
        return ByteBufferImageBuilder(owned, w, h, MPImage.IMAGE_FORMAT_RGB).build().also { image = it }
    }
    fun release() { image?.close(); image = null }
    override fun close() { release(); buffer = null }
}

internal interface OwnedPoseSource : PoseSource { fun releaseImage() }
internal interface OwnedFaceSource : FaceSource { fun releaseImage() }

internal class MediaPipePoseSource(context: Context, delegate: DelegateKind, model: String) : OwnedPoseSource {
    override val capability = SourceCapability(worldLandmarks = true)
    override val diagnostic = SourceDiagnostic(SourceStatus.READY, delegate, model)
    private val input = TaskImage()
    private var onResult: ((PoseFrameResult) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private val pending = AtomicBoolean(false)
    private var submittedTimestamp = -1L
    private val modelBuffer = mappedModel(context, "models/$model.task")
    private val task = PoseLandmarker.createFromOptions(context, PoseLandmarker.PoseLandmarkerOptions.builder()
        .setBaseOptions(BaseOptions.builder().setModelAssetBuffer(modelBuffer)
            .setDelegate(if (delegate == DelegateKind.GPU) Delegate.GPU else Delegate.CPU).build())
        .setNumPoses(1).setOutputSegmentationMasks(false).setRunningMode(RunningMode.LIVE_STREAM)
        .setResultListener { result, _ ->
            if (pending.compareAndSet(true, false)) {
                try {
                    require(result.timestampMs() == submittedTimestamp && result.landmarks().size <= 1)
                    require(result.landmarks().all { it.size == PoseLandmarks.ids.size })
                    require(result.worldLandmarks().size == result.landmarks().size && result.worldLandmarks().all { it.size == PoseLandmarks.ids.size })
                    val points = result.landmarks().firstOrNull()?.mapIndexed { i, l -> l.domain(PoseLandmarks.ids[i]) }.orEmpty()
                    val world = result.worldLandmarks().firstOrNull()?.mapIndexed { i, l ->
                        val v = l.visibility().orElse(null)?.toDouble()
                        val p = l.presence().orElse(null)?.toDouble()
                        require(l.x().isFinite() && l.y().isFinite() && l.z().isFinite())
                        require(v == null || (v.isFinite() && v in 0.0..1.0))
                        require(p == null || (p.isFinite() && p in 0.0..1.0))
                        Landmark(PoseLandmarks.ids[i], l.x().toDouble(), l.y().toDouble(), l.z().toDouble(), v, p)
                    }
                    onResult?.invoke(PoseFrameResult(result.timestampMs(), points, world))
                } catch (_: Exception) { onError?.invoke("POSE_RESULT_INVALID") }
            }
        }.setErrorListener { if (pending.compareAndSet(true, false)) onError?.invoke("POSE_RUNTIME_ERROR") }.build())
    override fun submit(frame: RgbFrame, result: (PoseFrameResult) -> Unit, error: (String) -> Unit) {
        check(!pending.get())
        onResult = result; onError = error; submittedTimestamp = frame.timestampMs; pending.set(true)
        try { task.detectAsync(input.prepare(frame), frame.timestampMs) }
        catch (failure: Exception) { Log.e("Phase2Perception", "POSE_SUBMIT_ERROR", failure); if (pending.compareAndSet(true, false)) error("POSE_SUBMIT_ERROR") }
    }
    override fun releaseImage() { input.release() }
    override fun close() { pending.set(false); try { task.close() } finally { input.close() } }
}

internal class MediaPipeFaceSource(context: Context, delegate: DelegateKind) : OwnedFaceSource {
    override val capability = SourceCapability()
    override val diagnostic = SourceDiagnostic(SourceStatus.READY, delegate, "face_landmarker")
    private val input = TaskImage()
    private var onResult: ((FaceFrameResult) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private val pending = AtomicBoolean(false)
    private var submittedTimestamp = -1L
    private val modelBuffer = mappedModel(context, "models/face_landmarker.task")
    private val task = FaceLandmarker.createFromOptions(context, FaceLandmarker.FaceLandmarkerOptions.builder()
        .setBaseOptions(BaseOptions.builder().setModelAssetBuffer(modelBuffer)
            .setDelegate(if (delegate == DelegateKind.GPU) Delegate.GPU else Delegate.CPU).build())
        .setNumFaces(1).setOutputFaceBlendshapes(false).setOutputFacialTransformationMatrixes(true)
        .setRunningMode(RunningMode.LIVE_STREAM).setResultListener { result, _ ->
            if (pending.compareAndSet(true, false)) {
                try {
                    require(result.timestampMs() == submittedTimestamp && result.faceLandmarks().size <= 1)
                    require(result.faceLandmarks().all { it.size == 478 })
                    val points = result.faceLandmarks().firstOrNull()?.mapIndexed { i, l -> l.domain("face_$i") }
                    val valid = points?.filter { it.x.isFinite() && it.y.isFinite() }
                    val face = if (points == null || valid.isNullOrEmpty()) null else FaceObservation(points,
                        BoundingBox(valid.minOf { it.x }, valid.minOf { it.y }, valid.maxOf { it.x }, valid.maxOf { it.y }),
                        result.facialTransformationMatrixes().orElse(null)?.firstOrNull()?.let(HeadPoseMatrix::decode))
                    onResult?.invoke(FaceFrameResult(result.timestampMs(), face))
                } catch (_: Exception) { onError?.invoke("FACE_RESULT_INVALID") }
            }
        }.setErrorListener { if (pending.compareAndSet(true, false)) onError?.invoke("FACE_RUNTIME_ERROR") }.build())
    override fun submit(frame: RgbFrame, result: (FaceFrameResult) -> Unit, error: (String) -> Unit) {
        check(!pending.get()); onResult = result; onError = error; submittedTimestamp = frame.timestampMs; pending.set(true)
        try { task.detectAsync(input.prepare(frame), frame.timestampMs) }
        catch (failure: Exception) { Log.e("Phase2Perception", "FACE_SUBMIT_ERROR", failure); if (pending.compareAndSet(true, false)) error("FACE_SUBMIT_ERROR") }
    }
    override fun releaseImage() { input.release() }
    override fun close() { pending.set(false); try { task.close() } finally { input.close() } }
}
