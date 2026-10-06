package com.aiphotographer.perception.mediapipe

import android.content.Context
import android.graphics.Bitmap
import com.aiphotographer.model.*
import com.aiphotographer.perception.*
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import java.util.concurrent.atomic.AtomicBoolean

private fun NormalizedLandmark.domain(id: String) = Landmark(id, x().toDouble(), y().toDouble(), z().toDouble(),
    visibility().orElse(null)?.toDouble(), presence().orElse(null)?.toDouble())

/** Each task retains its own bitmap while native LIVE_STREAM inference reads it. */
internal class TaskImage : AutoCloseable {
    private var bitmap: Bitmap? = null
    private var image: MPImage? = null
    fun prepare(frame: RgbFrame): MPImage {
        release()
        val w = frame.geometry.width; val h = frame.geometry.height
        if (bitmap?.width != w || bitmap?.height != h) {
            bitmap?.recycle(); bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
        bitmap!!.setPixels(frame.pixels, 0, w, 0, 0, w, h)
        return BitmapImageBuilder(bitmap!!).build().also { image = it }
    }
    fun release() { image?.close(); image = null }
    override fun close() { release(); bitmap?.recycle(); bitmap = null }
}

internal class MediaPipePoseSource(context: Context, delegate: DelegateKind, model: String) : PoseSource {
    override val capability = SourceCapability(worldLandmarks = true)
    override val diagnostic = SourceDiagnostic(SourceStatus.READY, delegate, model)
    private val input = TaskImage()
    private var onResult: ((PoseFrameResult) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private val pending = AtomicBoolean(false)
    private val task = PoseLandmarker.createFromOptions(context, PoseLandmarker.PoseLandmarkerOptions.builder()
        .setBaseOptions(BaseOptions.builder().setModelAssetPath("models/$model.task")
            .setDelegate(if (delegate == DelegateKind.GPU) Delegate.GPU else Delegate.CPU).build())
        .setNumPoses(1).setOutputSegmentationMasks(false).setRunningMode(RunningMode.LIVE_STREAM)
        .setResultListener { result, _ ->
            if (pending.compareAndSet(true, false)) {
                try {
                    val points = result.landmarks().firstOrNull()?.mapIndexed { i, l -> l.domain(PoseLandmarks.ids[i]) }.orEmpty()
                    val world = result.worldLandmarks().firstOrNull()?.mapIndexed { i, l ->
                        Landmark(PoseLandmarks.ids[i], l.x().toDouble(), l.y().toDouble(), l.z().toDouble(),
                            l.visibility().orElse(null)?.toDouble(), l.presence().orElse(null)?.toDouble())
                    }
                    onResult?.invoke(PoseFrameResult(result.timestampMs(), points, world))
                } catch (_: Exception) { onError?.invoke("POSE_RESULT_INVALID") }
            }
        }.setErrorListener { if (pending.compareAndSet(true, false)) onError?.invoke("POSE_RUNTIME_ERROR") }.build())
    override fun submit(frame: RgbFrame, result: (PoseFrameResult) -> Unit, error: (String) -> Unit) {
        check(!pending.get())
        onResult = result; onError = error; pending.set(true)
        try { task.detectAsync(input.prepare(frame), frame.timestampMs) }
        catch (_: Exception) { if (pending.compareAndSet(true, false)) error("POSE_SUBMIT_ERROR") }
    }
    fun releaseImage() { input.release() }
    override fun close() { pending.set(false); task.close(); input.close() }
}

internal class MediaPipeFaceSource(context: Context, delegate: DelegateKind) : FaceSource {
    override val capability = SourceCapability()
    override val diagnostic = SourceDiagnostic(SourceStatus.READY, delegate, "face_landmarker")
    private val input = TaskImage()
    private var onResult: ((FaceFrameResult) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private val pending = AtomicBoolean(false)
    private val task = FaceLandmarker.createFromOptions(context, FaceLandmarker.FaceLandmarkerOptions.builder()
        .setBaseOptions(BaseOptions.builder().setModelAssetPath("models/face_landmarker.task")
            .setDelegate(if (delegate == DelegateKind.GPU) Delegate.GPU else Delegate.CPU).build())
        .setNumFaces(1).setOutputFaceBlendshapes(false).setOutputFacialTransformationMatrixes(true)
        .setRunningMode(RunningMode.LIVE_STREAM).setResultListener { result, _ ->
            if (pending.compareAndSet(true, false)) {
                try {
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
        check(!pending.get()); onResult = result; onError = error; pending.set(true)
        try { task.detectAsync(input.prepare(frame), frame.timestampMs) }
        catch (_: Exception) { if (pending.compareAndSet(true, false)) error("FACE_SUBMIT_ERROR") }
    }
    fun releaseImage() { input.release() }
    override fun close() { pending.set(false); task.close(); input.close() }
}
