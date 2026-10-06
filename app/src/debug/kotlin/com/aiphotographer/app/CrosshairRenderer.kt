package com.aiphotographer.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.aiphotographer.geometry.Coordinates
import com.aiphotographer.geometry.Point
import com.aiphotographer.model.FrameGeometry

internal object CrosshairRenderer {
    private val paint = Paint().apply { color = Color.CYAN; strokeWidth = 3f }
    fun draw(canvas: Canvas, width: Int, height: Int, frame: FrameGeometry) {
        val p = Coordinates.analysisToPreview(Point(.25, .5), frame.width, frame.height, width, height, frame.isMirrored) ?: return
        canvas.drawLine(p.x.toFloat() - 16, p.y.toFloat(), p.x.toFloat() + 16, p.y.toFloat(), paint)
        canvas.drawLine(p.x.toFloat(), p.y.toFloat() - 16, p.x.toFloat(), p.y.toFloat() + 16, paint)
    }
}
