package com.aiphotographer.app

import android.graphics.Rect
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiphotographer.camera.FrameRouter
import com.aiphotographer.model.*
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrameRouterInstrumentedTest {
    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> proxy(crossinline answer: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ -> answer(method.name) } as T

    @Test fun everyProxyClosesOnSuccessSkipAndFailure() {
        val router = FrameRouter(CameraFacing.BACK, AnalysisResolution.R480P, { DeviceState(DeviceTier.MEDIUM) }, false)
        var closes = 0
        val plane = proxy<ImageProxy.PlaneProxy> { name -> when (name) {
            "getBuffer" -> ByteBuffer.wrap(byteArrayOf(10, 20, 30, 40))
            "getRowStride" -> 2
            "getPixelStride" -> 1
            else -> null
        } }
        val info = proxy<ImageInfo> { name -> when (name) {
            "getRotationDegrees" -> 0
            "getTimestamp" -> 1_000_000L
            else -> null
        } }
        fun image(broken: Boolean) = proxy<ImageProxy> { name -> when (name) {
            "getCropRect" -> Rect(0, 0, 2, 2)
            "getWidth", "getHeight" -> 2
            "getImageInfo" -> info
            "getPlanes" -> if (broken) emptyArray<ImageProxy.PlaneProxy>() else arrayOf(plane)
            "close" -> { closes++; null }
            else -> null
        } }
        router.analyze(image(false))
        assertEquals(1, closes)
        assertTrue(router.snapshot.value!!.analysis.subjects.isEmpty())
        assertEquals(64 * 64, router.snapshot.value!!.luma!!.values.size)
        router.analyze(image(false))
        assertEquals(2, closes)
        router.resetSessionWindow()
        router.analyze(image(true))
        assertEquals(3, closes)
        router.analyze(image(false))
        assertEquals(4, closes)
    }
}
