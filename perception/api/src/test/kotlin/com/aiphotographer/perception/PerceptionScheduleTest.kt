package com.aiphotographer.perception

import com.aiphotographer.model.*
import org.junit.Assert.*
import org.junit.Test

class PerceptionScheduleTest {
    @Test fun orderedDegradationAndHystereticRecovery() {
        val schedule = PerceptionSchedule(DeviceTier.MEDIUM)
        schedule.observe(0, ThermalStatus.MODERATE, 0.0, 10.0, null)
        schedule.observe(2999, ThermalStatus.MODERATE, 0.0, 10.0, null)
        assertEquals(0, schedule.level)
        schedule.observe(3000, ThermalStatus.MODERATE, 0.0, 10.0, null)
        assertEquals(3, schedule.level); assertEquals(500L, schedule.facePeriodMs)
        schedule.observe(6000, ThermalStatus.NONE, .2, 10.0, null)
        assertTrue(schedule.useLite)
        schedule.observe(9000, ThermalStatus.NONE, 0.0, 50.0, null)
        assertEquals(125.0, schedule.posePeriodMs, 0.0)
        schedule.observe(12000, ThermalStatus.NONE, .2, 10.0, null)
        assertTrue(schedule.recommend480p)
        schedule.observe(13000, ThermalStatus.NONE, 0.0, 10.0, null)
        schedule.observe(27999, ThermalStatus.NONE, 0.0, 10.0, null)
        assertEquals(6, schedule.level)
        schedule.observe(28000, ThermalStatus.NONE, 0.0, 10.0, null)
        assertEquals(5, schedule.level)
    }
    @Test fun staticCadenceAndMotionRecovery() {
        val schedule = PerceptionSchedule(DeviceTier.LOW)
        val a = listOf(Landmark("nose", .5, .5, visibility = 1.0))
        schedule.observe(0, ThermalStatus.UNKNOWN, 0.0, null, a)
        schedule.observe(100, ThermalStatus.UNKNOWN, 0.0, null, a)
        schedule.observe(1100, ThermalStatus.UNKNOWN, 0.0, null, a)
        assertEquals(200.0, schedule.posePeriodMs, 0.0)
        schedule.observe(1200, ThermalStatus.UNKNOWN, 0.0, null, a.map { it.copy(x = .6) })
        assertEquals(100.0, schedule.posePeriodMs, 0.0)
    }
    @Test fun headMatrixIdentityAndInvalidInputs() {
        val identity = FloatArray(16) { if (it % 5 == 0) 1f else 0f }
        val pose = HeadPoseMatrix.decode(identity)!!
        assertEquals(0.0, pose.yawDegrees, 1e-9)
        assertEquals(0.0, pose.pitchDegrees, 1e-9)
        assertEquals(0.0, pose.rollDegrees, 1e-9)
        assertNull(HeadPoseMatrix.decode(FloatArray(16)))
        assertNull(HeadPoseMatrix.decode(floatArrayOf(1f)))
        identity[0] = Float.NaN
        assertNull(HeadPoseMatrix.decode(identity))
    }
    @Test fun headMatrixKnownColumnMajorAxisRotations() {
        val c = .8660254f
        val yaw = HeadPoseMatrix.decode(floatArrayOf(c,0f,-.5f,0f, 0f,1f,0f,0f, .5f,0f,c,0f, 0f,0f,0f,1f))!!
        assertEquals(30.0, yaw.yawDegrees, 1e-4); assertEquals(0.0, yaw.pitchDegrees, 1e-4)
        val pitch = HeadPoseMatrix.decode(floatArrayOf(1f,0f,0f,0f, 0f,c,.5f,0f, 0f,-.5f,c,0f, 0f,0f,0f,1f))!!
        assertEquals(30.0, pitch.pitchDegrees, 1e-4); assertEquals(0.0, pitch.rollDegrees, 1e-4)
        val roll = HeadPoseMatrix.decode(floatArrayOf(c,-.5f,0f,0f, .5f,c,0f,0f, 0f,0f,1f,0f, 0f,0f,0f,1f))!!
        assertEquals(-30.0, roll.rollDegrees, 1e-4); assertEquals(0.0, roll.yawDegrees, 1e-4)
    }
}
