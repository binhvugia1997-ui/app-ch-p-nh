package com.aiphotographer.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiphotographer.geometry.Coordinates
import com.aiphotographer.geometry.Point
import com.aiphotographer.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Canvas fixture check; physical feature alignment still requires the documented camera test. */
@RunWith(AndroidJUnit4::class)
class CrosshairInstrumentedTest {
    @Test fun crosshairPixelsForBothCamerasOrientationsAndAspects() {
        for (front in listOf(false, true)) for (portrait in listOf(false, true)) for (wide in listOf(false, true)) {
            val w = if (portrait) 480 else 640
            val h = if (portrait) 640 else 480
            val vw = if (portrait) 300 else if (wide) 533 else 400
            val vh = if (portrait) if (wide) 533 else 400 else 300
            val bitmap = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            CrosshairRenderer.draw(canvas, vw, vh, FrameGeometry(w, h, if (portrait) 90 else 0,
                front, if (front) CameraFacing.FRONT else CameraFacing.BACK, AnalysisResolution.R480P))
            val scale = maxOf(vw.toDouble() / w, vh.toDouble() / h)
            val expectedX = ((if (front) .75 else .25) * w * scale + (vw - w * scale) / 2).toInt()
            assertEquals(Color.CYAN, bitmap.getPixel(expectedX, vh / 2))
            bitmap.recycle()
        }
    }
}
