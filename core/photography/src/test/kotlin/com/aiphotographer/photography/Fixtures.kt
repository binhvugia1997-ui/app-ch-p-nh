package com.aiphotographer.photography

import com.aiphotographer.model.AnalysisQuality
import com.aiphotographer.model.AnalysisResolution
import com.aiphotographer.model.BoundingBox
import com.aiphotographer.model.CameraFacing
import com.aiphotographer.model.DeviceState
import com.aiphotographer.model.DeviceTier
import com.aiphotographer.model.FaceObservation
import com.aiphotographer.model.FrameAnalysis
import com.aiphotographer.model.FrameGeometry
import com.aiphotographer.model.HeadPose
import com.aiphotographer.model.Landmark
import com.aiphotographer.model.ShotType
import com.aiphotographer.model.SourceQuality
import com.aiphotographer.model.Subject
import com.aiphotographer.model.ThermalStatus
import kotlin.math.cos
import kotlin.math.sin

/**
 * Synthetic scenes. Everything is placed from explicit numbers so a failing test points at a rule, not at
 * an accidental fixture.
 *
 * The head landmarks are placed **on a circle** of radius [HEAD_RADIUS] so `HeadTopEstimator` has a known
 * answer: `headTop = centreY − 1.45 · r`.
 */
object Fixtures {
    const val HEAD_RADIUS = 0.05
    private const val EYE_ANGLE_DEG = 25.0
    private const val EAR_ANGLE_DEG = 65.0

    fun geometry(
        width: Int = 640,
        height: Int = 480,
        rotation: Int = 0,
        mirrored: Boolean = false,
    ) = FrameGeometry(
        width = width, height = height, rotationDegrees = rotation, isMirrored = mirrored,
        primaryCameraFacing = CameraFacing.BACK, analysisResolution = AnalysisResolution.R480P,
    )

    fun landmark(id: String, x: Double, y: Double, visibility: Double = 0.99, presence: Double = 0.99) =
        Landmark(id = id, x = x, y = y, visibility = visibility, presence = presence)

    fun landmarkOrNull(id: String, x: Double, y: Double, included: Boolean) =
        if (included) landmark(id, x, y) else null

    fun scene(
        bbox: BoundingBox = BoundingBox(0.35, 0.10, 0.65, 0.90),
        faceHeight: Double? = null,
        headTop: Double = 0.115,
        feetVisible: Boolean = true,
        visibleFraction: Double = 0.9,
        roll: Double? = 0.0,
        imu: Double? = 0.9,
        ageMs: Long = 0,
        trackId: Int = 1,
        timestampMs: Long = 0,
        geometry: FrameGeometry = geometry(),
        degradationLevel: Int = 0,
        includeSubject: Boolean = true,
        landmarkVisibility: Double = 0.99,
    ): FrameAnalysis {
        val subjects = if (includeSubject) {
            listOf(subject(bbox, faceHeight, headTop, feetVisible, visibleFraction, trackId, landmarkVisibility))
        } else {
            emptyList()
        }
        return FrameAnalysis(
            timestampMs = timestampMs,
            frame = geometry,
            device = DeviceState(DeviceTier.MEDIUM, rollDegrees = roll, imuStability = imu, thermalStatus = ThermalStatus.NONE),
            subjects = subjects,
            quality = AnalysisQuality(
                sources = mapOf("pose" to SourceQuality(ageMs = ageMs, latencyMsP50 = 12.0, stale = ageMs > 500)),
                droppedFrameRatio = 0.0,
                degradationLevel = degradationLevel,
            ),
        )
    }

    fun subject(
        bbox: BoundingBox = BoundingBox(0.35, 0.10, 0.65, 0.90),
        faceHeight: Double? = null,
        headTop: Double = 0.115,
        feetVisible: Boolean = true,
        visibleFraction: Double = 0.9,
        trackId: Int = 1,
        visibility: Double = 0.99,
    ): Subject {
        val centreX = (bbox.left + bbox.right) / 2
        val height = bbox.bottom - bbox.top
        val width = bbox.right - bbox.left
        val centreY = headTop + Thresholds.HEAD_TOP_K * HEAD_RADIUS
        fun onCircle(angleDeg: Double, side: Int): Pair<Double, Double> {
            val angle = Math.toRadians(angleDeg)
            return (centreX + side * HEAD_RADIUS * sin(angle)) to (centreY - HEAD_RADIUS * cos(angle))
        }
        val leftEye = onCircle(EYE_ANGLE_DEG, -1)
        val rightEye = onCircle(EYE_ANGLE_DEG, 1)
        val leftEar = onCircle(EAR_ANGLE_DEG, -1)
        val rightEar = onCircle(EAR_ANGLE_DEG, 1)
        fun at(fraction: Double, dx: Double) = centreX + dx to bbox.top + fraction * height

        val landmarks = mutableListOf(
            landmark("nose", centreX, centreY - 0.9 * HEAD_RADIUS, visibility, visibility),
            landmark("left_eye", leftEye.first, leftEye.second, visibility, visibility),
            landmark("right_eye", rightEye.first, rightEye.second, visibility, visibility),
            landmark("left_ear", leftEar.first, leftEar.second, visibility, visibility),
            landmark("right_ear", rightEar.first, rightEar.second, visibility, visibility),
        )
        val shoulders = at(0.16, 0.0)
        landmarks += listOf(
            landmark("left_shoulder", shoulders.first - 0.13 * width, shoulders.second, visibility, visibility),
            landmark("right_shoulder", shoulders.first + 0.13 * width, shoulders.second, visibility, visibility),
            landmark("left_elbow", shoulders.first - 0.16 * width, at(0.30, 0.0).second, visibility, visibility),
            landmark("right_elbow", shoulders.first + 0.16 * width, at(0.30, 0.0).second, visibility, visibility),
            landmark("left_wrist", shoulders.first - 0.15 * width, at(0.42, 0.0).second, visibility, visibility),
            landmark("right_wrist", shoulders.first + 0.15 * width, at(0.42, 0.0).second, visibility, visibility),
            landmark("left_hip", shoulders.first - 0.09 * width, at(0.48, 0.0).second, visibility, visibility),
            landmark("right_hip", shoulders.first + 0.09 * width, at(0.48, 0.0).second, visibility, visibility),
            landmark("left_knee", shoulders.first - 0.07 * width, at(0.72, 0.0).second, visibility, visibility),
            landmark("right_knee", shoulders.first + 0.07 * width, at(0.72, 0.0).second, visibility, visibility),
            landmark("left_ankle", shoulders.first - 0.05 * width, at(0.94, 0.0).second, visibility, visibility),
            landmark("right_ankle", shoulders.first + 0.05 * width, at(0.94, 0.0).second, visibility, visibility),
        )
        landmarkOrNull("left_foot_index", shoulders.first - 0.05 * width, at(0.985, 0.0).second, feetVisible)
            ?.let { landmarks.add(it) }
        landmarkOrNull("right_foot_index", shoulders.first + 0.05 * width, at(0.985, 0.0).second, feetVisible)
            ?.let { landmarks.add(it) }

        val face = faceHeight?.let { heightOfFace ->
            FaceObservation(
                landmarks = emptyList(),
                bbox = BoundingBox(centreX - 0.06, bbox.top, centreX + 0.06, bbox.top + heightOfFace),
                headPose = HeadPose(yawDegrees = 0.0, pitchDegrees = 0.0, rollDegrees = 0.0),
            )
        }
        return Subject(
            trackId = trackId,
            landmarks = landmarks,
            bbox = bbox,
            visibleFraction = visibleFraction,
            shotType = ShotType.UNKNOWN,
            worldLandmarks = null,
            face = face,
            shotTypeConfidence = 0.0,
        )
    }

    fun candidate(
        action: SemanticAction,
        ruleId: RuleId = RuleId.FRAME_SUBJECT_TOO_SMALL,
        severity: Severity = Severity.IMPORTANT,
        confidence: Float = 0.9f,
        score: Float = 90f,
        persistence: Int = 0,
    ) = GuidanceCandidate(
        GuidanceInstruction(
            id = action.instructionId,
            action = action,
            messageId = action.messageKey,
            actor = action.actor,
            direction = null,
            magnitude = 0.5f,
            priority = Thresholds.PRIORITY_COMPOSITION_FRAMING,
            severity = severity,
            confidence = confidence,
            source = InstructionSource.COMPOSITION,
            ruleIds = listOf(ruleId),
            persistenceFrames = persistence,
            firstSeenMs = 0,
        ),
        score,
    )
}
