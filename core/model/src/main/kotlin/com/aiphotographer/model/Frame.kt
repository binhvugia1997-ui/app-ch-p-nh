package com.aiphotographer.model

enum class DeviceTier { LOW, MEDIUM, HIGH }
enum class CameraFacing { FRONT, BACK }
enum class AnalysisResolution { R480P, R720P, R1080P }
enum class ThermalStatus { NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN, UNKNOWN }
data class FrameGeometry(
    val width: Int, val height: Int, val rotationDegrees: Int, val isMirrored: Boolean,
    val primaryCameraFacing: CameraFacing, val analysisResolution: AnalysisResolution,
    val aspectRatio: Double = width.toDouble() / height,
)
data class DeviceState(
    val tier: DeviceTier, val rollDegrees: Double? = null, val imuStability: Double? = null,
    val thermalStatus: ThermalStatus = ThermalStatus.UNKNOWN,
)
data class SourceQuality(val ageMs: Long, val latencyMsP50: Double? = null, val stale: Boolean = false)
data class AnalysisQuality(val sources: Map<String, SourceQuality>, val droppedFrameRatio: Double, val degradationLevel: Int = 0)
data class Landmark(val id: String, val x: Double, val y: Double, val z: Double? = null, val visibility: Double? = null, val presence: Double? = null, val smoothed: Boolean = false)
data class BoundingBox(val left: Double, val top: Double, val right: Double, val bottom: Double)
enum class ShotType { HEADSHOT, CLOSE_PORTRAIT, HALF_BODY, THREE_QUARTER, FULL_BODY, UNKNOWN }
data class HeadPose(val yawDegrees: Double, val pitchDegrees: Double, val rollDegrees: Double)
data class FaceObservation(val landmarks: List<Landmark>, val bbox: BoundingBox, val headPose: HeadPose? = null, val blinkScore: Double? = null)
data class Subject(
    val trackId: Int, val landmarks: List<Landmark>, val bbox: BoundingBox, val visibleFraction: Double, val shotType: ShotType,
    val worldLandmarks: List<Landmark>? = null, val face: FaceObservation? = null, val shotTypeConfidence: Double = 0.0,
)
data class FrameAnalysis(
    val timestampMs: Long, val frame: FrameGeometry, val device: DeviceState,
    val subjects: List<Subject> = emptyList(), val quality: AnalysisQuality,
)
/** Separate envelope: the frozen FrameAnalysis JSON schema has no luma-grid property. */
data class ImageSnapshot(val analysis: FrameAnalysis, val luma: LumaGrid?)
data class LumaGrid(val width: Int, val height: Int, val values: List<Int>, val timestampMs: Long)
data class CapabilityReport(
    val tier: DeviceTier, val heuristicOnly: Boolean = true, val totalRamBytes: Long,
    val glEsVersion: String, val gpuEligible: Boolean, val emulator: Boolean,
    val modelBenchmarkMs: Double? = null,
    val supportedYuvSizes: List<String> = emptyList(), val threeUseCaseSupported: Boolean? = null,
    val cameraId: String? = null,
)
