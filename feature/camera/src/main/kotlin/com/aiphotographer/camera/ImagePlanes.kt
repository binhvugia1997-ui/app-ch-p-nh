package com.aiphotographer.camera

import com.aiphotographer.geometry.Coordinates
import com.aiphotographer.geometry.Point
import java.nio.ByteBuffer

data class Plane(val buffer: ByteBuffer, val rowStride: Int, val pixelStride: Int) {
    private val offset = buffer.position()
    init { require(rowStride > 0 && pixelStride > 0) }
    fun requireRegion(right: Int, bottom: Int) {
        require(right >= 0 && bottom >= 0 && offset.toLong() + bottom.toLong() * rowStride + right.toLong() * pixelStride < buffer.limit())
    }
    fun at(x: Int, y: Int): Int = buffer.get(offset + y * rowStride + x * pixelStride).toInt() and 255
}
data class Crop(val left: Int, val top: Int, val width: Int, val height: Int) {
    init { require(left >= 0 && top >= 0 && width > 0 && height > 0); Math.addExact(left, width); Math.addExact(top, height) }
}

object ImagePlanes {
    fun luma(y: Plane, crop: Crop, rotation: Int, gridSize: Int = 64): List<Int> {
        require(gridSize > 0)
        Coordinates.uprightSize(crop.width, crop.height, rotation)
        y.requireRegion(crop.left + crop.width - 1, crop.top + crop.height - 1)
        return List(Math.multiplyExact(gridSize, gridSize)) { index ->
            val p = Coordinates.analysisToSensor(Point((index % gridSize + .5) / gridSize, (index / gridSize + .5) / gridSize), rotation)
            y.at(crop.left + (p.x * crop.width).toInt().coerceIn(0, crop.width - 1),
                crop.top + (p.y * crop.height).toInt().coerceIn(0, crop.height - 1))
        }
    }

    /** Reusable output, rotated in the conversion pass; never JPEG-encodes analysis frames. */
    fun rgb(y: Plane, u: Plane, v: Plane, crop: Crop, rotation: Int, output: IntArray) {
        val (width, height) = Coordinates.uprightSize(crop.width, crop.height, rotation)
        require(output.size >= Math.multiplyExact(width, height))
        y.requireRegion(crop.left + crop.width - 1, crop.top + crop.height - 1)
        u.requireRegion((crop.left + crop.width - 1) / 2, (crop.top + crop.height - 1) / 2)
        v.requireRegion((crop.left + crop.width - 1) / 2, (crop.top + crop.height - 1) / 2)
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
