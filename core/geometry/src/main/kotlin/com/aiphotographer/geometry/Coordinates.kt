package com.aiphotographer.geometry

import kotlin.math.max

data class Point(val x: Double, val y: Double)

object Coordinates {
    fun sensorToAnalysis(p: Point, rotation: Int): Point = when (rotation) {
        90 -> Point(1 - p.y, p.x)
        180 -> Point(1 - p.x, 1 - p.y)
        270 -> Point(p.y, 1 - p.x)
        else -> p
    }
    fun analysisToSensor(p: Point, rotation: Int): Point = sensorToAnalysis(p, (360 - rotation) % 360)
    fun uprightSize(width: Int, height: Int, rotation: Int): Pair<Int, Int> =
        if (rotation == 90 || rotation == 270) height to width else width to height

    /** Input is upright, unmirrored ANALYSIS; rotation has already been applied exactly once. */
    fun analysisToPreview(p: Point, width: Int, height: Int, viewWidth: Int, viewHeight: Int, mirrored: Boolean): Point? {
        if (width <= 0 || height <= 0 || viewWidth <= 0 || viewHeight <= 0) return null
        val scale = max(viewWidth.toDouble() / width, viewHeight.toDouble() / height)
        val x = (if (mirrored) 1 - p.x else p.x) * width * scale
        return Point(x + (viewWidth - width * scale) / 2, p.y * height * scale + (viewHeight - height * scale) / 2)
    }
}
