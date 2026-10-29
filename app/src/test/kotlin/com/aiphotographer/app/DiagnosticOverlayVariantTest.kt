package com.aiphotographer.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for C1: raw Pose/Face landmarks are a development instrument and must be
 * unreachable from non-debug builds.
 *
 * The gating mechanism is the project's existing source-set convention (a class declared per build
 * type), not a runtime `BuildConfig` check, so the proof has to be structural: the drawing code
 * must not exist in `main`/`profile`/`release`, and the call-site must name the diagnostic
 * composable explicitly. Compiling `release`/`profile` then only ever links the no-op stub.
 */
class DiagnosticOverlayVariantTest {
    private val moduleDir: File = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app/src").isDirectory }
        ?: error("Cannot locate the app module from ${System.getProperty("user.dir")}")

    private fun source(variant: String, name: String) =
        File(moduleDir, "app/src/$variant/kotlin/com/aiphotographer/app/$name")

    private fun drawables(root: File): List<File> = root.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    @Test
    fun mainSourcesDrawNoLandmarks() {
        val main = File(moduleDir, "app/src/main/kotlin")
        val offenders = drawables(main).filter { file ->
            val text = file.readText()
            text.contains("PoseLandmarks.bones") || text.contains("drawLine(") ||
                text.contains("drawCircle(") || text.contains("faceDiagnostic) { drawCircle")
        }
        assertTrue("Landmark drawing must not live in main: $offenders", offenders.isEmpty())
    }

    @Test
    fun debugVariantOwnsTheDiagnosticOverlay() {
        val debug = source("debug", "DiagnosticLandmarkOverlay.kt")
        assertTrue("debug overlay missing at $debug", debug.isFile)
        val text = debug.readText()
        assertTrue(text.contains("fun DiagnosticLandmarkOverlay(camera: CameraSession)"))
        assertTrue("debug overlay must still draw the Pose skeleton", text.contains("PoseLandmarks.bones"))
        assertTrue("debug overlay must still draw Face points", text.contains("projected.face.forEach"))
    }

    @Test
    fun profileAndReleaseProvideNoOpStubs() {
        for (variant in listOf("profile", "release")) {
            val stub = source(variant, "DiagnosticLandmarkOverlay.kt")
            assertTrue("$variant must declare the diagnostic overlay stub", stub.isFile)
            val text = stub.readText()
            assertTrue(text.contains("fun DiagnosticLandmarkOverlay(camera: CameraSession) = Unit"))
            for (forbidden in listOf("PoseLandmarks", "Canvas", "Color.", "drawLine", "drawCircle", "CrosshairRenderer")) {
                assertFalse("$variant stub must not reference $forbidden", text.contains(forbidden))
            }
        }
    }

    @Test
    fun callSiteUsesTheDiagnosticNameOnly() {
        val activity = File(moduleDir, "app/src/main/kotlin/com/aiphotographer/app/MainActivity.kt").readText()
        assertTrue(activity.contains("DiagnosticLandmarkOverlay(camera)"))
        assertFalse("Ambiguous overlay name reappeared: gate it per build type", activity.contains("PerceptionOverlay(camera)"))
    }
}
