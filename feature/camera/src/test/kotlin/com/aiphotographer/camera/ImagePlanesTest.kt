package com.aiphotographer.camera

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class ImagePlanesTest {
    @Test fun invalidCropStrideBufferAndRotationFailBeforeOutputMutation() {
        val plane = Plane(ByteBuffer.wrap(byteArrayOf(16,16,16,16)),2,1)
        assertThrows(IllegalArgumentException::class.java) { Crop(-1,0,2,2) }
        assertThrows(IllegalArgumentException::class.java) { Crop(0,0,0,2) }
        assertThrows(IllegalArgumentException::class.java) { Plane(plane.buffer,0,1) }
        assertThrows(IllegalArgumentException::class.java) { ImagePlanes.luma(plane,Crop(0,0,2,2),45,2) }
        assertThrows(IllegalArgumentException::class.java) { ImagePlanes.luma(plane,Crop(0,0,2,2),0,0) }
        val output = IntArray(4) { 77 }
        assertThrows(IllegalArgumentException::class.java) { ImagePlanes.rgb(plane,plane,plane,Crop(2,0,2,2),0,output) }
        assertArrayEquals(IntArray(4) { 77 },output)
    }
    @Test fun lumaHonorsBufferPositionPaddingPixelStrideAndRotation() {
        val buffer = ByteBuffer.wrap(byteArrayOf(99, 10, 0, 20, 0, 0, 0, 30, 0, 40, 0, 0, 0))
        buffer.position(1)
        val plane = Plane(buffer, 6, 2)
        assertEquals(listOf(30, 10, 40, 20), ImagePlanes.luma(plane, Crop(0, 0, 2, 2), 90, 2))
        assertEquals(1, buffer.position())
    }
    @Test fun neutralChromaConvertsBlackAndWhiteAndRotates() {
        val y = Plane(ByteBuffer.wrap(byteArrayOf(16, 235.toByte(), 16, 235.toByte())), 2, 1)
        val uv = Plane(ByteBuffer.wrap(byteArrayOf(128.toByte())), 1, 1)
        val out = IntArray(4)
        ImagePlanes.rgb(y, uv, uv, Crop(0, 0, 2, 2), 90, out)
        assertArrayEquals(intArrayOf(0xff000000.toInt(), 0xff000000.toInt(), 0xffffffff.toInt(), 0xffffffff.toInt()), out)
    }
    @Test fun cropUsesSensorCoordinates() {
        val y = Plane(ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)), 4, 1)
        assertEquals(listOf(3, 4, 7, 8), ImagePlanes.luma(y, Crop(2, 0, 2, 2), 0, 2))
    }
    @Test fun rgbCropHonorsPlaneOffsetsPaddingAndAllFourRotationsWithoutMirroring() {
        val yBuffer = ByteBuffer.allocate(2 + 6 * 4).apply { position(2) }
        // Distinct upright crop corners: black, white, dark gray, light gray.
        val corners = listOf(16, 235, 81, 145)
        corners.forEachIndexed { i, value -> yBuffer.put(2 + (1 + i / 2) * 6 + 1 + i % 2, value.toByte()) }
        val chroma = ByteBuffer.allocate(1 + 6 * 2).apply { position(1) }
        for (row in 0..1) for (col in 0..1) chroma.put(1 + row * 6 + col * 2, 128.toByte())
        val gray = intArrayOf(0xff000000.toInt(), 0xffffffff.toInt(), 0xff4c4c4c.toInt(), 0xff969696.toInt())
        val arrangements = mapOf(0 to listOf(0, 1, 2, 3), 90 to listOf(2, 0, 3, 1),
            180 to listOf(3, 2, 1, 0), 270 to listOf(1, 3, 0, 2))
        arrangements.forEach { (rotation, order) ->
            val output = IntArray(4)
            ImagePlanes.rgb(Plane(yBuffer, 6, 1), Plane(chroma, 6, 2), Plane(chroma, 6, 2), Crop(1, 1, 2, 2), rotation, output)
            assertArrayEquals(order.map { gray[it] }.toIntArray(), output)
        }
        assertEquals(2, yBuffer.position()); assertEquals(1, chroma.position())
    }
    @Test fun optimizedRgbMatchesReferenceAcrossOddCropsStridesAndRotations() {
        val random = java.util.Random(123)
        for (pixelStride in 1..2) for (left in 0..2) for (top in 0..2) {
            fun plane(stride: Int): Plane {
                val bytes = ByteArray(256).also(random::nextBytes)
                return Plane(ByteBuffer.wrap(bytes).apply { position(3) }, stride, pixelStride)
            }
            val y = plane(24); val u = plane(16); val v = plane(16)
            val crop = Crop(left, top, 5, 3)
            for (rotation in listOf(0,90,180,270)) {
                val width = if (rotation == 90 || rotation == 270) 3 else 5
                val expected = IntArray(15)
                for (sy in 0..2) for (sx in 0..4) {
                    val yy = (y.at(left+sx,top+sy)-16).coerceAtLeast(0)
                    val uu = u.at((left+sx)/2,(top+sy)/2)-128
                    val vv = v.at((left+sx)/2,(top+sy)/2)-128
                    val r = ((298*yy+409*vv+128) shr 8).coerceIn(0,255)
                    val g = ((298*yy-100*uu-208*vv+128) shr 8).coerceIn(0,255)
                    val b = ((298*yy+516*uu+128) shr 8).coerceIn(0,255)
                    val dx = when(rotation) { 90 -> 2-sy; 180 -> 4-sx; 270 -> sy; else -> sx }
                    val dy = when(rotation) { 90 -> sx; 180 -> 2-sy; 270 -> 4-sx; else -> sy }
                    expected[dy*width+dx] = (255 shl 24) or (r shl 16) or (g shl 8) or b
                }
                val actual = IntArray(15)
                ImagePlanes.rgb(y,u,v,crop,rotation,actual)
                assertArrayEquals(expected,actual)
                assertEquals(3,y.buffer.position())
            }
        }
    }

}
