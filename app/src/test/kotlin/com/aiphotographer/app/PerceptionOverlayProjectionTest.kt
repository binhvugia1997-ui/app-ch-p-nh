package com.aiphotographer.app

import com.aiphotographer.model.*
import com.aiphotographer.perception.PerceptionSnapshot
import org.junit.Assert.*
import org.junit.Test

class PerceptionOverlayProjectionTest {
    private fun image(frame: FrameGeometry, poseStale: Boolean = false, faceStale: Boolean = false,
                      subjects: List<Subject> = listOf(subject())) = ImageSnapshot(
        FrameAnalysis(1_000, frame, DeviceState(DeviceTier.MEDIUM), subjects,
            AnalysisQuality(mapOf("pose" to SourceQuality(0, stale = poseStale),
                "face" to SourceQuality(0, stale = faceStale)), 0.0)), null)

    private fun subject(): Subject {
        val pose = PoseLandmarks.ids.map { Landmark(it, .25, .5, visibility = 1.0, presence = 1.0) }
        val face = FaceObservation(listOf(Landmark("face_0", .75, .5)), BoundingBox(.7, .4, .8, .6))
        return Subject(1, pose, BoundingBox(.2, .2, .8, .9), 1.0, ShotType.FULL_BODY, face = face)
    }

    @Test fun all33PosePointsAndFaceMirrorOnlyForFrontCamera() {
        for ((facing, expectedPoseX, expectedFaceX) in listOf(
            Triple(CameraFacing.BACK, 200.0, 600.0), Triple(CameraFacing.FRONT, 600.0, 200.0))) {
            val frame = FrameGeometry(640, 480, 0, facing == CameraFacing.FRONT, facing, AnalysisResolution.R480P)
            val projected = PerceptionOverlayProjection.project(image(frame), null, 800, 600)
            assertEquals(33, projected.pose.size)
            assertEquals(expectedPoseX, projected.pose.getValue("left_shoulder").x, 1e-6)
            assertEquals(300.0, projected.pose.getValue("left_shoulder").y, 1e-6)
            assertEquals(expectedFaceX, projected.face.single().x, 1e-6)
        }
    }

    @Test fun portraitAndCroppedPreviewUseDeliveredUprightGeometry() {
        val portrait = FrameGeometry(480, 640, 90, false, CameraFacing.BACK, AnalysisResolution.R480P)
        val portraitPoint = PerceptionOverlayProjection.project(image(portrait), null, 600, 800).pose.getValue("nose")
        assertEquals(150.0, portraitPoint.x, 1e-6)
        assertEquals(400.0, portraitPoint.y, 1e-6)
        val cropped = FrameGeometry(640, 480, 0, false, CameraFacing.BACK, AnalysisResolution.R720P)
        val croppedPoint = PerceptionOverlayProjection.project(image(cropped), null, 1_000, 1_000).pose.getValue("nose")
        assertEquals(166.6666667, croppedPoint.x, 1e-6)
        assertEquals(500.0, croppedPoint.y, 1e-6)
    }

    @Test fun frontWidePreviewMirrorsAfterCropInBothOrientations() {
        data class Case(val frame: FrameGeometry, val width: Int, val height: Int,
                        val poseX: Double, val faceX: Double)
        for (case in listOf(
            Case(FrameGeometry(1280, 720, 0, true, CameraFacing.FRONT, AnalysisResolution.R720P),
                1000, 600, 766.6666667, 233.3333333),
            Case(FrameGeometry(720, 1280, 90, true, CameraFacing.FRONT, AnalysisResolution.R720P),
                600, 1000, 450.0, 150.0)
        )) {
            val projected = PerceptionOverlayProjection.project(image(case.frame), null, case.width, case.height)
            assertEquals(case.poseX, projected.pose.getValue("nose").x, 1e-6)
            assertEquals(case.faceX, projected.face.single().x, 1e-6)
        }
    }

    @Test fun stalePoseAndFaceAreHidden() {
        val frame = FrameGeometry(640, 480, 0, false, CameraFacing.BACK, AnalysisResolution.R480P)
        val stale = PerceptionOverlayProjection.project(image(frame, poseStale = true, faceStale = true), null, 800, 600)
        assertTrue(stale.pose.isEmpty())
        assertTrue(stale.face.isEmpty())
    }

    @Test fun faceOnlyDiagnosticRequiresMatchingFreshFrame() {
        val frame = FrameGeometry(640, 480, 0, false, CameraFacing.BACK, AnalysisResolution.R480P)
        val image = image(frame, poseStale = true, subjects = emptyList())
        fun diagnostic(timestamp: Long, geometry: FrameGeometry = frame) =
            PerceptionSnapshot(timestamp, geometry, DeviceState(DeviceTier.MEDIUM), emptyList(), image.analysis.quality,
                subject().face)
        assertEquals(1, PerceptionOverlayProjection.project(image, diagnostic(1_000), 800, 600).face.size)
        assertTrue(PerceptionOverlayProjection.project(image, diagnostic(499), 800, 600).face.isEmpty())
        assertTrue(PerceptionOverlayProjection.project(image, diagnostic(1_001), 800, 600).face.isEmpty())
        assertTrue(PerceptionOverlayProjection.project(image, diagnostic(1_000,
            frame.copy(width = 480, height = 640)), 800, 600).face.isEmpty())
    }

    @Test fun unassociatedFaceIsNotDrawnOnPoseSubject() {
        val frame = FrameGeometry(640, 480, 0, false, CameraFacing.BACK, AnalysisResolution.R480P)
        val withoutAssociatedFace = subject().copy(face = null)
        val image = image(frame, subjects = listOf(withoutAssociatedFace))
        val diagnostic = PerceptionSnapshot(1_000, frame, DeviceState(DeviceTier.MEDIUM),
            listOf(withoutAssociatedFace), image.analysis.quality, subject().face)
        val projected = PerceptionOverlayProjection.project(image, diagnostic, 800, 600)
        assertEquals(33, projected.pose.size)
        assertTrue(projected.face.isEmpty())
    }
}
