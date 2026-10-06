package com.aiphotographer.camera

import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import com.aiphotographer.model.*
import kotlin.math.atan2

class DeviceMonitor(context: Context) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    @Volatile private var roll: Double? = null
    @Volatile var gravity: List<Float>? = null
        private set
    private val activity = context.getSystemService(ActivityManager::class.java)
    val capability: CapabilityReport
    init {
        val memory = ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
        val emulator = Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("Emulator") || Build.MODEL.contains("sdk_gphone") || Build.HARDWARE in listOf("goldfish", "ranchu")
        val gl = activity.deviceConfigurationInfo.reqGlEsVersion
        val tier = if (memory.totalMem <= 4L * 1024 * 1024 * 1024) DeviceTier.LOW else DeviceTier.MEDIUM
        capability = CapabilityReport(tier, totalRamBytes = memory.totalMem, glEsVersion = "${gl shr 16}.${gl and 0xffff}",
            gpuEligible = gl >= 0x30001 && !emulator, emulator = emulator)
    }
    fun start() {
        sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }
    fun stop() { sensors.unregisterListener(this) }
    fun state(): DeviceState = DeviceState(capability.tier, roll, null, thermal())
    fun thermal(): ThermalStatus = if (Build.VERSION.SDK_INT >= 29) {
        ThermalStatus.entries.getOrElse(power.currentThermalStatus) { ThermalStatus.UNKNOWN }
    } else ThermalStatus.UNKNOWN
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GRAVITY) {
            gravity = event.values.take(3)
            roll = Math.toDegrees(atan2(-event.values[0].toDouble(), event.values[1].toDouble()))
        } else if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val matrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(matrix, event.values)
            if (gravity == null) roll = -Math.toDegrees(SensorManager.getOrientation(matrix, FloatArray(3))[2].toDouble())
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
