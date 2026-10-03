package com.aiphotographer.camera

import com.aiphotographer.geometry.Coordinates
import com.aiphotographer.geometry.Point
import java.nio.ByteBuffer

data class Plane(val buffer: ByteBuffer, val rowStride: Int, val pixelStride: Int) {
    fun at(x: Int, y: Int): Int = buffer.get(buffer.position() + y * rowStride + x * pixelStride).toInt() and 255
}
data class Crop(val left: Int, val top: Int, val width: Int, val height: Int)

object ImagePlanes {
    fun luma(y: Plane, crop: Crop, rotation: Int, gridSize: Int = 64): List<Int> =
        List(gridSize * gridSize) { index ->
            val p = Coordinates.analysisToSensor(Point((index % gridSize + .5) / gridSize, (index / gridSize + .5) / gridSize), rotation)
            y.at(crop.left + (p.x * crop.width).toInt().coerceIn(0, crop.width - 1),
                crop.top + (p.y * crop.height).toInt().coerceIn(0, crop.height - 1))
        }

    /** Reusable output, rotated in the conversion pass; never JPEG-encodes analysis frames. */
    fun rgb(y: Plane, u: Plane, v: Plane, crop: Crop, rotation: Int, output: IntArray) {
        val (width, height) = Coordinates.uprightSize(crop.width, crop.height, rotation)
        require(output.size >= width * height)
        for (sy in 0 until crop.height) for (sx in 0 until crop.width) {
            val x = crop.left + sx
            val py = crop.top + sy
            val yy = (y.at(x, py) - 16).coerceAtLeast(0)
            val uu = u.at(x / 2, py / 2) - 128
            val vv = v.at(x / 2, py / 2) - 128
            val r = ((298 * yy + 409 * vv + 128) shr 8).coerceIn(0, 255)
            val g = ((298 * yy - 100 * uu - 208 * vv + 128) shr 8).coerceIn(0, 255)
            val b = ((298 * yy + 516 * uu + 128) shr 8).coerceIn(0, 255)
            val dx: Int
            val dy: Int
            when (rotation) {
                90 -> { dx = crop.height - 1 - sy; dy = sx }
                180 -> { dx = crop.width - 1 - sx; dy = crop.height - 1 - sy }
                270 -> { dx = sy; dy = crop.width - 1 - sx }
                else -> { dx = sx; dy = sy }
            }
            output[dy * width + dx] = (255 shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
