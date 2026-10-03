package com.aiphotographer.camera

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class ImagePlanesTest {
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
}
