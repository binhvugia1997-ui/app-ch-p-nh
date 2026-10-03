package com.aiphotographer.geometry

import org.junit.Assert.*
import org.junit.Test

class CoordinatesTest {
    private fun point(expected: Point, actual: Point?) {
        assertNotNull(actual)
        assertEquals(expected.x, actual!!.x, 1e-6)
        assertEquals(expected.y, actual.y, 1e-6)
    }
    @Test fun rotationRoundTrip() {
        for (r in listOf(0, 90, 180, 270)) for (p in listOf(Point(0.0, 0.0), Point(.2, .7), Point(1.0, 1.0))) {
            point(p, Coordinates.analysisToSensor(Coordinates.sensorToAnalysis(p, r), r))
        }
        point(Point(.3, .2), Coordinates.sensorToAnalysis(Point(.2, .7), 90))
        point(Point(.8, .3), Coordinates.sensorToAnalysis(Point(.2, .7), 180))
        point(Point(.7, .8), Coordinates.sensorToAnalysis(Point(.2, .7), 270))
    }
    @Test fun aspectFillKnownPoints() {
        point(Point(800.0, 450.0), Coordinates.analysisToPreview(Point(.5, .5), 640, 480, 1600, 900, false))
        point(Point(0.0, -150.0), Coordinates.analysisToPreview(Point(0.0, 0.0), 640, 480, 1600, 900, false))
        point(Point(-388.8888889, 0.0), Coordinates.analysisToPreview(Point(0.0, 0.0), 1280, 720, 1000, 1000, false))
        point(Point(320.0, 240.0), Coordinates.analysisToPreview(Point(.5, .5), 640, 480, 640, 480, false))
        point(Point(240.0, 320.0), Coordinates.analysisToPreview(Point(.5, .5), 480, 640, 480, 640, false))
    }
    @Test fun frontMirrorChangesSpatialSideOnly() {
        val anatomicalLeftShoulder = Point(.75, .4)
        point(Point(480.0, 192.0), Coordinates.analysisToPreview(anatomicalLeftShoulder, 640, 480, 640, 480, false))
        point(Point(160.0, 192.0), Coordinates.analysisToPreview(anatomicalLeftShoulder, 640, 480, 640, 480, true))
        assertEquals(.75, anatomicalLeftShoulder.x, 0.0)
    }
    @Test fun allCameraOrientationAspectCombinations() {
        for (r in listOf(0, 90, 180, 270)) for (mirror in listOf(false, true)) {
            val (w, h) = Coordinates.uprightSize(640, 480, r)
            for ((vw, vh) in listOf(400 to 300, 1600 to 900, 300 to 400, 900 to 1600)) {
                point(Point(vw / 2.0, vh / 2.0), Coordinates.analysisToPreview(Point(.5, .5), w, h, vw, vh, mirror))
            }
        }
    }
    @Test fun degenerateSizesAbstain() {
        assertNull(Coordinates.analysisToPreview(Point(.5, .5), 0, 480, 640, 480, false))
        assertNull(Coordinates.analysisToPreview(Point(.5, .5), 640, 480, 0, 480, false))
    }
}
